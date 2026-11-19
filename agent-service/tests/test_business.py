from types import SimpleNamespace
from unittest.mock import AsyncMock
from uuid import uuid4
import asyncio
import pytest
from langchain_core.messages import AIMessage
from app.tools.business import BusinessSession
from app.schemas.chat import Actor,NewMessage
from app.services.chat import ChatService
from app.messaging.store import TaskStore

USER=Actor(user_id=51,portal='customer',role='CUSTOMER',mcp_grant='private-grant')
DEFS=[{'type':'function','function':{'name':'list_appointments','description':'查询预约','parameters':{'type':'object'}}}]

async def prepared(env):
    c=await env.repo.create(USER,uuid4())
    row,_=await env.repo.start(USER,c['id'],NewMessage(request_id=uuid4(),content='查我的预约'),120,mq=True)
    return row

async def test_grant_durable_not_exposed(env):
    row=await prepared(env)
    actor,claimed=await TaskStore(env.pool).claim(row['id'],120)
    assert actor.mcp_grant.get_secret_value()=='private-grant'
    assert 'private-grant' not in str(ChatService.public(claimed))
    await env.repo.finish(row['id'],'done')
    assert (await env.repo.run(USER,row['id']))['mcp_grant']==''

async def test_tools_audit_links_and_owner(env):
    row=await prepared(env)
    result=SimpleNamespace(model_dump=lambda **kw:{'content':[{'type':'text','text':'{"data":[],"pageUrl":"/customer/appointments"}'}],'isError':False})
    session=SimpleNamespace(call_tool=AsyncMock(return_value=result))
    tools=BusinessSession(session,DEFS,env.repo,USER,row['id'])
    text=await tools.call('list_appointments',{'page':1})
    assert 'data' in text
    saved=await env.repo.run(USER,row['id'])
    assert saved['progress_label']=='正在查询预约'
    assert saved['result_links'][0]['url']=='/customer/appointments'
    async with env.pool.connection() as c:
        audit=await (await c.execute('SELECT * FROM agent_tool_calls WHERE run_id=%s',(row['id'],))).fetchone()
    assert audit['status']=='completed'
    assert audit['arguments']=={'page':1}
    assert '无权' in await tools.call('get_business_summary',{})
    assert session.call_tool.await_count==1

async def test_stopped_run_cannot_query(env):
    row=await prepared(env)
    await env.repo.finish(row['id'],'',('STOPPED','已停止生成'))
    session=SimpleNamespace(call_tool=AsyncMock())
    tools=BusinessSession(session,DEFS,env.repo,USER,row['id'])
    with pytest.raises(asyncio.CancelledError):
        await tools.call('list_appointments',{})
    session.call_tool.assert_not_called()

async def test_tool_failure_is_not_empty_result(env):
    row=await prepared(env)
    session=SimpleNamespace(call_tool=AsyncMock(side_effect=RuntimeError('secret-error')))
    tools=BusinessSession(session,DEFS,env.repo,USER,row['id'])
    result=await tools.call('list_appointments',{})
    assert '不可用' in result and 'secret' not in result
    async with env.pool.connection() as c:
        assert (await (await c.execute('SELECT status FROM agent_tool_calls WHERE run_id=%s',(row['id'],))).fetchone())['status']=='failed'

async def test_graph_calls_tools_then_answers(env):
    from app.agents.business import build_business_graph
    row=await prepared(env)
    class Planner:
        rounds=0
        def with_config(self,**kwargs): return self
        async def ainvoke(self,messages):
            self.rounds+=1
            return AIMessage(content='',tool_calls=[{'name':'list_appointments','args':{},'id':'call1','type':'tool_call'}]) if self.rounds==1 else AIMessage(content='ready')
    planner=Planner()
    class Model:
        def bind_tools(self,tools): return planner
        def with_config(self,**kwargs): return self
        async def ainvoke(self,messages):
            assert not any(getattr(m,'tool_call_id',None) for m in messages)
            assert '真实查询结果' in messages[-1].content
            assert not any(getattr(m,'tool_calls',[]) for m in messages)
            return AIMessage(content='你有一条预约。')
    business=SimpleNamespace(definitions=DEFS,call=AsyncMock(return_value='真实查询结果'),repo=env.repo,run_id=row['id'])
    graph=build_business_graph(Model(),env.checkpointer,business)
    result=await graph.ainvoke({'messages':[{'role':'user','content':'查我的预约'}],'role':'CUSTOMER'}, {'configurable':{'thread_id':str(uuid4())}})
    assert result['answer']=='你有一条预约。'
    business.call.assert_awaited_once_with('list_appointments',{})


def test_internal_protocol_detection():
    from app.agents.business import has_internal_markup
    assert has_internal_markup('<｜｜DSML｜｜ calls>')
    assert has_internal_markup('<||DSML|| invoke name="x">')
    assert has_internal_markup('<tool_call>')
    assert not has_internal_markup('价格 < 500 元，可选套餐如下。')

def test_answering_does_not_receive_tool_call_protocol():
    from app.agents.business import final_answer_messages
    from langchain_core.messages import SystemMessage, HumanMessage, ToolMessage
    trail=[AIMessage(content='',tool_calls=[{'name':'list_requests','args':{},'id':'c1','type':'tool_call'}]),ToolMessage(content='真实查询结果',tool_call_id='c1')]
    final=final_answer_messages([SystemMessage(content='业务查询'),HumanMessage(content='查申请')],trail)
    assert all(not isinstance(m,ToolMessage) and not getattr(m,'tool_calls',[]) for m in final)
    assert 'list_requests' in final[-1].content and '真实查询结果' in final[-1].content

async def test_split_protocol_never_reaches_progress_or_saved_answer(env, monkeypatch):
    from contextlib import asynccontextmanager
    from langchain_core.messages import AIMessageChunk
    from pydantic import SecretStr
    from app.schemas.chat import SaveModelConfig
    await env.configs.save(Actor(user_id=9,portal='admin',role='ADMIN'),SaveModelConfig(api_key=SecretStr('test-only-key'),model='test-model',version=0))
    row=await prepared(env)
    @asynccontextmanager
    async def connected(*args):
        yield SimpleNamespace(ready=True)
    monkeypatch.setattr('app.services.chat.connect_business',connected)
    recorded=[]
    original=env.repo.progress
    async def progress(run_id, text):
        recorded.append(text)
        await original(run_id,text)
    monkeypatch.setattr(env.repo,'progress',progress)
    class Graph:
        async def astream(self,*args,**kwargs):
            for text in ['<','｜｜DS','ML｜｜ calls>']:
                yield 'messages',(AIMessageChunk(content=text),{'langgraph_node':'respond'})
                await asyncio.sleep(.26)
            raise ValueError('invalid_final_answer')
    chat=ChatService(env.repo,env.checkpointer,env.settings,env.configs,lambda *a,**k:Graph())
    await chat.execute(USER,row)
    saved=await env.repo.run(USER,row['id'])
    assert saved['status']=='failed' and saved['error_code']=='INVALID_RESPONSE'
    assert saved['output']=='' and recorded and all(text=='' for text in recorded)
