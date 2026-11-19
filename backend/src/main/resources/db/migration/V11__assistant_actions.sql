CREATE TABLE assistant_action (
 id CHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, portal VARCHAR(16) NOT NULL, actor_role VARCHAR(24) NOT NULL,
 conversation_id CHAR(36) NOT NULL, run_id CHAR(36) NOT NULL, operation VARCHAR(40) NOT NULL,
 parameters JSON NOT NULL, summary TEXT NOT NULL, state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 receipt VARCHAR(500), expires_at DATETIME NOT NULL,
 UNIQUE KEY uq_action_run(user_id,portal,run_id),
 INDEX ix_action_conversation(user_id,portal,conversation_id,state)
);
CREATE TABLE customer_preference (
 user_id BIGINT NOT NULL, topic VARCHAR(40) NOT NULL, preference VARCHAR(300) NOT NULL,
 version BIGINT NOT NULL DEFAULT 1, PRIMARY KEY(user_id,topic)
);
