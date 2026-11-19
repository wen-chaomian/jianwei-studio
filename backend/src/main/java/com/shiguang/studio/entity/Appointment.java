package com.shiguang.studio.entity;

@lombok.Data
public class Appointment {
  private Long id;
  private String pendingRequestType;
  private String appointmentNo;
  private Long customerId;
  private Long packageId;
  private Long photographerId;
  private Long venueId;
  private java.time.LocalDateTime startTime;
  private java.time.LocalDateTime shootEndTime;
  private java.time.LocalDateTime occupiedEndTime;
  private String status;
  private java.math.BigDecimal amount;
  private String packageName;
  private String photographerName;
  private String venueName;
  private String customerName;
  private String customerPhone;
  private Integer durationMinutes;
  private Integer bufferMinutes;
  private String source;
  private String remark;
  private String requestId;
  private String requestFingerprint;
  private Long createdBy;
}
