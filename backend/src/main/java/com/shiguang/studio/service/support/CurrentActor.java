package com.shiguang.studio.service.support;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

public final class CurrentActor {

  public static long user() {
    return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
  }

  public static boolean role(String r) {
    return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_" + r));
  }

  public static void allowed(boolean ok) {
    if (!ok) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有操作权限");
  }

  public static void admin() {
    allowed(role("ADMIN"));
  }

  public static void desk() {
    allowed(role("ADMIN") || role("RECEPTIONIST"));
  }
}
