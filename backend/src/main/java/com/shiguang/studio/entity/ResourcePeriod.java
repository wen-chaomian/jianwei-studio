package com.shiguang.studio.entity;

@lombok.Data
public class ResourcePeriod {
  private Long resourceId;
  private java.time.LocalTime startTime;
  private java.time.LocalTime endTime;
}
