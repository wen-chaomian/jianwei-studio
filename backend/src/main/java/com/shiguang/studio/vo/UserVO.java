package com.shiguang.studio.vo;

import java.util.List;

public record UserVO(Long id, String phone, String displayName, List<String> roles) {}
