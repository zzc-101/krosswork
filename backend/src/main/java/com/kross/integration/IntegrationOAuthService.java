package com.kross.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kross.api.ApiException;
import com.kross.config.AppProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;

@Component
public class IntegrationOAuthService {
  private final HttpClient http;
  private final ObjectMapper mapper;
  private final AppProperties properties;
  private final SecureRandom random = new SecureRandom();

  public IntegrationOAuthService(ObjectMapper mapper, AppProperties properties) {
    this(mapper, properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
  }

  IntegrationOAuthService(ObjectMapper mapper, AppProperties properties, HttpClient http) {
    this.mapper = mapper;
    this.properties = properties;
    this.http = http;
  }

  public String callbackUri() {
    return joinUrl(properties.getExternalBaseUrl(), properties.getApi().getPrefix() + "/integrations/oauth/callback");
  }

  public String workbenchRedirect() {
    return joinUrl(properties.getExternalBaseUrl(), "/");
  }

  public Pkce newPkce() {
    byte[] raw = new byte[32];
    random.nextBytes(raw);
    String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
      return new Pkce(verifier, Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  public AuthorizationServer discover(String resourceUrl) {
    JsonNode resource = fetchProtectedResource(resourceUrl);
    String authorizationServer = firstText(resource.path("authorization_servers"))
        .orElseGet(() -> originOf(resourceUrl));
    JsonNode metadata = getJson(joinUrl(authorizationServer, "/.well-known/oauth-authorization-server"));
    String authorization = required(metadata, "authorization_endpoint");
    String token = required(metadata, "token_endpoint");
    Optional<String> registration = optionalText(metadata, "registration_endpoint");
    Optional<String> scope = joinTexts(metadata.path("scopes_supported"));
    return new AuthorizationServer(resourceUrl, authorizationServer, authorization, token, registration, scope);
  }

  public RegisteredClient registerClient(AuthorizationServer server) {
    String registration = server.registrationEndpoint()
        .orElseThrow(() -> new ApiException(
            "integration_oauth_unsupported",
            "This integration does not support automatic client registration",
            400));
    ObjectNode body = mapper.createObjectNode();
    body.put("client_name", "Kross");
    body.put("application_type", "web");
    body.put("token_endpoint_auth_method", "none");
    Optional.ofNullable(properties.getExternalBaseUrl()).map(String::trim).filter(value -> !value.isBlank())
        .ifPresent(value -> body.put("client_uri", value));
    body.putArray("redirect_uris").add(callbackUri());
    body.putArray("grant_types").add("authorization_code").add("refresh_token");
    body.putArray("response_types").add("code");
    JsonNode created = postJson(registration, body);
    String clientId = required(created, "client_id");
    Optional<String> secret = optionalText(created, "client_secret");
    return new RegisteredClient(clientId, secret);
  }

  public String authorizationUrl(
      AuthorizationServer server, RegisteredClient client, String state, String codeChallenge) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("response_type", "code");
    query.put("client_id", client.clientId());
    query.put("redirect_uri", callbackUri());
    query.put("code_challenge", codeChallenge);
    query.put("code_challenge_method", "S256");
    query.put("state", state);
    query.put("resource", server.resourceUrl());
    server.scope().ifPresent(scope -> query.put("scope", scope));
    return server.authorizationEndpoint()
        + (server.authorizationEndpoint().contains("?") ? "&" : "?")
        + encode(query);
  }

  public TokenSet exchange(
      AuthorizationServer server, RegisteredClient client, String code, String codeVerifier) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("grant_type", "authorization_code");
    form.put("code", code);
    form.put("redirect_uri", callbackUri());
    form.put("client_id", client.clientId());
    form.put("code_verifier", codeVerifier);
    form.put("resource", server.resourceUrl());
    client.clientSecret().ifPresent(secret -> form.put("client_secret", secret));
    return readToken(postForm(server.tokenEndpoint(), form));
  }

  public TokenSet refresh(AuthorizationServer server, RegisteredClient client, String refreshToken) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("grant_type", "refresh_token");
    form.put("refresh_token", refreshToken);
    form.put("client_id", client.clientId());
    form.put("resource", server.resourceUrl());
    client.clientSecret().ifPresent(secret -> form.put("client_secret", secret));
    return readToken(postForm(server.tokenEndpoint(), form));
  }

  private JsonNode fetchProtectedResource(String resourceUrl) {
    URI uri = URI.create(resourceUrl);
    String path = Optional.ofNullable(uri.getPath()).orElse("");
    String origin = originOf(resourceUrl);
    String inserted = path.isBlank() || "/".equals(path)
        ? origin + "/.well-known/oauth-protected-resource"
        : origin + "/.well-known/oauth-protected-resource" + path;
    String suffix = resourceUrl.replaceAll("/+$", "") + "/.well-known/oauth-protected-resource";
    String root = origin + "/.well-known/oauth-protected-resource";
    try {
      return getJson(inserted);
    } catch (ApiException first) {
      if (!suffix.equals(inserted)) {
        try {
          return getJson(suffix);
        } catch (ApiException ignored) {
          // Notion 文档写的是资源 URL 后直接挂 well-known。
        }
      }
      if (!root.equals(inserted)) {
        return getJson(root);
      }
      throw first;
    }
  }

  private TokenSet readToken(JsonNode body) {
    if (body.hasNonNull("error")) {
      String description = Optional.ofNullable(body.path("error_description").asText(null))
          .filter(value -> !value.isBlank())
          .orElse(body.path("error").asText("oauth_error"));
      throw new ApiException("integration_oauth_failed", description, 401);
    }
    String access = required(body, "access_token");
    Optional<String> refresh = optionalText(body, "refresh_token");
    int expiresIn = body.path("expires_in").asInt(3600);
    Instant expiresAt = Instant.now().plusSeconds(Math.max(expiresIn - 30L, 60L));
    String scope = body.path("scope").asText("");
    String label = optionalText(body, "workspace_name")
        .or(() -> optionalText(body, "email"))
        .orElse("已连接");
    return new TokenSet(access, refresh, expiresAt, scope, label);
  }

  private JsonNode getJson(String url) {
    try {
      HttpResponse<String> response = http.send(
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(12))
              .header("accept", "application/json")
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ApiException("integration_oauth_failed", "Failed to load OAuth metadata", 400);
      }
      return mapper.readTree(response.body());
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new ApiException("integration_oauth_failed", "Failed to load OAuth metadata", 400);
    }
  }

  private JsonNode postJson(String url, JsonNode body) {
    try {
      HttpResponse<String> response = http.send(
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(15))
              .header("accept", "application/json")
              .header("content-type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
              .build(),
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ApiException("integration_oauth_failed", "OAuth client registration failed", 400);
      }
      return mapper.readTree(response.body());
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new ApiException("integration_oauth_failed", "OAuth client registration failed", 400);
    }
  }

  private JsonNode postForm(String url, Map<String, String> form) {
    try {
      HttpResponse<String> response = http.send(
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(15))
              .header("accept", "application/json")
              .header("content-type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(encode(form)))
              .build(),
          HttpResponse.BodyHandlers.ofString());
      JsonNode body = mapper.readTree(Optional.ofNullable(response.body()).orElse("{}"));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        String description = Optional.ofNullable(body.path("error_description").asText(null))
            .filter(value -> !value.isBlank())
            .orElse("OAuth token request failed");
        throw new ApiException("integration_oauth_failed", description, 401);
      }
      return body;
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new ApiException("integration_oauth_failed", "OAuth token request failed", 401);
    }
  }

  private static String encode(Map<String, String> form) {
    return form.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  private static String required(JsonNode node, String field) {
    return optionalText(node, field)
        .orElseThrow(() -> new ApiException("integration_oauth_failed", "Missing " + field, 400));
  }

  private static Optional<String> optionalText(JsonNode node, String field) {
    return Optional.ofNullable(node).map(value -> value.path(field).asText(null))
        .filter(value -> !value.isBlank());
  }

  private static Optional<String> firstText(JsonNode node) {
    if (node instanceof ArrayNode array && !array.isEmpty()) {
      return Optional.ofNullable(array.get(0).asText(null)).filter(value -> !value.isBlank());
    }
    return Optional.empty();
  }

  private static Optional<String> joinTexts(JsonNode node) {
    if (!(node instanceof ArrayNode array) || array.isEmpty()) {
      return Optional.empty();
    }
    String joined = StreamSupport.stream(array.spliterator(), false)
        .map(item -> item.asText(""))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .collect(Collectors.joining(" "));
    return Optional.of(joined).filter(value -> !value.isBlank());
  }

  static String originOf(String url) {
    URI uri = URI.create(url);
    String origin = uri.getScheme() + "://" + uri.getAuthority();
    return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
  }

  static String joinUrl(String base, String path) {
    String left = Optional.ofNullable(base).orElse("").replaceAll("/+$", "");
    String right = Optional.ofNullable(path).orElse("");
    if (!right.startsWith("/")) {
      right = "/" + right;
    }
    return left + right;
  }

  public record Pkce(String verifier, String challenge) {}

  public record AuthorizationServer(
      String resourceUrl,
      String issuer,
      String authorizationEndpoint,
      String tokenEndpoint,
      Optional<String> registrationEndpoint,
      Optional<String> scope) {}

  public record RegisteredClient(String clientId, Optional<String> clientSecret) {}

  public record TokenSet(
      String accessToken,
      Optional<String> refreshToken,
      Instant expiresAt,
      String scope,
      String accountLabel) {}
}
