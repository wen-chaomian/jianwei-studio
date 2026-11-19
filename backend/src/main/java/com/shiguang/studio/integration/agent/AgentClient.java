package com.shiguang.studio.integration.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AgentClient {
  private final HttpClient client =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(3))
          .build();
  private final ObjectMapper json;
  private final String base, token;
  @org.springframework.beans.factory.annotation.Autowired
  private com.shiguang.studio.integration.mcp.McpAuthorization mcpAuthorization;

  public AgentClient(
      ObjectMapper json,
      @Value("${studio.agent.url:http://127.0.0.1:8000}") String base,
      @Value("${studio.agent.internal-token:}") String token) {
    this.json = json;
    this.base = base.replaceAll("/$", "");
    this.token = token;
  }

  private HttpRequest request(AgentIdentity actor, String path, String method, Object body)
      throws Exception {
    if (token.length() < 32)
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "助手服务尚未配置");
    var builder =
        HttpRequest.newBuilder(URI.create(base + "/internal/v1" + path))
            .timeout(Duration.ofSeconds(180))
            .header("X-Agent-Token", token)
            .header("X-Actor-Id", Long.toString(actor.userId()))
            .header("X-Actor-Portal", actor.portal())
            .header("X-Actor-Role", actor.role());
    if (method.equals("POST") && path.endsWith("/messages"))
      builder.header("X-Mcp-Grant", mcpAuthorization.issue(actor));
    return builder
        .header("Content-Type", "application/json")
        .method(
            method,
            body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
        .build();
  }

  public JsonNode call(AgentIdentity actor, String method, String path, Object body) {
    try {
      var req = request(actor, path, method, body);
      req = HttpRequest.newBuilder(req, (k, v) -> true).timeout(Duration.ofSeconds(15)).build();
      var response = client.send(req, HttpResponse.BodyHandlers.ofString());
      check(response.statusCode(), response.body());
      return json.readTree(response.body());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw unavailable();
    } catch (Exception e) {
      throw unavailable();
    }
  }

  public InputStream stream(AgentIdentity actor, String path) {
    try {
      var response =
          client.send(request(actor, path, "GET", null), HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
        try (var input = response.body()) {
          check(
              response.statusCode(),
              new String(input.readNBytes(8192), java.nio.charset.StandardCharsets.UTF_8));
        }
      }
      if (!response.headers().firstValue("Content-Type").orElse("").contains("text/event-stream")) {
        response.body().close();
        throw unavailable();
      }
      return response.body();
    } catch (ResponseStatusException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw unavailable();
    } catch (Exception e) {
      throw unavailable();
    }
  }

  private void check(int status, String body) {
    if (status < 400) return;
    if (status == 401 || status >= 500) throw unavailable();
    String message = "助手请求未完成，请稍后重试";
    try {
      var detail = json.readTree(body).get("detail");
      if (detail != null && detail.isTextual()) message = detail.asText();
    } catch (Exception ignored) {
    }
    throw new ResponseStatusException(HttpStatus.valueOf(status), message);
  }

  private ResponseStatusException unavailable() {
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "助手服务暂不可用，请确认 Python 服务与 PostgreSQL 已启动");
  }
}
