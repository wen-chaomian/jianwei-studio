package com.shiguang.studio.controller;

import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.dto.OperationsRequests.*;
import com.shiguang.studio.service.OperationsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {
  private final OperationsService service;

  public NotificationController(OperationsService service) {
    this.service = service;
  }

  @GetMapping("/notifications")
  public ApiResponse<?> notifications(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.ok(service.notifications(page, size));
  }

  @PatchMapping("/notifications/{id}/read")
  public ApiResponse<?> read(@PathVariable long id) {
    service.readNotification(id);
    return ApiResponse.ok(null);
  }
}
