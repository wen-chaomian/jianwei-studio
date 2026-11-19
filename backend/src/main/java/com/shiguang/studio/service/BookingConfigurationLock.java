package com.shiguang.studio.service;

import com.shiguang.studio.mapper.BookingMapper;
import org.springframework.stereotype.Component;

/** Configuration edits are exclusive; independent booking transactions share the guard. */
@Component
public class BookingConfigurationLock {
  private final BookingMapper mapper;

  public BookingConfigurationLock(BookingMapper mapper) {
    this.mapper = mapper;
  }

  public void read() {
    mapper.lockConfigurationShared();
  }

  public void write() {
    mapper.lockConfigurationExclusive();
  }
}
