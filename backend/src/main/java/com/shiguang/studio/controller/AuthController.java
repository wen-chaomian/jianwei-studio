package com.shiguang.studio.controller;

import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.dto.*;
import com.shiguang.studio.service.UserService;
import com.shiguang.studio.vo.UserVO;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final UserService users;
  private final SecurityContextRepository contexts;

  public AuthController(UserService users, SecurityContextRepository contexts) {
    this.users = users;
    this.contexts = contexts;
  }

  @GetMapping("/csrf")
  public ApiResponse<?> csrf(CsrfToken token) {
    return ApiResponse.ok(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
  }

  @PostMapping("/register")
  public ApiResponse<UserVO> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest req) {
    if (com.shiguang.studio.security.Portal.from(req)
        != com.shiguang.studio.security.Portal.CUSTOMER)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "此端不开放注册");
    return ApiResponse.ok(users.register(request));
  }

  @PostMapping("/login")
  public ApiResponse<UserVO> login(
      @Valid @RequestBody LoginRequest body, HttpServletRequest req, HttpServletResponse res) {
    var account = users.login(body);
    UserVO user = account.user();
    if (!com.shiguang.studio.security.Portal.from(req).accepts(user.roles()))
      throw new com.shiguang.studio.exception.BusinessException("账号不属于当前入口，请使用对应身份账号登录");
    req.getSession();
    req.changeSessionId();
    req.getSession().setAttribute("accountVersion", account.version());
    var auth =
        UsernamePasswordAuthenticationToken.authenticated(
            user.id().toString(),
            null,
            user.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    var ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(auth);
    SecurityContextHolder.setContext(ctx);
    contexts.saveContext(ctx, req, res);
    return ApiResponse.ok(user);
  }
}
