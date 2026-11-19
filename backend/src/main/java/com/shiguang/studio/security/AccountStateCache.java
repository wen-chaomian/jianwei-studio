package com.shiguang.studio.security;

import com.shiguang.studio.cache.RedisReadCache;
import com.shiguang.studio.mapper.UserMapper;
import com.shiguang.studio.vo.UserVO;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AccountStateCache {
  public record State(boolean enabled, int version, UserVO user) {}

  private final RedisReadCache cache;
  private final UserMapper users;

  public AccountStateCache(RedisReadCache cache, UserMapper users) {
    this.cache = cache;
    this.users = users;
  }

  public State get(long id) {
    return cache.getAccount(
        List.of("account:" + id),
        "state:" + id,
        State.class,
        Duration.ofSeconds(15),
        () -> load(id));
  }

  public void evictAfterCommit(long id) {
    cache.invalidateAfterCommit("account:" + id);
  }

  private State load(long id) {
    var user = users.selectById(id);
    if (user == null) return new State(false, -1, null);
    boolean enabled = Boolean.TRUE.equals(user.getEnabled());
    return new State(
        enabled,
        user.getAuthVersion(),
        new UserVO(
            user.getId(),
            user.getPhone(),
            user.getDisplayName(),
            enabled ? List.copyOf(users.roles(id)) : List.of()));
  }
}
