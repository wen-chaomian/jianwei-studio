package com.shiguang.studio.service.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.shiguang.studio.cache.SyncVersions;
import com.shiguang.studio.integration.agent.AgentMqGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
@lombok.RequiredArgsConstructor
public class KnowledgeJobs {
  private final JdbcTemplate db;
  private final SyncVersions sync;

  public void retry(long id) {
    db.update("""
      UPDATE knowledge_article SET job_id=UUID(), next_attempt=NOW(6),
        index_state='QUEUED',index_reason='' WHERE id=?
      """,id);
    sync.changed("desk");
  }

  public void wakeConfiguration() {
    db.update("""
      UPDATE knowledge_article SET job_id=UUID(),next_attempt=NOW(6),index_state='QUEUED',index_reason=''
      WHERE status='PUBLISHED' AND index_state IN ('WAIT_CONFIG','FAILED','PROCESSING','QUEUED')
      """);
    sync.changed("desk");
  }

  public void receive(JsonNode result) {
    String state=result.required("state").asText();
    if (!List.of("READY","OFF","WAIT_CONFIG","FAILED").contains(state)) return;
    int changed=db.update("""
      UPDATE knowledge_article SET index_state=?,index_reason=?,next_attempt=NULL,
        indexed_version=CASE WHEN ?='READY' THEN version ELSE indexed_version END
      WHERE id=? AND version=? AND job_id=? AND index_state IN ('QUEUED','PROCESSING')
      """,state,result.path("reason").asText(""),state,result.required("articleId").asLong(),
      result.required("version").asLong(),result.required("jobId").asText());
    if (changed>0) sync.changed("desk");
  }

  @Component
  @EnableScheduling
  @ConditionalOnProperty(name="studio.knowledge.dispatch",matchIfMissing=true)
  @lombok.RequiredArgsConstructor
  static class Dispatcher {
    private final JdbcTemplate db;
    private final AgentMqGateway mq;
    private final SyncVersions sync;

    @Scheduled(fixedDelay=3000,initialDelay=5000)
    void publish() {
      var jobs=db.queryForList("""
        SELECT id AS articleId, version, job_id AS jobId, status,title,content
        FROM knowledge_article WHERE next_attempt<=NOW(6)
        AND index_state IN ('QUEUED','PROCESSING') ORDER BY next_attempt,id LIMIT 8
        """);
      for(var job:jobs) {
        int claimed=db.update("""
          UPDATE knowledge_article SET next_attempt=TIMESTAMPADD(SECOND,120,NOW(6)),index_state='PROCESSING'
          WHERE job_id=? AND next_attempt<=NOW(6)
          """,job.get("jobId"));
        if(claimed==0) continue;
        job.put("action","index_knowledge");
        if (!mq.sendKnowledge(job)) {
          db.update("""
            UPDATE knowledge_article SET index_state='QUEUED',next_attempt=TIMESTAMPADD(SECOND,15,NOW(6))
            WHERE job_id=? AND index_state='PROCESSING'
            """,job.get("jobId"));
        }
        sync.changed("desk");
      }
    }
  }
}
