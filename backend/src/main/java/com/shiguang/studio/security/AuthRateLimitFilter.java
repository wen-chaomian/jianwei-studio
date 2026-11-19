package com.shiguang.studio.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthRateLimitFilter extends OncePerRequestFilter {
  private final StringRedisTemplate redis;
  private static final DefaultRedisScript<Long> COUNT =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]);if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1])"
              + " end;return n",
          Long.class);

  public AuthRateLimitFilter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest r) {
    return !r.getMethod().equals("POST")
        || !(r.getRequestURI().equals("/api/v1/auth/login")
            || r.getRequestURI().equals("/api/v1/auth/register"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    Long count =
        redis.execute(
            COUNT,
            List.of("shiguang:auth-rate:" + req.getRequestURI() + ":" + req.getRemoteAddr()),
            "60");
    if (count != null && count > 20) {
      res.setStatus(429);
      res.setHeader("Retry-After", "60");
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter().write("{\"message\":\"操作频繁，请稍后再试\"}");
      return;
    }
    chain.doFilter(req, res);
  }
}
