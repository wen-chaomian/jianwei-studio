package com.shiguang.studio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class BookingRequests {
  public record PackageRequest(
      @NotBlank @Size(max = 80) String name,
      @NotNull @Size(max = 1000) String description,
      @Size(max = 500) String coverUrl,
      @NotNull @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
          BigDecimal price,
      @NotNull @Min(30) @Max(480) Integer durationMinutes,
      @NotNull @Min(0) @Max(120) Integer bufferMinutes,
      boolean enabled,
      @NotEmpty List<@NotNull @Positive Long> photographerIds,
      @NotEmpty List<@NotNull @Positive Long> venueIds) {}

  public record VenueRequest(
      @NotBlank @Size(max = 80) String name,
      @NotNull @Size(max = 200) String location,
      @NotNull @Size(max = 500) String description,
      boolean enabled) {}

  public record PhotographerRequest(boolean bookable) {}

  public record PeriodRequest(@NotNull LocalTime startTime, @NotNull LocalTime endTime) {}

  public record ScheduleRequest(
      @NotNull @Positive Long resourceId,
      @NotNull LocalDate date,
      boolean enabled,
      @NotNull @Size(max = 8) List<@NotNull @Valid PeriodRequest> periods) {}

  public record WeeklyDayRequest(@Min(1) @Max(7) int dayOfWeek,boolean enabled,
      @NotNull @Size(max=8) List<@NotNull @Valid PeriodRequest> periods) {}
  public record WeeklyScheduleRequest(@NotNull @Positive Long resourceId,
      @NotNull @Size(min=7,max=7) List<@NotNull @Valid WeeklyDayRequest> days) {}

  public record CreateAppointmentRequest(
      @NotNull @Positive Long packageId,
      @NotNull @Positive Long photographerId,
      @NotNull LocalDateTime startTime,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{16,64}") String requestId,
      @Size(max = 500) String remark,
      @Size(max = 40) String customerName,
      @Size(max = 20) String customerPhone) {}
}
