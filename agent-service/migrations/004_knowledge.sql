CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS agent_embedding_config (
 id integer PRIMARY KEY CHECK(id=1), region text NOT NULL DEFAULT 'cn-beijing',
 workspace text NOT NULL DEFAULT '', encrypted_key text, version bigint NOT NULL DEFAULT 0
);
INSERT INTO agent_embedding_config(id) VALUES (1) ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS agent_knowledge_chunk (
 article_id bigint NOT NULL, version bigint NOT NULL, position integer NOT NULL,
 passage text NOT NULL, embedding vector(1024) NOT NULL,
 PRIMARY KEY(article_id,version,position)
);
