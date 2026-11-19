package com.shiguang.studio.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class PortalAccessFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (req.getRequestURI().startsWith("/api/")
        && auth != null
        && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken)) {
      Portal portal = Portal.from(req);
      boolean allowed =
          portal.accepts(
              auth.getAuthorities().stream()
                  .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                  .toList());
      String uri = req.getRequestURI();
      allowed &= !uri.startsWith("/api/v1/admin/") || portal == Portal.ADMIN;
      allowed &= !uri.startsWith("/api/v1/staff/") || portal == Portal.STAFF;
      if (!allowed) {
        res.setStatus(403);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"当前账号无权访问此端\"}");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
