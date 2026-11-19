package com.shiguang.studio.config;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.server.ResponseStatusException;

class PortalSessionConfigTest {
  private final HttpSessionIdResolver resolver = new PortalSessionConfig().httpSessionIdResolver();
  private final String reception = "11111111-1111-4111-8111-111111111111";
  private final String photographer = "22222222-2222-4222-8222-222222222222";

  private MockHttpServletRequest request(String portal, String scope) {
    var req = new MockHttpServletRequest();
    req.setServletPath("/api/v1/users/me");
    req.addHeader("X-Studio-Portal", portal);
    if (scope != null) req.addHeader("X-Studio-Session-Scope", scope);
    return req;
  }

  private Cookie cookie(String suffix, String value) {
    return new Cookie(
        "SHIGUANG_STAFF_SESSION" + suffix,
        Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void twoStaffTabsResolveOnlyTheirOwnCookieEvenWithLegacyCookiePresent() {
    var a = request("staff", reception);
    var b = request("staff", photographer);
    Cookie[] browserCookies = {
      cookie("_" + reception, "reception-session"),
      cookie("_" + photographer, "photographer-session"),
      cookie("", "old-shared-session")
    };
    a.setCookies(browserCookies);
    b.setCookies(browserCookies);
    assertEquals(List.of("reception-session"), resolver.resolveSessionIds(a));
    assertEquals(List.of("photographer-session"), resolver.resolveSessionIds(b));
  }

  @Test
  void newTabDoesNotInheritSharedStaffLogin() {
    var req = request("staff", reception);
    req.setCookies(
        cookie("", "old-shared-session"), cookie("_" + photographer, "photographer-session"));
    assertTrue(resolver.resolveSessionIds(req).isEmpty());
  }

  @Test
  void loginAndLogoutWriteOnlyTheSelectedTabCookieWithSecurityAttributes() {
    var req = request("staff", reception);
    var login = new MockHttpServletResponse();
    resolver.setSessionId(req, login, "session-id");
    String header = login.getHeader("Set-Cookie");
    assertNotNull(header);
    assertTrue(header.startsWith("SHIGUANG_STAFF_SESSION_" + reception + "="));
    assertTrue(header.contains("Max-Age=604800"));
    assertTrue(header.contains("HttpOnly"));
    assertTrue(header.contains("SameSite=Lax"));
    assertTrue(header.contains("Path=/api"));
    var logout = new MockHttpServletResponse();
    resolver.expireSession(req, logout);
    assertTrue(
        logout.getHeader("Set-Cookie").startsWith("SHIGUANG_STAFF_SESSION_" + reception + "="));
    assertTrue(logout.getHeader("Set-Cookie").contains("Max-Age=0"));
  }

  @Test
  void allPortalsUseIndependentScopedCookies() {
    for (String portal : List.of("customer", "admin")) {
      var res = new MockHttpServletResponse();
      resolver.setSessionId(request(portal, reception), res, "id");
      assertTrue(
          res.getHeader("Set-Cookie")
              .startsWith("SHIGUANG_" + portal.toUpperCase() + "_SESSION_" + reception + "="));
    }
  }

  @Test
  void invalidScopeCannotFallBackToSharedSession() {
    for (String scope : List.of("", "../../staff", "invalid;cookie=1", "a".repeat(1000)))
      assertThrows(
          ResponseStatusException.class, () -> resolver.resolveSessionIds(request("staff", scope)));
  }

  @Test
  void legacyCookiesAreNotAcceptedAsAuthenticatedSessions() {
    for (String portal : List.of("customer", "staff", "admin")) {
      var req = request(portal, null);
      req.setCookies(new Cookie("SHIGUANG_" + portal.toUpperCase() + "_SESSION", "legacy"));
      assertTrue(resolver.resolveSessionIds(req).isEmpty());
    }
  }

  @Test
  void sameScopeCannotReadAnotherPortalCookie() {
    var req = request("customer", reception);
    req.setCookies(cookie("_" + reception, "staff-session"));
    assertTrue(resolver.resolveSessionIds(req).isEmpty());
  }
}
