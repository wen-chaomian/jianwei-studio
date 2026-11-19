package com.shiguang.studio.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.studio.integration.agent.*;
import com.shiguang.studio.security.AccountStateCache;
import jakarta.servlet.http.HttpSession;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AgentChatService {
  private final AgentClient client;
  private final AccountStateCache accounts;
  private final SessionRepository<?> sessions;
  private final ObjectMapper json;
  private final AgentMqGateway mq;
  private final AgentResultCache results;
  private final Semaphore streams = new Semaphore(48);
  private final java.util.concurrent.ExecutorService executor =
      Executors.newVirtualThreadPerTaskExecutor();

  public AgentChatService(
      AgentClient client,
      AccountStateCache accounts,
      SessionRepository<?> sessions,
      ObjectMapper json, AgentMqGateway mq, AgentResultCache results) {
    this.client = client;
    this.accounts = accounts;
    this.sessions = sessions;
    this.json = json;
    this.mq = mq; this.results = results;
  }

  public JsonNode call(AgentIdentity actor, String method, String path, Object body) {
    var result = client.call(actor, method, path, body);
    if (method.equals("POST") && path.endsWith("/messages"))
      mq.dispatch(UUID.fromString(result.path("id").asText()), "generate");
    else if (method.equals("POST") && path.endsWith("/stop"))
      mq.dispatch(UUID.fromString(result.path("id").asText()), "stop");
    return result;
  }

  public JsonNode admin(AgentIdentity actor, String method, String path, Object body) {
    if (!actor.portal().equals("admin") || !actor.role().equals("ADMIN"))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可配置模型");
    return client.call(actor, method, path, body);
  }

  public SseEmitter events(AgentIdentity actor, UUID id, HttpSession session) {
    if (!streams.tryAcquire())
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "对话连接较多，请稍后重试");
    JsonNode initial;
    try {
      // Always verify ownership against authoritative storage before opening an SSE stream.
      initial = client.call(actor, "GET", "/runs/" + id, null);
    } catch (RuntimeException e) {
      streams.release();
      throw e;
    }
    String sessionId = session.getId();
    Object version = session.getAttribute("accountVersion");
    var emitter = new SseEmitter(180000L);
    var closed = new AtomicBoolean();
    Runnable close =
        () -> {
          if (closed.compareAndSet(false, true)) {
            streams.release();
          }
        };
    emitter.onCompletion(close);
    emitter.onTimeout(
        () -> {
          close.run();
          emitter.complete();
        });
    emitter.onError(e -> close.run());
    executor.submit(
        () -> {
          try {
            long revision=-1, heartbeat=0;
            JsonNode latest=initial;
            while (!closed.get()) {
              var current = sessions.findById(sessionId);
              var state = accounts.get(actor.userId());
              if (current == null
                  || current.isExpired()
                  || !state.enabled()
                  || !Objects.equals(version, state.version())) {
                emitter.send(
                    SseEmitter.event()
                        .name("unauthorized")
                        .data(Map.of("message", "登录状态已失效，请重新登录")));
                break;
              }
              JsonNode cached=results.find(actor,id);
              if(cached!=null && cached.path("revision").asLong()>latest.path("revision").asLong()) latest=cached;
              if(latest.path("revision").asLong()!=revision){
                revision=latest.path("revision").asLong();
                emitter.send(SseEmitter.event().name("snapshot").data(latest));
              }
              if(!latest.path("status").asText().equals("running")) break;
              if(++heartbeat%10==0)emitter.send(SseEmitter.event().comment("heartbeat"));
              // Recovery snapshot only once per 30 seconds, not a database poll per token.
              if(heartbeat%60==0){
                var recovered=client.call(actor,"GET","/runs/"+id,null);
                if(recovered.path("revision").asLong()>latest.path("revision").asLong())latest=recovered;
              }
              Thread.sleep(500);
            }
            emitter.complete();
          } catch (Exception e) {
            emitter.completeWithError(new IOException("对话连接中断，请重新连接"));
          } finally {
            close.run();
          }
        });
    return emitter;
  }

  @jakarta.annotation.PreDestroy
  public void close() {
    executor.close();
  }
}
