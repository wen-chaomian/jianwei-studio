package com.shiguang.studio.entity;

@lombok.Data
public class ResourceOccupancy {
  private Long appointmentId;
  private Long photographerId;
  private Long venueId;
  private java.time.LocalDateTime startTime;
  private java.time.LocalDateTime endTime;
}
