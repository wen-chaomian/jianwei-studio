package com.shiguang.studio.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class OperationsRequests {
  public record ActionRequest(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{16,64}") String requestId,
      @Min(0) int version,
      @NotNull @Size(max = 500) String reason) {}

  public record RescheduleRequest(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{16,64}") String requestId,
      @Min(0) int version,
      @NotNull @Size(max = 500) String reason,
      @NotNull @Positive Long photographerId,
      @NotNull LocalDateTime startTime) {}

  public record PaymentRequest(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{16,64}") String requestId,
      @NotNull @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
          BigDecimal amount,
      @NotBlank String method,
      @Size(max = 500) String reason) {}

  public record RequestCreate(
      @NotNull @Positive Long appointmentId,
      @NotBlank String type,
      @NotBlank @Size(max = 1000) String content,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{16,64}") String requestId) {}

  public record RequestHandle(
      @NotBlank String status,
      @Size(max = 500) String result,
      @Min(0) int version,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{16,64}") String requestId,
      @NotNull @Min(0) Integer appointmentVersion,
      @Positive Long photographerId,
      LocalDateTime startTime) {}

  public record RuleRequest(
      @NotNull LocalTime openTime,
      @NotNull LocalTime closeTime,
      @Min(15) @Max(60) int slotMinutes,
      @Min(0) @Max(168) int minAdvanceHours,
      @Min(1) @Max(90) int maxAdvanceDays,
      @Min(0) @Max(720) int changeCutoffHours) {}

  public record EmployeeRequest(
      @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone,
      @NotBlank @Size(max = 40) String name,
      @NotBlank @Size(max = 30) String employeeNo,
      @Size(min = 6, max = 72) String password,
      @NotEmpty @Size(max = 2)
          List<@NotBlank @Pattern(regexp = "RECEPTIONIST|PHOTOGRAPHER") String> roles,
      boolean enabled,
      boolean bookable) {}

  public record ProfileRequest(
      @NotBlank @Size(max = 40) String displayName,
      @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String contactPhone) {}

  public record CustomerUpdate(
      @NotBlank @Size(max = 40) String name,
      @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone,
      @NotNull @Size(max = 500) String remark) {}

  public record PasswordRequest(@NotBlank @Size(min = 8, max = 72) String password) {}

  public record NotesRequest(@NotNull @Size(max = 1000) String notes) {}
}
