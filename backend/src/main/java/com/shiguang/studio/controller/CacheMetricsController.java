package com.shiguang.studio.controller;

import com.shiguang.studio.cache.CacheProtection;
import com.shiguang.studio.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/cache-metrics")
public class CacheMetricsController {
  private final CacheProtection protection;

  public CacheMetricsController(CacheProtection protection) {
    this.protection = protection;
  }

  @GetMapping
  public ApiResponse<?> metrics() {
    return ApiResponse.ok(protection.snapshot());
  }
}
