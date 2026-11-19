package com.shiguang.studio.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ServerSocket;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "RUN_CACHE_IT", matches = "true")
class CacheProtectionIntegrationTest {
  Process process;
  int port;
  Path dir;
  LettuceConnectionFactory factory;
  StringRedisTemplate redis;
  RedisReadCache a, b;
  CacheProtection guard;

  record Value(String text) {}

  @BeforeEach
  void start() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    dir = Files.createTempDirectory("studio-cache-it-");
    startRedis();
    factory =
        new LettuceConnectionFactory(
            new org.springframework.data.redis.connection.RedisStandaloneConfiguration(
                "127.0.0.1", port),
            org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(200))
                .build());
    factory.afterPropertiesSet();
    factory.start();
    redis = new StringRedisTemplate(factory);
    guard = new CacheProtection(4, 2, 30);
    a = new RedisReadCache(redis, new ObjectMapper(), guard);
    b = new RedisReadCache(redis, new ObjectMapper(), new CacheProtection(4, 2, 30));
  }

  void startRedis() throws Exception {
    process =
        new ProcessBuilder(
                System.getenv().getOrDefault("REDIS_TEST_BIN", "redis-server"),
                "--bind",
                "127.0.0.1",
                "--port",
                "" + port,
                "--save",
                "",
                "--appendonly",
                "no",
                "--dir",
                dir.toString())
            .redirectErrorStream(true)
            .redirectOutput(dir.resolve("redis.log").toFile())
            .start();
    for (int i = 0; i < 100; i++) {
      try (var socket = new java.net.Socket("127.0.0.1", port)) {
        return;
      } catch (Exception e) {
        Thread.sleep(20);
      }
    }
    throw new IllegalStateException("Redis failed to start");
  }

  @AfterEach
  void stop() throws Exception {
    if (a != null) a.close();
    if (b != null) b.close();
    if (factory != null) factory.destroy();
    if (process != null) {
      process.destroy();
      if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
    }
  }

  Value read(RedisReadCache c, java.util.function.Supplier<Value> loader) {
    return c.get(List.of("catalog"), "one", Value.class, Duration.ofMillis(100), loader);
  }

  @Test
  void twoInstancesRebuildExpiredHotKeyOnce() throws Exception {
    read(a, () -> new Value("old"));
    Thread.sleep(150);
    AtomicInteger loads = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(20)) {
      List<Future<Value>> work = new ArrayList<>();
      for (int i = 0; i < 40; i++) {
        var cache = i % 2 == 0 ? a : b;
        work.add(
            pool.submit(
                () ->
                    read(
                        cache,
                        () -> {
                          loads.incrementAndGet();
                          try {
                            Thread.sleep(100);
                          } catch (Exception e) {
                            throw new RuntimeException(e);
                          }
                          return new Value("fresh");
                        })));
      }
      for (var f : work) assertEquals("fresh", f.get(5, TimeUnit.SECONDS).text());
    }
    assertEquals(1, loads.get());
  }

  @Test
  void nullHasShortTtlAndCreationInvalidatesIt() {
    AtomicInteger loads = new AtomicInteger();
    for (int i = 0; i < 10; i++)
      assertNull(
          read(
              i % 2 == 0 ? a : b,
              () -> {
                loads.incrementAndGet();
                return null;
              }));
    assertEquals(1, loads.get());
    String key = redis.keys("*value:*").iterator().next();
    Long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
    assertTrue(ttl >= 14000 && ttl <= 30000);
    a.invalidateAfterCommit("catalog");
    assertEquals("created", read(b, () -> new Value("created")).text());
  }

  @Test
  void slowLoadDoesNotLetOtherInstanceHitDatabase() throws Exception {
    CountDownLatch started = new CountDownLatch(1), finish = new CountDownLatch(1);
    AtomicInteger loads = new AtomicInteger();
    try (var pool = Executors.newSingleThreadExecutor()) {
      var f =
          pool.submit(
              () ->
                  read(
                      a,
                      () -> {
                        loads.incrementAndGet();
                        started.countDown();
                        try {
                          finish.await(3, TimeUnit.SECONDS);
                        } catch (Exception e) {
                          throw new RuntimeException(e);
                        }
                        return new Value("fresh");
                      }));
      assertTrue(started.await(2, TimeUnit.SECONDS));
      try {
        assertThrows(
            CacheBusyException.class,
            () ->
                read(
                    b,
                    () -> {
                      loads.incrementAndGet();
                      return new Value("wrong");
                    }));
        assertEquals(1, loads.get());
      } finally {
        finish.countDown();
      }
      assertEquals("fresh", f.get().text());
    }
  }

  @Test
  void simultaneousMissesRespectBulkheadAndReserveAccountCapacity() throws Exception {
    CountDownLatch entered = new CountDownLatch(4), finish = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(4)) {
      List<Future<Value>> work = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        final int id = i;
        work.add(
            pool.submit(
                () ->
                    a.get(
                        List.of("catalog"),
                        "key" + id,
                        Value.class,
                        Duration.ofMinutes(1),
                        () -> {
                          entered.countDown();
                          try {
                            finish.await(3, TimeUnit.SECONDS);
                          } catch (Exception e) {
                            throw new RuntimeException(e);
                          }
                          return new Value("ok");
                        })));
      }
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      try {
        assertThrows(
            CacheBusyException.class,
            () ->
                a.get(
                    List.of("catalog"),
                    "overflow",
                    Value.class,
                    Duration.ofMinutes(1),
                    () -> fail("must not query DB")));
        assertEquals(
            "account",
            a.getAccount(
                    List.of("account"),
                    "user",
                    Value.class,
                    Duration.ofSeconds(15),
                    () -> new Value("account"))
                .text());
        assertEquals(4L, guard.snapshot().get("business.active"));
      } finally {
        finish.countDown();
      }
      for (var f : work) f.get();
    }
    assertEquals(0L, guard.snapshot().get("business.active"));
  }

  @Test
  void originFailureIsNotNullCachedOrRepeatedImmediately() {
    AtomicInteger loads = new AtomicInteger();
    assertThrows(
        org.springframework.dao.DataAccessResourceFailureException.class,
        () ->
            read(
                a,
                () -> {
                  loads.incrementAndGet();
                  throw new org.springframework.dao.DataAccessResourceFailureException(
                      "db offline");
                }));
    assertThrows(
        CacheBusyException.class,
        () ->
            read(
                a,
                () -> {
                  loads.incrementAndGet();
                  return null;
                }));
    assertEquals(1, loads.get());
    assertTrue(redis.keys("*value:*").isEmpty());
  }

  @Test
  void outageAndRecoveryKeepStrictReadsFresh() throws Exception {
    a.getDisplay(
        List.of("catalog"),
        "display",
        Value.class,
        Duration.ofMinutes(1),
        () -> new Value("display-old"));
    read(a, () -> new Value("strict-old"));
    process.destroy();
    assertTrue(process.waitFor(3, TimeUnit.SECONDS));
    assertEquals(
        "display-old",
        a.getDisplay(
                List.of("catalog"),
                "display",
                Value.class,
                Duration.ofMinutes(1),
                () -> fail("display snapshot expected"))
            .text());
    assertEquals("strict-new", read(a, () -> new Value("strict-new")).text());
    a.invalidateAfterCommit("catalog");
    startRedis();
    Thread.sleep(2100);
    assertEquals("recovered", read(a, () -> new Value("recovered")).text());
    assertEquals("recovered", read(b, () -> fail("cache should recover")).text());
  }

  @Test
  void rebuildLeaseIsRenewedWhileOriginIsSlow() throws Exception {
    CountDownLatch started = new CountDownLatch(1), finish = new CountDownLatch(1);
    try (var pool = Executors.newSingleThreadExecutor()) {
      var f =
          pool.submit(
              () ->
                  read(
                      a,
                      () -> {
                        started.countDown();
                        try {
                          finish.await(15, TimeUnit.SECONDS);
                        } catch (Exception e) {
                          throw new RuntimeException(e);
                        }
                        return new Value("renewed");
                      }));
      assertTrue(started.await(2, TimeUnit.SECONDS));
      try {
        Thread.sleep(11000);
        String key = redis.keys("*:loading").iterator().next();
        assertTrue(
            redis.getExpire(key, TimeUnit.MILLISECONDS) > 26000,
            "Lease must be renewed after ten seconds");
      } finally {
        finish.countDown();
      }
      assertEquals("renewed", f.get().text());
      assertTrue(redis.keys("*:loading").isEmpty());
    }
  }
}
