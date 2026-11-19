package com.shiguang.studio.service.booking;

import static com.shiguang.studio.service.booking.BookingPolicy.column;
import static com.shiguang.studio.service.booking.BookingPolicy.table;
import static com.shiguang.studio.service.booking.BookingPolicy.validDate;
import static com.shiguang.studio.service.booking.BookingPolicy.validStart;
import static com.shiguang.studio.service.booking.BookingPolicy.withinHours;
import static com.shiguang.studio.service.support.ServiceChecks.require;

import com.shiguang.studio.cache.BookingReadCache;
import com.shiguang.studio.dto.OperationsRequests.RuleRequest;
import com.shiguang.studio.entity.Appointment;
import com.shiguang.studio.entity.Photographer;
import com.shiguang.studio.entity.Schedule;
import com.shiguang.studio.entity.Venue;
import com.shiguang.studio.exception.BusinessException;
import com.shiguang.studio.mapper.BookingMapper;
import com.shiguang.studio.service.BookingConfigurationLock;
import com.shiguang.studio.service.BookingRedisLock;
import com.shiguang.studio.service.BookingSlotLock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingAllocationService {
  private final BookingMapper mapper;
  private final BookingReadCache cache;
  private final BookingConfigurationLock configurationLock;
  private final BookingRedisLock redisLock;
  private final BookingSlotLock slotLock;

  public BookingAllocationService(
      BookingMapper mapper,
      BookingReadCache cache,
      BookingConfigurationLock configurationLock,
      BookingRedisLock redisLock,
      BookingSlotLock slotLock) {
    this.mapper = mapper;
    this.cache = cache;
    this.configurationLock = configurationLock;
    this.redisLock = redisLock;
    this.slotLock = slotLock;
  }

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  public boolean within(
      Schedule s, String t, LocalDateTime start, LocalDateTime end, RuleRequest rule) {
    return withinHours(start, end, rule)
        && s != null
        && s.getEnabled()
        && mapper.periods("WEEKLY".equals(s.getSource()) ? t + "_weekly" : t, s.getId()).stream()
            .anyMatch(
                p ->
                    !start.toLocalTime().isBefore(p.getStartTime())
                        && !end.toLocalTime().isAfter(p.getEndTime()));
  }

  public boolean free(
      String kind, long id, LocalDateTime start, LocalDateTime end, RuleRequest rule) {
    String t = table(kind);
    var s = mapper.schedule(t, id, start.toLocalDate());
    return within(s, t, start, end, rule)
        && mapper.conflicts(column(kind), id, start, end).isEmpty();
  }

  public Photographer photographer(long id) {
    return mapper.photographers().stream()
        .filter(p -> p.getId() == id && p.getEnabled() && p.getBookable())
        .findFirst()
        .orElseThrow(() -> new BusinessException("摄影师不可预约"));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void reallocate(Appointment a, long photographerId, LocalDateTime start) {
    var lockKeys =
        new ArrayList<>(
            redisLock.keys(
                a.getPackageId(),
                photographerId,
                start,
                start.plusMinutes(a.getDurationMinutes() + a.getBufferMinutes())));
    lockKeys.addAll(
        redisLock.keys(
            a.getPackageId(), a.getPhotographerId(), a.getStartTime(), a.getOccupiedEndTime()));
    lockKeys.addAll(
        com.shiguang.studio.service.BookingTimeSlots.slots(
                "venue", a.getVenueId(), a.getStartTime(), a.getOccupiedEndTime())
            .stream()
            .map(com.shiguang.studio.service.BookingTimeSlots.Slot::redisKey)
            .toList());
    redisLock.holdUntilCompletion(lockKeys);
    configurationLock.read();
    var rule = cache.currentRules();
    validDate(start.toLocalDate(), rule);
    require(validStart(start, rule, LocalDateTime.now(ZONE)), "新时间不符合预约提前时间或间隔规则");
    mapper.sharePackage(a.getPackageId());
    var person = photographer(photographerId);
    require(mapper.photographerIds(a.getPackageId()).contains(photographerId), "该摄影师不支持原套餐");
    var end = start.plusMinutes(a.getDurationMinutes() + a.getBufferMinutes());
    var slots = new ArrayList<>(allocationSlots(a.getPackageId(), photographerId, start, end));
    slots.addAll(
        com.shiguang.studio.service.BookingTimeSlots.slots(
            "photographer", a.getPhotographerId(), a.getStartTime(), a.getOccupiedEndTime()));
    slots.addAll(
        com.shiguang.studio.service.BookingTimeSlots.slots(
            "venue", a.getVenueId(), a.getStartTime(), a.getOccupiedEndTime()));
    slotLock.lock(slots);
    require(
        freeExcept("photographer", photographerId, start, end, a.getId(), rule),
        "新摄影师时段不可用，原预约保持不变");
    Venue selected = null;
    for (long id : mapper.venueIds(a.getPackageId())) {
      var v = mapper.venue(id);
      if (v != null && v.getEnabled() && freeExcept("venue", id, start, end, a.getId(), rule)) {
        selected = v;
        break;
      }
    }
    require(selected != null, "新场地时段不可用，原预约保持不变");
    a.setStartTime(start);
    a.setShootEndTime(start.plusMinutes(a.getDurationMinutes()));
    a.setOccupiedEndTime(end);
    a.setPhotographerId(photographerId);
    a.setPhotographerName(person.getName());
    a.setVenueId(selected.getId());
    a.setVenueName(selected.getName());
  }

  public List<com.shiguang.studio.service.BookingTimeSlots.Slot> allocationSlots(
      long packageId, long photographer, LocalDateTime start, LocalDateTime end) {
    var slots =
        new ArrayList<>(
            com.shiguang.studio.service.BookingTimeSlots.slots(
                "photographer", photographer, start, end));
    for (long venue : mapper.venueIds(packageId))
      slots.addAll(com.shiguang.studio.service.BookingTimeSlots.slots("venue", venue, start, end));
    return slots;
  }

  public boolean freeExcept(
      String kind,
      long id,
      LocalDateTime start,
      LocalDateTime end,
      long excluded,
      RuleRequest rule) {
    String t = table(kind);
    return within(mapper.schedule(t, id, start.toLocalDate()), t, start, end, rule)
        && mapper.conflicts(column(kind), id, start, end).stream()
            .allMatch(a -> a.getId() == excluded);
  }
}
