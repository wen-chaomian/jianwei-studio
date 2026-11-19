package com.shiguang.studio.common;

public record ApiResponse<T>(T data, String message) {
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(data, "成功");
  }
}
