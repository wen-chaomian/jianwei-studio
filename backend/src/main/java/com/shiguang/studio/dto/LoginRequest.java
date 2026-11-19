package com.shiguang.studio.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
    @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入11位手机号") String phone,
    @NotBlank @Size(max = 64) String password) {}
