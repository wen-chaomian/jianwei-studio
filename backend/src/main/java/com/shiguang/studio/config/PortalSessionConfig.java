package com.shiguang.studio.config;

import com.shiguang.studio.security.Portal;
import jakarta.servlet.http.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.session.web.http.*;
import org.springframework.web.server.ResponseStatusException;

@Configuration
public class PortalSessionConfig {
  private static final Pattern SCOPE =
      Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

  private static CookieHttpSessionIdResolver resolver(String name) {
    DefaultCookieSerializer cookie = new DefaultCookieSerializer();
    cookie.setCookieName(name);
    cookie.setCookiePath("/api");
    cookie.setCookieMaxAge(7 * 24 * 60 * 60);
    cookie.setUseHttpOnlyCookie(true);
    cookie.setSameSite("Lax");
    CookieHttpSessionIdResolver resolver = new CookieHttpSessionIdResolver();
    resolver.setCookieSerializer(cookie);
    return resolver;
  }

  @Bean
  HttpSessionIdResolver httpSessionIdResolver() {
    return new HttpSessionIdResolver() {
      private CookieHttpSessionIdResolver forRequest(HttpServletRequest req) {
        Portal portal = Portal.from(req);
        String scope = req.getHeader("X-Studio-Session-Scope");
        if (scope == null) return null;
        if (!SCOPE.matcher(scope).matches())
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话标识无效");
        // Shared legacy cookies must never select an account for a different browser tab.
        return resolver("SHIGUANG_" + portal.name() + "_SESSION_" + scope);
      }

      public List<String> resolveSessionIds(HttpServletRequest req) {
        var resolver = forRequest(req);
        return resolver == null ? List.of() : resolver.resolveSessionIds(req);
      }

      public void setSessionId(HttpServletRequest req, HttpServletResponse res, String id) {
        var resolver = forRequest(req);
        if (resolver == null)
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请刷新页面后重新登录");
        resolver.setSessionId(req, res, id);
      }

      public void expireSession(HttpServletRequest req, HttpServletResponse res) {
        var resolver = forRequest(req);
        if (resolver != null) resolver.expireSession(req, res);
      }
    };
  }
}
