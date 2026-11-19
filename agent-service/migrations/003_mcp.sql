ALTER TABLE agent_runs ADD COLUMN IF NOT EXISTS mcp_grant text NOT NULL DEFAULT '';
ALTER TABLE agent_runs ADD COLUMN IF NOT EXISTS progress_label text NOT NULL DEFAULT '';
ALTER TABLE agent_runs ADD COLUMN IF NOT EXISTS result_links jsonb NOT NULL DEFAULT '[]';
CREATE TABLE IF NOT EXISTS agent_tool_calls (
 id uuid PRIMARY KEY, run_id uuid NOT NULL REFERENCES agent_runs(id),
 tool_name text NOT NULL, arguments jsonb NOT NULL,
 status text NOT NULL, duration_ms integer, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_agent_tool_calls_run ON agent_tool_calls(run_id,created_at);
