package com.kross.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.kross.api.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/mcp/integrations/{installationId}")
public class IntegrationMcpController {
  private final IntegrationMcpService mcp;

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<JsonNode> post(
      @PathVariable String installationId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestBody(required = false) JsonNode body) {
    try {
      return mcp.dispatch(installationId, authorization, body);
    } catch (ApiException error) {
      if (error.getStatus() == 401) {
        return IntegrationMcpService.unauthorized();
      }
      throw error;
    }
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(
      @PathVariable String installationId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    try {
      return mcp.close(installationId, authorization);
    } catch (ApiException error) {
      if (error.getStatus() == 401) {
        return ResponseEntity.status(401).build();
      }
      throw error;
    }
  }
}
