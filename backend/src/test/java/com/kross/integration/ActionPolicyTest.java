package com.kross.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActionPolicyTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void allowlistAcceptsOnlyNamedTools() {
    var policy = ActionPolicy.allowlist(mapper, List.of("search", "fetch"));
    assertThat(ActionPolicy.allows(policy, "search")).isTrue();
    assertThat(ActionPolicy.allows(policy, "create-pages")).isFalse();
    assertThat(ActionPolicy.allowsAll(policy)).isFalse();
  }

  @Test
  void allAllowsAnyTool() {
    var policy = ActionPolicy.all(mapper);
    assertThat(ActionPolicy.allowsAll(policy)).isTrue();
    assertThat(ActionPolicy.allows(policy, "anything")).isTrue();
  }

  @Test
  void filterDropsToolsOutsideAllowlist() {
    var policy = ActionPolicy.allowlist(mapper, List.of("search", "fetch"));
    var tools = mapper.createArrayNode()
        .add(mapper.createObjectNode().put("name", "search"))
        .add(mapper.createObjectNode().put("name", "create-pages"))
        .add(mapper.createObjectNode().put("name", "fetch"));

    var kept = ActionPolicy.filter(mapper, tools, policy);

    assertThat(kept).hasSize(2);
    assertThat(kept.get(0).path("name").asText()).isEqualTo("search");
    assertThat(kept.get(1).path("name").asText()).isEqualTo("fetch");
  }
}
