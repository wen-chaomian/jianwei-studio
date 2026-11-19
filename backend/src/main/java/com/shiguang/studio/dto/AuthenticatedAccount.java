package com.shiguang.studio.dto;

import com.shiguang.studio.vo.UserVO;

public record AuthenticatedAccount(UserVO user, int version) {}
