package com.shiguang.studio.service.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shiguang.studio.integration.agent.AgentIdentity;
import com.shiguang.studio.integration.mcp.McpAuthorization.Grant;
import com.shiguang.studio.mapper.BookingMapper;
import static com.shiguang.studio.service.support.ServiceChecks.require;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class ActionCards {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final ActionExecution executor;
  private final BookingMapper booking;

  @Transactional(isolation=Isolation.READ_COMMITTED)
  public Object prepare(ActionKind kind,ObjectNode input,Grant actor){
    require(kind.permits(actor.role()),"没有办理权限");
    var args=input.deepCopy();
    var thread=UUID.fromString(args.path("_conversation").asText()).toString();
    var run=UUID.fromString(args.path("_run").asText()).toString();
    args.remove(List.of("_conversation","_run"));
    kind.validate(args);
    var intent=args.deepCopy();
    booking.lockUser(actor.userId());
    var existing=db.queryForList("SELECT id, operation, summary, parameters FROM assistant_action WHERE user_id=? AND portal=? AND run_id=?",actor.userId(),actor.portal(),run);
    if(!existing.isEmpty()){
      var row=existing.get(0);
      require(kind.name().equals(row.get("operation")),"请另发一条消息创建新的办理方案");
      var previous=parameters(row).get("_intent");
      require(previous==null||sameIntent(previous,intent),"办理内容已改变，请另发一条消息重新核对");
      return link(actor,row.get("id").toString(),row.get("summary").toString(),kind);
    }
    var summary=executor.describe(kind,args,actor.role());
    args.set("_intent",intent);
    var id=UUID.randomUUID().toString();
    db.update("UPDATE assistant_action SET state='REPLACED' WHERE user_id=? AND portal=? AND conversation_id=? AND state='PENDING'",actor.userId(),actor.portal(),thread);
    db.update("INSERT INTO assistant_action(id,user_id,portal,actor_role,conversation_id,run_id,operation,parameters,summary,expires_at) VALUES(?,?,?,?,?,?,?,?,?,DATE_ADD(NOW(),INTERVAL 15 MINUTE))",
        id,actor.userId(),actor.portal(),actor.role(),thread,run,kind.name(),args.toString(),summary);
    return link(actor,id,summary,kind);
  }
  private boolean sameIntent(com.fasterxml.jackson.databind.JsonNode previous,ObjectNode intent){
    try{return previous.equals(json.readTree(json.writeValueAsBytes(intent)));}
    catch(java.io.IOException e){throw new IllegalStateException(e);}
  }
  private ObjectNode parameters(Map<String,Object> row){
    try{return (ObjectNode)json.readTree(row.get("parameters").toString());}
    catch(java.io.IOException e){throw new IllegalStateException(e);}
  }
  private Object link(Grant actor,String id,String summary,ActionKind kind){
    return Map.of("pageUrl","/"+actor.portal()+"/assistant/actions/"+id,"label","核对"+kind.label,"data",Map.of("summary",summary,"state","需本人点击确认，尚未执行"));
  }
  private Map<String,Object> row(AgentIdentity actor,UUID id,boolean lock){
    var rows=db.queryForList("SELECT *, expires_at<=NOW() AS expired FROM assistant_action WHERE id=? AND user_id=? AND portal=? AND actor_role=?"+(lock?" FOR UPDATE":""),id.toString(),actor.userId(),actor.portal(),actor.role());
    if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"确认单不存在");
    return rows.get(0);
  }
  private Object view(Map<String,Object> row){
    var state=row.get("state").toString();
    if("PENDING".equals(state)&&Integer.parseInt(row.get("expired").toString())!=0)state="EXPIRED";
    return Map.of("title",ActionKind.valueOf(row.get("operation").toString()).label,"summary",row.get("summary"),"state",state,"receipt",Objects.toString(row.get("receipt"),""));
  }
  public Object fetch(AgentIdentity actor,UUID id){return view(row(actor,id,false));}
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public Object decide(AgentIdentity actor,UUID id,boolean confirm){
    var card=row(actor,id,true);
    if(!"PENDING".equals(card.get("state")))return view(card);
    require(Integer.parseInt(card.get("expired").toString())==0,"确认单已过期，请重新生成");
    var kind=ActionKind.valueOf(card.get("operation").toString());
    require(kind.permits(actor.role()),"没有办理权限");
    String receipt="";
    if(confirm){
      receipt=executor.execute(kind,parameters(card),id.toString());
    }
    db.update("UPDATE assistant_action SET state=?,receipt=? WHERE id=?",confirm?"DONE":"CANCELLED",receipt,id.toString());
    return view(row(actor,id,false));
  }
}
