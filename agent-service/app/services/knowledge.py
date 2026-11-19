import json
from fastapi import HTTPException
from psycopg.types.json import Jsonb


def passages(title, content):
    groups, current = [], ''
    for line in content.splitlines():
        line = line.strip()
        if not line:
            continue
        for start in range(0, len(line), 650):
            part = line[start:start+650]
            if len(current) + len(part) > 800 and current:
                groups.append(current)
                current = ''
            current = (current + '\n' + part).strip()
    if current:
        groups.append(current)
    return [title + '\n' + group for group in groups]


class KnowledgeIndex:
    def __init__(self, pool, embeddings):
        self.pool, self.embeddings = pool, embeddings

    async def apply(self, job):
        article, version = job['articleId'], job['version']
        if job['status'] != 'PUBLISHED':
            async with self.pool.connection() as conn:
                await conn.execute('DELETE FROM agent_knowledge_chunk WHERE article_id=%s AND version<=%s',(article,version))
            return 'OFF'
        async with self.pool.connection() as conn, conn.transaction():
            config = await (await conn.execute(
                'SELECT * FROM agent_embedding_config WHERE id=1 FOR SHARE')).fetchone()
            exists = await (await conn.execute('SELECT 1 FROM agent_knowledge_chunk WHERE article_id=%s AND version=%s LIMIT 1',(article,version))).fetchone()
            if not exists:
                chunks = passages(job['title'], job['content'])
                vectors = await self.embeddings.vectors(chunks, config)
                async with conn.cursor() as cursor:
                    await cursor.executemany(
                        'INSERT INTO agent_knowledge_chunk VALUES(%s,%s,%s,%s,%s::vector) ON CONFLICT DO NOTHING',
                        [(article,version,i,chunk,json.dumps(vector)) for i,(chunk,vector) in enumerate(zip(chunks,vectors,strict=True))])
        return 'READY'

    async def search(self, question, allowed):
        if not allowed:
            return []
        async with self.pool.connection() as conn, conn.transaction():
            await conn.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY')
            config = await (await conn.execute('SELECT * FROM agent_embedding_config WHERE id=1')).fetchone()
            vector, = await self.embeddings.vectors([question], config)
            return await (await conn.execute("""
                SELECT c.article_id,c.version,c.position,c.passage,1-(c.embedding <=> %s::vector) AS score
                FROM agent_knowledge_chunk c JOIN jsonb_to_recordset(%s::jsonb) AS a(id bigint, version bigint)
                  ON c.article_id=a.id AND c.version=a.version
                WHERE 1-(c.embedding <=> %s::vector) >= 0.43
                ORDER BY score DESC,c.article_id,c.position LIMIT 4
            """,(json.dumps(vector),Jsonb(allowed),json.dumps(vector)))).fetchall()
