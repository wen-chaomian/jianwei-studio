package com.shiguang.studio.controller;

import com.shiguang.studio.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class WorkspaceController {
  @GetMapping("/api/v1/admin/workspace")
  public ApiResponse<?> admin() {
    return ApiResponse.ok(Map.of("name", "商家管理", "phase", "基础工程"));
  }

  @GetMapping("/api/v1/staff/workspace")
  public ApiResponse<?> staff() {
    return ApiResponse.ok(Map.of("name", "员工工作台", "phase", "基础工程"));
  }
}
