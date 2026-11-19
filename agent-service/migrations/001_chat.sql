CREATE TABLE IF NOT EXISTS agent_conversations (
 id uuid PRIMARY KEY,
 owner_id bigint NOT NULL,
 portal varchar(16) NOT NULL CHECK (portal IN ('customer','staff','admin')),
 request_id uuid NOT NULL,
 title varchar(80) NOT NULL DEFAULT '新对话',
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(owner_id,portal,request_id)
);
CREATE INDEX IF NOT EXISTS agent_conversations_owner ON agent_conversations(owner_id,portal,updated_at DESC,id);
CREATE TABLE IF NOT EXISTS agent_runs (
 id uuid PRIMARY KEY,
 conversation_id uuid NOT NULL REFERENCES agent_conversations(id),
 request_id uuid NOT NULL,
 input text NOT NULL,
 status varchar(20) NOT NULL CHECK (status IN ('running','completed','failed')),
 output text NOT NULL DEFAULT '',
 error_code varchar(50),
 error_message text,
 revision bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 deadline timestamptz NOT NULL,
 UNIQUE(conversation_id,request_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_one_active_run ON agent_runs(conversation_id) WHERE status='running';
CREATE INDEX IF NOT EXISTS agent_runs_deadline ON agent_runs(deadline) WHERE status='running';
CREATE TABLE IF NOT EXISTS agent_messages (
 seq bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 conversation_id uuid NOT NULL REFERENCES agent_conversations(id),
 run_id uuid NOT NULL REFERENCES agent_runs(id),
 role varchar(16) NOT NULL CHECK (role IN ('user','assistant')),
 content text NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(run_id,role)
);
CREATE INDEX IF NOT EXISTS agent_messages_conversation ON agent_messages(conversation_id,seq);

CREATE TABLE IF NOT EXISTS agent_model_config (
 id integer PRIMARY KEY CHECK(id=1),
 encrypted_key text NOT NULL DEFAULT '',
 model varchar(100) NOT NULL DEFAULT '',
 version bigint NOT NULL DEFAULT 0,
 updated_by bigint,
 updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO agent_model_config(id) VALUES(1) ON CONFLICT DO NOTHING;

ALTER TABLE agent_conversations ADD COLUMN IF NOT EXISTS deleted_at timestamptz;
ALTER TABLE agent_conversations ADD COLUMN IF NOT EXISTS custom_title boolean NOT NULL DEFAULT false;
