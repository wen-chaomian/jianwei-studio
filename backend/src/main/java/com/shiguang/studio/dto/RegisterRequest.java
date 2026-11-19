package com.shiguang.studio.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank @Pattern(regexp = "^1[3-9][0-9]{9}$", message = "请输入11位手机号") String phone,
    @NotBlank @Size(min = 2, max = 40) String displayName,
    @NotBlank @Size(min = 8, max = 64, message = "密码需为8至64位") String password,
    @NotBlank String confirmPassword) {}
