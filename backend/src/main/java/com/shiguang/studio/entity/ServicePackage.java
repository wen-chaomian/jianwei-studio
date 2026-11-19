package com.shiguang.studio.entity;

@lombok.Data
public class ServicePackage {
  private Long id;
  private String name;
  private String description;
  private String coverUrl;
  private java.math.BigDecimal price;
  private Integer durationMinutes;
  private Integer bufferMinutes;
  private Boolean enabled;
}
