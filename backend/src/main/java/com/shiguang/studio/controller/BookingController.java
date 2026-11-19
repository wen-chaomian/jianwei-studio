package com.shiguang.studio.controller;

import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.dto.BookingRequests.*;
import com.shiguang.studio.service.BookingService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BookingController {
  private final BookingService service;

  public BookingController(BookingService service) {
    this.service = service;
  }

  @GetMapping("/availability")
  public ApiResponse<?> availability(
      @RequestParam long packageId,
      @RequestParam long photographerId,
      @RequestParam LocalDate date) {
    return ApiResponse.ok(service.availability(packageId, photographerId, date));
  }

  @PostMapping("/appointments")
  public ApiResponse<?> create(@Valid @RequestBody CreateAppointmentRequest request) {
    return ApiResponse.ok(service.create(request));
  }

  @GetMapping("/appointments")
  public ApiResponse<?> appointments(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) LocalDate date) {
    return ApiResponse.ok(service.appointments(page, size, date));
  }

  @GetMapping("/appointments/{id}/reschedule-options")
  public ApiResponse<?> rescheduleOptions(
      @PathVariable long id, @RequestParam long photographerId, @RequestParam LocalDate date) {
    return ApiResponse.ok(service.rescheduleAvailability(id, photographerId, date));
  }

  @GetMapping("/appointments/{id}")
  public ApiResponse<?> detail(@PathVariable long id) {
    return ApiResponse.ok(service.detail(id));
  }
}
