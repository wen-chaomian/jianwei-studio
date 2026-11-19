package com.shiguang.studio.service.operations;

import static com.shiguang.studio.service.support.CurrentActor.desk;
import static com.shiguang.studio.service.support.CurrentActor.user;
import static com.shiguang.studio.service.support.ServiceChecks.require;

import com.shiguang.studio.dto.OperationsRequests.PaymentRequest;
import com.shiguang.studio.mapper.OperationsMapper;
import com.shiguang.studio.service.support.AppointmentGuard;
import com.shiguang.studio.service.support.BusinessEvents;
import com.shiguang.studio.service.support.CommandReceipts;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
  private final OperationsMapper ops;
  private final BusinessEvents events;
  private final CommandReceipts commands;
  private final AppointmentGuard guard;
  private final AppointmentActivityService activityService;

  public PaymentService(
      OperationsMapper ops,
      BusinessEvents events,
      CommandReceipts commands,
      AppointmentGuard guard,
      AppointmentActivityService activityService) {
    this.ops = ops;
    this.events = events;
    this.commands = commands;
    this.guard = guard;
    this.activityService = activityService;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Object payment(long id, PaymentRequest r) {
    desk();
    money(r.amount());
    var a = guard.locked(id);
    String payload = id + ":payment:" + r;
    if (commands.replay(r.requestId(), payload)) return activityService.activity(id);
    require(
        Set.of("CHECKED_IN", "IN_PROGRESS", "COMPLETED").contains(a.getStatus()),
        "仅已到店、服务中或已完成预约可登记收款");
    require(Set.of("CASH", "WECHAT", "ALIPAY", "BANK").contains(r.method()), "收款方式不正确");
    require(ops.net(id).add(r.amount()).compareTo(a.getAmount()) <= 0, "收款不能超过剩余应收金额");
    String note = r.reason() == null || r.reason().isBlank() ? "登记收款" : r.reason().strip();
    ops.paymentSave(id, "PAYMENT", r.amount(), r.method(), null, note, user());
    events.record(a, "payment", "登记收款 ¥" + r.amount() + "；" + note);
    commands.done(r.requestId(), payload);
    return activityService.activity(id);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Object refund(long paymentId, PaymentRequest r) {
    desk();
    money(r.amount());
    require(r.reason() != null && !r.reason().isBlank(), "请填写退款原因");
    var p = ops.payment(paymentId);
    require(p != null && p.get("type").equals("PAYMENT"), "原收款不存在");
    long id = ((Number) p.get("appointment_id")).longValue();
    var a = guard.locked(id);
    String payload = paymentId + ":refund:" + r;
    if (commands.replay(r.requestId(), payload)) return activityService.activity(id);
    BigDecimal original = (BigDecimal) p.get("amount");
    require(r.amount().add(ops.refunded(paymentId)).compareTo(original) <= 0, "退款金额超过该笔收款可退余额");
    ops.paymentSave(
        id, "REFUND", r.amount(), p.get("method").toString(), paymentId, r.reason(), user());
    events.record(a, "refund", "原流水 #" + paymentId + "，退款 ¥" + r.amount() + "；" + r.reason());
    commands.done(r.requestId(), payload);
    return activityService.activity(id);
  }

  private void money(BigDecimal value) {
    require(
        value != null
            && value.signum() > 0
            && value.compareTo(new BigDecimal("99999999.99")) <= 0
            && value.scale() <= 2,
        "金额必须为0.01至99999999.99，最多两位小数");
  }
}
