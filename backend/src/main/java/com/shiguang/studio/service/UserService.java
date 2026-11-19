package com.shiguang.studio.service;

import com.shiguang.studio.dto.*;
import com.shiguang.studio.vo.UserVO;

public interface UserService {
  UserVO register(RegisterRequest request);

  AuthenticatedAccount login(LoginRequest request);

  UserVO get(Long id);
}
