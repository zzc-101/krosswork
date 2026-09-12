package com.kross.integration.entity;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationGrant {
  private String id;
  private String installationId;
  private String userId;
  private String organizationId;
  private String status;
  private String accountLabel;
  private String accessCiphertext;
  private String refreshCiphertext;
  private Instant tokenExpiresAt;
  private String scopes;
  private Instant createdAt;
  private Instant updatedAt;
}
