package com.kross.integration.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationInstallation {
  private String id;
  private String organizationId;
  private String catalogId;
  private String status;
  private JsonNode config;
  private String secretCiphertext;
  private JsonNode actionPolicy;
  private String createdBy;
  private Instant createdAt;
  private Instant updatedAt;
}
