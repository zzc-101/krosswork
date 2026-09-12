package com.kross.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ActionPolicy {
  private ActionPolicy() {}

  static ObjectNode allowlist(ObjectMapper mapper, List<String> tools) {
    ObjectNode policy = mapper.createObjectNode();
    policy.put("tools", "allowlist");
    policy.put("onNewTool", "disable");
    ArrayNode allowed = policy.putArray("allowed");
    Optional.ofNullable(tools).orElse(List.of()).forEach(allowed::add);
    return policy;
  }

  static ObjectNode all(ObjectMapper mapper) {
    ObjectNode policy = mapper.createObjectNode();
    policy.put("tools", "all");
    policy.put("onNewTool", "enable");
    policy.set("allowed", mapper.createArrayNode());
    return policy;
  }

  static boolean allowsAll(JsonNode policy) {
    return policy != null && "all".equals(policy.path("tools").asText(""));
  }

  static List<String> allowed(JsonNode policy) {
    List<String> names = new ArrayList<>();
    if (policy == null || !policy.path("allowed").isArray()) {
      return names;
    }
    policy.path("allowed").forEach(node -> {
      String name = node.asText("").trim();
      if (!name.isEmpty()) {
        names.add(name);
      }
    });
    return names;
  }

  static boolean allows(JsonNode policy, String toolName) {
    if (allowsAll(policy)) {
      return true;
    }
    return allowed(policy).contains(toolName);
  }

  static ArrayNode filter(ObjectMapper mapper, JsonNode tools, JsonNode policy) {
    ArrayNode kept = mapper.createArrayNode();
    if (tools == null || !tools.isArray()) {
      return kept;
    }
    tools.forEach(tool -> {
      if (allows(policy, tool.path("name").asText(""))) {
        kept.add(tool);
      }
    });
    return kept;
  }
}
