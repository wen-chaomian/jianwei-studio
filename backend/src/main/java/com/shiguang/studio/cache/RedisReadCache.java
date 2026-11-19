package com.shiguang.studio.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

/** Generation-fenced cache with negative entries, bounded rebuilding and strict read defaults. */
@Component
public class RedisReadCache {
  private static final String PREFIX = "shiguang:{read-cache-v2}:";
  private static final String EMPTY = "~empty:v1~";
  private static final long LEASE_MS = 30000, WAIT_MS = 500;
  private static final DefaultRedisScript<Long> RELEASE =
      new DefaultRedisScript<>(
          "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end;return"
              + " 0",
          Long.class);
  private static final DefaultRedisScript<Long> RENEW =
      new DefaultRedisScript<>(
          "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('PEXPIRE',KEYS[1],ARGV[2])"
              + " end;return 0",
          Long.class);
  private static final DefaultRedisScript<Long> STORE =
      new DefaultRedisScript<>(
          "if redis.call('GET',KEYS[1])==ARGV[1] then"
              + " redis.call('SET',KEYS[2],ARGV[2],'PX',ARGV[3]);return 1 end;return 0",
          Long.class);
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final CacheProtection protection;
  private final ConcurrentMap<String, CompletableFuture<Object>> inFlight =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, String> pending = new ConcurrentHashMap<>();
  private final Map<String, Long> originFailures =
      Collections.synchronizedMap(new LinkedHashMap<>());
  private final Map<String, LocalValue> display = new LinkedHashMap<>(16, .75f, true);
  private final ScheduledExecutorService maintenance =
      Executors.newScheduledThreadPool(
          2,
          r -> {
            Thread t = new Thread(r, "cache-maintenance");
            t.setDaemon(true);
            return t;
          });
  private volatile long retryAt;
  private int failures;

  private record LocalValue(Object value, long expires) {}

  private record Hit<T>(boolean found, T value) {}

  private static final class RedisUnavailable extends RuntimeException {}

  public RedisReadCache(StringRedisTemplate redis, ObjectMapper json, CacheProtection protection) {
    this.redis = redis;
    this.json = json;
    this.protection = protection;
    maintenance.scheduleWithFixedDelay(
        () -> {
          if (!pending.isEmpty() && System.currentTimeMillis() >= retryAt)
            try {
              flushInvalidations();
            } catch (RedisUnavailable ignored) {
            }
        },
        1,
        1,
        TimeUnit.SECONDS);
  }

  public <T> T get(
      List<String> scopes, String name, Class<T> type, Duration ttl, Supplier<T> loader) {
    return get(scopes, name, type, ttl, loader, "business", false);
  }

  public <T> T getDisplay(
      List<String> scopes, String name, Class<T> type, Duration ttl, Supplier<T> loader) {
    return get(scopes, name, type, ttl, loader, "business", true);
  }

  public <T> T getAccount(
      List<String> scopes, String name, Class<T> type, Duration ttl, Supplier<T> loader) {
    return get(scopes, name, type, ttl, loader, "account", false);
  }

  private <T> T get(
      List<String> scopes,
      String name,
      Class<T> type,
      Duration ttl,
      Supplier<T> loader,
      String lane,
      boolean allowStale) {
    // Authoritative reads in write transactions never publish uncommitted data.
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      protection.count(lane, "transactionBypass");
      return loader.get();
    }
    String identity = lane + ":" + name + ":" + String.join(",", scopes);
    String flightKey = identity + ":" + allowStale;
    CompletableFuture<Object> own = new CompletableFuture<>(),
        other = inFlight.putIfAbsent(flightKey, own);
    if (other != null) {
      protection.count(lane, "coalescedWait");
      try {
        return type.cast(other.get(WAIT_MS, TimeUnit.MILLISECONDS));
      } catch (TimeoutException ex) {
        protection.count(lane, "waitRejected");
        return staleOrThrow(identity, type, allowStale, lane);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new CacheBusyException();
      } catch (ExecutionException ex) {
        if (ex.getCause() instanceof CacheBusyException)
          return staleOrThrow(identity, type, allowStale, lane);
        throw propagate(ex.getCause());
      }
    }
    try {
      T value;
      try {
        if (System.currentTimeMillis() < retryAt) throw new RedisUnavailable();
        if (!pending.isEmpty()) flushInvalidations();
        value = cached(scopes, identity, type, ttl, loader, lane, allowStale);
      } catch (RedisUnavailable ex) {
        protection.count(lane, "redisFallback");
        LocalValue old = allowStale ? local(identity) : null;
        if (old != null) {
          protection.count(lane, "staleServed");
          value = type.cast(old.value());
        } else value = origin(identity, lane, loader);
      }
      own.complete(value);
      return value;
    } catch (CacheBusyException ex) {
      try {
        T value = staleOrThrow(identity, type, allowStale, lane);
        own.complete(value);
        return value;
      } catch (CacheBusyException unavailable) {
        own.completeExceptionally(unavailable);
        throw unavailable;
      }
    } catch (Throwable ex) {
      own.completeExceptionally(ex);
      throw propagate(ex);
    } finally {
      inFlight.remove(flightKey, own);
    }
  }

  private <T> T cached(
      List<String> scopes,
      String identity,
      Class<T> type,
      Duration ttl,
      Supplier<T> loader,
      String lane,
      boolean allowStale) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
    for (int generationAttempt = 0; generationAttempt < 3; generationAttempt++) {
      String key = key(scopes, identity);
      Hit<T> hit = read(key, type);
      if (hit.found() && key.equals(key(scopes, identity))) {
        rememberHit(identity, key, hit.value(), allowStale);
        protection.count(lane, hit.value() == null ? "emptyHit" : "hit");
        healthy();
        return hit.value();
      }
      protection.count(lane, "miss");
      String token = UUID.randomUUID().toString(), lock = key + ":loading";
      while (!tryLock(lock, token)) {
        protection.count(lane, "rebuildWait");
        if (System.nanoTime() >= deadline) {
          protection.count(lane, "waitRejected");
          throw new CacheBusyException();
        }
        try {
          Thread.sleep(15);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new CacheBusyException();
        }
        key = key(scopes, identity);
        hit = read(key, type);
        if (hit.found() && key.equals(key(scopes, identity))) {
          rememberHit(identity, key, hit.value(), allowStale);
          protection.count(lane, hit.value() == null ? "emptyHit" : "hit");
          healthy();
          return hit.value();
        }
        lock = key + ":loading";
      }
      final String heldLock = lock;
      ScheduledFuture<?> renew =
          maintenance.scheduleAtFixedRate(
              () -> {
                try {
                  io(() -> redis.execute(RENEW, List.of(heldLock), token, Long.toString(LEASE_MS)));
                } catch (RedisUnavailable ignored) {
                }
              },
              10,
              10,
              TimeUnit.SECONDS);
      try {
        // Recheck after acquiring ownership; another process may just have completed the load.
        hit = read(key, type);
        if (hit.found() && key.equals(key(scopes, identity))) {
          rememberHit(identity, key, hit.value(), allowStale);
          healthy();
          return hit.value();
        }
        T value = origin(identity, lane, loader);
        try {
          if (!key.equals(key(scopes, identity))) {
            protection.count(lane, "generationRetry");
            continue;
          }
          long lifetime =
              value == null
                  ? ThreadLocalRandom.current().nextLong(15000, 30001)
                  : ttl.toMillis()
                      + ThreadLocalRandom.current().nextLong(Math.max(2, ttl.toMillis() / 10));
          String encoded = value == null ? EMPTY : json.writeValueAsString(value);
          final String target = key;
          Long stored =
              io(
                  () ->
                      redis.execute(
                          STORE,
                          List.of(heldLock, target),
                          token,
                          encoded,
                          Long.toString(lifetime)));
          if (!key.equals(key(scopes, identity))) {
            protection.count(lane, "generationRetry");
            continue;
          }
          if (!Long.valueOf(1).equals(stored)) {
            protection.count(lane, "leaseLost");
            throw new CacheBusyException();
          }
          if (allowStale && value != null)
            remember(identity, value, System.currentTimeMillis() + lifetime + 30000);
          healthy();
        } catch (RedisUnavailable ex) {
          /* Already loaded authoritative data: never execute the database query twice. */
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
          protection.count(lane, "serializationFailure");
        }
        return value;
      } finally {
        renew.cancel(false);
        try {
          io(() -> redis.execute(RELEASE, List.of(heldLock), token));
        } catch (RedisUnavailable ignored) {
        }
      }
    }
    throw new CacheBusyException();
  }

  private <T> T origin(String identity, String lane, Supplier<T> loader) {
    Long until = originFailures.get(identity);
    if (until != null && until > System.currentTimeMillis()) {
      protection.count(lane, "originBackoff");
      throw new CacheBusyException();
    }
    try {
      T value = protection.load(lane, loader);
      originFailures.remove(identity);
      return value;
    } catch (org.springframework.dao.DataAccessException ex) {
      synchronized (originFailures) {
        originFailures.put(identity, System.currentTimeMillis() + 1000);
        while (originFailures.size() > 1024)
          originFailures.remove(originFailures.keySet().iterator().next());
      }
      throw ex;
    }
  }

  private boolean tryLock(String key, String token) {
    return Boolean.TRUE.equals(
        io(() -> redis.opsForValue().setIfAbsent(key, token, Duration.ofMillis(LEASE_MS))));
  }

  private void rememberHit(String identity, String key, Object value, boolean allowed) {
    if (!allowed || value == null) return;
    try {
      Long ttl = io(() -> redis.getExpire(key, TimeUnit.MILLISECONDS));
      if (ttl != null && ttl > 0)
        remember(identity, value, System.currentTimeMillis() + ttl + 30000);
    } catch (RedisUnavailable ignored) {
    }
  }

  private String key(List<String> scopes, String identity) {
    List<String> values =
        io(
            () ->
                redis
                    .opsForValue()
                    .multiGet(scopes.stream().map(s -> PREFIX + "generation:" + s).toList()));
    String generation =
        values == null
            ? "0"
            : String.join(".", values.stream().map(v -> v == null ? "0" : v).toList());
    return PREFIX + "value:" + identity + ":" + generation;
  }

  private <T> Hit<T> read(String key, Class<T> type) {
    String raw = io(() -> redis.opsForValue().get(key));
    if (raw == null) return new Hit<>(false, null);
    if (EMPTY.equals(raw)) return new Hit<>(true, null);
    try {
      return new Hit<>(true, json.readValue(raw, type));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      protection.count("business", "corruptEntry");
      return new Hit<>(false, null);
    }
  }

  private <T> T io(Supplier<T> action) {
    try {
      return action.get();
    } catch (org.springframework.dao.DataAccessException ex) {
      failed();
      throw new RedisUnavailable();
    }
  }

  private synchronized void failed() {
    failures = Math.min(failures + 1, 5);
    retryAt = System.currentTimeMillis() + Math.min(10000, 250L * (1 << failures));
    protection.count("redis", "failure");
  }

  private synchronized void healthy() {
    failures = 0;
    retryAt = 0;
  }

  public void invalidateAfterCommit(String... scopes) {
    Runnable action =
        () -> {
          synchronized (display) {
            display.clear();
          }
          Arrays.stream(scopes)
              .distinct()
              .forEach(s -> pending.put(s, UUID.randomUUID().toString()));
          try {
            flushInvalidations();
          } catch (RedisUnavailable ignored) {
          }
        };
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    else action.run();
  }

  private synchronized void flushInvalidations() {
    if (pending.isEmpty()) return;
    Map<String, String> batch = new HashMap<>(pending), updates = new HashMap<>();
    batch.forEach((k, v) -> updates.put(PREFIX + "generation:" + k, v));
    io(
        () -> {
          redis.opsForValue().multiSet(updates);
          return true;
        });
    batch.forEach((k, v) -> pending.remove(k, v));
    healthy();
  }

  private void remember(String identity, Object value, long expires) {
    synchronized (display) {
      display.put(identity, new LocalValue(value, expires));
      while (display.size() > 64) display.remove(display.keySet().iterator().next());
    }
  }

  private LocalValue local(String identity) {
    synchronized (display) {
      LocalValue v = display.get(identity);
      if (v != null && v.expires() > System.currentTimeMillis()) return v;
      display.remove(identity);
      return null;
    }
  }

  private <T> T staleOrThrow(String identity, Class<T> type, boolean allowed, String lane) {
    LocalValue v = allowed ? local(identity) : null;
    if (v == null) throw new CacheBusyException();
    protection.count(lane, "staleServed");
    return type.cast(v.value());
  }

  private RuntimeException propagate(Throwable e) {
    if (e instanceof RuntimeException r) return r;
    if (e instanceof Error error) throw error;
    return new IllegalStateException(e);
  }

  @PreDestroy
  public void close() {
    maintenance.shutdownNow();
  }
}
