package com.kross.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record IntegrationPreset(
    String id,
    String name,
    String description,
    String mcpUrl,
    List<String> defaultAllowedTools,
    boolean installable,
    String defaultHost) {

  public static final IntegrationPreset NOTION = new IntegrationPreset(
      "notion",
      "Notion",
      "用成员自己的 Notion 账号授权，检索和编辑其有权访问的页面。",
      "https://mcp.notion.com/mcp",
      List.of("search", "fetch", "create-pages", "update-page"),
      true,
      "");

  public static final IntegrationPreset GITLAB = new IntegrationPreset(
      "gitlab",
      "GitLab",
      "连接 GitLab.com 或自建实例的官方 MCP。查 Merge Request、Issue、流水线和发布。",
      "https://{host}/api/v4/mcp",
      List.of(
          "search",
          "get_issue",
          "get_merge_request",
          "list_merge_requests",
          "get_merge_request_commits",
          "get_merge_request_diffs",
          "get_merge_request_pipelines",
          "get_merge_request_notes",
          "list_pipelines",
          "get_pipeline",
          "get_pipeline_jobs",
          "get_job",
          "list_releases",
          "get_commit",
          "get_repository_file",
          "list_work_items",
          "get_work_item"),
      true,
      "gitlab.com");

  public static final IntegrationPreset GMAIL = new IntegrationPreset(
      "gmail",
      "Gmail",
      "连接 Google 官方 Gmail MCP。",
      "https://gmailmcp.googleapis.com/mcp/v1",
      List.of(),
      false,
      "");

  private static final List<IntegrationPreset> ALL = List.of(NOTION, GITLAB, GMAIL);

  public static List<IntegrationPreset> all() {
    return ALL;
  }

  public static Optional<IntegrationPreset> find(String id) {
    return ALL.stream().filter(item -> item.id().equals(id)).findFirst();
  }

  public static IntegrationPreset require(String id) {
    return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown integration: " + id));
  }

  public boolean requiresHost() {
    return mcpUrl.contains("{host}");
  }

  public Optional<String> resolveMcpUrl(JsonNode config) {
    if (!requiresHost()) {
      return Optional.ofNullable(mcpUrl).filter(value -> !value.isBlank());
    }
    return normalizeHost(hostOf(config)).map(host -> mcpUrl.replace("{host}", host));
  }

  static Optional<String> hostOf(JsonNode config) {
    return Optional.ofNullable(config)
        .map(node -> node.path("host").asText(""))
        .map(String::trim)
        .filter(value -> !value.isEmpty());
  }

  static Optional<String> normalizeHost(String raw) {
    String value = Optional.ofNullable(raw).map(String::trim).orElse("");
    if (value.isEmpty()) {
      return Optional.empty();
    }
    if (!value.contains("://")) {
      value = "https://" + value;
    }
    try {
      URI uri = URI.create(value);
      String host = Optional.ofNullable(uri.getHost()).map(String::trim).orElse("");
      if (host.isEmpty() || Optional.ofNullable(uri.getUserInfo()).filter(item -> !item.isBlank()).isPresent()) {
        return Optional.empty();
      }
      int port = uri.getPort();
      return Optional.of(port > 0 ? host.toLowerCase(Locale.ROOT) + ":" + port : host.toLowerCase(Locale.ROOT));
    } catch (IllegalArgumentException error) {
      return Optional.empty();
    }
  }
}
