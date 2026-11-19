package com.shiguang.studio.entity;

@lombok.Data
public class Venue {
  private Long id;
  private String name;
  private String location;
  private String description;
  private Boolean enabled;
}
