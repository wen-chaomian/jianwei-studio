package com.shiguang.studio.service.booking;

import static com.shiguang.studio.service.booking.BookingPolicy.column;
import static com.shiguang.studio.service.booking.BookingPolicy.table;
import static com.shiguang.studio.service.support.CurrentActor.admin;
import static com.shiguang.studio.service.support.CurrentActor.role;
import static com.shiguang.studio.service.support.CurrentActor.user;
import static com.shiguang.studio.service.support.ServiceChecks.require;

import com.shiguang.studio.cache.BookingReadCache;
import com.shiguang.studio.cache.SyncVersions;
import com.shiguang.studio.dto.BookingRequests.PeriodRequest;
import com.shiguang.studio.dto.BookingRequests.ScheduleRequest;
import com.shiguang.studio.mapper.BookingMapper;
import com.shiguang.studio.service.BookingConfigurationLock;
import com.shiguang.studio.service.support.BusinessEvents;
import com.shiguang.studio.vo.BookingViews.PeriodVO;
import com.shiguang.studio.vo.BookingViews.ScheduleVO;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceScheduleService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private final BookingMapper mapper;
  private final BookingReadCache cache;
  private final BookingConfigurationLock configurationLock;
  private final SyncVersions sync;
  private final BusinessEvents events;
  private final CatalogService catalog;

  public ResourceScheduleService(
      BookingMapper mapper,
      BookingReadCache cache,
      BookingConfigurationLock configurationLock,
      SyncVersions sync,
      BusinessEvents events,
      CatalogService catalog) {
    this.mapper = mapper;
    this.cache = cache;
    this.configurationLock = configurationLock;
    this.sync = sync;
    this.events = events;
    this.catalog = catalog;
  }

  public ScheduleVO schedule(String kind, long id, LocalDate date) {
    String t = table(kind);
    if (!role("ADMIN")) {
      require(
          kind.equals("photographer") && Objects.equals(mapper.employeeId(user()), id), "只能查看本人排班");
    }
    var s = mapper.schedule(t, id, date);
    return new ScheduleVO(
        id,
        date,
        s != null && s.getEnabled(),
        s == null
            ? List.of()
            : mapper.periods("WEEKLY".equals(s.getSource()) ? t + "_weekly" : t, s.getId()).stream()
                .map(p -> new PeriodVO(p.getStartTime(), p.getEndTime()))
                .toList(),
        s == null ? "NONE" : s.getSource());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ScheduleVO saveSchedule(String kind, ScheduleRequest r) {
    admin();
    configurationLock.write();
    String t = table(kind);
    require(!r.date().isBefore(LocalDate.now(ZONE)), "不能修改过去日期的排班");
    require(
        kind.equals("photographer")
            ? catalog.photographers().stream().anyMatch(p -> p.id().equals(r.resourceId()))
            : mapper.venue(r.resourceId()) != null,
        "资源不存在");
    var periods =
        r.periods().stream().sorted(Comparator.comparing(PeriodRequest::startTime)).toList();
    require(!r.enabled() || !periods.isEmpty(), "工作日请至少设置一个时段");
    LocalTime previous = null;
    for (var p : periods) {
      require(
          p.startTime().isBefore(p.endTime())
              && p.startTime().getSecond() == 0
              && p.endTime().getSecond() == 0
              && p.startTime().getNano() == 0
              && p.endTime().getNano() == 0,
          "排班时间不正确");
      require(previous == null || !p.startTime().isBefore(previous), "排班时段不能重叠");
      previous = p.endTime();
    }
    mapper.ensureSchedule(t, r.resourceId(), r.date());
    var s = mapper.lockSchedule(t, r.resourceId(), r.date());
    for (var a :
        mapper.conflicts(
            column(kind),
            r.resourceId(),
            r.date().atStartOfDay(),
            r.date().plusDays(1).atStartOfDay()))
      require(
          r.enabled()
              && periods.stream()
                  .anyMatch(
                      p ->
                          !a.getStartTime().isBefore(r.date().atTime(p.startTime()))
                              && !a.getOccupiedEndTime().isAfter(r.date().atTime(p.endTime()))),
          "新排班影响已有预约，请先处理预约");
    mapper.setSchedule(t, s.getId(), r.enabled());
    mapper.clearPeriods(t, s.getId());
    if (r.enabled())
      periods.forEach(p -> mapper.insertPeriod(t, s.getId(), p.startTime(), p.endTime()));
    events.audit(
        user(),
        "SCHEDULE",
        "SAVE",
        r.resourceId(),
        kind + " / " + r.date() + " / 开放：" + r.enabled());
    cache.invalidateAvailabilityAfterCommit(r.date());
    sync.changed("schedule");
    return schedule(kind, r.resourceId(), r.date());
  }
}
