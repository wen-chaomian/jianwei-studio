package com.shiguang.studio.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Objects;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccountStateFilter extends OncePerRequestFilter {
  private final AccountStateCache accounts;

  public AccountStateFilter(AccountStateCache accounts) {
    this.accounts = accounts;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (req.getRequestURI().startsWith("/api/")
        && auth != null
        && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken)) {
      long id = Long.parseLong(auth.getName());
      var session = req.getSession(false);
      com.shiguang.studio.security.AccountStateCache.State state;
      try {
        state = accounts.get(id);
      } catch (com.shiguang.studio.cache.CacheBusyException ex) {
        res.setStatus(503);
        res.setHeader("Retry-After", "1");
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"账号校验暂时繁忙，请稍后重试\"}");
        return;
      }
      if (!state.enabled()
          || session == null
          || !Objects.equals(session.getAttribute("accountVersion"), state.version())) {
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        res.setStatus(401);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"账号状态已更新，请重新登录\"}");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
