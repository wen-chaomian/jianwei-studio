package com.shiguang.studio.service;

import java.time.*;
import java.util.*;

/** Common half-open [start,end) occupancy buckets for Redis and database guards. */
public final class BookingTimeSlots {
  private BookingTimeSlots() {}

  public record Slot(String kind, long resourceId, LocalDateTime start)
      implements Comparable<Slot> {
    @Override
    public int compareTo(Slot other) {
      int c = kind.compareTo(other.kind);
      if (c != 0) return c;
      c = Long.compare(resourceId, other.resourceId);
      return c != 0 ? c : start.compareTo(other.start);
    }

    public String redisKey() {
      return "shiguang:{booking-lock}:slot:" + kind + ":" + resourceId + ":" + start;
    }
  }

  public static List<Slot> slots(String kind, long id, LocalDateTime start, LocalDateTime end) {
    if (!Set.of("photographer", "venue").contains(kind)
        || id <= 0
        || start == null
        || end == null
        || !end.isAfter(start)
        || end.isAfter(start.plusDays(1)))
      throw new IllegalArgumentException("Invalid booking occupancy interval");
    List<Slot> result = new ArrayList<>();
    LocalDateTime bucket = start.withMinute(start.getMinute() / 15 * 15).withSecond(0).withNano(0);
    for (; bucket.isBefore(end); bucket = bucket.plusMinutes(15))
      result.add(new Slot(kind, id, bucket));
    return result;
  }
}
