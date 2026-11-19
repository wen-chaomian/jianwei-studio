package com.shiguang.studio.entity;

@lombok.Data
public class Schedule {
  private Long id;
  private Long resourceId;
  private java.time.LocalDate workDate;
  private Boolean enabled;
  private String source;
}
