package com.shiguang.studio.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.support.*;

class SyncVersionsTest {
  Map<String, String> values;
  StringRedisTemplate redis;
  ValueOperations<String, String> ops;
  SyncVersions sync;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() {
    values = new HashMap<>();
    redis = mock(StringRedisTemplate.class);
    ops = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(ops);
    when(ops.multiGet(anyCollection()))
        .thenAnswer(
            i -> ((Collection<String>) i.getArgument(0)).stream().map(values::get).toList());
    doAnswer(
            i -> {
              values.putAll(i.getArgument(0));
              return null;
            })
        .when(ops)
        .multiSet(anyMap());
    sync = new SyncVersions(redis);
  }

  @AfterEach
  void cleanup() {
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.clearSynchronization();
  }

  org.springframework.security.core.Authentication auth(String id, String role) {
    return new UsernamePasswordAuthenticationToken(
        id, "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  }

  @Test
  void publishesOnlyAfterCommitAndRollbackDoesNotPublish() {
    TransactionSynchronizationManager.initSynchronization();
    sync.user(1L);
    assertTrue(values.isEmpty());
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    assertTrue(values.isEmpty());
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.initSynchronization();
    sync.user(1L);
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    assertFalse(values.isEmpty());
  }

  @Test
  void unrelatedCustomerDoesNotInvalidateOtherCustomersOrPhotographers() {
    var a = auth("1", "CUSTOMER");
    var b = auth("2", "CUSTOMER");
    var p = auth("3", "PHOTOGRAPHER");
    var beforeA = sync.snapshot(a, false);
    var beforeB = sync.snapshot(b, false);
    var beforeP = sync.snapshot(p, false);
    sync.user(1L);
    sync.changed("desk");
    assertNotEquals(beforeA, sync.snapshot(a, false));
    assertEquals(beforeB, sync.snapshot(b, false));
    assertEquals(beforeP, sync.snapshot(p, false));
  }

  @Test
  void deskAndConfigurationHaveAppropriateAudience() {
    var desk = auth("1", "RECEPTIONIST");
    var user = auth("2", "CUSTOMER");
    var d = sync.snapshot(desk, false);
    var c = sync.snapshot(user, false);
    var publicVersion = sync.snapshot(null, true);
    sync.changed("desk");
    assertNotEquals(d, sync.snapshot(desk, false));
    assertEquals(c, sync.snapshot(user, false));
    assertEquals(publicVersion, sync.snapshot(null, true));
    sync.changed("configuration");
    assertNotEquals(c, sync.snapshot(user, false));
    assertNotEquals(publicVersion, sync.snapshot(null, true));
  }

  @Test
  void redisFailureIsRecoverableAndDoesNotFailCommittedOperation() {
    when(ops.multiGet(anyCollection()))
        .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
    assertFalse(sync.snapshot(auth("1", "CUSTOMER"), false).available());
    doThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"))
        .when(ops)
        .multiSet(anyMap());
    assertDoesNotThrow(() -> sync.user(1L));
  }

  @Test
  void checksAreOnlyRedisReadsAndStableWithoutWrites() {
    var a = auth("1", "CUSTOMER");
    var first = sync.snapshot(a, false);
    assertEquals(first, sync.snapshot(a, false));
    verify(ops, times(2)).multiGet(anyCollection());
    verify(ops, never()).multiSet(anyMap());
  }
}
