package com.shiguang.studio.controller;

import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.integration.agent.AgentIdentity;
import com.shiguang.studio.service.agent.AgentChatService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {
  private final AgentChatService chat;
  private final com.shiguang.studio.cache.SyncVersions versions;

  public AgentController(AgentChatService chat, com.shiguang.studio.cache.SyncVersions versions) {
    this.chat = chat;
    this.versions = versions;
  }

  public record NewConversation(@NotNull UUID request_id) {}

  public record Message(@NotNull UUID request_id, @NotBlank @Size(max = 4000) String content) {}

  public record ModelConfig(
      @Size(max = 512) String api_key,
      @NotNull @Pattern(regexp = "[a-zA-Z0-9_.:/-]*") @Size(max = 100) String model,
      @NotNull @Min(0) Long version) {}

  @GetMapping("/status")
  public ApiResponse<?> status(Authentication auth, HttpServletRequest req) {
    return ApiResponse.ok(chat.call(AgentIdentity.from(auth, req), "GET", "/status", null));
  }

  @GetMapping("/conversations")
  public ApiResponse<?> list(
      @RequestParam(defaultValue = "0") int offset, Authentication auth, HttpServletRequest req) {
    if (offset < 0 || offset > 10000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不正确");
    return ApiResponse.ok(
        chat.call(AgentIdentity.from(auth, req), "GET", "/conversations?offset=" + offset, null));
  }

  @PostMapping("/conversations")
  public ApiResponse<?> create(
      @Valid @RequestBody NewConversation body, Authentication auth, HttpServletRequest req) {
    return ApiResponse.ok(chat.call(AgentIdentity.from(auth, req), "POST", "/conversations", body));
  }

  @GetMapping("/conversations/{id}")
  public ApiResponse<?> detail(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "9223372036854775807") long before,
      Authentication auth,
      HttpServletRequest req) {
    if (before < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不正确");
    return ApiResponse.ok(
        chat.call(
            AgentIdentity.from(auth, req),
            "GET",
            "/conversations/" + id + "?before=" + before,
            null));
  }

  @PostMapping("/conversations/{id}/messages")
  public ApiResponse<?> send(
      @PathVariable UUID id,
      @Valid @RequestBody Message body,
      Authentication auth,
      HttpServletRequest req) {
    return ApiResponse.ok(
        chat.call(
            AgentIdentity.from(auth, req), "POST", "/conversations/" + id + "/messages", body));
  }

  @GetMapping(value = "/runs/{id}/events", produces = "text/event-stream")
  public org.springframework.web.servlet.mvc.method.annotation.SseEmitter events(
      @PathVariable UUID id, Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Accel-Buffering", "no");
    return chat.events(AgentIdentity.from(auth, req), id, req.getSession(false));
  }

  public record Rename(@NotBlank @Size(max = 80) String title) {}

  @PatchMapping("/conversations/{id}")
  public ApiResponse<?> rename(@PathVariable UUID id, @Valid @RequestBody Rename body,
      Authentication auth, HttpServletRequest req) {
    return ApiResponse.ok(chat.call(AgentIdentity.from(auth, req), "PATCH", "/conversations/" + id, body));
  }

  @DeleteMapping("/conversations/{id}")
  public ApiResponse<?> delete(@PathVariable UUID id, Authentication auth, HttpServletRequest req) {
    return ApiResponse.ok(chat.call(AgentIdentity.from(auth, req), "DELETE", "/conversations/" + id, null));
  }

  @PostMapping("/runs/{id}/stop")
  public ApiResponse<?> stop(@PathVariable UUID id, Authentication auth, HttpServletRequest req) {
    return ApiResponse.ok(chat.call(AgentIdentity.from(auth, req), "POST", "/runs/" + id + "/stop", null));
  }

  @GetMapping("/model-config")
  public ApiResponse<?> config(Authentication auth, HttpServletRequest req) {
    return ApiResponse.ok(chat.admin(AgentIdentity.from(auth, req), "GET", "/model-config", null));
  }

  @PostMapping("/model-config/key")
  public org.springframework.http.ResponseEntity<?> copyConfigKey(Authentication auth, HttpServletRequest req) {
    var result = chat.admin(AgentIdentity.from(auth, req), "POST", "/model-config/key", null);
    return org.springframework.http.ResponseEntity.ok()
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(ApiResponse.ok(result));
  }

  @PutMapping("/model-config")
  public ApiResponse<?> saveConfig(
      @Valid @RequestBody ModelConfig body, Authentication auth, HttpServletRequest req) {
    var result = chat.admin(AgentIdentity.from(auth, req), "PUT", "/model-config", body);
    versions.changed("configuration");
    return ApiResponse.ok(result);
  }

  @GetMapping("/models")
  public ApiResponse<?> models(Authentication auth, HttpServletRequest req) {
    return ApiResponse.ok(chat.admin(AgentIdentity.from(auth, req), "GET", "/models", null));
  }
}
