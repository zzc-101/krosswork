package com.kross.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kross.agent.AgentMapper;
import com.kross.agent.AgentTokenDirectory;
import com.kross.agent.entity.Agent;
import com.kross.config.AppProperties;
import com.kross.integration.entity.IntegrationGrant;
import com.kross.integration.entity.IntegrationInstallation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IntegrationMcpServiceTest {
  @Test
  void managedServersOmitRiskAndPointAtProxy() {
    IntegrationService service = mock(IntegrationService.class);
    IntegrationGrant grant = new IntegrationGrant();
    grant.setInstallationId("inst-1");
    IntegrationInstallation installation = new IntegrationInstallation();
    installation.setId("inst-1");
    installation.setCatalogId("notion");
    installation.setStatus("active");
    when(service.activeGrants("org-1", "user-1")).thenReturn(List.of(grant));
    when(service.activeInstallation("inst-1")).thenReturn(Optional.of(installation));
    AppProperties properties = new AppProperties();
    properties.setPublicBaseUrl("https://kross.example");
    IntegrationMcpService mcp = new IntegrationMcpService(
        service,
        mock(AgentTokenDirectory.class),
        mock(AgentMapper.class),
        properties,
        new ObjectMapper());
    Agent agent = new Agent();
    agent.setOrganizationId("org-1");
    agent.setUserId("user-1");

    Map<String, Object> servers = mcp.managedServers(agent);

    assertThat(servers).containsOnlyKeys("integrations_notion");
    @SuppressWarnings("unchecked")
    Map<String, Object> notion = (Map<String, Object>) servers.get("integrations_notion");
    assertThat(notion).doesNotContainKey("risk");
    assertThat(notion).containsEntry("transport", "streamable-http");
    assertThat(notion).containsEntry("url", "https://kross.example/mcp/integrations/inst-1");
  }

  @Test
  void managedServersSkipMissingInstallation() {
    IntegrationService service = mock(IntegrationService.class);
    IntegrationGrant grant = new IntegrationGrant();
    grant.setInstallationId("inst-1");
    when(service.activeGrants("org-1", "user-1")).thenReturn(List.of(grant));
    when(service.activeInstallation("inst-1")).thenReturn(Optional.empty());
    IntegrationMcpService mcp = new IntegrationMcpService(
        service,
        mock(AgentTokenDirectory.class),
        mock(AgentMapper.class),
        new AppProperties(),
        new ObjectMapper());
    Agent agent = new Agent();
    agent.setOrganizationId("org-1");
    agent.setUserId("user-1");

    assertThat(mcp.managedServers(agent)).isEmpty();
  }

  @Test
  void managedServersEmptyWithoutGrant() {
    IntegrationService service = mock(IntegrationService.class);
    when(service.activeGrants("org-1", "user-1")).thenReturn(List.of());
    IntegrationMcpService mcp = new IntegrationMcpService(
        service,
        mock(AgentTokenDirectory.class),
        mock(AgentMapper.class),
        new AppProperties(),
        new ObjectMapper());
    Agent agent = new Agent();
    agent.setOrganizationId("org-1");
    agent.setUserId("user-1");

    assertThat(mcp.managedServers(agent)).isEmpty();
  }
}
