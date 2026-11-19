from dataclasses import dataclass
import httpx
from cryptography.fernet import Fernet
from fastapi import HTTPException


@dataclass(frozen=True)
class ModelConfig:
    key: str
    model: str
    version: int

    @property
    def ready(self):
        return bool(self.key and self.model)


class ModelConfigService:
    def __init__(self, pool, settings):
        self.pool, self.settings = pool, settings
        self.cipher = Fernet(settings.encryption_key.get_secret_value().encode())

    async def snapshot(self):
        async with self.pool.connection() as conn:
            row = await (
                await conn.execute("SELECT * FROM agent_model_config WHERE id=1")
            ).fetchone()
        key = (
            self.cipher.decrypt(row["encrypted_key"].encode()).decode()
            if row["encrypted_key"]
            else self.settings.deepseek_api_key.get_secret_value()
        )
        return ModelConfig(
            key, row["model"] or self.settings.deepseek_model, row["version"]
        )

    async def public(self):
        c = await self.snapshot()
        return {
            "provider": "DeepSeek",
            "base_url": "https://api.deepseek.com",
            "key_configured": bool(c.key),
            "key_masked": (c.key[:7] + "••••••••" + c.key[-4:]) if len(c.key) > 12 else ("••••••••" if c.key else ""),
            "model": c.model,
            "version": c.version,
            "model_ready": c.ready,
        }

    async def save(self, actor, body):
        async with self.pool.connection() as conn, conn.transaction():
            row = await (
                await conn.execute(
                    "SELECT * FROM agent_model_config WHERE id=1 FOR UPDATE"
                )
            ).fetchone()
            if body.version != row["version"]:
                raise HTTPException(409, "配置已更新，请重新加载后保存")
            encrypted = (
                self.cipher.encrypt(
                    body.api_key.get_secret_value().strip().encode()
                ).decode()
                if body.api_key and body.api_key.get_secret_value().strip()
                else row["encrypted_key"]
            )
            await conn.execute(
                "UPDATE agent_model_config SET encrypted_key=%s,model=%s,version=version+1,updated_by=%s,updated_at=now() WHERE id=1",
                (encrypted, body.model.strip(), actor.user_id),
            )
        return await self.public()

    async def models(self):
        c = await self.snapshot()
        if not c.key:
            raise HTTPException(409, "请先保存 API Key，再获取可用模型")
        try:
            async with httpx.AsyncClient(timeout=10, follow_redirects=False) as client:
                response = await client.get(
                    "https://api.deepseek.com/models",
                    headers={"Authorization": "Bearer " + c.key},
                )
            if response.status_code == 401:
                raise HTTPException(400, "API Key 校验失败，请检查后重新保存")
            if response.status_code == 429:
                raise HTTPException(429, "模型平台请求频繁，请稍后再试")
            response.raise_for_status()
            return {
                "items": [
                    {"id": m["id"]}
                    for m in response.json().get("data", [])
                    if isinstance(m.get("id"), str)
                ]
            }
        except HTTPException:
            raise
        except Exception:
            raise HTTPException(
                502, "暂时无法获取 DeepSeek 模型列表，请稍后重试"
            ) from None
