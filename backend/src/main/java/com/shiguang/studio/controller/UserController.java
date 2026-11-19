package com.shiguang.studio.controller;

import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.service.UserService;
import com.shiguang.studio.vo.UserVO;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserService users;

  public UserController(UserService users) {
    this.users = users;
  }

  @GetMapping("/me")
  public ApiResponse<UserVO> me(Principal principal) {
    return ApiResponse.ok(users.get(Long.valueOf(principal.getName())));
  }
}
