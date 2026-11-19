package com.shiguang.studio.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shiguang.studio.cache.BookingReadCache;
import com.shiguang.studio.dto.OperationsRequests.*;
import com.shiguang.studio.entity.Appointment;
import com.shiguang.studio.exception.BusinessException;
import com.shiguang.studio.mapper.*;
import com.shiguang.studio.security.AccountStateCache;
import com.shiguang.studio.service.impl.OperationsServiceImpl;
import com.shiguang.studio.vo.BookingViews.AppointmentVO;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

class OperationsServiceTest {
  private OperationsMapper ops;
  private BookingMapper data;
  private BookingService booking;
  private UserMapper users;
  private PasswordEncoder encoder;
  private AccountStateCache accountCache;
  private BookingReadCache readCache;
  private BookingConfigurationLock configLock;
  private OperationsServiceImpl service;
  private Appointment appointment;
  private static final String REQUEST = "80acf684-633e-4528-9587-2de79b63bd83";

  @BeforeEach
  void setUp() {
    ops = mock(OperationsMapper.class);
    data = mock(BookingMapper.class);
    booking = mock(BookingService.class);
    users = mock(UserMapper.class);
    encoder = mock(PasswordEncoder.class);
    accountCache = mock(AccountStateCache.class);
    readCache = mock(BookingReadCache.class);
    configLock = mock(BookingConfigurationLock.class);
    service =
        ServiceTestFactory.operations(
            ops,
            data,
            booking,
            users,
            encoder,
            accountCache,
            readCache,
            configLock,
            mock(com.shiguang.studio.cache.SyncVersions.class));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "1", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    appointment = new Appointment();
    appointment.setId(10L);
    appointment.setCustomerId(20L);
    appointment.setPhotographerId(30L);
    appointment.setPackageId(40L);
    appointment.setAmount(new BigDecimal("399.00"));
    appointment.setStatus("COMPLETED");
    appointment.setStartTime(LocalDateTime.now().plusDays(3));
    when(ops.lockAppointment(10)).thenReturn(appointment);
    when(data.appointment(10)).thenReturn(appointment);
    when(booking.detail(10))
        .thenReturn(
            new AppointmentVO(
                10L,
                "A10",
                "写真",
                "李老师",
                "自然光影棚",
                "李女士",
                "13800000001",
                appointment.getStartTime(),
                null,
                null,
                appointment.getStatus(),
                appointment.getAmount(),
                ""));
    when(readCache.rules())
        .thenReturn(new RuleRequest(LocalTime.of(9, 0), LocalTime.of(20, 0), 30, 2, 30, 24));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void pendingRequest(String type) {
    appointment.setStatus("CONFIRMED");
    var q =
        Map.<String, Object>of(
            "appointment_id", 10L, "version", 0, "type", type, "status", "PENDING");
    when(ops.request(7)).thenReturn(q);
    when(ops.lockRequest(7)).thenReturn(q);
  }

  @Test
  void approvingCancellationNeedsNoRepeatedExplanation() {
    pendingRequest("CANCEL");
    service.handleRequest(7, new RequestHandle("RESOLVED", "", 0, REQUEST, 0, null, null));
    verify(ops).status(10, "CANCELLED");
    verify(ops).handle(7, "RESOLVED", 1, "已同意取消预约");
  }

  @Test
  void processingCanUseAutomaticProgressMessage() {
    pendingRequest("CANCEL");
    service.handleRequest(7, new RequestHandle("PROCESSING", null, 0, REQUEST, 0, null, null));
    verify(ops).handle(7, "PROCESSING", 1, "正在与客户沟通");
    verify(ops, never()).status(anyLong(), anyString());
  }

  @Test
  void closingRequestStillRequiresAnExplanation() {
    pendingRequest("CANCEL");
    assertThrows(
        BusinessException.class,
        () ->
            service.handleRequest(7, new RequestHandle("CLOSED", "  ", 0, REQUEST, 0, null, null)));
    verify(ops, never()).handle(anyLong(), anyString(), anyLong(), anyString());
  }

  @Test
  void otherRequestNeedsAConcreteResult() {
    pendingRequest("OTHER");
    assertThrows(
        BusinessException.class,
        () ->
            service.handleRequest(7, new RequestHandle("RESOLVED", "", 0, REQUEST, 0, null, null)));
  }

  @Test
  void paymentOverRemainingBalanceDoesNotWrite() {
    when(ops.net(10)).thenReturn(new BigDecimal("398.99"));
    assertThrows(
        BusinessException.class,
        () ->
            service.payment(10, new PaymentRequest(REQUEST, new BigDecimal("0.02"), "CASH", "补款")));
    var order = inOrder(ops);
    order.verify(ops).lockAppointment(10);
    order.verify(ops).receipt(1, REQUEST);
    order.verify(ops).net(10);
    verify(ops, never())
        .paymentSave(anyLong(), anyString(), any(), anyString(), any(), anyString(), anyLong());
  }

  @Test
  void excessiveRefundIsCheckedAfterAppointmentLock() {
    when(ops.payment(90))
        .thenReturn(
            Map.of(
                "appointment_id",
                10L,
                "type",
                "PAYMENT",
                "amount",
                new BigDecimal("100.00"),
                "method",
                "CASH"));
    when(ops.refunded(90)).thenReturn(new BigDecimal("99.99"));
    assertThrows(
        BusinessException.class,
        () ->
            service.refund(90, new PaymentRequest(REQUEST, new BigDecimal("0.02"), "CASH", "退款")));
    var order = inOrder(ops);
    order.verify(ops).lockAppointment(10);
    order.verify(ops).refunded(90);
    verify(ops, never())
        .paymentSave(anyLong(), anyString(), any(), anyString(), any(), anyString(), anyLong());
  }

  @Test
  void subCentAmountCannotReachLedgerEvenForInternalCalls() {
    assertThrows(
        BusinessException.class,
        () ->
            service.payment(
                10, new PaymentRequest(REQUEST, new BigDecimal("0.001"), "CASH", "补款")));
    verify(ops, never()).lockAppointment(anyLong());
  }

  @Test
  void ledgerTotalsAndRowsUseTheSameQueryResult() {
    when(ops.payments(10))
        .thenReturn(
            List.of(
                Map.of("type", "PAYMENT", "amount", new BigDecimal("399.00")),
                Map.of("type", "REFUND", "amount", new BigDecimal("20.01"))));
    var result = (Map<?, ?>) service.activity(10);
    assertEquals(new BigDecimal("378.99"), result.get("net"));
    assertEquals(new BigDecimal("399.00"), result.get("received"));
    assertEquals(false, result.get("selfChangeAllowed"));
    verify(ops, never()).total(anyLong(), anyString());
    verify(ops, times(1)).payments(10);
  }

  @Test
  void staleVersionCannotTransitionStatus() {
    appointment.setStatus("CONFIRMED");
    when(ops.version(10)).thenReturn(2);
    assertThrows(
        BusinessException.class,
        () -> service.act(10, "cancel", new ActionRequest(REQUEST, 1, "调整计划")));
    verify(ops, never()).status(anyLong(), anyString());
  }

  @Test
  void profileUpdatesNeverOverwriteReceptionistsRemark() {
    when(data.customerId(1)).thenReturn(20L);
    service.profile(new ProfileRequest(" 李女士 ", "13800000002"));
    var order = inOrder(data, ops);
    order.verify(data).lockUser(1);
    order.verify(ops).profile(1, "李女士");
    order.verify(ops).customerContact(20, "李女士", "13800000002");
    verify(ops, never()).customer(anyLong());
    verify(ops, never()).customerSave(anyLong(), anyString(), anyString(), anyString());
    verify(accountCache).evictAfterCommit(1);
  }

  @Test
  void passwordResetUsesTargetedWriteUnderAccountLock() {
    when(ops.customer(20)).thenReturn(Map.of("userId", 2L));
    when(users.roles(2L)).thenReturn(List.of("CUSTOMER"));
    when(encoder.encode("12345678")).thenReturn("hash");
    service.resetCustomerPassword(20, new PasswordRequest("12345678"));
    var order = inOrder(data, ops);
    order.verify(data).lockUser(2);
    order.verify(ops).password(2, "hash");
    order.verify(ops).revoke(2);
    verify(users, never()).updateById(any(com.shiguang.studio.entity.SysUser.class));
    verify(accountCache).evictAfterCommit(2);
  }

  @Test
  void employeeChangeLocksAccountBeforeEmployeeRow() {
    when(ops.employeeUser(30)).thenReturn(3L);
    when(ops.lockEmployee(30)).thenReturn(Map.of("userId", 3L));
    when(users.roles(3L)).thenReturn(List.of("PHOTOGRAPHER"));
    service.employee(
        30L,
        new EmployeeRequest(
            "19999005555", "李老师", "E003", null, List.of("PHOTOGRAPHER"), true, true));
    var order = inOrder(configLock, data, ops);
    order.verify(configLock).write();
    order.verify(data).lockUser(3);
    order.verify(ops).lockEmployee(30);
    order.verify(ops).employeeAccount(3, "19999005555", "李老师", true, null);
    verify(accountCache).evictAfterCommit(3);
    verify(readCache).invalidateConfigurationAfterCommit();
  }

  @Test
  void subSecondBusinessHoursAreRejected() {
    assertThrows(
        BusinessException.class,
        () ->
            service.saveRules(
                new RuleRequest(LocalTime.of(9, 0, 0, 1), LocalTime.of(20, 0), 30, 2, 30, 24)));
    verify(ops, never()).saveRule(any());
  }

  @Test
  void contradictoryAdvanceWindowCannotDisableEveryBookingDate() {
    assertThrows(
        BusinessException.class,
        () ->
            service.saveRules(
                new RuleRequest(LocalTime.of(9, 0), LocalTime.of(20, 0), 30, 168, 1, 24)));
    verify(ops, never()).saveRule(any());
  }

  @Test
  void nullRoleAndNegativeResourceIdsAreValidationErrors() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      var invalidEmployee =
          new EmployeeRequest(
              "19999005555", "李老师", "E003", null, Arrays.asList((String) null), true, false);
      assertFalse(validator.validate(invalidEmployee).isEmpty());
      assertFalse(
          validator
              .validate(new RescheduleRequest(REQUEST, 0, "改期", -1L, LocalDateTime.now()))
              .isEmpty());
    }
  }
}
