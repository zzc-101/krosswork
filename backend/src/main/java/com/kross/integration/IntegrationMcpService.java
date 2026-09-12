package com.kross.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kross.agent.AgentMapper;
import com.kross.agent.AgentTokenDirectory;
import com.kross.agent.entity.Agent;
import com.kross.agent.entity.AgentSession;
import com.kross.api.ApiException;
import com.kross.config.AppProperties;
import com.kross.integration.entity.IntegrationGrant;
import com.kross.integration.entity.IntegrationInstallation;
import com.kross.knowledge.KnowledgeMcpService;
import com.kross.support.Tokens;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class IntegrationMcpService {
  private static final int PARSE_ERROR = -32700;
  private static final int INVALID_REQUEST = -32600;
  private static final int INVALID_PARAMS = -32602;
  private static final int INTERNAL_ERROR = -32603;

  private final IntegrationService integrations;
  private final AgentTokenDirectory tokens;
  private final AgentMapper agents;
  private final AppProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient http;
  private final ConcurrentHashMap<String, String> sessions = new ConcurrentHashMap<>();

  public IntegrationMcpService(
      IntegrationService integrations,
      AgentTokenDirectory tokens,
      AgentMapper agents,
      AppProperties properties,
      ObjectMapper mapper) {
    this(
        integrations,
        tokens,
        agents,
        properties,
        mapper,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
  }

  IntegrationMcpService(
      IntegrationService integrations,
      AgentTokenDirectory tokens,
      AgentMapper agents,
      AppProperties properties,
      ObjectMapper mapper,
      HttpClient http) {
    this.integrations = integrations;
    this.tokens = tokens;
    this.agents = agents;
    this.properties = properties;
    this.mapper = mapper;
    this.http = http;
  }

  public Map<String, Object> managedServers(Agent agent) {
    Map<String, Object> servers = new LinkedHashMap<>();
    String base = Optional.ofNullable(properties.getPublicBaseUrl()).map(String::trim)
        .filter(value -> !value.isBlank())
        .orElse("http://127.0.0.1:8787")
        .replaceAll("/+$", "");
    for (IntegrationGrant grant : integrations.activeGrants(agent.getOrganizationId(), agent.getUserId())) {
      Optional<IntegrationInstallation> installation = integrations.activeInstallation(grant.getInstallationId());
      if (installation.isEmpty()) {
        continue;
      }
      String catalogId = installation.get().getCatalogId();
      Map<String, Object> authorization = new LinkedHashMap<>();
      authorization.put("type", "bearer-env");
      authorization.put("env", KnowledgeMcpService.AGENT_TOKEN_ENV);
      Map<String, Object> server = new LinkedHashMap<>();
      server.put("transport", "streamable-http");
      server.put("url", base + "/mcp/integrations/" + installation.get().getId());
      server.put("authorization", authorization);
      servers.put("integrations_" + catalogId, server);
    }
    return servers;
  }

  public ResponseEntity<JsonNode> dispatch(String installationId, String authorization, JsonNode body) {
    AgentSession session = requireSession(authorization);
    Agent agent = agents.findByIdOnly(session.getAgentId())
        .orElseThrow(() -> new ApiException("agent_unauthenticated", "Invalid or expired agent token", 401));
    IntegrationInstallation installation = integrations.activeInstallation(installationId)
        .filter(row -> agent.getOrganizationId().equals(row.getOrganizationId()))
        .orElseThrow(() -> ApiException.notFound("Integration"));
    IntegrationGrant grant = integrations.activeGrant(installation.getId(), agent.getUserId())
        .orElseThrow(() -> new ApiException("integration_reauth_required", "Reconnect this integration", 401));
    if (body == null || body.isMissingNode() || body.isNull() || !body.isObject()) {
      return jsonRpcError(null, PARSE_ERROR, "Parse error");
    }
    if (!"2.0".equals(body.path("jsonrpc").asText())) {
      return jsonRpcError(idOf(body), INVALID_REQUEST, "Invalid Request");
    }
    if (!body.has("id")) {
      forward(agent, installation, grant, body, null, false);
      return ResponseEntity.accepted().build();
    }
    String method = body.path("method").asText("");
    JsonNode id = idOf(body);
    if ("tools/list".equals(method)) {
      return filterTools(forward(agent, installation, grant, body, id, false), installation);
    }
    if ("tools/call".equals(method)) {
      return callTool(agent, installation, grant, body, id);
    }
    return forward(agent, installation, grant, body, id, false);
  }

  public ResponseEntity<Void> close(String installationId, String authorization) {
    AgentSession session = requireSession(authorization);
    sessions.remove(session.getAgentId() + ":" + installationId);
    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<JsonNode> callTool(
      Agent agent,
      IntegrationInstallation installation,
      IntegrationGrant grant,
      JsonNode body,
      JsonNode id) {
    String name = body.path("params").path("name").asText("");
    if (name.isBlank()) {
      return jsonRpcError(id, INVALID_PARAMS, "Tool name is required");
    }
    if (!ActionPolicy.allows(installation.getActionPolicy(), name)) {
      return jsonRpcError(id, INVALID_PARAMS, "Unknown tool: " + name);
    }
    return forward(agent, installation, grant, body, id, true);
  }

  private ResponseEntity<JsonNode> filterTools(ResponseEntity<JsonNode> response, IntegrationInstallation installation) {
    JsonNode payload = response.getBody();
    JsonNode result = payload == null ? null : payload.get("result");
    if (payload == null || !(result instanceof ObjectNode resultObject) || !resultObject.path("tools").isArray()
        || ActionPolicy.allowsAll(installation.getActionPolicy())) {
      return response;
    }
    resultObject.set("tools", ActionPolicy.filter(mapper, resultObject.path("tools"), installation.getActionPolicy()));
    return ResponseEntity.status(response.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(payload);
  }

  private ResponseEntity<JsonNode> forward(
      Agent agent,
      IntegrationInstallation installation,
      IntegrationGrant grant,
      JsonNode body,
      JsonNode id,
      boolean retryOnUnauthorized) {
    IntegrationPreset preset = IntegrationPreset.require(installation.getCatalogId());
    String mcpUrl = preset.resolveMcpUrl(installation.getConfig())
        .orElseThrow(() -> ApiException.invalidRequest("This integration is missing its server address"));
    String access;
    try {
      access = integrations.accessToken(grant, installation);
    } catch (ApiException error) {
      return jsonRpcError(id, INTERNAL_ERROR, error.getMessage());
    }
    String sessionKey = agent.getId() + ":" + installation.getId();
    try {
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(mcpUrl))
          .timeout(Duration.ofSeconds(30))
          .header("accept", "application/json, text/event-stream")
          .header("content-type", "application/json")
          .header("authorization", "Bearer " + access)
          .header("mcp-protocol-version", KnowledgeMcpService.PROTOCOL_VERSION)
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
      Optional.ofNullable(sessions.get(sessionKey)).ifPresent(value -> builder.header("mcp-session-id", value));
      HttpResponse<String> upstream = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      upstream.headers().firstValue("mcp-session-id")
          .or(() -> upstream.headers().firstValue("MCP-Session-Id"))
          .ifPresent(value -> sessions.put(sessionKey, value));
      if (retryOnUnauthorized && upstream.statusCode() == 401) {
        grant.setTokenExpiresAt(java.time.Instant.EPOCH);
        return forward(agent, installation, grant, body, id, false);
      }
      if (upstream.statusCode() == 401) {
        return jsonRpcError(id, INTERNAL_ERROR, "Reconnect this integration");
      }
      JsonNode rpc = readJsonRpc(upstream);
      if (rpc == null) {
        return jsonRpcError(id, INTERNAL_ERROR, "Upstream MCP returned an empty response");
      }
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rpc);
    } catch (ApiException error) {
      return jsonRpcError(id, INTERNAL_ERROR, error.getMessage());
    } catch (Exception error) {
      return jsonRpcError(id, INTERNAL_ERROR, "Failed to call the connected service");
    }
  }

  private JsonNode readJsonRpc(HttpResponse<String> response) {
    String contentType = response.headers().firstValue("content-type").orElse("");
    String raw = Optional.ofNullable(response.body()).orElse("");
    try {
      if (contentType.contains("text/event-stream")) {
        for (String line : raw.split("\n")) {
          String trimmed = line.trim();
          if (!trimmed.startsWith("data:")) {
            continue;
          }
          String data = trimmed.substring(5).trim();
          if (data.isEmpty() || "[DONE]".equals(data)) {
            continue;
          }
          return mapper.readTree(data);
        }
        return null;
      }
      if (raw.isBlank()) {
        return null;
      }
      return mapper.readTree(raw);
    } catch (Exception error) {
      return null;
    }
  }

  private AgentSession requireSession(String authorization) {
    String token = bearer(authorization)
        .orElseThrow(() -> new ApiException("agent_unauthenticated", "Invalid or expired agent token", 401));
    String calculated = Tokens.sha256Hex(token);
    AgentSession session = tokens.findSession(calculated);
    if (session == null || !Tokens.hashEquals(calculated, session.getTokenHash())) {
      throw new ApiException("agent_unauthenticated", "Invalid or expired agent token", 401);
    }
    return session;
  }

  private static Optional<String> bearer(String authorization) {
    return Optional.ofNullable(authorization)
        .filter(value -> value.length() > 7 && value.regionMatches(true, 0, "Bearer ", 0, 7))
        .map(value -> value.substring(7).trim())
        .filter(value -> !value.isBlank());
  }

  private static JsonNode idOf(JsonNode body) {
    return body != null && body.has("id") ? body.get("id") : null;
  }

  private ResponseEntity<JsonNode> jsonRpcError(JsonNode id, int code, String message) {
    ObjectNode error = mapper.createObjectNode();
    error.put("code", code);
    error.put("message", Optional.ofNullable(message).orElse("Internal error"));
    ObjectNode payload = mapper.createObjectNode();
    payload.put("jsonrpc", "2.0");
    if (id == null || id.isMissingNode()) {
      payload.putNull("id");
    } else {
      payload.set("id", id);
    }
    payload.set("error", error);
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(payload);
  }

  public static ResponseEntity<JsonNode> unauthorized() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .build();
  }
}
