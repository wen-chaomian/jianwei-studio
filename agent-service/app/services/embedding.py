from psycopg.types.json import Jsonb
import math
import httpx
from cryptography.fernet import Fernet
from fastapi import HTTPException
from pydantic import BaseModel, Field, SecretStr
from typing import Literal

REGIONS = {
    'cn-beijing': 'https://dashscope.aliyuncs.com',
    'ap-southeast-1': 'https://dashscope-intl.aliyuncs.com',
}
MODEL = 'text-embedding-v4'
SUPPORTED = {MODEL, 'text-embedding-v3', 'qwen3.7-text-embedding', 'qwen3.7-text-embedding-flash'}
DIMENSIONS = 1024


class SettingsUpdate(BaseModel):
    region: Literal['cn-beijing', 'ap-southeast-1']
    workspace: str = Field(default="", pattern=r"^(?:ws-[a-z0-9-]{1,60})?$")
    model: str | None = Field(default=None, max_length=100)
    api_key: SecretStr | None = Field(default=None, max_length=512)
    version: int = Field(ge=0)


class EmbeddingService:
    def __init__(self, pool, settings):
        self.pool = pool
        self.default_workspace = settings.embedding_workspace
        self.cipher = Fernet(settings.encryption_key.get_secret_value().encode())

    async def configuration(self):
        async with self.pool.connection() as conn:
            row = await (await conn.execute('SELECT * FROM agent_embedding_config WHERE id=1')).fetchone()
        return row

    async def public(self):
        row = await self.configuration()
        workspace = self.default_workspace if row['version']==0 else row['workspace']
        masked = ''
        if row['encrypted_key']:
            secret = self.cipher.decrypt(row['encrypted_key'].encode()).decode()
            masked = secret[:5] + '••••••••' + secret[-4:] if len(secret)>12 else '••••••••'
        return {'version': row['version'], 'region': row['region'], 'workspace':workspace, 'key_masked':masked, 'configured': bool(row['encrypted_key']), 'model': row['model'], 'pending_model': (row['pending'] or {}).get('model'), 'rebuild_error': row['rebuild_error']}

    async def save(self, body):
        key = body.api_key.get_secret_value().strip() if body.api_key else ''
        encrypted = self.cipher.encrypt(key.encode()).decode() if key else None
        row = await self.configuration()
        target = dict(region=body.region, workspace=body.workspace, model=body.model or row['model'],
                      encrypted_key=encrypted or row['encrypted_key'])
        if not target['encrypted_key']:
            raise HTTPException(409, '首次保存需填写密钥')
        if target['model'] not in SUPPORTED:
            raise HTTPException(400, '请选择支持的文本向量模型')
        if target['model'] != row['model']:
            await self.vectors(['验证模型连接'], target)
        async with self.pool.connection() as conn, conn.transaction():
            current = await (await conn.execute(
                'SELECT * FROM agent_embedding_config WHERE id=1 FOR UPDATE')).fetchone()
            if current['version'] != body.version:
                raise HTTPException(409, '配置已更新，请重新加载')
            # Existing chunks retain their original model until the shadow index is complete.
            await conn.execute('DELETE FROM agent_embedding_stage')
            rebuild = target['model'] != current['model'] and current['encrypted_key']
            if rebuild:
                await conn.execute('UPDATE agent_embedding_config SET pending=%s, rebuild_error=NULL, version=version+1 WHERE id=1',
                                   (Jsonb(target),))
            else:
                await conn.execute('UPDATE agent_embedding_config SET model=%(model)s,region=%(region)s,workspace=%(workspace)s,'
                                   'encrypted_key=%(encrypted_key)s,pending=NULL,rebuild_error=NULL,version=version+1 WHERE id=1',target)
        return await self.public()

    def endpoint(self, row):
        if not row['encrypted_key']:
            raise HTTPException(409, '请先保存百炼密钥')
        key = self.cipher.decrypt(row['encrypted_key'].encode()).decode()
        host = (f"https://{row['workspace']}.{row['region']}.maas.aliyuncs.com"
                if row['workspace'] else REGIONS[row['region']])
        return host, {'Authorization': 'Bearer ' + key}

    async def models(self):
        host, headers = self.endpoint(await self.configuration())
        found = set()
        async with httpx.AsyncClient(timeout=10) as client:
            page = 1
            while True:
                response = await client.get(host + '/api/v1/models', headers=headers,
                    params={'capabilities':'TR', 'page_no':page, 'page_size':100})
                if response.status_code != 200:
                    raise HTTPException(502, '百炼模型列表获取失败，请检查地域、业务空间和密钥')
                output = response.json().get('output') or {}
                rows = output.get('models', [])
                found.update(item['model'] for item in rows if item.get('model') in SUPPORTED)
                if not rows or page * 100 >= output.get('total', len(rows)):
                    break
                page += 1
        return {'items': [{'id': name} for name in sorted(found)]}

    async def vectors(self, texts, row=None):
        if row is None:
            row = await self.configuration()
        host, headers = self.endpoint(row)
        endpoint = host + '/compatible-mode/v1/embeddings'
        vectors = []
        async with httpx.AsyncClient(timeout=15, follow_redirects=False) as client:
            for start in range(0, len(texts), 10):
                batch = texts[start:start+10]
                response = await client.post(endpoint, headers=headers,
                    json={'model': row['model'], 'dimensions': DIMENSIONS, 'input': batch, 'encoding_format':'float'})
                if response.status_code in (401, 403):
                    raise HTTPException(400, '请检查百炼密钥、开通地域和模型权限')
                if response.status_code != 200:
                    raise HTTPException(502, '百炼未能生成向量，请检查模型权限或稍后重试')
                rows = sorted(response.json()['data'], key=lambda item: item['index'])
                if [r['index'] for r in rows] != list(range(len(batch))):
                    raise ValueError('invalid embedding response')
                for item in rows:
                    vector = item['embedding']
                    if len(vector) != DIMENSIONS or not all(math.isfinite(x) for x in vector) or not any(vector):
                        raise ValueError('invalid embedding vector')
                    vectors.append(vector)
        return vectors
