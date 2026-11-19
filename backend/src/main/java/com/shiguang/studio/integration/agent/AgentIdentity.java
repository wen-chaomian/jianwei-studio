package com.shiguang.studio.integration.agent;

import com.shiguang.studio.security.Portal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

public record AgentIdentity(long userId, String portal, String role) {
  public static AgentIdentity from(Authentication auth, HttpServletRequest request) {
    Portal portal = Portal.from(request);
    var roles =
        auth.getAuthorities().stream()
            .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
            .toList();
    if (!portal.accepts(roles))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权访问此端");
    String role =
        switch (portal) {
          case CUSTOMER -> "CUSTOMER";
          case ADMIN -> "ADMIN";
          case STAFF -> roles.contains("RECEPTIONIST") ? "RECEPTIONIST" : "PHOTOGRAPHER";
        };
    return new AgentIdentity(
        Long.parseLong(auth.getName()), portal.name().toLowerCase(java.util.Locale.ROOT), role);
  }
}
