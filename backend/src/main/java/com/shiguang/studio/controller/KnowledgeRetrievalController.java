package com.shiguang.studio.controller;

import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.integration.agent.*;
import com.shiguang.studio.service.knowledge.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static com.shiguang.studio.service.support.CurrentActor.admin;

@RestController
@RequestMapping("/api/v1")
@lombok.RequiredArgsConstructor
public class KnowledgeRetrievalController {
  private final KnowledgeSearch search;
  private final KnowledgeJobs jobs;
  private final AgentClient agent;

  public record Query(@NotBlank @Size(max=500) String question) {}
  public record Config(@NotNull @Pattern(regexp="cn-beijing|ap-southeast-1") String region,
      @Pattern(regexp="(?:ws-[a-z0-9-]{1,60})?") String workspace,
      @Size(max=100) String model, @Size(max=512) String api_key,@NotNull @Min(0) Long version) {}

  @GetMapping("/admin/knowledge/config")
  public ApiResponse<?> config(Authentication auth,HttpServletRequest req) {
    admin();
    return ApiResponse.ok(agent.call(AgentIdentity.from(auth,req),"GET","/knowledge/config",null));
  }
  @GetMapping("/admin/knowledge/config/models")
  public ApiResponse<?> models(Authentication auth,HttpServletRequest req) {
    admin();
    return ApiResponse.ok(agent.call(AgentIdentity.from(auth,req),"GET","/knowledge/config/models",null));
  }
  @PostMapping("/admin/knowledge/config/key")
  public ApiResponse<?> copyKey(Authentication auth,HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res) {
    admin();
    res.setHeader("Cache-Control","no-store");
    return ApiResponse.ok(agent.call(AgentIdentity.from(auth,req),"POST","/knowledge/config/key",null));
  }
  @PatchMapping("/admin/knowledge/config")
  public ApiResponse<?> configure(@Valid @RequestBody Config config,Authentication auth,HttpServletRequest req) {
    admin();
    var result=agent.call(AgentIdentity.from(auth,req),"PUT","/knowledge/config",config);
    jobs.wakeConfiguration();
    return ApiResponse.ok(result);
  }
  @PostMapping("/admin/knowledge/search")
  public ApiResponse<?> preview(@Valid @RequestBody Query query,Authentication auth,HttpServletRequest req) {
    admin();
    return ApiResponse.ok(search.search(AgentIdentity.from(auth,req),query.question()));
  }
  @PostMapping("/admin/knowledge/{id}/retry")
  public ApiResponse<?> retry(@PathVariable long id) {
    admin();
    jobs.retry(id);
    return ApiResponse.ok(null);
  }
  @GetMapping("/knowledge/source/{id}/{version}")
  public ApiResponse<?> publicSource(@PathVariable long id,@PathVariable long version) {
    return ApiResponse.ok(search.source("customer",id,version));
  }
  @GetMapping({"/staff/knowledge/source/{id}/{version}","/admin/knowledge/source/{id}/{version}"})
  public ApiResponse<?> source(@PathVariable long id,@PathVariable long version,Authentication auth,HttpServletRequest req) {
    return ApiResponse.ok(search.source(AgentIdentity.from(auth,req).portal(),id,version));
  }
}
