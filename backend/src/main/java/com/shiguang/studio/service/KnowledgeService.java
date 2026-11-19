package com.shiguang.studio.service;
import static com.shiguang.studio.service.support.CurrentActor.*;
import static com.shiguang.studio.service.support.ServiceChecks.*;
import com.shiguang.studio.dto.KnowledgeRequest;
import com.shiguang.studio.entity.KnowledgeArticle;
import com.shiguang.studio.mapper.KnowledgeMapper;
import com.shiguang.studio.cache.SyncVersions;
import com.shiguang.studio.service.support.BusinessEvents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@lombok.RequiredArgsConstructor
public class KnowledgeService {
 private final KnowledgeMapper mapper;
 private final BusinessEvents events;
 private final SyncVersions sync;
 private final com.shiguang.studio.service.knowledge.KnowledgeJobs jobs;
 public Object list(boolean management,int p,int size) {
  if(management) admin();
  page(p,size);
  return page(management?mapper.list(size,(p-1)*size):mapper.published(size,(p-1)*size),management?mapper.count():mapper.publishedCount(),p,size);
 }
 @Transactional
 public KnowledgeArticle save(Long id,KnowledgeRequest r) {
  admin();
  require(r.title()!=null&&!r.title().isBlank()&&r.content()!=null&&!r.content().isBlank(),"标题和内容不能为空");
  var a=new KnowledgeArticle();a.setId(id);a.setTitle(r.title().strip());a.setCategory(r.category().strip());a.setContent(r.content().strip());a.setStatus(r.status());a.setVersion(r.version());
  var previous=id==null?null:mapper.find(id);
  a.setAudience(r.audience()!=null?r.audience():previous!=null?previous.getAudience():"PUBLIC");
  if(id==null){require(r.version()==0,"新内容版本不正确");mapper.insert(a);}
  else {require(mapper.update(a)==1,"内容已被修改或不存在，请重新打开后编辑");}
  mapper.revision(a.getId());
  jobs.retry(a.getId());
  events.audit(user(),"KNOWLEDGE",r.status(),a.getId(),a.getTitle());
  sync.changed("configuration");
  return mapper.find(a.getId());
 }
}
