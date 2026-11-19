ALTER TABLE knowledge_article
 ADD audience VARCHAR(12) NOT NULL DEFAULT 'PUBLIC',
 ADD index_state VARCHAR(16) NOT NULL DEFAULT 'OFF',
 ADD index_reason VARCHAR(180) NOT NULL DEFAULT '',
 ADD indexed_version BIGINT NULL,
 ADD source_floor BIGINT NOT NULL DEFAULT 1,
 ADD job_id CHAR(36) NULL,
 ADD next_attempt DATETIME(6) NULL,
 ADD INDEX knowledge_dispatch (index_state,next_attempt);
ALTER TABLE knowledge_revision ADD audience VARCHAR(12) NOT NULL DEFAULT 'PUBLIC';
UPDATE knowledge_article SET job_id=UUID(), index_state='QUEUED', next_attempt=NOW(6)
WHERE status='PUBLISHED';
