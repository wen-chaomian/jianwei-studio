from app.schemas.chat import Actor


class TaskStore:
    def __init__(self, pool):
        self.pool = pool

    async def claim(self, run_id, timeout):
        async with self.pool.connection() as c, c.transaction():
            row = await c.execute("""UPDATE agent_runs SET worker_started_at=now(),
                deadline=now()+(%s+15)*interval '1 second'
                WHERE id=%s AND mq_enabled AND status='running'
                AND worker_started_at IS NULL AND deadline>now() RETURNING *""", (timeout, run_id))
            run = await row.fetchone()
            if not run:
                return None
            owner = await (await c.execute("SELECT owner_id,portal FROM agent_conversations WHERE id=%s AND deleted_at IS NULL", (run['conversation_id'],))).fetchone()
            if not owner:
                return None
            return Actor(user_id=owner['owner_id'], portal=owner['portal'], role=run['actor_role'], mcp_grant=run.get('mcp_grant','')), run

    async def pending(self):
        async with self.pool.connection() as c:
            return await (await c.execute("""SELECT o.kind,o.revision AS event_revision,r.*,c.owner_id,c.portal
                FROM agent_outbox o JOIN agent_runs r ON r.id=o.run_id
                JOIN agent_conversations c ON c.id=r.conversation_id
                ORDER BY o.created_at LIMIT 64""")).fetchall()

    async def delivered(self, row):
        async with self.pool.connection() as c:
            await c.execute("DELETE FROM agent_outbox WHERE run_id=%s AND kind=%s AND revision=%s", (row['id'], row['kind'], row['event_revision']))

    async def stopped(self, ids):
        if not ids:
            return []
        async with self.pool.connection() as c:
            return [r['id'] for r in await (await c.execute("SELECT id FROM agent_runs WHERE id=ANY(%s) AND status!='running'", (ids,))).fetchall()]
