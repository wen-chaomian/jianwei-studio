"""MCP transport and audit. Credentials never enter model-visible tool arguments."""
import asyncio
import json
import time
from contextlib import asynccontextmanager
from uuid import uuid4
import httpx2
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client
from psycopg.types.json import Jsonb

LABELS = {
    'search_knowledge':'正在查询服务说明',
    'list_packages': '正在查询套餐', 'get_booking_rules': '正在查询预约规则',
    'list_reschedule_slots': '正在查询改期时段', 'list_available_slots': '正在查询可预约时段', 'list_appointments': '正在查询预约',
    'get_appointment': '正在查询预约详情', 'get_appointment_activity': '正在查询办理与收退款记录',
    'list_requests': '正在查询申请进度', 'get_my_schedule': '正在查询我的排班',
    'get_employee_schedule': '正在查询员工排班', 'get_business_summary': '正在查询经营汇总',
}


class BusinessSession:
    def __init__(self, session, definitions, repo, actor, run_id):
        self.session, self.repo, self.actor, self.run_id = session, repo, actor, run_id
        self.definitions = definitions
        self.allowed = {d['function']['name'] for d in definitions}
        self.links = []
        self.knowledge_links = []

    async def finalize_sources(self, answer):
        """Only expose retrieved articles named by the answer as citations."""
        cited = [item for item in self.knowledge_links if item['label'] in answer]
        if not cited:
            return
        async with self.repo.pool.connection() as conn:
            await conn.execute(
                "UPDATE agent_runs SET result_links=%s, revision=revision+1 WHERE id=%s AND status='running'",
                (Jsonb(self.links + cited), self.run_id))

    async def call(self, name, arguments):
        if name not in self.allowed:
            return '工具不存在或当前身份无权调用。'
        current = await self.repo.run(self.actor, self.run_id)
        if current['status'] != 'running':
            raise asyncio.CancelledError()
        audit_id, started = uuid4(), time.monotonic()
        # Only numeric identifiers/dates are accepted by the server schemas. Do not log prompt/credentials.
        safe = {k: v for k, v in arguments.items() if k in {'packageId','photographerId','appointmentId','date','from','to','page','size'} and isinstance(v,(int,str)) and len(str(v)) <= 32}
        async with self.repo.pool.connection() as c:
            await c.execute('INSERT INTO agent_tool_calls(id,run_id,tool_name,arguments,status) VALUES(%s,%s,%s,%s,%s)', (audit_id,self.run_id,name,Jsonb(safe),'running'))
            await c.execute("UPDATE agent_runs SET progress_label=%s,revision=revision+1 WHERE id=%s AND status='running'", (('正在准备确认单' if name.startswith('prepare_') else LABELS.get(name,'正在查询')),self.run_id))
        status = 'failed'
        try:
            if name.startswith('prepare_'):
                arguments = {**arguments, '_conversation': str(current['conversation_id']), '_run': str(self.run_id)}
            result = await self.session.call_tool(name, arguments)
            raw = result.model_dump(by_alias=True)
            text = '\n'.join(b.get('text','') for b in raw.get('content',[]) if b.get('type')=='text')
            if raw.get('isError'):
                return '工具返回错误（不是无数据）：'+text
            status = 'completed'
            try:
                value = json.loads(text)
                if name == 'search_knowledge':
                    items = [{'url':hit['url'], 'label':hit['title']} for hit in value['data']['items']]
                elif name.startswith('prepare_'):
                    items = [{'url':value['pageUrl'], 'label':value['label']}]
                else:
                    items = [{'url':value.get('pageUrl',''), 'label':'查看'+LABELS.get(name,'查询结果').replace('正在查询','')}]
                for item in items:
                    link = item['url']
                    if not (link.startswith('/'+self.actor.portal+'/') or link=='/'+self.actor.portal):
                        continue
                    if name == 'search_knowledge':
                        if item not in self.knowledge_links:
                            self.knowledge_links.append(item)
                        continue
                    if item not in self.links:
                        self.links.append(item)
                        async with self.repo.pool.connection() as c:
                            await c.execute("UPDATE agent_runs SET result_links=%s,revision=revision+1 WHERE id=%s AND status='running'",(Jsonb(self.links),self.run_id))
            except (ValueError,TypeError):
                pass
            # A bounded tool payload prevents accidental unbounded context. Explicitly flag truncation.
            if len(text)>24000:
                return text[:24000]+'\n[结果过长已截断，不能据此断言全部结果；请缩小查询范围或使用分页。]'
            return text
        except asyncio.CancelledError:
            status='cancelled'
            raise
        except Exception:
            return '业务查询服务暂不可用，未取得有效结果；请稍后重试，不能编造数据。'
        finally:
            async with self.repo.pool.connection() as c:
                await c.execute('UPDATE agent_tool_calls SET status=%s,duration_ms=%s WHERE id=%s',(status,int((time.monotonic()-started)*1000),audit_id))


@asynccontextmanager
async def connect_business(settings, actor, repo, run_id):
    headers={'X-Agent-Token':settings.internal_token.get_secret_value(), 'Authorization':'Bearer '+actor.mcp_grant.get_secret_value()}
    async with httpx2.AsyncClient(headers=headers, timeout=20, trust_env=False) as http:
        async with streamable_http_client(settings.mcp_url,http_client=http) as streams:
            async with ClientSession(*streams, read_timeout_seconds=20) as session:
                await session.initialize()
                listed=await session.list_tools()
                definitions=[]
                for tool in listed.tools:
                    d=tool.model_dump(by_alias=True)
                    definitions.append({'type':'function','function':{'name':d['name'],'description':d.get('description',''),'parameters':d['inputSchema']}})
                yield BusinessSession(session,definitions,repo,actor,run_id)
