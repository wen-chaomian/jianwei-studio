package com.shiguang.studio.service.knowledge;

import com.shiguang.studio.integration.agent.*;
import com.shiguang.studio.entity.KnowledgeArticle;
import com.shiguang.studio.mapper.KnowledgeMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
@lombok.RequiredArgsConstructor
public class KnowledgeSearch {
  private final JdbcTemplate db;
  private final AgentClient agent;
  private final KnowledgeMapper articles;

  public static boolean visible(String scope,String portal) {
    return "PUBLIC".equals(scope) || "admin".equals(portal) || ("STAFF".equals(scope) && "staff".equals(portal));
  }

  public Map<String,Object> source(String portal,long id,long version) {
    var rows=db.queryForList("""
      SELECT r.article_id AS id,r.version,r.title,r.content FROM knowledge_revision r
      JOIN knowledge_article a ON a.id=r.article_id WHERE a.id=? AND r.version=?
      AND a.status='PUBLISHED' AND r.status='PUBLISHED' AND r.version>=a.source_floor
      AND (a.audience='PUBLIC' OR ?='admin' OR (a.audience='STAFF' AND ?='staff'))
      AND (r.audience='PUBLIC' OR ?='admin' OR (r.audience='STAFF' AND ?='staff'))
      """,id,version,portal,portal,portal,portal);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"这份资料已停用或不可查看");
    return rows.getFirst();
  }

  public Map<String,Object> search(AgentIdentity who,String question) {
    if(question==null || question.isBlank() || question.length()>500)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请输入 1 至 500 字的问题");
    var eligible=db.queryForList("""
      SELECT id,indexed_version AS version FROM knowledge_article
      WHERE status='PUBLISHED' AND indexed_version>=source_floor
      AND (audience='PUBLIC' OR ?='admin' OR (audience='STAFF' AND ?='staff'))
      """,who.portal(),who.portal());
    var items=new ArrayList<Map<String,Object>>();
    if(eligible.isEmpty()) return Map.of("items",items,"message","当前没有可检索的已发布资料");
    var hits=agent.call(who,"POST","/knowledge/search",Map.of("question",question,"allowed",eligible));
    for(var hit:hits) {
      long id=hit.required("article_id").asLong(),version=hit.required("version").asLong();
      var a=articles.find(id);
      if(a==null || !"PUBLISHED".equals(a.getStatus()) || !Objects.equals(a.getIndexedVersion(),version)
          || !visible(a.getAudience(),who.portal())) continue;
      try {
        var src=source(who.portal(),id,version);
        items.add(Map.of("title",src.get("title"),"passage",hit.required("passage").asText(),"version",version,
            "url","/"+who.portal()+"/notices/"+id+"/"+version));
      } catch(ResponseStatusException e) {
        if(e.getStatusCode()!=HttpStatus.NOT_FOUND) throw e;
      }
    }
    return Map.of("items",items);
  }
}
