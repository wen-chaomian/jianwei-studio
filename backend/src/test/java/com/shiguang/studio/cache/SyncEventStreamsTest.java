package com.shiguang.studio.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class SyncEventStreamsTest {
  private org.springframework.security.core.Authentication auth(String id, String role) {
    return new UsernamePasswordAuthenticationToken(
        id, "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  }

  @Test
  void customersAndPublicSubscribersNeverReceiveAnotherUsersNotifications() {
    assertFalse(
        SyncEventStreams.interested(
            auth("1", "CUSTOMER"), false, Set.of("user:2", "desk", "schedule")));
    assertTrue(SyncEventStreams.interested(auth("1", "CUSTOMER"), false, Set.of("user:1")));
    assertFalse(SyncEventStreams.interested(null, true, Set.of("user:1", "desk")));
    assertTrue(SyncEventStreams.interested(null, true, Set.of("configuration")));
  }

  @Test
  void staffRolesAndAdminHaveOnlyTheirPermittedEventScopes() {
    assertTrue(SyncEventStreams.interested(auth("2", "RECEPTIONIST"), false, Set.of("desk")));
    assertTrue(SyncEventStreams.interested(auth("3", "ADMIN"), false, Set.of("desk")));
    assertFalse(SyncEventStreams.interested(auth("4", "PHOTOGRAPHER"), false, Set.of("desk")));
    assertTrue(SyncEventStreams.interested(auth("4", "PHOTOGRAPHER"), false, Set.of("schedule")));
  }
}
