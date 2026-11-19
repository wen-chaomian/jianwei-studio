package com.shiguang.studio.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SessionScopeFilter extends OncePerRequestFilter {
  private static final Pattern SCOPE =
      Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (req.getRequestURI().startsWith("/api/")) {
      String portal = req.getHeader("X-Studio-Portal"),
          scope = req.getHeader("X-Studio-Session-Scope");
      if ((portal != null && !Set.of("customer", "staff", "admin").contains(portal))
          || (scope != null && !SCOPE.matcher(scope).matches())
          || (scope == null && req.getRequestURI().startsWith("/api/v1/auth/"))) {
        res.setStatus(400);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"登录入口或会话已失效，请刷新页面后重试\"}");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
