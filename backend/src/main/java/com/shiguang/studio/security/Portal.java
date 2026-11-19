package com.shiguang.studio.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

public enum Portal {
  CUSTOMER(Set.of("CUSTOMER")),
  ADMIN(Set.of("ADMIN")),
  STAFF(Set.of("RECEPTIONIST", "PHOTOGRAPHER"));
  private final Set<String> roles;

  Portal(Set<String> roles) {
    this.roles = roles;
  }

  public boolean accepts(java.util.Collection<String> actual) {
    return actual.stream().anyMatch(roles::contains);
  }

  public static Portal from(HttpServletRequest request) {
    String value = request.getHeader("X-Studio-Portal");
    if (value == null) return CUSTOMER;
    return switch (value) {
      case "admin" -> ADMIN;
      case "staff" -> STAFF;
      default -> CUSTOMER;
    };
  }
}
