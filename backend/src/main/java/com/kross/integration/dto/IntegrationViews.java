package com.kross.integration.dto;

public final class IntegrationViews {
  private IntegrationViews() {}

  public record CatalogItem(
      String catalogId,
      String name,
      String description,
      boolean installable,
      boolean installed,
      boolean hostRequired,
      String host,
      String installationId,
      String status) {}

  public record MemberItem(
      String installationId,
      String catalogId,
      String name,
      boolean enabled,
      boolean connected,
      String host,
      String accountLabel,
      String grantStatus) {}

  public record ConnectResponse(String authorizationUrl) {}

  public record InstallRequest(String catalogId, String tools, String host) {}

  public record PatchRequest(String status, String tools, String host) {}
}
