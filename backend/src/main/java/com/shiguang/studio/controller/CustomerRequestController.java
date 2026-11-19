package com.shiguang.studio.controller;

import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.dto.OperationsRequests.*;
import com.shiguang.studio.service.OperationsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CustomerRequestController {
  private final OperationsService service;

  public CustomerRequestController(OperationsService service) {
    this.service = service;
  }

  @GetMapping("/customer-requests")
  public ApiResponse<?> requests(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.ok(service.requests(page, size));
  }

  @PostMapping("/customer-requests")
  public ApiResponse<?> request(@Valid @RequestBody RequestCreate r) {
    service.createRequest(r);
    return ApiResponse.ok(null);
  }

  @PatchMapping("/customer-requests/{id}")
  public ApiResponse<?> handle(@PathVariable long id, @Valid @RequestBody RequestHandle r) {
    service.handleRequest(id, r);
    return ApiResponse.ok(null);
  }
}
