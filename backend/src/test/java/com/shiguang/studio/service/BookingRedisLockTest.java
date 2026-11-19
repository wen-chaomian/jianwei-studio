package com.shiguang.studio.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.shiguang.studio.cache.BookingReadCache;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

class BookingRedisLockTest {
  StringRedisTemplate redis;
  BookingRedisLock guard;

  @BeforeEach
  void setup() {
    redis = mock(StringRedisTemplate.class);
    guard = new BookingRedisLock(redis, mock(BookingReadCache.class));
  }

  @AfterEach
  void close() {
    guard.close();
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.clearSynchronization();
  }

  @Test
  void contentionDoesNotEnterBusinessTransaction() {
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
    AtomicBoolean called = new AtomicBoolean();
    var e =
        assertThrows(
            ResponseStatusException.class,
            () ->
                guard.execute(
                    List.of("p"),
                    () -> {
                      called.set(true);
                      return 1;
                    }));
    assertEquals(409, e.getStatusCode().value());
    assertFalse(called.get());
  }

  @Test
  void unavailableRedisFailsClosed() {
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
    var e = assertThrows(ResponseStatusException.class, () -> guard.acquire(List.of("p")));
    assertEquals(503, e.getStatusCode().value());
  }

  @Test
  void supplierFailureReleasesOnlyOwnedToken() {
    List<String> tokens = new ArrayList<>();
    List<String> scripts = new ArrayList<>();
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenAnswer(
            i -> {
              scripts.add(((RedisScript<?>) i.getArgument(0)).getScriptAsString());
              tokens.add(i.getArgument(2));
              return 1L;
            });
    assertThrows(
        IllegalArgumentException.class,
        () ->
            guard.execute(
                List.of("p"),
                () -> {
                  throw new IllegalArgumentException();
                }));
    assertEquals(2, tokens.size());
    assertEquals(tokens.get(0), tokens.get(1));
    assertTrue(scripts.get(1).contains("redis.call('GET',k)==ARGV[1]"));
  }

  @Test
  void transactionLeaseIsHeldThroughRollback() {
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
    TransactionSynchronizationManager.initSynchronization();
    guard.holdUntilCompletion(List.of("p"));
    verify(redis, times(1)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    verify(redis, times(2)).execute(any(RedisScript.class), anyList(), any(Object[].class));
  }

  @Test
  void explicitRescheduleIntervalUsesOriginalDurationAndBuffer() {
    var cache = mock(BookingReadCache.class);
    var local = new BookingRedisLock(redis, cache);
    var venue = new com.shiguang.studio.vo.BookingViews.ResourceVO(2L, "场地", "", "", true, false);
    var p =
        new com.shiguang.studio.vo.BookingViews.PackageVO(
            1L,
            "套餐",
            "",
            "",
            new java.math.BigDecimal("399"),
            60,
            30,
            true,
            List.of(),
            List.of(venue));
    when(cache.catalog())
        .thenReturn(new BookingReadCache.Catalog(List.of(p), List.of(), List.of(venue)));
    var start = java.time.LocalDateTime.of(2026, 11, 10, 10, 0);
    try {
      assertEquals(12, local.keys(1, 1, start).size());
      assertEquals(16, local.keys(1, 1, start, start.plusMinutes(120)).size());
      var keys = new HashSet<>(local.keys(1, 1, start));
      keys.retainAll(local.keys(1, 1, start.plusMinutes(90)));
      assertTrue(keys.isEmpty());
    } finally {
      local.close();
    }
  }
}
