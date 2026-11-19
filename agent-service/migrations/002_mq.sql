ALTER TABLE agent_runs ADD COLUMN IF NOT EXISTS mq_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE agent_runs ADD COLUMN IF NOT EXISTS actor_role varchar(30);
ALTER TABLE agent_runs ADD COLUMN IF NOT EXISTS worker_started_at timestamptz;
CREATE TABLE IF NOT EXISTS agent_outbox (
 run_id uuid NOT NULL REFERENCES agent_runs(id), kind varchar(20) NOT NULL,
 revision bigint NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(run_id,kind)
);
-- Coalesce intermediate snapshots. A terminal revision cannot be deleted by an older send.
CREATE OR REPLACE FUNCTION agent_enqueue_event() RETURNS trigger AS $$
BEGIN
 IF NEW.mq_enabled THEN
  INSERT INTO agent_outbox(run_id,kind,revision) VALUES(NEW.id,'result',NEW.revision)
   ON CONFLICT(run_id,kind) DO UPDATE SET revision=EXCLUDED.revision;
  IF TG_OP='INSERT' THEN
   INSERT INTO agent_outbox(run_id,kind,revision) VALUES(NEW.id,'generate',0) ON CONFLICT DO NOTHING;
  ELSIF NEW.error_code='STOPPED' AND OLD.status='running' THEN
   INSERT INTO agent_outbox(run_id,kind,revision) VALUES(NEW.id,'stop',NEW.revision) ON CONFLICT DO NOTHING;
  END IF;
 END IF;
 RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS agent_run_outbox ON agent_runs;
CREATE TRIGGER agent_run_outbox AFTER INSERT OR UPDATE OF revision ON agent_runs
 FOR EACH ROW EXECUTE FUNCTION agent_enqueue_event();
