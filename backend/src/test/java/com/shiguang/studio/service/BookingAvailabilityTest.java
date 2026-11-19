package com.shiguang.studio.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shiguang.studio.cache.BookingReadCache;
import com.shiguang.studio.dto.OperationsRequests.RuleRequest;
import com.shiguang.studio.entity.ResourceOccupancy;
import com.shiguang.studio.entity.ResourcePeriod;
import com.shiguang.studio.exception.BusinessException;
import com.shiguang.studio.mapper.BookingMapper;
import com.shiguang.studio.mapper.OperationsMapper;
import com.shiguang.studio.service.impl.BookingServiceImpl;
import com.shiguang.studio.vo.BookingViews.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingAvailabilityTest {
  private final BookingMapper mapper = mock(BookingMapper.class);
  private final OperationsMapper operations = mock(OperationsMapper.class);
  private final BookingReadCache cache = mock(BookingReadCache.class);
  private final BookingServiceImpl booking =
      ServiceTestFactory.booking(
          mapper,
          operations,
          cache,
          mock(BookingConfigurationLock.class),
          mock(com.shiguang.studio.cache.SyncVersions.class),
          mock(com.shiguang.studio.service.BookingRedisLock.class),
          mock(com.shiguang.studio.service.BookingSlotLock.class));
  private final LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(2);
  private final ResourceVO person = new ResourceVO(3L, "李老师", "", "", true, true);
  private final ResourceVO venue = new ResourceVO(5L, "自然光影棚", "", "", true, false);

  @BeforeEach
  void fixtures() {
    when(cache.rules())
        .thenReturn(new RuleRequest(LocalTime.of(9, 0), LocalTime.of(20, 0), 30, 2, 30, 24));
    var p =
        new PackageVO(
            4L,
            "自然光个人写真",
            "",
            "",
            new BigDecimal("399"),
            60,
            30,
            true,
            List.of(person),
            List.of(venue));
    when(cache.catalog())
        .thenReturn(new BookingReadCache.Catalog(List.of(p), List.of(person), List.of(venue)));
  }

  private ResourcePeriod period(long id, int start, int end) {
    ResourcePeriod p = new ResourcePeriod();
    p.setResourceId(id);
    p.setStartTime(LocalTime.of(start, 0));
    p.setEndTime(LocalTime.of(end, 0));
    return p;
  }

  private ResourceOccupancy occupied(long photo, long room, int start, int hourEnd, int minuteEnd) {
    ResourceOccupancy a = new ResourceOccupancy();
    a.setAppointmentId(10L);
    a.setPhotographerId(photo);
    a.setVenueId(room);
    a.setStartTime(date.atTime(start, 0));
    a.setEndTime(date.atTime(hourEnd, minuteEnd));
    return a;
  }

  private List<LocalTime> times() {
    return booking.availability(4L, 3L, date).stream()
        .map(s -> s.startTime().toLocalTime())
        .toList();
  }

  @Test
  void bufferAndLunchBreakMustFitOneContinuousShiftAndAdjacentBookingsMayTouch() {
    when(cache.day(date))
        .thenReturn(
            new BookingReadCache.DaySnapshot(
                List.of(period(3, 9, 12), period(3, 13, 20)),
                List.of(period(5, 9, 20)),
                List.of(occupied(3, 5, 9, 10, 30))));
    var starts = times();
    assertFalse(starts.contains(LocalTime.of(10, 0)), "buffer occupies until 10:30");
    assertTrue(starts.contains(LocalTime.of(10, 30)), "half-open intervals may meet at 10:30");
    assertFalse(starts.contains(LocalTime.of(11, 0)), "buffer may not extend into the lunch break");
    assertTrue(
        starts.contains(LocalTime.of(18, 30)), "buffer ending exactly at closing time is valid");
    assertFalse(starts.contains(LocalTime.of(19, 0)), "buffer may not extend beyond closing time");
    verifyNoInteractions(mapper, operations);
  }

  @Test
  void venueOccupiedByAnotherPhotographerStillBlocksTheSlot() {
    when(cache.day(date))
        .thenReturn(
            new BookingReadCache.DaySnapshot(
                List.of(period(3, 9, 20)),
                List.of(period(5, 9, 20)),
                List.of(occupied(8, 5, 10, 11, 30))));
    var starts = times();
    assertFalse(starts.contains(LocalTime.of(10, 0)));
    assertFalse(starts.contains(LocalTime.of(11, 0)));
    assertTrue(starts.contains(LocalTime.of(11, 30)));
  }

  @Test
  void dateOutsideBookingWindowIsRejectedBeforeLoadingDay() {
    assertThrows(
        BusinessException.class,
        () -> booking.availability(4, 3, LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1)));
    assertThrows(
        BusinessException.class,
        () -> booking.availability(4, 3, LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(31)));
    verify(cache, never()).day(any());
  }
}
