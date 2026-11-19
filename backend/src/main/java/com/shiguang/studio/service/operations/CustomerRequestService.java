package com.shiguang.studio.service.operations;

import static com.shiguang.studio.service.support.CurrentActor.allowed;
import static com.shiguang.studio.service.support.CurrentActor.desk;
import static com.shiguang.studio.service.support.CurrentActor.role;
import static com.shiguang.studio.service.support.CurrentActor.user;
import static com.shiguang.studio.service.support.ServiceChecks.page;
import static com.shiguang.studio.service.support.ServiceChecks.requestLabel;
import static com.shiguang.studio.service.support.ServiceChecks.require;

import com.shiguang.studio.cache.BookingReadCache;
import com.shiguang.studio.dto.OperationsRequests.RequestCreate;
import com.shiguang.studio.dto.OperationsRequests.RequestHandle;
import com.shiguang.studio.mapper.BookingMapper;
import com.shiguang.studio.mapper.OperationsMapper;
import com.shiguang.studio.service.BookingConfigurationLock;
import com.shiguang.studio.service.BookingService;
import com.shiguang.studio.service.support.AppointmentGuard;
import com.shiguang.studio.service.support.BusinessEvents;
import com.shiguang.studio.service.support.CommandReceipts;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerRequestService {
  private final OperationsMapper ops;
  private final BookingMapper data;
  private final BookingService booking;
  private final BookingReadCache readCache;
  private final BookingConfigurationLock configurationLock;
  private final BusinessEvents events;
  private final CommandReceipts commands;
  private final AppointmentGuard guard;

  public CustomerRequestService(
      OperationsMapper ops,
      BookingMapper data,
      BookingService booking,
      BookingReadCache readCache,
      BookingConfigurationLock configurationLock,
      BusinessEvents events,
      CommandReceipts commands,
      AppointmentGuard guard) {
    this.ops = ops;
    this.data = data;
    this.booking = booking;
    this.readCache = readCache;
    this.configurationLock = configurationLock;
    this.events = events;
    this.commands = commands;
    this.guard = guard;
  }

  public Object requests(int p, int s) {
    page(p, s);
    allowed(role("CUSTOMER") || role("ADMIN") || role("RECEPTIONIST"));
    Long customer = role("CUSTOMER") ? data.customerId(user()) : null;
    allowed(!role("CUSTOMER") || customer != null);
    return page(ops.requests(customer, s, (p - 1) * s), ops.requestCount(customer), p, s);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void createRequest(RequestCreate r) {
    allowed(role("CUSTOMER"));
    configurationLock.read();
    var a = guard.locked(r.appointmentId());
    String payload = "request:" + r;
    if (commands.replay(r.requestId(), payload)) return;
    require(Set.of("RESCHEDULE", "CANCEL", "OTHER").contains(r.type()), "申请类型不正确");
    if (!r.type().equals("OTHER")) {
      require(a.getStatus().equals("CONFIRMED"), "仅待到店预约可以申请取消或改期");
      guard.noPendingChange(a.getId());
    }
    ops.requestCreate(data.customerId(user()), a.getId(), r.type(), r.content());
    ops.touch(a.getId());
    String label = requestLabel(r.type());
    events.notifyUser(user(), "申请已提交", label + "已提交，等待工作室处理。", a.getId());
    ops.notifyDesk(
        a.getId(),
        a.getCustomerName()
            + " · "
            + label
            + "："
            + r.content().substring(0, Math.min(600, r.content().length())));
    events.audit(user(), "REQUEST", "CREATE", a.getId(), r.type() + "：" + r.content());
    commands.done(r.requestId(), payload);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void handleRequest(long id, RequestHandle r) {
    desk();
    configurationLock.read();
    var info = ops.request(id);
    require(info != null, "申请不存在");
    long appointmentId = ((Number) info.get("appointment_id")).longValue();
    var a = guard.locked(appointmentId);
    var q = ops.lockRequest(id);
    String payload = id + ":handle-request:" + r;
    if (commands.replay(r.requestId(), payload)) return;
    require(((Number) q.get("version")).intValue() == r.version(), "申请已被修改，请刷新");
    guard.version(appointmentId, r.appointmentVersion());
    require(Set.of("PENDING", "PROCESSING").contains(q.get("status").toString()), "已结束的申请不能重复处理");
    require(Set.of("PROCESSING", "RESOLVED", "CLOSED").contains(r.status()), "处理状态不正确");
    String type = q.get("type").toString();
    String result = r.result() == null ? "" : r.result().strip();
    if (r.status().equals("CLOSED") || (r.status().equals("RESOLVED") && type.equals("OTHER")))
      require(!result.isBlank(), "请填写处理结果或结束原因");
    if (result.isBlank())
      result =
          r.status().equals("PROCESSING") ? "正在与客户沟通" : type.equals("CANCEL") ? "已同意取消预约" : "已同意改期";
    if (r.status().equals("RESOLVED") && !type.equals("OTHER")) {
      require(a.getStatus().equals("CONFIRMED"), "预约状态已变化，请核对后结束申请");
      if (type.equals("CANCEL")) {
        ops.status(appointmentId, "CANCELLED");
        readCache.invalidateAvailabilityAfterCommit(a.getStartTime().toLocalDate());
        events.record(a, "cancel", "同意取消申请 #" + id + "：" + result);
      } else {
        require(r.photographerId() != null && r.startTime() != null, "请选择新摄影师和拍摄时段");
        LocalDate previous = a.getStartTime().toLocalDate();
        Long oldPhotographer = a.getPhotographerId();
        String before = a.getStartTime().toString();
        booking.reallocate(a, r.photographerId(), r.startTime());
        ops.reschedule(a);
        readCache.invalidateAvailabilityAfterCommit(previous, a.getStartTime().toLocalDate());
        events.record(
            a,
            "reschedule",
            "同意改期申请 #" + id + "：" + before + " → " + a.getStartTime() + "；" + result);
        if (!oldPhotographer.equals(a.getPhotographerId()))
          events.notifyUser(
              ops.employeeUser(oldPhotographer), "预约已改期", "原拍摄安排已调整至其他摄影师", appointmentId);
      }
    } else ops.touch(appointmentId);
    ops.handle(id, r.status(), user(), result);
    String state =
        r.status().equals("PROCESSING")
            ? "处理中"
            : r.status().equals("RESOLVED") ? "已办理" : "已结束（未执行变更）";
    ops.log(appointmentId, "request-handled", user(), "申请 #" + id + " " + state + "：" + result);
    Long owner = ops.customerUser(a.getCustomerId());
    if (owner != null) events.notifyUser(owner, "申请处理进展", state + "：" + result, appointmentId);
    events.audit(user(), "REQUEST", r.status(), id, result);
    commands.done(r.requestId(), payload);
  }
}
