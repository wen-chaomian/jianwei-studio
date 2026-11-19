package com.shiguang.studio.exception;

import com.shiguang.studio.common.ApiResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(com.shiguang.studio.cache.CacheBusyException.class)
  public ResponseEntity<?> cacheBusy(com.shiguang.studio.cache.CacheBusyException e) {
    return ResponseEntity.status(503)
        .header("Retry-After", "1")
        .body(new ApiResponse<>(null, e.getMessage()));
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<?> business(BusinessException e) {
    return ResponseEntity.badRequest().body(new ApiResponse<>(null, e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> invalid(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest()
        .body(
            new ApiResponse<>(
                null, e.getBindingResult().getFieldErrors().getFirst().getDefaultMessage()));
  }

  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseEntity<?> duplicate(jakarta.servlet.http.HttpServletRequest request) {
    return ResponseEntity.status(409)
        .body(
            new ApiResponse<>(
                null,
                request.getRequestURI().endsWith("/auth/register")
                    ? "手机号已注册"
                    : "请求与已有记录冲突，请刷新后重试"));
  }

  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  public ResponseEntity<?> status(org.springframework.web.server.ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(new ApiResponse<>(null, e.getReason() == null ? "记录不存在或无权访问" : e.getReason()));
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<?> malformed(Exception e) {
    return ResponseEntity.badRequest().body(new ApiResponse<>(null, "请求参数格式不正确"));
  }

  @ExceptionHandler(org.springframework.dao.CannotAcquireLockException.class)
  public ResponseEntity<?> conflict(Exception e) {
    return ResponseEntity.status(409).body(new ApiResponse<>(null, "当前数据正在被其他操作更新，请刷新后重试"));
  }
}
