package com.shiguang.studio.controller;
import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.dto.KnowledgeRequest;
import com.shiguang.studio.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
@lombok.RequiredArgsConstructor
public class KnowledgeController {
 private final KnowledgeService service;
 @GetMapping("/knowledge") public ApiResponse<?> published(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(service.list(false,page,size));}
 @GetMapping("/admin/knowledge") public ApiResponse<?> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(service.list(true,page,size));}
 @PostMapping("/admin/knowledge") public ApiResponse<?> create(@Valid @RequestBody KnowledgeRequest r){return ApiResponse.ok(service.save(null,r));}
 @PatchMapping("/admin/knowledge/{id}") public ApiResponse<?> update(@PathVariable Long id,@Valid @RequestBody KnowledgeRequest r){return ApiResponse.ok(service.save(id,r));}
}
