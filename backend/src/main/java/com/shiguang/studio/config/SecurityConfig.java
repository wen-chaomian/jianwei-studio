package com.shiguang.studio.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;

@Configuration
public class SecurityConfig {
  @Bean
  @org.springframework.core.annotation.Order(1)
  SecurityFilterChain mcpSecurity(HttpSecurity http) throws Exception {
    return http.securityMatcher("/internal/mcp")
        .csrf(c -> c.disable())
        .sessionManagement(c -> c.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
        .requestCache(c -> c.disable())
        .authorizeHttpRequests(c -> c.anyRequest().permitAll()).build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      SecurityContextRepository repository,
      org.springframework.data.redis.core.StringRedisTemplate redis,
      com.shiguang.studio.security.AccountStateCache accounts)
      throws Exception {
    return http.addFilterBefore(
            new com.shiguang.studio.security.PortalAccessFilter(),
            org.springframework.security.web.access.intercept.AuthorizationFilter.class)
        .addFilterBefore(
            new com.shiguang.studio.security.AccountStateFilter(accounts),
            com.shiguang.studio.security.PortalAccessFilter.class)
        .addFilterBefore(
            new com.shiguang.studio.security.AuthRateLimitFilter(redis),
            org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
                .class)
        .securityContext(c -> c.securityContextRepository(repository))
        .requestCache(c -> c.disable())
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC)
                    .permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/api/v1/sync/catalog",
                        "/api/v1/sync/catalog-events",
                        "/api/v1/service-packages",
                        "/api/v1/service-packages/*",
                        "/api/v1/availability",
                        "/api/v1/booking-rule", "/api/v1/knowledge", "/api/v1/knowledge/source/*/*")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/csrf", "/api/v1/auth/register", "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/staff/**")
                    .hasAnyRole("RECEPTIONIST", "PHOTOGRAPHER")
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter().write("{\"message\":\"请先登录\"}");
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter().write("{\"message\":\"没有操作权限或请求已过期，请刷新\"}");
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/v1/auth/logout")
                    .logoutSuccessHandler((req, res, a) -> res.setStatus(204)))
        .build();
  }
}
