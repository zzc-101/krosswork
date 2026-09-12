package com.kross.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kross.api.ApiException;
import com.kross.catalog.CredentialVault;
import com.kross.identity.OrganizationAccess;
import com.kross.identity.OrganizationAction;
import com.kross.identity.OrganizationContext;
import com.kross.integration.dto.IntegrationViews;
import com.kross.integration.entity.IntegrationGrant;
import com.kross.integration.entity.IntegrationInstallation;
import com.kross.integration.entity.IntegrationOAuthState;
import com.kross.support.Ids;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationService {
  private static final java.time.Duration STATE_TTL = java.time.Duration.ofMinutes(15);

  private final IntegrationMapper integrations;
  private final IntegrationOAuthService oauth;
  private final CredentialVault vault;
  private final OrganizationAccess access;
  private final ObjectMapper mapper;

  public IntegrationService(
      IntegrationMapper integrations,
      IntegrationOAuthService oauth,
      CredentialVault vault,
      OrganizationAccess access,
      ObjectMapper mapper) {
    this.integrations = integrations;
    this.oauth = oauth;
    this.vault = vault;
    this.access = access;
    this.mapper = mapper;
  }

  public List<IntegrationViews.CatalogItem> listCatalog(String organizationId) {
    access.require(organizationId, OrganizationAction.ORGANIZATION_READ);
    List<IntegrationInstallation> installed = integrations.listInstallations(organizationId);
    return IntegrationPreset.all().stream()
        .map(preset -> {
          Optional<IntegrationInstallation> row = installed.stream()
              .filter(item -> preset.id().equals(item.getCatalogId()))
              .findFirst();
          return new IntegrationViews.CatalogItem(
              preset.id(),
              preset.name(),
              preset.description(),
              preset.installable(),
              row.isPresent(),
              preset.requiresHost(),
              row.flatMap(item -> IntegrationPreset.hostOf(item.getConfig())).orElse(null),
              row.map(IntegrationInstallation::getId).orElse(null),
              row.map(IntegrationInstallation::getStatus).orElse(null));
        })
        .toList();
  }

  @Transactional
  public IntegrationViews.CatalogItem install(String organizationId, IntegrationViews.InstallRequest request) {
    OrganizationContext context = access.require(organizationId, OrganizationAction.ORGANIZATION_UPDATE);
    String catalogId = Ids.requireResourceId(
        Optional.ofNullable(request).map(IntegrationViews.InstallRequest::catalogId).orElse(""),
        "Invalid integration");
    IntegrationPreset preset = IntegrationPreset.find(catalogId)
        .filter(IntegrationPreset::installable)
        .orElseThrow(() -> ApiException.invalidRequest("This integration is not available yet"));
    Optional<IntegrationInstallation> existing = integrations.findInstallationByCatalog(organizationId, catalogId);
    if (existing.isPresent()) {
      IntegrationInstallation row = existing.get();
      applyHost(preset, row, Optional.ofNullable(request).map(IntegrationViews.InstallRequest::host));
      if (!"active".equals(row.getStatus())) {
        row.setStatus("active");
      }
      row.setUpdatedAt(Instant.now());
      integrations.updateInstallation(row);
      return toCatalogItem(preset, row);
    }
    Instant now = Instant.now();
    IntegrationInstallation row = new IntegrationInstallation();
    row.setId(UUID.randomUUID().toString());
    row.setOrganizationId(organizationId);
    row.setCatalogId(preset.id());
    row.setStatus("active");
    row.setConfig(mapper.createObjectNode());
    applyHost(preset, row, Optional.ofNullable(request).map(IntegrationViews.InstallRequest::host));
    row.setActionPolicy(policyFrom(preset, Optional.ofNullable(request).map(IntegrationViews.InstallRequest::tools)));
    row.setCreatedBy(context.userId());
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    integrations.insertInstallation(row);
    return toCatalogItem(preset, row);
  }

  @Transactional
  public IntegrationViews.CatalogItem patch(
      String organizationId, String installationId, IntegrationViews.PatchRequest request) {
    access.require(organizationId, OrganizationAction.ORGANIZATION_UPDATE);
    IntegrationInstallation row = requireInstallation(organizationId, installationId);
    IntegrationPreset preset = IntegrationPreset.require(row.getCatalogId());
    Optional.ofNullable(request).map(IntegrationViews.PatchRequest::status)
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .ifPresent(status -> {
          if (!"active".equals(status) && !"disabled".equals(status)) {
            throw ApiException.invalidRequest("Invalid integration status");
          }
          row.setStatus(status);
        });
    Optional.ofNullable(request).map(IntegrationViews.PatchRequest::tools)
        .ifPresent(tools -> row.setActionPolicy(policyFrom(preset, Optional.of(tools))));
    applyHost(preset, row, Optional.ofNullable(request).map(IntegrationViews.PatchRequest::host));
    row.setUpdatedAt(Instant.now());
    integrations.updateInstallation(row);
    return toCatalogItem(preset, row);
  }

  @Transactional
  public void uninstall(String organizationId, String installationId) {
    access.require(organizationId, OrganizationAction.ORGANIZATION_UPDATE);
    IntegrationInstallation row = requireInstallation(organizationId, installationId);
    integrations.deleteGrantsByInstallation(row.getId());
    integrations.deleteInstallation(row.getId());
  }

  public List<IntegrationViews.MemberItem> listMine(String organizationId) {
    OrganizationContext context = access.require(organizationId, OrganizationAction.AGENT_READ);
    return integrations.listInstallations(organizationId).stream()
        .filter(row -> "active".equals(row.getStatus()))
        .map(row -> {
          IntegrationPreset preset = IntegrationPreset.require(row.getCatalogId());
          Optional<IntegrationGrant> grant = integrations.findGrant(row.getId(), context.userId())
              .filter(item -> !"revoked".equals(item.getStatus()));
          return new IntegrationViews.MemberItem(
              row.getId(),
              preset.id(),
              preset.name(),
              true,
              grant.filter(item -> "active".equals(item.getStatus())).isPresent(),
              IntegrationPreset.hostOf(row.getConfig()).orElse(null),
              grant.map(IntegrationGrant::getAccountLabel).orElse(null),
              grant.map(IntegrationGrant::getStatus).orElse(null));
        })
        .toList();
  }

  @Transactional
  public IntegrationViews.ConnectResponse startConnect(String organizationId, String installationId) {
    OrganizationContext context = access.require(organizationId, OrganizationAction.AGENT_CHAT);
    IntegrationInstallation row = requireActiveInstallation(organizationId, installationId);
    IntegrationPreset preset = IntegrationPreset.require(row.getCatalogId());
    IntegrationOAuthService.AuthorizationServer server = oauth.discover(mcpUrl(preset, row));
    IntegrationOAuthService.RegisteredClient client = registeredClient(row, server);
    IntegrationOAuthService.Pkce pkce = oauth.newPkce();
    integrations.deleteExpiredOAuthStates();
    Instant now = Instant.now();
    IntegrationOAuthState state = new IntegrationOAuthState();
    state.setId(UUID.randomUUID().toString());
    state.setInstallationId(row.getId());
    state.setUserId(context.userId());
    state.setOrganizationId(organizationId);
    state.setCodeVerifier(pkce.verifier());
    state.setRedirectAfter(oauth.workbenchRedirect());
    state.setExpiresAt(now.plus(STATE_TTL));
    state.setCreatedAt(now);
    integrations.insertOAuthState(state);
    return new IntegrationViews.ConnectResponse(
        oauth.authorizationUrl(server, client, state.getId(), pkce.challenge()));
  }

  @Transactional
  public String completeConnect(String code, String stateId) {
    String token = Optional.ofNullable(code).map(String::trim).filter(value -> !value.isEmpty())
        .orElseThrow(() -> ApiException.invalidRequest("Missing authorization code"));
    String stateKey = Ids.requireResourceId(
        Optional.ofNullable(stateId).orElse(""), "Invalid OAuth state");
    IntegrationOAuthState state = integrations.findOAuthState(stateKey)
        .orElseThrow(() -> ApiException.invalidRequest("Authorization session expired"));
    integrations.deleteOAuthState(state.getId());
    if (state.getExpiresAt() != null && state.getExpiresAt().isBefore(Instant.now())) {
      throw ApiException.invalidRequest("Authorization session expired");
    }
    IntegrationInstallation row = integrations.findInstallation(state.getInstallationId())
        .orElseThrow(() -> ApiException.notFound("Integration"));
    IntegrationPreset preset = IntegrationPreset.require(row.getCatalogId());
    IntegrationOAuthService.AuthorizationServer server = oauth.discover(mcpUrl(preset, row));
    IntegrationOAuthService.RegisteredClient client = registeredClient(row, server);
    IntegrationOAuthService.TokenSet tokens = oauth.exchange(server, client, token, state.getCodeVerifier());
    saveGrant(row, state.getUserId(), tokens);
    return Optional.ofNullable(state.getRedirectAfter()).filter(value -> !value.isBlank())
        .orElseGet(oauth::workbenchRedirect);
  }

  @Transactional
  public void disconnect(String organizationId, String installationId) {
    OrganizationContext context = access.require(organizationId, OrganizationAction.AGENT_CHAT);
    requireInstallation(organizationId, installationId);
    integrations.findGrant(installationId, context.userId())
        .map(IntegrationGrant::getId)
        .ifPresent(integrations::deleteGrant);
  }

  String accessToken(IntegrationGrant grant, IntegrationInstallation installation) {
    if (grant.getTokenExpiresAt() != null && grant.getTokenExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
      return vault.decryptText(grant.getAccessCiphertext());
    }
    String refresh = Optional.ofNullable(grant.getRefreshCiphertext())
        .filter(value -> !value.isBlank())
        .map(vault::decryptText)
        .orElse("");
    if (refresh.isBlank()) {
      markExpired(grant);
      throw new ApiException("integration_reauth_required", "Reconnect this integration", 401);
    }
    IntegrationPreset preset = IntegrationPreset.require(installation.getCatalogId());
    IntegrationOAuthService.AuthorizationServer server = oauth.discover(mcpUrl(preset, installation));
    IntegrationOAuthService.RegisteredClient client = registeredClient(installation, server);
    try {
      IntegrationOAuthService.TokenSet tokens = oauth.refresh(server, client, refresh);
      saveGrant(installation, grant.getUserId(), tokens);
      return tokens.accessToken();
    } catch (ApiException error) {
      markExpired(grant);
      throw error;
    }
  }

  Optional<IntegrationInstallation> activeInstallation(String id) {
    return integrations.findInstallation(id).filter(row -> "active".equals(row.getStatus()));
  }

  Optional<IntegrationGrant> activeGrant(String installationId, String userId) {
    return integrations.findGrant(installationId, userId).filter(row -> "active".equals(row.getStatus()));
  }

  List<IntegrationGrant> activeGrants(String organizationId, String userId) {
    return integrations.listActiveGrants(organizationId, userId);
  }

  private void saveGrant(IntegrationInstallation installation, String userId, IntegrationOAuthService.TokenSet tokens) {
    Instant now = Instant.now();
    Optional<IntegrationGrant> existing = integrations.findGrant(installation.getId(), userId);
    IntegrationGrant row = existing.orElseGet(IntegrationGrant::new);
    if (row.getId() == null) {
      row.setId(UUID.randomUUID().toString());
      row.setInstallationId(installation.getId());
      row.setUserId(userId);
      row.setOrganizationId(installation.getOrganizationId());
      row.setCreatedAt(now);
    }
    row.setStatus("active");
    row.setAccountLabel(tokens.accountLabel());
    row.setAccessCiphertext(vault.encryptText(tokens.accessToken()));
    row.setRefreshCiphertext(tokens.refreshToken()
        .map(vault::encryptText)
        .orElseGet(() -> existing.map(IntegrationGrant::getRefreshCiphertext).orElse(null)));
    row.setTokenExpiresAt(tokens.expiresAt());
    row.setScopes(tokens.scope());
    row.setUpdatedAt(now);
    if (existing.isPresent()) {
      integrations.updateGrant(row);
    } else {
      integrations.insertGrant(row);
    }
  }

  private void markExpired(IntegrationGrant grant) {
    grant.setStatus("expired");
    grant.setUpdatedAt(Instant.now());
    integrations.updateGrant(grant);
  }

  private IntegrationOAuthService.RegisteredClient registeredClient(
      IntegrationInstallation installation, IntegrationOAuthService.AuthorizationServer server) {
    String clientId = Optional.ofNullable(installation.getConfig())
        .map(node -> node.path("oauthClientId").asText(""))
        .orElse("");
    if (!clientId.isBlank()) {
      Optional<String> secret = Optional.ofNullable(installation.getSecretCiphertext())
          .filter(value -> !value.isBlank())
          .map(vault::decryptText);
      return new IntegrationOAuthService.RegisteredClient(clientId, secret);
    }
    IntegrationOAuthService.RegisteredClient created = oauth.registerClient(server);
    ObjectNode config = objectConfig(installation);
    config.put("oauthClientId", created.clientId());
    installation.setConfig(config);
    created.clientSecret()
        .filter(secret -> secret.length() >= 8)
        .ifPresent(secret -> installation.setSecretCiphertext(vault.encryptText(secret)));
    installation.setUpdatedAt(Instant.now());
    integrations.updateInstallation(installation);
    return created;
  }

  private IntegrationInstallation requireInstallation(String organizationId, String installationId) {
    Ids.requireResourceId(installationId, "Invalid integration");
    IntegrationInstallation row = integrations.findInstallation(installationId)
        .orElseThrow(() -> ApiException.notFound("Integration"));
    if (!organizationId.equals(row.getOrganizationId())) {
      throw ApiException.notFound("Integration");
    }
    return row;
  }

  private IntegrationInstallation requireActiveInstallation(String organizationId, String installationId) {
    IntegrationInstallation row = requireInstallation(organizationId, installationId);
    if (!"active".equals(row.getStatus())) {
      throw ApiException.notFound("Integration");
    }
    return row;
  }

  private void applyHost(IntegrationPreset preset, IntegrationInstallation row, Optional<String> requested) {
    if (!preset.requiresHost()) {
      return;
    }
    String next = requested.map(String::trim).filter(value -> !value.isEmpty())
        .or(() -> IntegrationPreset.hostOf(row.getConfig()))
        .or(() -> Optional.ofNullable(preset.defaultHost()).filter(value -> !value.isBlank()))
        .orElse("");
    String host = IntegrationPreset.normalizeHost(next)
        .orElseThrow(() -> ApiException.invalidRequest("GitLab host is required"));
    String previous = IntegrationPreset.hostOf(row.getConfig()).orElse("");
    ObjectNode config = objectConfig(row);
    config.put("host", host);
    row.setConfig(config);
    if (!previous.isEmpty() && !previous.equals(host)) {
      config.remove("oauthClientId");
      row.setSecretCiphertext(null);
      if (row.getId() != null) {
        integrations.deleteGrantsByInstallation(row.getId());
      }
    }
  }

  private ObjectNode objectConfig(IntegrationInstallation row) {
    if (row.getConfig() instanceof ObjectNode object) {
      return object;
    }
    ObjectNode created = mapper.createObjectNode();
    row.setConfig(created);
    return created;
  }

  private static String mcpUrl(IntegrationPreset preset, IntegrationInstallation row) {
    return preset.resolveMcpUrl(row.getConfig())
        .orElseThrow(() -> ApiException.invalidRequest("This integration is missing its server address"));
  }

  private JsonNode policyFrom(IntegrationPreset preset, Optional<String> tools) {
    String mode = tools.map(String::trim).filter(value -> !value.isEmpty()).orElse("allowlist");
    if ("all".equals(mode)) {
      return ActionPolicy.all(mapper);
    }
    if ("allowlist".equals(mode)) {
      return ActionPolicy.allowlist(mapper, preset.defaultAllowedTools());
    }
    throw ApiException.invalidRequest("Invalid tool policy");
  }

  private static IntegrationViews.CatalogItem toCatalogItem(
      IntegrationPreset preset, IntegrationInstallation row) {
    return new IntegrationViews.CatalogItem(
        preset.id(),
        preset.name(),
        preset.description(),
        preset.installable(),
        true,
        preset.requiresHost(),
        IntegrationPreset.hostOf(row.getConfig()).orElse(null),
        row.getId(),
        row.getStatus());
  }
}
