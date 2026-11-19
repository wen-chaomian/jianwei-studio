package com.shiguang.studio.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RedisReadCacheTest {
  private final ConcurrentMap<String, String> values = new ConcurrentHashMap<>();
  private StringRedisTemplate redis;
  private RedisReadCache cache;

  record Value(String value) {}

  @BeforeEach
  void setUp() {
    redis = new MemoryCacheRedis().redis;
    cache = new RedisReadCache(redis, new ObjectMapper(), new CacheProtection(4, 2, 50));
  }

  @AfterEach
  void cleanTransaction() {
    cache.close();
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  void concurrentColdReadersLoadDatabaseOnlyOnce() throws Exception {
    AtomicInteger loads = new AtomicInteger();
    try (ExecutorService executor = Executors.newFixedThreadPool(12)) {
      var futures = new ArrayList<Future<Value>>();
      for (int i = 0; i < 40; i++)
        futures.add(
            executor.submit(
                () ->
                    cache.get(
                        List.of("configuration"),
                        "catalog",
                        Value.class,
                        Duration.ofSeconds(30),
                        () -> {
                          loads.incrementAndGet();
                          return new Value("available");
                        })));
      for (var future : futures) assertEquals("available", future.get(5, TimeUnit.SECONDS).value());
    }
    assertEquals(1, loads.get());
  }

  @Test
  void committedInvalidationFencesOffAnAlreadyRunningOldLoad() throws Exception {
    AtomicInteger loads = new AtomicInteger();
    CountDownLatch started = new CountDownLatch(1), finish = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<Value> oldLoad =
          executor.submit(
              () ->
                  cache.get(
                      List.of("configuration"),
                      "catalog",
                      Value.class,
                      Duration.ofMinutes(1),
                      () -> {
                        started.countDown();
                        try {
                          assertTrue(finish.await(5, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                          throw new RuntimeException(e);
                        }
                        return new Value(loads.incrementAndGet() == 1 ? "old" : "new");
                      }));
      assertTrue(started.await(5, TimeUnit.SECONDS));
      cache.invalidateAfterCommit("configuration");
      finish.countDown();
      assertEquals("new", oldLoad.get(5, TimeUnit.SECONDS).value());
      Value afterCommit =
          cache.get(
              List.of("configuration"),
              "catalog",
              Value.class,
              Duration.ofMinutes(1),
              () -> new Value("new"));
      assertEquals(
          "new",
          afterCommit.value(),
          "An old in-flight read may finish, but cannot repopulate the current generation");
    }
  }

  @Test
  void rollbackDoesNotInvalidateAndTransactionReadsNeverPublishUncommittedValues() {
    cache.get(
        List.of("configuration"),
        "rules",
        Value.class,
        Duration.ofMinutes(1),
        () -> new Value("committed"));
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    cache.invalidateAfterCommit("configuration");
    assertEquals(
        "uncommitted",
        cache
            .get(
                List.of("configuration"),
                "rules",
                Value.class,
                Duration.ofMinutes(1),
                () -> new Value("uncommitted"))
            .value());
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(false);
    assertEquals(
        "committed",
        cache
            .get(
                List.of("configuration"),
                "rules",
                Value.class,
                Duration.ofMinutes(1),
                () -> fail("rolled back writes must not flush committed data"))
            .value());
  }

  @Test
  void invalidationWaitsForSuccessfulCommit() {
    cache.get(
        List.of("configuration"),
        "rules",
        Value.class,
        Duration.ofMinutes(1),
        () -> new Value("before"));
    TransactionSynchronizationManager.initSynchronization();
    cache.invalidateAfterCommit("configuration");
    assertEquals(
        "before",
        cache
            .get(
                List.of("configuration"),
                "rules",
                Value.class,
                Duration.ofMinutes(1),
                () -> fail("not committed"))
            .value());
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    assertEquals(
        "after",
        cache
            .get(
                List.of("configuration"),
                "rules",
                Value.class,
                Duration.ofMinutes(1),
                () -> new Value("after"))
            .value());
  }

  @Test
  void redisOutageFallsBackToDatabase() {
    when(redis.opsForValue().multiGet(anyCollection()))
        .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("offline"));
    assertEquals(
        "database",
        cache
            .get(
                List.of("configuration"),
                "rules",
                Value.class,
                Duration.ofMinutes(1),
                () -> new Value("database"))
            .value());
    assertDoesNotThrow(() -> cache.invalidateAfterCommit("configuration"));
  }
}
