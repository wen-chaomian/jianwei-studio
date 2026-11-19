package com.shiguang.studio.service.operations;

import static com.shiguang.studio.service.support.CurrentActor.allowed;
import static com.shiguang.studio.service.support.CurrentActor.desk;
import static com.shiguang.studio.service.support.CurrentActor.role;
import static com.shiguang.studio.service.support.CurrentActor.user;
import static com.shiguang.studio.service.support.ServiceChecks.now;
import static com.shiguang.studio.service.support.ServiceChecks.require;
import static com.shiguang.studio.service.support.ServiceChecks.statusLabel;

import com.shiguang.studio.cache.BookingReadCache;
import com.shiguang.studio.dto.OperationsRequests.ActionRequest;
import com.shiguang.studio.dto.OperationsRequests.NotesRequest;
import com.shiguang.studio.dto.OperationsRequests.RescheduleRequest;
import com.shiguang.studio.exception.BusinessException;
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
public class AppointmentWorkflowService {
  private final OperationsMapper ops;
  private final BookingService booking;
  private final BookingReadCache readCache;
  private final BookingConfigurationLock configurationLock;
  private final BusinessEvents events;
  private final CommandReceipts commands;
  private final AppointmentGuard guard;

  public AppointmentWorkflowService(
      OperationsMapper ops,
      BookingService booking,
      BookingReadCache readCache,
      BookingConfigurationLock configurationLock,
      BusinessEvents events,
      CommandReceipts commands,
      AppointmentGuard guard) {
    this.ops = ops;
    this.booking = booking;
    this.readCache = readCache;
    this.configurationLock = configurationLock;
    this.events = events;
    this.commands = commands;
    this.guard = guard;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Object act(long id, String action, ActionRequest r) {
    configurationLock.read();
    var a = guard.locked(id);
    String payload = id + ":" + action + ":" + r;
    allowed(!role("CUSTOMER") || action.equals("cancel"));
    if (commands.replay(r.requestId(), payload)) return booking.detail(id);
    guard.version(id, r.version());
    if (Set.of("cancel", "check-in", "mark-no-show").contains(action)) guard.noPendingChange(id);
    String next;
    switch (action) {
      case "cancel" -> {
        guard.mayChange(a, r.reason());
        next = "CANCELLED";
      }
      case "check-in" -> {
        desk();
        require(a.getStatus().equals("CONFIRMED"), "仅待到店预约可登记到店");
        require(!now().toLocalDate().isBefore(a.getStartTime().toLocalDate()), "尚未到预约日期");
        next = "CHECKED_IN";
        ops.ensureService(id);
        ops.serviceTimes(id, now(), null, null, user());
      }
      case "start" -> {
        allowed(role("ADMIN") || role("PHOTOGRAPHER"));
        require(a.getStatus().equals("CHECKED_IN"), "请先登记到店");
        next = "IN_PROGRESS";
        ops.ensureService(id);
        ops.serviceTimes(id, null, now(), null, user());
      }
      case "complete" -> {
        allowed(role("ADMIN") || role("PHOTOGRAPHER"));
        require(a.getStatus().equals("IN_PROGRESS"), "请先开始服务");
        next = "COMPLETED";
        ops.ensureService(id);
        ops.serviceTimes(id, null, null, now(), user());
      }
      case "mark-no-show" -> {
        desk();
        require(
            a.getStatus().equals("CONFIRMED") && !now().isBefore(a.getShootEndTime()),
            "仅拍摄结束仍未到店的预约可标记爽约");
        require(!r.reason().isBlank(), "请填写爽约说明");
        next = "NO_SHOW";
      }
      default -> throw new BusinessException("不支持的操作");
    }
    ops.status(id, next);
    if (Set.of("CANCELLED", "NO_SHOW").contains(next))
      readCache.invalidateAvailabilityAfterCommit(a.getStartTime().toLocalDate());
    events.record(
        a,
        action,
        "状态 "
            + statusLabel(a.getStatus())
            + " → "
            + statusLabel(next)
            + (r.reason().isBlank() ? "" : "；" + r.reason()));
    commands.done(r.requestId(), payload);
    return booking.detail(id);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Object reschedule(long id, RescheduleRequest r) {
    configurationLock.read();
    var a = guard.locked(id);
    LocalDate previousDate = a.getStartTime().toLocalDate();
    String payload = id + ":reschedule:" + r;
    allowed(role("CUSTOMER") || role("ADMIN") || role("RECEPTIONIST"));
    if (commands.replay(r.requestId(), payload)) return booking.detail(id);
    guard.version(id, r.version());
    guard.noPendingChange(id);
    guard.mayChange(a, r.reason());
    String before = a.getStartTime() + " / " + a.getPhotographerName() + " / " + a.getVenueName();
    Long oldPhotographer = a.getPhotographerId();
    booking.reallocate(a, r.photographerId(), r.startTime());
    ops.reschedule(a);
    readCache.invalidateAvailabilityAfterCommit(previousDate, a.getStartTime().toLocalDate());
    events.record(
        a,
        "reschedule",
        before
            + " → "
            + a.getStartTime()
            + " / "
            + a.getPhotographerName()
            + " / "
            + a.getVenueName()
            + "；"
            + r.reason());
    if (!oldPhotographer.equals(a.getPhotographerId()))
      events.notifyUser(
          ops.employeeUser(oldPhotographer), "预约已改期", a.getPackageName() + "已调整至其他摄影师", id);
    commands.done(r.requestId(), payload);
    return booking.detail(id);
  }

  @Transactional
  public void notes(long id, NotesRequest r) {
    allowed(!role("CUSTOMER"));
    var a = guard.locked(id);
    require(ops.service(id) != null, "尚未登记到店");
    ops.notes(id, r.notes(), user());
    events.record(a, "service-notes", r.notes());
  }
}
