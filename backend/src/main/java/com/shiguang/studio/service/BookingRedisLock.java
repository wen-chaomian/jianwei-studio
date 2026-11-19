package com.shiguang.studio.service;

import com.shiguang.studio.cache.BookingReadCache;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

/** Admission control only. Database locks remain the authority for resource allocation. */
@Component
public class BookingRedisLock {
  private static final long LEASE_MS = 30000;
  private static final DefaultRedisScript<Long> TAKE =
      new DefaultRedisScript<>(
          "for _,k in ipairs(KEYS) do if redis.call('EXISTS',k)==1 then return 0 end end; for _,k"
              + " in ipairs(KEYS) do redis.call('SET',k,ARGV[1],'PX',ARGV[2]) end; return 1",
          Long.class);
  private static final DefaultRedisScript<Long> RENEW =
      new DefaultRedisScript<>(
          "for _,k in ipairs(KEYS) do if redis.call('GET',k)~=ARGV[1] then return 0 end end; for"
              + " _,k in ipairs(KEYS) do redis.call('PEXPIRE',k,ARGV[2]) end; return 1",
          Long.class);
  private static final DefaultRedisScript<Long> RELEASE =
      new DefaultRedisScript<>(
          "local n=0; for _,k in ipairs(KEYS) do if redis.call('GET',k)==ARGV[1] then"
              + " n=n+redis.call('DEL',k) end end; return n",
          Long.class);
  private final StringRedisTemplate redis;
  private final BookingReadCache cache;
  private final ScheduledExecutorService renewal =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "booking-lock-renewal");
            t.setDaemon(true);
            return t;
          });

  public BookingRedisLock(StringRedisTemplate redis, BookingReadCache cache) {
    this.redis = redis;
    this.cache = cache;
  }

  public List<String> keys(long packageId, long photographer, LocalDateTime start) {
    var p = packageView(packageId);
    return keys(p, photographer, start, start.plusMinutes(p.durationMinutes() + p.bufferMinutes()));
  }

  public List<String> keys(
      long packageId, long photographer, LocalDateTime start, LocalDateTime end) {
    return keys(packageView(packageId), photographer, start, end);
  }

  private List<String> keys(
      com.shiguang.studio.vo.BookingViews.PackageVO p,
      long photographer,
      LocalDateTime start,
      LocalDateTime end) {
    List<BookingTimeSlots.Slot> slots =
        new ArrayList<>(BookingTimeSlots.slots("photographer", photographer, start, end));
    p.venues().forEach(v -> slots.addAll(BookingTimeSlots.slots("venue", v.id(), start, end)));
    return slots.stream().map(BookingTimeSlots.Slot::redisKey).distinct().sorted().toList();
  }

  private com.shiguang.studio.vo.BookingViews.PackageVO packageView(long id) {
    return cache.catalog().packages().stream()
        .filter(v -> v.id() == id)
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "套餐不存在"));
  }

  public <T> T execute(List<String> keys, Supplier<T> action) {
    try (Lease lease = acquire(keys)) {
      return action.get();
    }
  }

  /** For rescheduling within an existing transaction, hold through commit/rollback. */
  public void holdUntilCompletion(List<String> keys) {
    if (!TransactionSynchronizationManager.isSynchronizationActive())
      throw new IllegalStateException("Booking lock requires a transaction");
    Lease lease = acquire(keys);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            lease.close();
          }
        });
  }

  public Lease acquire(List<String> input) {
    List<String> keys = input.stream().distinct().sorted().toList();
    String token = UUID.randomUUID().toString();
    try {
      if (!Long.valueOf(1).equals(redis.execute(TAKE, keys, token, Long.toString(LEASE_MS))))
        throw new ResponseStatusException(HttpStatus.CONFLICT, "该摄影师或场地的此时段正在办理预约，请稍后重试");
    } catch (org.springframework.dao.DataAccessException ex) {
      // Fail closed for admission when Redis cannot confirm ownership; existing bookings remain
      // untouched.
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "预约服务暂时繁忙，请稍后重试");
    }
    return new Lease(keys, token);
  }

  public final class Lease implements AutoCloseable {
    private final List<String> keys;
    private final String token;
    private final ScheduledFuture<?> task;

    private Lease(List<String> keys, String token) {
      this.keys = keys;
      this.token = token;
      task =
          renewal.scheduleAtFixedRate(
              () -> {
                try {
                  redis.execute(RENEW, keys, token, Long.toString(LEASE_MS));
                } catch (org.springframework.dao.DataAccessException ignored) {
                  /* DB guards still protect allocation if the lease expires. */
                }
              },
              10,
              10,
              TimeUnit.SECONDS);
    }

    public void close() {
      task.cancel(false);
      try {
        redis.execute(RELEASE, keys, token);
      } catch (org.springframework.dao.DataAccessException ignored) {
        /* Lease expires without deleting another owner's lock. */
      }
    }
  }

  @PreDestroy
  public void close() {
    renewal.shutdownNow();
  }
}
