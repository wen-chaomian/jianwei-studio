package com.shiguang.studio.controller;

import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.dto.OperationsRequests.*;
import com.shiguang.studio.service.OperationsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OperationsController {
  private final OperationsService service;

  public OperationsController(OperationsService service) {
    this.service = service;
  }

  @GetMapping("/appointments/{id}/activity")
  public ApiResponse<?> activity(@PathVariable long id) {
    return ApiResponse.ok(service.activity(id));
  }

  @PostMapping("/appointments/{id}/reschedule")
  public ApiResponse<?> reschedule(@PathVariable long id, @Valid @RequestBody RescheduleRequest r) {
    return ApiResponse.ok(service.reschedule(id, r));
  }

  @PostMapping("/appointments/{id}/{action:cancel|check-in|start|complete|mark-no-show}")
  public ApiResponse<?> action(
      @PathVariable long id, @PathVariable String action, @Valid @RequestBody ActionRequest r) {
    return ApiResponse.ok(service.act(id, action, r));
  }

  @PostMapping("/appointments/{id}/payments")
  public ApiResponse<?> payment(@PathVariable long id, @Valid @RequestBody PaymentRequest r) {
    return ApiResponse.ok(service.payment(id, r));
  }

  @PostMapping("/payments/{id}/refunds")
  public ApiResponse<?> refund(@PathVariable long id, @Valid @RequestBody PaymentRequest r) {
    return ApiResponse.ok(service.refund(id, r));
  }

  @PatchMapping("/appointments/{id}/service-record")
  public ApiResponse<?> notes(@PathVariable long id, @Valid @RequestBody NotesRequest r) {
    service.notes(id, r);
    return ApiResponse.ok(null);
  }
}
