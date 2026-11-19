package com.shiguang.studio.service.booking;

import static com.shiguang.studio.service.support.ServiceChecks.require;

import com.shiguang.studio.dto.OperationsRequests.RuleRequest;
import com.shiguang.studio.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class BookingPolicy {
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  public static String table(String kind) {
    return switch (kind) {
      case "photographer" -> "photographer_schedule";
      case "venue" -> "venue_schedule";
      default -> throw new BusinessException("无效的资源类型");
    };
  }

  public static String column(String kind) {
    return kind.equals("photographer") ? "photographer_id" : "venue_id";
  }

  public static boolean withinHours(LocalDateTime start, LocalDateTime end, RuleRequest rule) {
    return start.toLocalDate().equals(end.toLocalDate())
        && !start.toLocalTime().isBefore(rule.openTime())
        && !end.toLocalTime().isAfter(rule.closeTime());
  }

  public static void validDate(LocalDate date, RuleRequest rule) {
    var today = LocalDate.now(ZONE);
    require(
        !date.isBefore(today) && !date.isAfter(today.plusDays(rule.maxAdvanceDays())),
        "超出允许的预约日期范围");
  }

  public static boolean validStart(LocalDateTime start, RuleRequest rule, LocalDateTime now) {
    return start.getSecond() == 0
        && start.getNano() == 0
        && (start.getHour() * 60 + start.getMinute()) % rule.slotMinutes() == 0
        && !start.isBefore(now.plusHours(rule.minAdvanceHours()));
  }
}
