package com.shiguang.studio.service.support;

import com.shiguang.studio.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

public final class ServiceChecks {
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  public static void require(boolean ok, String msg) {
    if (!ok) throw new BusinessException(msg);
  }

  public static LocalDateTime now() {
    return LocalDateTime.now(ZONE);
  }

  public static void page(int page, int size) {
    require(page > 0 && page <= 100000 && size > 0 && size <= 100, "分页参数不正确");
  }

  public static Object page(Object items, long total, int page, int size) {
    return Map.of("items", items, "total", total, "page", page, "size", size);
  }

  public static String statusLabel(String s) {
    return switch (s) {
      case "CONFIRMED" -> "待到店";
      case "CHECKED_IN" -> "已到店";
      case "IN_PROGRESS" -> "服务中";
      case "COMPLETED" -> "已完成";
      case "CANCELLED" -> "已取消";
      case "NO_SHOW" -> "已爽约";
      default -> s;
    };
  }

  public static String requestLabel(String type) {
    return type.equals("CANCEL") ? "取消申请" : type.equals("RESCHEDULE") ? "改期申请" : "人工申请";
  }
}
