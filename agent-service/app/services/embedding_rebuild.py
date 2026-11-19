import asyncio
import json
import logging

# Shadow rows follow the original passage lifetime, including retirements during a rebuild.
MISSING = """SELECT c.article_id,c.version,c.position,c.passage
 FROM agent_knowledge_chunk c LEFT JOIN agent_embedding_stage s USING(article_id,version,position)
 WHERE s.article_id IS NULL ORDER BY c.article_id,c.version,c.position LIMIT 10"""


class EmbeddingRebuild:
    def __init__(self, pool, embeddings):
        self.pool, self.embeddings = pool, embeddings

    async def step(self):
        async with self.pool.connection() as conn, conn.transaction():
            locked = await (await conn.execute('SELECT pg_try_advisory_xact_lock(73510243) AS ok')).fetchone()
            if not locked['ok']:
                return False
            config = await (await conn.execute('SELECT * FROM agent_embedding_config WHERE id=1')).fetchone()
            if not config['pending']:
                return False
            chunks = await (await conn.execute(MISSING)).fetchall()
            try:
                vectors = await self.embeddings.vectors([c['passage'] for c in chunks], config['pending']) if chunks else []
            except Exception as exc:
                await conn.execute('UPDATE agent_embedding_config SET rebuild_error=%s WHERE id=1 AND version=%s',
                                   ('生成新索引失败，将自动重试', config['version']))
                logging.getLogger(__name__).warning('embedding_rebuild_failed type=%s', type(exc).__name__)
                return False
            current = await (await conn.execute('SELECT version FROM agent_embedding_config WHERE id=1 FOR UPDATE')).fetchone()
            if current['version'] != config['version']:
                return True
            for chunk, vector in zip(chunks, vectors, strict=True):
                await conn.execute("""INSERT INTO agent_embedding_stage
                    SELECT article_id,version,position,%s::vector FROM agent_knowledge_chunk
                    WHERE article_id=%s AND version=%s AND position=%s FOR KEY SHARE
                    ON CONFLICT DO NOTHING""",
                    (json.dumps(vector),chunk['article_id'],chunk['version'],chunk['position']))
            if await (await conn.execute(MISSING)).fetchone():
                return True
            await conn.execute("""UPDATE agent_knowledge_chunk c SET embedding=s.embedding
                FROM agent_embedding_stage s WHERE (c.article_id,c.version,c.position)=(s.article_id,s.version,s.position)""")
            await conn.execute("""UPDATE agent_embedding_config SET model=pending->>'model',region=pending->>'region',
                workspace=pending->>'workspace',encrypted_key=pending->>'encrypted_key',
                pending=NULL,rebuild_error=NULL,version=version+1 WHERE id=1""")
            await conn.execute('DELETE FROM agent_embedding_stage')
            return True

    async def run(self):
        while True:
            try:
                busy = await self.step()
            except Exception as exc:
                logging.getLogger(__name__).warning('embedding_rebuild_retry type=%s', type(exc).__name__)
                busy = False
            await asyncio.sleep(0 if busy else 5)
