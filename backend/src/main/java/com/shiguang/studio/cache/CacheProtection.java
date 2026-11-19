package com.shiguang.studio.cache;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Per-instance bulkheads reserve separate database capacity for account validation. */
@Component
public class CacheProtection {
  private final Map<String, Semaphore> permits;
  private final Map<String, Integer> limits;
  private final ConcurrentMap<String, LongAdder> counters = new ConcurrentHashMap<>();
  private final long waitMillis;

  @Value("${studio.cache.account-wait-ms:500}")
  private long accountWaitMillis = 500;

  public CacheProtection(
      @Value("${studio.cache.business-loads:4}") int business,
      @Value("${studio.cache.account-loads:2}") int account,
      @Value("${studio.cache.load-wait-ms:50}") long waitMillis) {
    if (business < 1 || account < 1 || waitMillis < 0)
      throw new IllegalArgumentException("Invalid cache bulkhead limits");
    limits = Map.of("business", business, "account", account);
    permits =
        Map.of("business", new Semaphore(business, true), "account", new Semaphore(account, true));
    this.waitMillis = waitMillis;
  }

  public void count(String lane, String name) {
    counters.computeIfAbsent(lane + "." + name, k -> new LongAdder()).increment();
  }

  public <T> T load(String lane, Supplier<T> loader) {
    Semaphore gate = permits.get(lane);
    boolean acquired = false;
    long started = System.nanoTime();
    try {
      acquired =
          gate.tryAcquire(
              lane.equals("account") ? Math.max(waitMillis, accountWaitMillis) : waitMillis,
              TimeUnit.MILLISECONDS);
      if (!acquired) {
        count(lane, "loadRejected");
        throw new CacheBusyException();
      }
      count(lane, "databaseLoads");
      return loader.get();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      count(lane, "loadRejected");
      throw new CacheBusyException();
    } finally {
      counters
          .computeIfAbsent(lane + ".loadElapsedNanos", k -> new LongAdder())
          .add(System.nanoTime() - started);
      if (acquired) gate.release();
    }
  }

  public Map<String, Long> snapshot() {
    Map<String, Long> out = new TreeMap<>();
    counters.forEach((k, v) -> out.put(k, v.sum()));
    permits.forEach(
        (k, v) -> {
          out.put(k + ".limit", limits.get(k).longValue());
          out.put(k + ".active", (long) (limits.get(k) - v.availablePermits()));
        });
    return out;
  }
}
