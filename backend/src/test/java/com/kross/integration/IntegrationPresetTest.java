package com.kross.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class IntegrationPresetTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void gitlabResolvesHostIntoOfficialMcpUrl() {
    ObjectNode config = mapper.createObjectNode().put("host", "gitlab.example.com:8443");
    assertThat(IntegrationPreset.GITLAB.requiresHost()).isTrue();
    assertThat(IntegrationPreset.GITLAB.resolveMcpUrl(config))
        .contains("https://gitlab.example.com:8443/api/v4/mcp");
  }

  @Test
  void normalizeHostAcceptsUrlOrBareHost() {
    assertThat(IntegrationPreset.normalizeHost("https://GitLab.com/api/v4/mcp")).contains("gitlab.com");
    assertThat(IntegrationPreset.normalizeHost("gitlab.example.com")).contains("gitlab.example.com");
    assertThat(IntegrationPreset.normalizeHost("user:pass@gitlab.com")).isEmpty();
    assertThat(IntegrationPreset.normalizeHost("")).isEmpty();
  }

  @Test
  void notionIgnoresHost() {
    ObjectNode config = mapper.createObjectNode().put("host", "ignored.example");
    assertThat(IntegrationPreset.NOTION.requiresHost()).isFalse();
    assertThat(IntegrationPreset.NOTION.resolveMcpUrl(config)).contains("https://mcp.notion.com/mcp");
  }
}
