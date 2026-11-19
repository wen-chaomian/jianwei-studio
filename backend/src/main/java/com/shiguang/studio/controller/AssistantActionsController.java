package com.shiguang.studio.controller;
import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.integration.agent.AgentIdentity;
import com.shiguang.studio.service.assistant.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1")
public class AssistantActionsController {
  private final ActionCards actions;
  private final CustomerPreferences preferences;
  @GetMapping("/assistant/actions/{id}")
  public ApiResponse<?> action(@PathVariable UUID id,Authentication auth,HttpServletRequest req){
    return ApiResponse.ok(actions.fetch(AgentIdentity.from(auth,req),id));
  }
  @PostMapping("/assistant/actions/{id}/{decision:confirm|cancel}")
  public ApiResponse<?> decide(@PathVariable UUID id,@PathVariable String decision,Authentication auth,HttpServletRequest req){
    return ApiResponse.ok(actions.decide(AgentIdentity.from(auth,req),id,decision.equals("confirm")));
  }
  @GetMapping("/customer/preferences")
  public ApiResponse<?> preferences(){return ApiResponse.ok(preferences.list());}
  public record Edit(@NotBlank @Size(max=40) String topic,@Size(max=300) String preference,@Positive long version){}
  @PatchMapping("/customer/preferences")
  public ApiResponse<?> edit(@RequestBody @Valid Edit e){preferences.save(e.topic(),e.preference(),e.version());return ApiResponse.ok(null);}
  @PostMapping("/customer/preferences/remove")
  public ApiResponse<?> remove(@RequestBody @Valid Edit e){preferences.remove(e.topic(),e.version());return ApiResponse.ok(null);}
}
