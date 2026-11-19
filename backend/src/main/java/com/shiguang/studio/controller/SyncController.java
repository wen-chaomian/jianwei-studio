package com.shiguang.studio.controller;

import com.shiguang.studio.cache.SyncVersions;
import com.shiguang.studio.common.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {
  private final SyncVersions versions;
  private final com.shiguang.studio.cache.SyncEventStreams streams;

  public SyncController(SyncVersions versions, com.shiguang.studio.cache.SyncEventStreams streams) {
    this.versions = versions;
    this.streams = streams;
  }

  @GetMapping(value = "/events", produces = "text/event-stream")
  public org.springframework.web.servlet.mvc.method.annotation.SseEmitter events(
      Authentication auth,
      jakarta.servlet.http.HttpServletRequest req,
      jakarta.servlet.http.HttpServletResponse res) {
    return streams.open(auth, req.getSession(false), false, res);
  }

  @GetMapping(value = "/catalog-events", produces = "text/event-stream")
  public org.springframework.web.servlet.mvc.method.annotation.SseEmitter catalogEvents(
      jakarta.servlet.http.HttpServletResponse res) {
    return streams.open(null, null, true, res);
  }

  @GetMapping("/version")
  public ApiResponse<?> version(Authentication auth) {
    return ApiResponse.ok(versions.snapshot(auth, false));
  }

  @GetMapping("/catalog")
  public ApiResponse<?> catalog() {
    return ApiResponse.ok(versions.snapshot(null, true));
  }
}
