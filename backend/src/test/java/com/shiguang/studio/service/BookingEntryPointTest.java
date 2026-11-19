package com.shiguang.studio.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shiguang.studio.dto.BookingRequests.CreateAppointmentRequest;
import com.shiguang.studio.service.booking.*;
import com.shiguang.studio.service.impl.BookingServiceImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class BookingEntryPointTest {
  private final BookingRedisLock locks = mock(BookingRedisLock.class);
  private final AppointmentCreationService target = mock(AppointmentCreationService.class);
  private final RecordingTransactions transactions = new RecordingTransactions();
  private final CreateAppointmentRequest request =
      new CreateAppointmentRequest(
          1L, 2L, LocalDateTime.of(2030, 1, 2, 10, 0), "entry-test", "", null, null);

  private BookingService entry() {
    ProxyFactory proxy = new ProxyFactory(target);
    proxy.setProxyTargetClass(true);
    proxy.addAdvice(
        new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
    return new BookingServiceImpl(
        locks,
        mock(CatalogService.class),
        mock(ResourceScheduleService.class),
        mock(AvailabilityService.class),
        mock(BookingAllocationService.class),
        mock(AppointmentQueryService.class),
        (AppointmentCreationService) proxy.getProxy());
  }

  private void lockBoundary() {
    var keys = List.of("photographer:2:2030-01-02T10:00");
    when(locks.keys(1, 2, request.startTime())).thenReturn(keys);
    when(locks.execute(eq(keys), any()))
        .thenAnswer(
            call -> {
              assertFalse(
                  TransactionSynchronizationManager.isActualTransactionActive(),
                  "Waiting for the Redis lock must not hold a database transaction");
              try {
                return ((Supplier<?>) call.getArgument(1)).get();
              } finally {
                assertFalse(
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    "The database transaction must finish before releasing the Redis lock");
              }
            });
  }

  @Test
  void serviceCallCommitsBeforeReleasingAdmissionLock() {
    lockBoundary();
    when(target.create(request))
        .thenAnswer(
            call -> {
              assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
              return null;
            });
    entry().create(request);
    assertEquals(1, transactions.commits);
    assertEquals(0, transactions.rollbacks);
    verify(locks).execute(anyList(), any());
  }

  @Test
  void failedCreationRollsBackBeforeTheLockIsReleased() {
    lockBoundary();
    when(target.create(request))
        .thenAnswer(
            call -> {
              assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
              throw new IllegalStateException("模拟创建失败");
            });
    assertThrows(IllegalStateException.class, () -> entry().create(request));
    assertEquals(0, transactions.commits);
    assertEquals(1, transactions.rollbacks);
  }

  private static class RecordingTransactions extends AbstractPlatformTransactionManager {
    int commits;
    int rollbacks;

    protected Object doGetTransaction() {
      return new Object();
    }

    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    protected void doCommit(DefaultTransactionStatus status) {
      commits++;
    }

    protected void doRollback(DefaultTransactionStatus status) {
      rollbacks++;
    }
  }
}
