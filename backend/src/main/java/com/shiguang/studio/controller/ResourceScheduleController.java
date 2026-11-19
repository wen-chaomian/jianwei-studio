package com.shiguang.studio.controller;

import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.dto.BookingRequests.*;
import com.shiguang.studio.service.BookingService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ResourceScheduleController {
  private final BookingService service;

  public ResourceScheduleController(BookingService service) {
    this.service = service;
  }

  @GetMapping("/admin/schedules/{kind}")
  public ApiResponse<?> schedule(
      @PathVariable String kind, @RequestParam long resourceId, @RequestParam LocalDate date) {
    return ApiResponse.ok(service.schedule(kind, resourceId, date));
  }

  @PostMapping("/admin/schedules/{kind}")
  public ApiResponse<?> schedule(
      @PathVariable String kind, @Valid @RequestBody ScheduleRequest request) {
    return ApiResponse.ok(service.saveSchedule(kind, request));
  }
}
