package com.shiguang.studio.cache;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Deterministic Redis double. Real Lua/expiry behavior is tested separately against isolated Redis.
 */
public class MemoryCacheRedis {
  public final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  public final ConcurrentMap<String, String> values = new ConcurrentHashMap<>();
  public final ConcurrentMap<String, Long> expires = new ConcurrentHashMap<>();
  public volatile boolean unavailable;
  public Runnable beforeStore;

  void check() {
    if (unavailable)
      throw new org.springframework.data.redis.RedisConnectionFailureException("offline");
  }

  String read(String k) {
    Long end = expires.get(k);
    if (end != null && end <= System.currentTimeMillis()) {
      values.remove(k);
      expires.remove(k);
    }
    return values.get(k);
  }

  @SuppressWarnings("unchecked")
  public MemoryCacheRedis() {
    ValueOperations<String, String> ops = mock(ValueOperations.class);
    when(redis.opsForValue())
        .thenAnswer(
            i -> {
              check();
              return ops;
            });
    when(ops.get(anyString()))
        .thenAnswer(
            i -> {
              check();
              return read(i.getArgument(0));
            });
    when(ops.multiGet(anyCollection()))
        .thenAnswer(
            i -> {
              check();
              return ((Collection<String>) i.getArgument(0)).stream().map(this::read).toList();
            });
    doAnswer(
            i -> {
              check();
              Map<String, String> m = i.getArgument(0);
              values.putAll(m);
              return null;
            })
        .when(ops)
        .multiSet(anyMap());
    when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .thenAnswer(
            i -> {
              check();
              synchronized (values) {
                String k = i.getArgument(0);
                if (read(k) != null) return false;
                values.put(k, i.getArgument(1));
                expires.put(
                    k, System.currentTimeMillis() + ((Duration) i.getArgument(2)).toMillis());
                return true;
              }
            });
    when(redis.getExpire(anyString(), eq(TimeUnit.MILLISECONDS)))
        .thenAnswer(
            i -> {
              check();
              return expires.getOrDefault(i.getArgument(0), System.currentTimeMillis())
                  - System.currentTimeMillis();
            });
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenAnswer(
            i -> {
              check();
              String script = ((RedisScript<?>) i.getArgument(0)).getScriptAsString();
              List<String> keys = i.getArgument(1);
              String token = i.getArgument(2);
              synchronized (values) {
                if (!token.equals(read(keys.get(0)))) return 0L;
                if (script.contains("'SET',KEYS[2]")) {
                  if (beforeStore != null) {
                    Runnable hook = beforeStore;
                    beforeStore = null;
                    hook.run();
                  }
                  values.put(keys.get(1), i.getArgument(3));
                  expires.put(
                      keys.get(1), System.currentTimeMillis() + Long.parseLong(i.getArgument(4)));
                  return 1L;
                }
                if (script.contains("'PEXPIRE'")) {
                  expires.put(
                      keys.get(0), System.currentTimeMillis() + Long.parseLong(i.getArgument(3)));
                  return 1L;
                }
                values.remove(keys.get(0));
                expires.remove(keys.get(0));
                return 1L;
              }
            });
  }
}
