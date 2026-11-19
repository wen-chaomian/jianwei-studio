package com.shiguang.studio.entity;

@lombok.Data
public class Photographer {
  private Long id;
  private Long userId;
  private String name;
  private Boolean bookable;
  private Boolean enabled;
}
