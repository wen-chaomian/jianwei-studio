package com.shiguang.studio.cache;

import com.shiguang.studio.security.AccountStateCache;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Sends only opaque version notifications. Business data remains behind authorized HTTP APIs. */
@Component
public class SyncEventStreams {
  private final SyncVersions versions;
  private final AccountStateCache accounts;
  private final SessionRepository<? extends Session> sessions;
  private final Set<Client> clients = ConcurrentHashMap.newKeySet();
  private final ScheduledExecutorService clock =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            var t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
          });
  private final ExecutorService delivery =
      new ThreadPoolExecutor(
          2,
          4,
          30,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(256),
          r -> {
            var t = new Thread(r, "sse-delivery");
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.AbortPolicy());

  private static class Client {
    final Authentication auth;
    final String sessionId;
    final Object accountVersion;
    final boolean catalog;
    final SseEmitter emitter = new SseEmitter(120000L);
    String lastVersion;
    final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();

    Client(Authentication auth, HttpSession session, boolean catalog) {
      this.auth = auth;
      this.catalog = catalog;
      sessionId = session == null ? null : session.getId();
      accountVersion = session == null ? null : session.getAttribute("accountVersion");
    }
  }

  public SyncEventStreams(
      SyncVersions versions,
      AccountStateCache accounts,
      SessionRepository<? extends Session> sessions) {
    this.versions = versions;
    this.accounts = accounts;
    this.sessions = sessions;
    clock.scheduleWithFixedDelay(() -> clients.forEach(this::enqueue), 15, 15, TimeUnit.SECONDS);
  }

  public SseEmitter open(
      Authentication auth, HttpSession session, boolean catalog, HttpServletResponse res) {
    if (!catalog && (auth == null || session == null))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    synchronized (clients) {
      if (clients.size() >= 500)
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "实时连接繁忙，请稍后重试");
      Client c = new Client(auth, session, catalog);
      clients.add(c);
      c.emitter.onCompletion(() -> remove(c));
      c.emitter.onTimeout(() -> close(c));
      c.emitter.onError(e -> remove(c));
      res.setHeader("Cache-Control", "no-store");
      res.setHeader("X-Accel-Buffering", "no");
      enqueue(c);
      return c.emitter;
    }
  }

  public void changed(Set<String> scopes) {
    clients.stream().filter(c -> interested(c.auth, c.catalog, scopes)).forEach(this::enqueue);
  }

  static boolean interested(Authentication auth, boolean catalog, Set<String> scopes) {
    if (scopes.contains("configuration") || (catalog && scopes.contains("availability")))
      return true;
    if (catalog || auth == null) return false;
    if (scopes.contains("user:" + auth.getName())) return true;
    return auth.getAuthorities().stream()
        .anyMatch(
            a ->
                (Set.of("ROLE_ADMIN", "ROLE_RECEPTIONIST").contains(a.getAuthority())
                        && scopes.contains("desk"))
                    || (a.getAuthority().equals("ROLE_PHOTOGRAPHER")
                        && scopes.contains("schedule")));
  }

  private void enqueue(Client c) {
    try {
      delivery.execute(() -> send(c));
    } catch (RejectedExecutionException ex) {
      close(c);
    }
  }

  private void send(Client c) {
    synchronized (c) {
      if (c.closed.get()) return;
      try {
        if (!c.catalog) {
          Session session = sessions.findById(c.sessionId);
          if (session == null
              || session.isExpired()
              || !Objects.equals(c.accountVersion, session.getAttribute("accountVersion"))) {
            close(c);
            return;
          }
          var state = accounts.get(Long.parseLong(c.auth.getName()));
          if (!state.enabled() || !Objects.equals(c.accountVersion, state.version())) {
            close(c);
            return;
          }
        }
        var snapshot = versions.snapshot(c.auth, c.catalog);
        if (!snapshot.available()) {
          close(c);
          return;
        }
        try {
          if (!Objects.equals(c.lastVersion, snapshot.version())) {
            c.emitter.send(SseEmitter.event().name("version").id(snapshot.version()).data(snapshot));
            c.lastVersion = snapshot.version();
          } else c.emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (java.io.IOException | IllegalStateException disconnected) {
          // The servlet container owns completion after a failed write.
          remove(c);
        }
      } catch (Exception ex) {
        close(c);
      }
    }
  }

  private boolean remove(Client c) {
    if (!c.closed.compareAndSet(false, true)) return false;
    clients.remove(c);
    return true;
  }

  private void close(Client c) {
    if (!remove(c)) return;
    try {
      c.emitter.complete();
    } catch (IllegalStateException alreadyFinished) {
      // The container can finish the async request before its callback reaches us.
    }
  }

  @PreDestroy
  public void shutdown() {
    clock.shutdownNow();
    clients.forEach(this::close);
    delivery.shutdownNow();
  }
}
