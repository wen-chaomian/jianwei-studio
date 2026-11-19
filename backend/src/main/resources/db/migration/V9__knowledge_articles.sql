CREATE TABLE knowledge_article (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 title VARCHAR(120) NOT NULL,
 category VARCHAR(40) NOT NULL,
 content TEXT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 version BIGINT NOT NULL DEFAULT 1,
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CHECK (status IN ('DRAFT','PUBLISHED','DISABLED')),
 INDEX idx_knowledge_status (status,id)
);
CREATE TABLE knowledge_revision (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 article_id BIGINT NOT NULL,
 version BIGINT NOT NULL,
 title VARCHAR(120) NOT NULL,
 category VARCHAR(40) NOT NULL,
 content TEXT NOT NULL,
 status VARCHAR(16) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_knowledge_revision (article_id,version),
 FOREIGN KEY (article_id) REFERENCES knowledge_article(id)
);
