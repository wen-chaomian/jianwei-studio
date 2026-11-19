from unittest.mock import AsyncMock
from uuid import uuid4
import pytest
from pydantic import SecretStr
from app.services.embedding import SettingsUpdate
from app.services.knowledge import passages
from app.messaging.rocketmq import RocketTransport


async def test_embedding_config_separate_encrypted_and_scoped(env):
    service = env.app.state.embeddings
    await service.save(SettingsUpdate(api_key=SecretStr('fixture-key'),region='cn-beijing',version=0))
    public = await service.public()
    assert public['configured'] and 'fixture-key' not in str(public)
    assert not (await env.configs.public())['key_configured']
    assert 'fixture-key' not in (await service.configuration())['encrypted_key']
    await service.save(SettingsUpdate(region='ap-southeast-1',version=1))
    with pytest.raises(Exception):
        await service.save(SettingsUpdate(region='cn-beijing',version=1))
    for portal,role in [('staff','PHOTOGRAPHER'),('customer','CUSTOMER')]:
        response=await env.client.get('/internal/v1/knowledge/config',headers={
            'X-Agent-Token':env.settings.internal_token.get_secret_value(),
            'X-Actor-Id':'1','X-Actor-Portal':portal,'X-Actor-Role':role})
        assert response.status_code==403


async def test_index_real_pgvector_with_version_filters_and_redelivery(env):
    index=env.app.state.knowledge
    def vectors(texts, config):
        return [[1.,0.]+[0.]*1022 if '宠物' in s else [0.,1.]+[0.]*1022 for s in texts]
    index.embeddings.vectors=AsyncMock(side_effect=vectors)
    job=dict(articleId=1,version=1,title='宠物',content='先与工作室确认',status='PUBLISHED')
    await index.apply(job)
    await index.apply(job)
    assert index.embeddings.vectors.await_count==1
    await index.apply(dict(job,articleId=2,title='内部须知'))
    hits=await index.search('宠物',[{'id':1,'version':1}])
    assert len(hits)==1 and hits[0]['article_id']==1
    assert not await index.search('宠物',[{'id':2,'version':1}])
    await index.apply(dict(job,version=2,content='新版宠物政策'))
    assert (await index.search('宠物',[{'id':1,'version':1}]))[0]['version']==1
    await index.apply(dict(job,version=3,status='DISABLED'))
    assert not await index.search('宠物',[{'id':1,'version':2}])


def test_paragraph_chunk_lengths():
    chunks=passages('标题','a'*1700+'\n下一段')
    assert len(chunks)>=3 and all(len(c)<=803 for c in chunks)
    assert ''.join(c.split('\n',1)[1].replace('\n','') for c in chunks)=='a'*1700+'下一段'


async def test_mq_index_command_waits_for_config_and_signs_result(env):
    transport=RocketTransport(env.settings,env.chat,env.app.state.knowledge)
    sent=[]
    from types import SimpleNamespace
    transport.producer=SimpleNamespace(send=sent.append)
    job=dict(action='index_knowledge',articleId=3,version=1,jobId=str(uuid4()),status='PUBLISHED',title='说明',content='内容')
    assert transport.decode(transport.envelope(job))==job
    await transport.handle(job)
    import json
    result=json.loads(json.loads(sent[0].body)['body'])
    assert result['state']=='WAIT_CONFIG' and result['jobId']==job['jobId']

async def test_citations_only_include_articles_named_in_final_answer(env):
    from types import SimpleNamespace
    from app.tools.business import BusinessSession
    from app.schemas.chat import Actor,NewMessage
    user=Actor(user_id=17,portal='customer',role='CUSTOMER')
    conv=await env.repo.create(user,uuid4())
    run,_=await env.repo.start(user,conv['id'],NewMessage(request_id=uuid4(),content='宠物'),120,mq=True)
    session=BusinessSession(None,[],env.repo,user,run['id'])
    session.knowledge_links=[{'url':'/customer/notices/3/1','label':'宠物须知'},
                             {'url':'/customer/notices/5/1','label':'如何停车'}]
    await session.finalize_sources('请提前确认，来源《宠物须知》。')
    row=await env.repo.run(user,run['id'])
    assert row['result_links']==session.knowledge_links[:1]


async def test_shadow_index_survives_failure_edits_and_atomic_promotion(env):
    from app.services.embedding_rebuild import EmbeddingRebuild
    service = env.app.state.embeddings
    index = env.app.state.knowledge
    worker = EmbeddingRebuild(env.pool, service)
    old, new = 'text-embedding-v4', 'text-embedding-v3'
    broken = False
    async def encode(texts, config):
        if broken and config['model'] == new:
            raise RuntimeError('temporary outage')
        coords = [1.,0.] if config['model'] == old else [0.,1.]
        return [coords + [0.]*1022 for _ in texts]
    service.vectors = encode
    await service.save(SettingsUpdate(region='cn-beijing',api_key='test-original-key',version=0))
    job = dict(articleId=1,version=1,title='标题',content='原文',status='PUBLISHED')
    for article in range(1,13):
        await index.apply(dict(job,articleId=article))
    config = await service.save(SettingsUpdate(region='cn-beijing', model=new,version=1))
    assert config['model']==old and config['pending_model']==new
    assert await worker.step()
    broken = True
    assert not await worker.step()
    assert (await service.public())['rebuild_error']
    assert await index.search('原文',[{'id':1,'version':1}])
    await index.apply(dict(job,version=2,content='更新后的文章'))
    await index.apply(dict(job,articleId=2,version=2,status='DISABLED'))
    broken = False
    while (await service.public())['pending_model']:
        await worker.step()
    assert (await service.public())['model']==new
    hits = await index.search('更新',[{'id':1,'version':2},{'id':2,'version':1}])
    assert [h['article_id'] for h in hits]==[1] and hits[0]['score']>0.99


async def test_bailian_catalog_and_copy_require_admin(env, monkeypatch):
    import httpx
    service = env.app.state.embeddings
    await service.save(SettingsUpdate(region='cn-beijing',workspace='ws-fixture',api_key='fixture-copy-key',version=0))
    path = '/internal/v1/knowledge/config'
    headers = {'X-Agent-Token':env.settings.internal_token.get_secret_value(),
               'X-Actor-Id':'1','X-Actor-Portal':'admin','X-Actor-Role':'ADMIN'}
    def catalog(request):
        assert request.url.host == 'ws-fixture.cn-beijing.maas.aliyuncs.com'
        assert request.url.params['capabilities'] == 'TR'
        return httpx.Response(200,json={'output':{'total':2,'models':[
            {'model':'text-embedding-v3'},{'model':'chat-only'}]}})
    client = httpx.AsyncClient
    monkeypatch.setattr(httpx, 'AsyncClient', lambda **kwargs: client(transport=httpx.MockTransport(catalog), **kwargs))
    assert (await env.client.get(path+'/models',headers=headers)).json()=={'items':[{'id':'text-embedding-v3'}]}
    copied = await env.client.post(path+'/key',headers=headers)
    assert copied.json()=={'api_key':'fixture-copy-key'} and copied.headers['cache-control']=='no-store'
    for portal, role in [('customer','CUSTOMER'),('staff','RECEPTIONIST')]:
        other = dict(headers, **{'X-Actor-Portal':portal,'X-Actor-Role':role})
        assert (await env.client.post(path+'/key',headers=other)).status_code==403
        assert (await env.client.get(path+'/models',headers=other)).status_code==403
