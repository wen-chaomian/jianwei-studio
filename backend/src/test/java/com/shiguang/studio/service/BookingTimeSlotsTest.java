package com.shiguang.studio.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class BookingTimeSlotsTest {
  LocalDateTime t = LocalDateTime.of(2026, 11, 10, 10, 0);

  Set<BookingTimeSlots.Slot> slots(LocalDateTime from, LocalDateTime to) {
    return new HashSet<>(BookingTimeSlots.slots("photographer", 1, from, to));
  }

  boolean overlap(Set<BookingTimeSlots.Slot> a, Set<BookingTimeSlots.Slot> b) {
    a.retainAll(b);
    return !a.isEmpty();
  }

  @Test
  void adjacentOccupancyDoesNotOverlap() {
    assertFalse(overlap(slots(t, t.plusMinutes(90)), slots(t.plusMinutes(90), t.plusMinutes(180))));
  }

  @Test
  void sameDaySeparateTimesDoNotOverlap() {
    assertFalse(overlap(slots(t, t.plusMinutes(90)), slots(t.plusHours(4), t.plusHours(5))));
  }

  @Test
  void differentStartsStillConflictWhenIntervalsOverlap() {
    assertTrue(overlap(slots(t, t.plusMinutes(90)), slots(t.plusMinutes(15), t.plusMinutes(105))));
  }

  @Test
  void bufferPeriodIsProtected() {
    assertTrue(overlap(slots(t, t.plusMinutes(90)), slots(t.plusMinutes(60), t.plusMinutes(150))));
  }

  @Test
  void arbitraryBoundariesAreRoundedConservatively() {
    assertEquals(2, slots(t.plusMinutes(1), t.plusMinutes(16)).size());
    assertEquals(1, slots(t, t.plusMinutes(15)).size());
  }

  @Test
  void otherResourcesAndKindsAreIndependent() {
    assertFalse(
        overlap(
            slots(t, t.plusHours(1)),
            new HashSet<>(BookingTimeSlots.slots("photographer", 2, t, t.plusHours(1)))));
    assertFalse(
        overlap(
            slots(t, t.plusHours(1)),
            new HashSet<>(BookingTimeSlots.slots("venue", 1, t, t.plusHours(1)))));
  }

  @Test
  void invalidRangesCannotGenerateUnboundedLocks() {
    assertThrows(IllegalArgumentException.class, () -> slots(t, t));
    assertThrows(IllegalArgumentException.class, () -> slots(t, t.plusDays(2)));
  }
}
