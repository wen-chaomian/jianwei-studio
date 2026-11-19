ALTER TABLE agent_embedding_config ADD COLUMN IF NOT EXISTS model text NOT NULL DEFAULT 'text-embedding-v4',
 ADD COLUMN IF NOT EXISTS pending jsonb, ADD COLUMN IF NOT EXISTS rebuild_error text;
CREATE TABLE IF NOT EXISTS agent_embedding_stage (
 article_id bigint NOT NULL, version bigint NOT NULL, position integer NOT NULL,
 embedding vector(1024) NOT NULL, PRIMARY KEY(article_id,version,position),
 FOREIGN KEY(article_id,version,position) REFERENCES agent_knowledge_chunk ON DELETE CASCADE
);
