from uuid import uuid4
from fastapi import HTTPException


class ChatRepository:
    def __init__(self, pool):
        self.pool = pool

    async def create(self, actor, request_id):
        async with self.pool.connection() as conn:
            row = await conn.execute(
                """INSERT INTO agent_conversations(id,owner_id,portal,request_id)
                VALUES (%s,%s,%s,%s) ON CONFLICT(owner_id,portal,request_id)
                DO UPDATE SET request_id=EXCLUDED.request_id RETURNING *""",
                (uuid4(), actor.user_id, actor.portal, request_id),
            )
            return await row.fetchone()

    async def conversations(self, actor, offset):
        async with self.pool.connection() as conn:
            rows = await conn.execute(
                """SELECT * FROM agent_conversations WHERE owner_id=%s AND portal=%s AND deleted_at IS NULL
                ORDER BY updated_at DESC,id LIMIT 51 OFFSET %s""",
                (actor.user_id, actor.portal, offset),
            )
            data = await rows.fetchall()
            return {"items": data[:50], "has_more": len(data) > 50}

    async def owned(self, conn, actor, conversation_id, lock=False):
        row = await conn.execute(
            """SELECT * FROM agent_conversations
            WHERE id=%s AND owner_id=%s AND portal=%s AND deleted_at IS NULL"""
            + (" FOR UPDATE" if lock else ""),
            (conversation_id, actor.user_id, actor.portal),
        )
        result = await row.fetchone()
        if not result:
            raise HTTPException(404, "对话不存在或无权访问")
        return result

    async def detail(self, actor, conversation_id, before):
        async with self.pool.connection() as conn:
            conversation = await self.owned(conn, actor, conversation_id)
            rows = await conn.execute(
                """SELECT m.*,r.status,r.error_message,r.result_links FROM agent_messages m
                JOIN agent_runs r ON r.id=m.run_id WHERE m.conversation_id=%s AND m.seq < %s
                ORDER BY m.seq DESC LIMIT 101""",
                (conversation_id, before),
            )
            messages = await rows.fetchall()
            running = await conn.execute(
                "SELECT id FROM agent_runs WHERE conversation_id=%s AND status='running'",
                (conversation_id,),
            )
            run = await running.fetchone()
            return {
                "conversation": conversation,
                "messages": list(reversed(messages[:100])),
                "has_more": len(messages) > 100,
                "active_run": run["id"] if run else None,
            }

    async def start(self, actor, conversation_id, message, timeout, mq=False):
        async with self.pool.connection() as conn, conn.transaction():
            await self.owned(conn, actor, conversation_id, True)
            old = await conn.execute(
                "SELECT * FROM agent_runs WHERE conversation_id=%s AND request_id=%s",
                (conversation_id, message.request_id),
            )
            existing = await old.fetchone()
            if existing:
                if existing["input"] != message.content:
                    raise HTTPException(409, "请求编号已用于其他消息")
                return existing, False
            active = await conn.execute(
                "SELECT id FROM agent_runs WHERE conversation_id=%s AND status='running'",
                (conversation_id,),
            )
            if await active.fetchone():
                raise HTTPException(409, "此对话正在回复，请等待完成")
            # Serialise only the same account's admission, not different users' model calls.
            await conn.execute("SELECT pg_advisory_xact_lock(%s)", (actor.user_id,))
            count = await conn.execute(
                """SELECT count(*) AS n FROM agent_runs r JOIN agent_conversations c
                ON c.id=r.conversation_id WHERE c.owner_id=%s AND r.status='running'""",
                (actor.user_id,),
            )
            if (await count.fetchone())["n"] >= 3:
                raise HTTPException(429, "同时进行的对话较多，请稍后发送")
            row = await conn.execute(
                """INSERT INTO agent_runs(id,conversation_id,request_id,input,status,deadline,mq_enabled,actor_role,mcp_grant)
                VALUES(%s,%s,%s,%s,'running',now() + %s * interval '1 second',%s,%s,%s) RETURNING *""",
                (
                    uuid4(),
                    conversation_id,
                    message.request_id,
                    message.content,
                    600 if mq else timeout + 15,
                    mq, actor.role, actor.mcp_grant.get_secret_value(),
                ),
            )
            run = await row.fetchone()
            await conn.execute(
                "INSERT INTO agent_messages(conversation_id,run_id,role,content) VALUES(%s,%s,'user',%s)",
                (conversation_id, run["id"], message.content),
            )
            await conn.execute(
                "UPDATE agent_conversations SET title=CASE WHEN title='新对话' AND NOT custom_title THEN %s ELSE title END, updated_at=now() WHERE id=%s",
                (message.content[:40], conversation_id),
            )
            return run, True

    async def run(self, actor, run_id):
        async with self.pool.connection() as conn:
            row = await conn.execute(
                """SELECT r.* FROM agent_runs r JOIN agent_conversations c ON c.id=r.conversation_id
                WHERE r.id=%s AND c.owner_id=%s AND c.portal=%s AND c.deleted_at IS NULL""",
                (run_id, actor.user_id, actor.portal),
            )
            run = await row.fetchone()
            if not run:
                raise HTTPException(404, "任务不存在或无权访问")
            return run

    async def context(self, conversation_id, run_id):
        async with self.pool.connection() as conn:
            rows = await conn.execute(
                """SELECT m.role,m.content FROM agent_messages m JOIN agent_runs r ON r.id=m.run_id
                WHERE m.conversation_id=%s AND (r.status='completed' OR r.id=%s)
                ORDER BY m.seq DESC LIMIT 21""",
                (conversation_id, run_id),
            )
            return list(reversed(await rows.fetchall()))

    async def progress(self, run_id, output):
        async with self.pool.connection() as conn:
            await conn.execute(
                "UPDATE agent_runs SET output=%s, revision=revision+1,updated_at=now() WHERE id=%s AND status='running'",
                (output, run_id),
            )

    async def finish(self, run_id, output, error=None):
        async with self.pool.connection() as conn, conn.transaction():
            row = await conn.execute(
                """UPDATE agent_runs SET status=%s,output=%s,error_code=%s,error_message=%s,mcp_grant='',
                revision=revision+1,updated_at=now() WHERE id=%s AND status='running' RETURNING conversation_id""",
                (
                    "failed" if error else "completed",
                    output,
                    error[0] if error else None,
                    error[1] if error else None,
                    run_id,
                ),
            )
            updated = await row.fetchone()
            if updated:
                await conn.execute(
                    "INSERT INTO agent_messages(conversation_id,run_id,role,content) VALUES(%s,%s,'assistant',%s)",
                    (updated["conversation_id"], run_id, output),
                )
                await conn.execute(
                    "UPDATE agent_conversations SET updated_at=now() WHERE id=%s",
                    (updated["conversation_id"],),
                )

    async def expire(self):
        async with self.pool.connection() as conn, conn.transaction():
            rows = await conn.execute("""UPDATE agent_runs SET status='failed',error_code='INTERRUPTED',mcp_grant='',
                error_message='回复中断，请重新发送。',revision=revision+1,updated_at=now()
                WHERE status='running' AND deadline < now() RETURNING *""")
            for run in await rows.fetchall():
                await conn.execute(
                    """INSERT INTO agent_messages(conversation_id,run_id,role,content)
                    VALUES(%s,%s,'assistant',%s) ON CONFLICT(run_id,role) DO NOTHING""",
                    (run["conversation_id"], run["id"], run["output"]),
                )

    async def rename(self, actor, conversation_id, title):
        async with self.pool.connection() as conn, conn.transaction():
            await self.owned(conn, actor, conversation_id, True)
            await conn.execute("UPDATE agent_conversations SET title=%s,custom_title=true WHERE id=%s", (title, conversation_id))
        return {"ok": True}

    async def delete(self, actor, conversation_id):
        async with self.pool.connection() as conn, conn.transaction():
            await self.owned(conn, actor, conversation_id, True)
            rows = await conn.execute("SELECT id FROM agent_runs WHERE conversation_id=%s AND status='running'", (conversation_id,))
            runs = [r["id"] for r in await rows.fetchall()]
            if runs:
                raise HTTPException(409, "请先停止当前回复，再删除对话")
            await conn.execute("UPDATE agent_conversations SET deleted_at=now() WHERE id=%s", (conversation_id,))
        return {"ok": True}
