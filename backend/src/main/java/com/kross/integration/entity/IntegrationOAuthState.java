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
public class IntegrationOAuthState {
  private String id;
  private String installationId;
  private String userId;
  private String organizationId;
  private String codeVerifier;
  private String redirectAfter;
  private Instant expiresAt;
  private Instant createdAt;
}
