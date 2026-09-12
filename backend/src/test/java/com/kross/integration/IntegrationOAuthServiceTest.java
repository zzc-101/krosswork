package com.kross.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kross.config.AppProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IntegrationOAuthServiceTest {
  @Test
  void joinUrlAndOrigin() {
    assertThat(IntegrationOAuthService.joinUrl("https://kross.example/", "/api/v2/x"))
        .isEqualTo("https://kross.example/api/v2/x");
    assertThat(IntegrationOAuthService.originOf("https://mcp.notion.com/mcp"))
        .isEqualTo("https://mcp.notion.com");
  }

  @Test
  void pkceIsUrlSafeAndStableForSameVerifier() {
    AppProperties properties = new AppProperties();
    IntegrationOAuthService oauth = new IntegrationOAuthService(new ObjectMapper(), properties);
    IntegrationOAuthService.Pkce first = oauth.newPkce();
    IntegrationOAuthService.Pkce second = oauth.newPkce();
    assertThat(first.verifier()).isNotBlank().doesNotContain("+", "/", "=");
    assertThat(first.challenge()).isNotBlank().doesNotContain("+", "/", "=");
    assertThat(first.verifier()).isNotEqualTo(second.verifier());
  }

  @Test
  void authorizationUrlIncludesPkceAndResource() {
    AppProperties properties = new AppProperties();
    properties.setExternalBaseUrl("https://kross.example");
    IntegrationOAuthService oauth = new IntegrationOAuthService(new ObjectMapper(), properties);
    IntegrationOAuthService.AuthorizationServer server = new IntegrationOAuthService.AuthorizationServer(
        "https://mcp.notion.com/mcp",
        "https://auth.notion.com",
        "https://auth.notion.com/authorize",
        "https://auth.notion.com/token",
        Optional.empty(),
        Optional.of("mcp"));
    IntegrationOAuthService.RegisteredClient client =
        new IntegrationOAuthService.RegisteredClient("client-1", Optional.empty());

    String url = oauth.authorizationUrl(server, client, "state-1", "challenge-1");

    assertThat(url).startsWith("https://auth.notion.com/authorize?");
    assertThat(url).contains("response_type=code");
    assertThat(url).contains("client_id=client-1");
    assertThat(url).contains("code_challenge=challenge-1");
    assertThat(url).contains("code_challenge_method=S256");
    assertThat(url).contains("state=state-1");
    assertThat(url).contains("resource=" + java.net.URLEncoder.encode(
        "https://mcp.notion.com/mcp", java.nio.charset.StandardCharsets.UTF_8));
    assertThat(url).contains("scope=mcp");
    assertThat(url).contains("redirect_uri=" + java.net.URLEncoder.encode(
        "https://kross.example/api/v2/integrations/oauth/callback",
        java.nio.charset.StandardCharsets.UTF_8));
  }
}
