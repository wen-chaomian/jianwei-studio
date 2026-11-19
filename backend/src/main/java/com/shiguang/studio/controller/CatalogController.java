package com.shiguang.studio.controller;

import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.dto.BookingRequests.*;
import com.shiguang.studio.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
  private final BookingService service;

  public CatalogController(BookingService service) {
    this.service = service;
  }

  @GetMapping("/service-packages")
  public ApiResponse<?> packages() {
    return ApiResponse.ok(service.packages(false));
  }

  @GetMapping("/service-packages/{id}")
  public ApiResponse<?> packageDetail(@PathVariable long id) {
    return ApiResponse.ok(service.packageDetail(id, false));
  }

  @GetMapping("/admin/service-packages")
  public ApiResponse<?> adminPackages() {
    return ApiResponse.ok(service.packages(true));
  }

  @PostMapping("/admin/service-packages")
  public ApiResponse<?> addPackage(@Valid @RequestBody PackageRequest request) {
    return ApiResponse.ok(service.savePackage(null, request));
  }

  @PatchMapping("/admin/service-packages/{id}")
  public ApiResponse<?> updatePackage(
      @PathVariable long id, @Valid @RequestBody PackageRequest request) {
    return ApiResponse.ok(service.savePackage(id, request));
  }

  @GetMapping("/admin/venues")
  public ApiResponse<?> venues() {
    return ApiResponse.ok(service.venues());
  }

  @PostMapping("/admin/venues")
  public ApiResponse<?> addVenue(@Valid @RequestBody VenueRequest request) {
    return ApiResponse.ok(service.saveVenue(null, request));
  }

  @PatchMapping("/admin/venues/{id}")
  public ApiResponse<?> updateVenue(
      @PathVariable long id, @Valid @RequestBody VenueRequest request) {
    return ApiResponse.ok(service.saveVenue(id, request));
  }

  @GetMapping("/admin/photographers")
  public ApiResponse<?> photographers() {
    return ApiResponse.ok(service.photographers());
  }

  @PatchMapping("/admin/photographers/{id}")
  public ApiResponse<?> setBookable(
      @PathVariable long id, @RequestBody PhotographerRequest request) {
    service.setBookable(id, request.bookable());
    return ApiResponse.ok(null);
  }
}
