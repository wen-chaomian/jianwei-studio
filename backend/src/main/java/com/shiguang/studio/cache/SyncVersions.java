package com.shiguang.studio.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class SyncVersions {
  public static final String CHANNEL = "shiguang:sync:events:v1";
  private static final String PREFIX = "shiguang:sync:v1:";
  private final StringRedisTemplate redis;

  public SyncVersions(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public record Snapshot(boolean available, String version) {}

  public void user(Long id) {
    if (id != null) changed("user:" + id);
  }

  public void changed(String... scopes) {
    Set<String> unique = new HashSet<>(Arrays.asList(scopes));
    Runnable publish =
        () -> {
          try {
            Map<String, String> values = new HashMap<>();
            unique.forEach(s -> values.put(PREFIX + s, UUID.randomUUID().toString()));
            redis.opsForValue().multiSet(values);
            redis.convertAndSend(CHANNEL, String.join(",", unique));
          } catch (org.springframework.dao.DataAccessException ex) {
            // The client performs a bounded periodic reconciliation after missed notifications.
            org.slf4j.LoggerFactory.getLogger(SyncVersions.class)
                .warn("Sync version publication failed; periodic reconciliation will recover");
          }
        };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status == STATUS_COMMITTED) publish.run();
            }
          });
    } else publish.run();
  }

  public Snapshot snapshot(Authentication auth, boolean catalogOnly) {
    List<String> scopes = new ArrayList<>(List.of("configuration"));
    if (catalogOnly) scopes.add("availability");
    if (!catalogOnly) {
      scopes.add("user:" + auth.getName());
      if (auth.getAuthorities().stream()
          .anyMatch(a -> Set.of("ROLE_ADMIN", "ROLE_RECEPTIONIST").contains(a.getAuthority())))
        scopes.add("desk");
      if (auth.getAuthorities().stream()
          .anyMatch(a -> a.getAuthority().equals("ROLE_PHOTOGRAPHER"))) scopes.add("schedule");
    }
    try {
      List<String> values =
          redis.opsForValue().multiGet(scopes.stream().map(s -> PREFIX + s).toList());
      String value = String.valueOf(values);
      return new Snapshot(
          true,
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(value.getBytes(StandardCharsets.UTF_8))));
    } catch (org.springframework.dao.DataAccessException ex) {
      return new Snapshot(false, "");
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
