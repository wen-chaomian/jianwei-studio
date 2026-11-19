package com.shiguang.studio.service;

import com.shiguang.studio.mapper.BookingMapper;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Each upsert takes an exclusive InnoDB record lock until transaction completion. */
@Component
public class BookingSlotLock {
  private final BookingMapper mapper;

  public BookingSlotLock(BookingMapper mapper) {
    this.mapper = mapper;
  }

  public void lock(Collection<BookingTimeSlots.Slot> slots) {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Resource locks require a transaction");
    for (var slot : new TreeSet<>(slots))
      mapper.lockResourceSlot(slot.kind(), slot.resourceId(), slot.start());
  }
}
