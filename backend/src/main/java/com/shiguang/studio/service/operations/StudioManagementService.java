package com.shiguang.studio.service.operations;

import static com.shiguang.studio.service.support.CurrentActor.admin;
import static com.shiguang.studio.service.support.CurrentActor.allowed;
import static com.shiguang.studio.service.support.CurrentActor.role;
import static com.shiguang.studio.service.support.CurrentActor.user;
import static com.shiguang.studio.service.support.ServiceChecks.page;
import static com.shiguang.studio.service.support.ServiceChecks.require;

import com.shiguang.studio.cache.BookingReadCache;
import com.shiguang.studio.dto.OperationsRequests.RuleRequest;
import com.shiguang.studio.mapper.BookingMapper;
import com.shiguang.studio.mapper.OperationsMapper;
import com.shiguang.studio.service.BookingConfigurationLock;
import com.shiguang.studio.service.BookingService;
import com.shiguang.studio.service.support.BusinessEvents;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudioManagementService {
  private final OperationsMapper ops;
  private final BookingMapper data;
  private final BookingService booking;
  private final BookingReadCache readCache;
  private final BookingConfigurationLock configurationLock;
  private final BusinessEvents events;

  public StudioManagementService(
      OperationsMapper ops,
      BookingMapper data,
      BookingService booking,
      BookingReadCache readCache,
      BookingConfigurationLock configurationLock,
      BusinessEvents events) {
    this.ops = ops;
    this.data = data;
    this.booking = booking;
    this.readCache = readCache;
    this.configurationLock = configurationLock;
    this.events = events;
  }

  public RuleRequest rules() {
    return readCache.rules();
  }

  @Transactional
  public void saveRules(RuleRequest r) {
    admin();
    configurationLock.write();
    require(
        r.openTime().isBefore(r.closeTime())
            && r.openTime().getSecond() == 0
            && r.closeTime().getSecond() == 0
            && r.openTime().getNano() == 0
            && r.closeTime().getNano() == 0,
        "营业时间不正确");
    require(Set.of(15, 30, 60).contains(r.slotMinutes()), "开始间隔支持15、30或60分钟");
    require(r.minAdvanceHours() <= r.maxAdvanceDays() * 24, "最少提前时间不能超过最长可预约天数");
    ops.saveRule(r);
    readCache.invalidateConfigurationAfterCommit();
    events.audit(user(), "RULE", "UPDATE", 1L, r.toString());
  }

  public Object dashboard(LocalDate from, LocalDate to) {
    admin();
    require(!to.isBefore(from) && !to.isAfter(from.plusDays(366)), "统计区间最多366天");
    return Map.of(
        "bookings",
        ops.stats(from, to.plusDays(1)),
        "revenue",
        ops.revenue(from, to.plusDays(1)),
        "pendingRequests",
        ops.pendingRequests());
  }

  public Object logs(int p, int s) {
    admin();
    page(p, s);
    return page(ops.audits(s, (p - 1) * s), ops.auditCount(), p, s);
  }

  public Object mySchedule(LocalDate date) {
    allowed(role("PHOTOGRAPHER"));
    return booking.schedule("photographer", data.employeeId(user()), date);
  }
}
