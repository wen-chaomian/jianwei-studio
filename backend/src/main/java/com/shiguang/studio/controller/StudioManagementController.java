package com.shiguang.studio.controller;

import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.dto.OperationsRequests.*;
import com.shiguang.studio.service.OperationsService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StudioManagementController {
  private final OperationsService service;

  public StudioManagementController(OperationsService service) {
    this.service = service;
  }

  @GetMapping("/booking-rule")
  public ApiResponse<?> rules() {
    return ApiResponse.ok(service.rules());
  }

  @GetMapping("/admin/booking-rule")
  public ApiResponse<?> adminRules() {
    return ApiResponse.ok(service.rules());
  }

  @PatchMapping("/admin/booking-rule")
  public ApiResponse<?> saveRules(@Valid @RequestBody RuleRequest r) {
    service.saveRules(r);
    return ApiResponse.ok(null);
  }

  @GetMapping("/admin/dashboard")
  public ApiResponse<?> dashboard(@RequestParam LocalDate from, @RequestParam LocalDate to) {
    return ApiResponse.ok(service.dashboard(from, to));
  }

  @GetMapping("/admin/operation-logs")
  public ApiResponse<?> logs(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.ok(service.logs(page, size));
  }

  @GetMapping("/staff/my-schedule")
  public ApiResponse<?> schedule(@RequestParam LocalDate date) {
    return ApiResponse.ok(service.mySchedule(date));
  }
}
