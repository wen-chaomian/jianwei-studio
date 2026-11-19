package com.shiguang.studio.service.booking;

import static com.shiguang.studio.service.booking.BookingPolicy.validDate;
import static com.shiguang.studio.service.booking.BookingPolicy.validStart;
import static com.shiguang.studio.service.booking.BookingPolicy.withinHours;
import static com.shiguang.studio.service.support.ServiceChecks.require;

import com.shiguang.studio.cache.BookingReadCache;
import com.shiguang.studio.dto.OperationsRequests.RuleRequest;
import com.shiguang.studio.exception.BusinessException;
import com.shiguang.studio.mapper.BookingMapper;
import com.shiguang.studio.vo.BookingViews.ResourceVO;
import com.shiguang.studio.vo.BookingViews.SlotVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class AvailabilityService {
  private final BookingMapper mapper;
  private final BookingReadCache cache;
  private final CatalogService catalog;
  private final AppointmentQueryService queries;

  public AvailabilityService(
      BookingMapper mapper,
      BookingReadCache cache,
      CatalogService catalog,
      AppointmentQueryService queries) {
    this.mapper = mapper;
    this.cache = cache;
    this.catalog = catalog;
    this.queries = queries;
  }

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  public List<SlotVO> availability(long packageId, long photographerId, LocalDate date) {
    var rule = cache.rules();
    validDate(date, rule);
    var p = catalog.packageDetail(packageId, false);
    require(p.photographers().stream().anyMatch(v -> v.id() == photographerId), "该摄影师不可预约或不支持此套餐");
    return availableSlots(
        photographerId, date, p.durationMinutes(), p.bufferMinutes(), p.venues(), null, rule);
  }

  private List<SlotVO> availableSlots(
      long photographerId,
      LocalDate date,
      int duration,
      int buffer,
      List<ResourceVO> venues,
      Long excluded,
      RuleRequest rule) {
    var snapshot = cache.day(date);
    var now = LocalDateTime.now(ZONE);
    List<SlotVO> result = new ArrayList<>();
    for (int minute = 0; minute < 1440; minute += rule.slotMinutes()) {
      var start = date.atStartOfDay().plusMinutes(minute);
      var end = start.plusMinutes(duration + buffer);
      if (validStart(start, rule, now)
          && withinHours(start, end, rule)
          && freeInSnapshot(true, photographerId, start, end, excluded, snapshot)
          && venues.stream()
              .anyMatch(
                  v ->
                      v.enabled() && freeInSnapshot(false, v.id(), start, end, excluded, snapshot)))
        result.add(new SlotVO(start, start.plusMinutes(duration), end));
    }
    return result;
  }

  private boolean freeInSnapshot(
      boolean photographer,
      long id,
      LocalDateTime start,
      LocalDateTime end,
      Long excluded,
      BookingReadCache.DaySnapshot day) {
    var periods = photographer ? day.photographerPeriods() : day.venuePeriods();
    boolean scheduled =
        periods.stream()
            .anyMatch(
                p ->
                    p.getResourceId() == id
                        && !start.toLocalTime().isBefore(p.getStartTime())
                        && !end.toLocalTime().isAfter(p.getEndTime()));
    return scheduled
        && day.occupancy().stream()
            .noneMatch(
                a ->
                    !Objects.equals(a.getAppointmentId(), excluded)
                        && (photographer ? a.getPhotographerId() : a.getVenueId()) == id
                        && a.getStartTime().isBefore(end)
                        && a.getEndTime().isAfter(start));
  }

  public List<SlotVO> rescheduleAvailability(
      long appointmentId, long photographerId, LocalDate date) {
    queries.detail(appointmentId);
    var a = mapper.appointment(appointmentId);
    require(a.getStatus().equals("CONFIRMED"), "当前状态不能改期");
    var rule = cache.rules();
    validDate(date, rule);
    var p =
        cache.catalog().packages().stream()
            .filter(v -> v.id().equals(a.getPackageId()))
            .findFirst()
            .orElseThrow(() -> new BusinessException("原套餐不存在"));
    require(
        p.photographers().stream()
            .anyMatch(v -> v.id() == photographerId && v.enabled() && v.bookable()),
        "摄影师不可预约或不支持该套餐");
    // Existing bookings retain their original duration and buffer even when the package is later
    // edited.
    return availableSlots(
        photographerId,
        date,
        a.getDurationMinutes(),
        a.getBufferMinutes(),
        p.venues(),
        a.getId(),
        rule);
  }
}
