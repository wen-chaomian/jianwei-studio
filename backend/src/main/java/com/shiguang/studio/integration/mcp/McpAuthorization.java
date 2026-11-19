package com.shiguang.studio.integration.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.studio.integration.agent.AgentIdentity;
import com.shiguang.studio.security.AccountStateCache;
import java.time.Duration;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Component
public class McpAuthorization {
  public record Grant(long userId, String portal, String role, String sessionId, int version) {}
  private final StringRedisTemplate redis;
  private final SessionRepository<?> sessions;
  private final AccountStateCache accounts;
  private final ObjectMapper json;
  public McpAuthorization(StringRedisTemplate redis, SessionRepository<?> sessions,
      AccountStateCache accounts, ObjectMapper json) {
    this.redis=redis; this.sessions=sessions; this.accounts=accounts; this.json=json;
  }
  public String issue(AgentIdentity actor) {
    var attrs = RequestContextHolder.getRequestAttributes();
    if (!(attrs instanceof ServletRequestAttributes servlet)) throw denied();
    var session=servlet.getRequest().getSession(false);
    if(session==null || !(session.getAttribute("accountVersion") instanceof Integer version)) throw denied();
    var grant=new Grant(actor.userId(),actor.portal(),actor.role(),session.getId(),version);
    String key=UUID.randomUUID()+"-"+UUID.randomUUID();
    try {redis.opsForValue().set("jianwei:mcp:grant:"+key,json.writeValueAsString(grant),Duration.ofMinutes(15));}
    catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"查询授权暂不可用");}
    return key;
  }
  public Grant verify(String bearer) {
    if(bearer==null || !bearer.matches("Bearer [0-9a-f-]{73}")) throw denied();
    try {
      String raw=redis.opsForValue().get("jianwei:mcp:grant:"+bearer.substring(7));
      if(raw==null) throw denied();
      var g=json.readValue(raw,Grant.class);
      var session=sessions.findById(g.sessionId());
      var state=accounts.get(g.userId());
      if(session==null || session.isExpired() || !state.enabled() || state.version()!=g.version()
          || !Objects.equals(session.getAttribute("accountVersion"),g.version())) throw denied();
      SecurityContext context=session.getAttribute("SPRING_SECURITY_CONTEXT");
      if(context==null || context.getAuthentication()==null
          || !context.getAuthentication().getName().equals(Long.toString(g.userId()))
          || !state.user().roles().contains(g.role())
          || !context.getAuthentication().getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+g.role()))) throw denied();
      return g;
    } catch(ResponseStatusException ex){throw ex;}
    catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"查询授权校验暂不可用");}
  }
  private ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"登录已失效，请重新登录后查询");}
}
