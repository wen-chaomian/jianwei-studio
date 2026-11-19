package com.shiguang.studio.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class SessionScopeFilterTest {
  @Test
  void rejectsInvalidHeadersAndUnscopedAuthBeforeSessionCreation() throws Exception {
    for (String[] input :
        new String[][] {
          {"staff", "bad"},
          {"unexpected", "11111111-1111-4111-8111-111111111111"},
          {"customer", null}
        }) {
      var request = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
      request.addHeader("X-Studio-Portal", input[0]);
      if (input[1] != null) request.addHeader("X-Studio-Session-Scope", input[1]);
      var response = new MockHttpServletResponse();
      var chain = new MockFilterChain();
      new SessionScopeFilter().doFilter(request, response, chain);
      assertEquals(400, response.getStatus());
      assertNull(chain.getRequest());
      assertNull(request.getSession(false));
    }
  }

  @Test
  void publicApiCanRemainAnonymous() throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/v1/service-packages");
    var chain = new MockFilterChain();
    new SessionScopeFilter().doFilter(request, new MockHttpServletResponse(), chain);
    assertNotNull(chain.getRequest());
  }
}
