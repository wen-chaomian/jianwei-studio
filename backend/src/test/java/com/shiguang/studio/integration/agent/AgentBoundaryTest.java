package com.shiguang.studio.integration.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.studio.security.AccountStateCache;
import com.shiguang.studio.service.agent.AgentChatService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.session.SessionRepository;
import org.springframework.web.server.ResponseStatusException;

class AgentBoundaryTest {
  @Test
  void identityComesFromAuthenticatedPrincipalNotClientHeaders() {
    var request = new MockHttpServletRequest();
    request.addHeader("X-Studio-Portal", "staff");
    request.addHeader("X-Actor-Id", "999");
    request.addHeader("X-Actor-Role", "ADMIN");
    var auth =
        new UsernamePasswordAuthenticationToken(
            "23", "", List.of(new SimpleGrantedAuthority("ROLE_PHOTOGRAPHER")));
    assertEquals(new AgentIdentity(23, "staff", "PHOTOGRAPHER"), AgentIdentity.from(auth, request));
    request = new MockHttpServletRequest();
    request.addHeader("X-Studio-Portal", "admin");
    var denied = request;
    assertThrows(ResponseStatusException.class, () -> AgentIdentity.from(auth, denied));
  }

  @Test
  void nonAdminCannotReadOrWriteModelCredentials() {
    var client = mock(AgentClient.class);
    var service =
        new AgentChatService(
            client,
            mock(AccountStateCache.class),
            mock(SessionRepository.class),
            new ObjectMapper(), mock(AgentMqGateway.class), mock(AgentResultCache.class));
    for (var actor :
        List.of(
            new AgentIdentity(1, "customer", "CUSTOMER"),
            new AgentIdentity(2, "staff", "RECEPTIONIST"))) {
      assertThrows(
          ResponseStatusException.class,
          () -> service.admin(actor, "PUT", "/model-config", Map.of("model", "x")));
      assertThrows(
          ResponseStatusException.class, () -> service.admin(actor, "GET", "/model-config", null));
    }
    verifyNoInteractions(client);
    service.close();
  }

  @Test
  void internalClientPropagatesOnlyServerIdentityAndSanitizesServerErrors() throws Exception {
    var identity = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/internal/v1/status",
        exchange -> {
          identity.set(
              exchange.getRequestHeaders().getFirst("X-Actor-Id")
                  + ":"
                  + exchange.getRequestHeaders().getFirst("X-Actor-Portal"));
          byte[] b = "{\"available\":true}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, b.length);
          exchange.getResponseBody().write(b);
          exchange.close();
        });
    server.createContext(
        "/internal/v1/failure",
        exchange -> {
          byte[] b = "secret-provider-error".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(500, b.length);
          exchange.getResponseBody().write(b);
          exchange.close();
        });
    server.start();
    try {
      var client =
          new AgentClient(
              new ObjectMapper(),
              "http://127.0.0.1:" + server.getAddress().getPort(),
              "test-internal-token-at-least-32-characters");
      var actor = new AgentIdentity(42, "customer", "CUSTOMER");
      assertTrue(client.call(actor, "GET", "/status", null).get("available").asBoolean());
      assertEquals("42:customer", identity.get());
      var failure =
          assertThrows(
              ResponseStatusException.class, () -> client.call(actor, "GET", "/failure", null));
      assertFalse(failure.getReason().contains("secret"));
    } finally {
      server.stop(0);
    }
  }
}
