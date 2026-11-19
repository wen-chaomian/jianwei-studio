package com.shiguang.studio.vo;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class BookingViews {
  public record ResourceVO(
      Long id,
      String name,
      String location,
      String description,
      boolean enabled,
      boolean bookable) {}

  public record PackageVO(
      Long id,
      String name,
      String description,
      String coverUrl,
      BigDecimal price,
      int durationMinutes,
      int bufferMinutes,
      boolean enabled,
      List<ResourceVO> photographers,
      List<ResourceVO> venues) {}

  public record PeriodVO(LocalTime startTime, LocalTime endTime) {}

  public record ScheduleVO(
      Long resourceId, LocalDate date, boolean enabled, List<PeriodVO> periods, String source) {
    public ScheduleVO(Long resourceId,LocalDate date,boolean enabled,List<PeriodVO> periods){this(resourceId,date,enabled,periods,"DATE");}
  }

  public record WeeklyDayVO(int dayOfWeek,boolean enabled,List<PeriodVO> periods) {}

  public record SlotVO(
      LocalDateTime startTime, LocalDateTime shootEndTime, LocalDateTime occupiedEndTime) {}

  public record AppointmentVO(
      Long id,
      String appointmentNo,
      String packageName,
      String photographerName,
      String venueName,
      String customerName,
      String customerPhone,
      LocalDateTime startTime,
      LocalDateTime shootEndTime,
      LocalDateTime occupiedEndTime,
      String status,
      BigDecimal amount,
      String remark,
      String pendingRequestType) {
    public AppointmentVO(
        Long id,
        String appointmentNo,
        String packageName,
        String photographerName,
        String venueName,
        String customerName,
        String customerPhone,
        LocalDateTime startTime,
        LocalDateTime shootEndTime,
        LocalDateTime occupiedEndTime,
        String status,
        BigDecimal amount,
        String remark) {
      this(
          id,
          appointmentNo,
          packageName,
          photographerName,
          venueName,
          customerName,
          customerPhone,
          startTime,
          shootEndTime,
          occupiedEndTime,
          status,
          amount,
          remark,
          null);
    }
  }

  public record PageVO<T>(List<T> items, long total, int page, int size) {}
}
