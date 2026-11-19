package com.shiguang.studio.dto;
import jakarta.validation.constraints.*;
public record KnowledgeRequest(@NotBlank @Size(max=120) String title,
 @NotBlank @Size(max=40) String category, @NotBlank @Size(max=12000) String content,
 @NotNull @Pattern(regexp="DRAFT|PUBLISHED|DISABLED") String status,
 @NotNull @Min(0) Long version, @Pattern(regexp="PUBLIC|STAFF|ADMIN") String audience) {
 public KnowledgeRequest(String title,String category,String content,String status,Long version) {
   this(title,category,content,status,version,null);
 }
}
