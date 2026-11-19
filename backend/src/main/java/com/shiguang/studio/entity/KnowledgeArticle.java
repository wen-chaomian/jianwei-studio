package com.shiguang.studio.entity;
@lombok.Data
public class KnowledgeArticle {
 private Long id;
 private String title;
 private String category;
 private String audience;
 private String indexState;
 private String indexReason;
 private Long indexedVersion;
 private String content;
 private String status;
 private Long version;
 private java.time.LocalDateTime updatedAt;
}
