package com.shiguang.studio.controller;

import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.dto.OperationsRequests.*;
import com.shiguang.studio.service.OperationsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountManagementController {
  private final OperationsService service;

  public AccountManagementController(OperationsService service) {
    this.service = service;
  }

  @GetMapping("/admin/employees")
  public ApiResponse<?> employees() {
    return ApiResponse.ok(service.employees());
  }

  @PostMapping("/admin/employees")
  public ApiResponse<?> employee(@Valid @RequestBody EmployeeRequest r) {
    service.employee(null, r);
    return ApiResponse.ok(null);
  }

  @PatchMapping("/admin/employees/{id}")
  public ApiResponse<?> employee(@PathVariable long id, @Valid @RequestBody EmployeeRequest r) {
    service.employee(id, r);
    return ApiResponse.ok(null);
  }

  @GetMapping("/customers")
  public ApiResponse<?> customers(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "") String q) {
    return ApiResponse.ok(service.customers(page, size, q));
  }

  @GetMapping("/customers/{id}")
  public ApiResponse<?> customer(
      @PathVariable long id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.ok(service.customer(id, page, size));
  }

  @PatchMapping("/customers/{id}")
  public ApiResponse<?> customer(@PathVariable long id, @Valid @RequestBody CustomerUpdate r) {
    service.updateCustomer(id, r);
    return ApiResponse.ok(null);
  }

  @PostMapping("/admin/customers/{id}/reset-password")
  public ApiResponse<?> reset(@PathVariable long id, @Valid @RequestBody PasswordRequest r) {
    service.resetCustomerPassword(id, r);
    return ApiResponse.ok(null);
  }

  @GetMapping("/profile")
  public ApiResponse<?> profile() {
    return ApiResponse.ok(service.profile());
  }

  @PatchMapping("/profile")
  public ApiResponse<?> profile(@Valid @RequestBody ProfileRequest r) {
    service.profile(r);
    return ApiResponse.ok(null);
  }
}
