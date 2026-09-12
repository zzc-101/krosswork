package com.kross.controller;

import com.kross.api.ApiException;
import com.kross.integration.IntegrationOAuthService;
import com.kross.integration.IntegrationService;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/integrations/oauth")
public class IntegrationOAuthController {
  private final IntegrationService integrations;
  private final IntegrationOAuthService oauth;

  @GetMapping("/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,
      @RequestParam(value = "error_description", required = false) String errorDescription) {
    String target = oauth.workbenchRedirect();
    try {
      if (error != null && !error.isBlank()) {
        String detail = Optional.ofNullable(errorDescription).filter(value -> !value.isBlank()).orElse(error);
        return redirect(target, "error", detail);
      }
      String location = integrations.completeConnect(code, state);
      return redirect(location, "integration", "connected");
    } catch (ApiException exception) {
      return redirect(target, "error", exception.getMessage());
    }
  }

  private static ResponseEntity<Void> redirect(String base, String key, String value) {
    String separator = base.contains("?") ? "&" : "?";
    URI location = URI.create(base + separator + key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
  }
}
