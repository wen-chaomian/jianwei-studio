package com.shiguang.studio.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.studio.cache.*;
import com.shiguang.studio.entity.SysUser;
import com.shiguang.studio.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AccountStateCacheTest {
  private final UserMapper users = mock(UserMapper.class);
  private final MemoryCacheRedis redis = new MemoryCacheRedis();
  private final RedisReadCache reads =
      new RedisReadCache(redis.redis, new ObjectMapper(), new CacheProtection(4, 2, 50));
  private final AccountStateCache cache = new AccountStateCache(reads, users);
  private SysUser user;

  @BeforeEach
  void setup() {
    user = new SysUser();
    user.setId(1L);
    user.setPhone("13800000001");
    user.setDisplayName("李女士");
    user.setEnabled(true);
    user.setAuthVersion(0);
    when(users.selectById(1L)).thenAnswer(inv -> user);
    when(users.roles(1L)).thenReturn(List.of("CUSTOMER"));
  }

  @Test
  void repeatedRequestsReuseStateWithoutReadingDatabase() {
    assertTrue(cache.get(1L).enabled());
    for (int i = 0; i < 20; i++) assertEquals("李女士", cache.get(1L).user().displayName());
    verify(users, times(1)).selectById(1L);
    verify(users, times(1)).roles(1L);
  }

  @Test
  void concurrentInvalidationCannotRepublishAnOldCredentialVersion() {
    redis.beforeStore =
        () -> {
          user.setAuthVersion(1);
          user.setEnabled(false);
          cache.evictAfterCommit(1L);
        };
    var state = cache.get(1L);
    assertFalse(state.enabled());
    assertEquals(1, state.version());
    assertFalse(cache.get(1L).enabled());
    verify(users, times(2)).selectById(1L);
  }

  @Test
  void rollbackDoesNotEvictAndCommitDoesEvict() {
    cache.get(1L);
    TransactionSynchronizationManager.initSynchronization();
    try {
      cache.evictAfterCommit(1L);
      assertEquals(
          0L, redis.values.keySet().stream().filter(k -> k.contains("generation:")).count());
      TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(1));
      assertEquals(
          0L, redis.values.keySet().stream().filter(k -> k.contains("generation:")).count());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.initSynchronization();
    try {
      cache.evictAfterCommit(1L);
      TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
      assertEquals(
          1L, redis.values.keySet().stream().filter(k -> k.contains("generation:")).count());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void transactionBypassesCacheAndDoesNotPublishUncommittedProfile() {
    cache.get(1L);
    user.setDisplayName("尚未提交");
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      assertEquals("尚未提交", cache.get(1L).user().displayName());
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    assertEquals("李女士", cache.get(1L).user().displayName());
  }

  @Test
  void redisFailureFallsBackToAuthoritativeDatabase() {
    redis.unavailable = true;
    assertTrue(cache.get(1L).enabled());
    user.setEnabled(false);
    assertFalse(cache.get(1L).enabled());
  }

  @Test
  void failedEvictionCannotReuseOldEnabledStateWhenRedisRecovers() {
    assertTrue(cache.get(1L).enabled());
    user.setEnabled(false);
    user.setAuthVersion(1);
    redis.unavailable = true;
    cache.evictAfterCommit(1L);
    redis.unavailable = false;
    assertFalse(cache.get(1L).enabled());
    assertEquals(1, cache.get(1L).version());
  }

  @org.junit.jupiter.api.AfterEach
  void close() {
    reads.close();
  }
}
