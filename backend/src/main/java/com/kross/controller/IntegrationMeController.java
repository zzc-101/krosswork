package com.kross.controller;

import com.kross.api.ApiHeaders;
import com.kross.api.ItemList;
import com.kross.api.Res;
import com.kross.integration.IntegrationService;
import com.kross.integration.dto.IntegrationViews;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/me/integrations")
public class IntegrationMeController {
  private final IntegrationService integrations;

  @GetMapping
  public Res<ItemList<IntegrationViews.MemberItem>> list(
      @RequestHeader(ApiHeaders.ORGANIZATION_ID) String organizationId) {
    return Res.ok(new ItemList<>(integrations.listMine(organizationId)));
  }

  @PostMapping("/{installationId}/connect")
  public Res<IntegrationViews.ConnectResponse> connect(
      @RequestHeader(ApiHeaders.ORGANIZATION_ID) String organizationId,
      @PathVariable String installationId) {
    return Res.ok(integrations.startConnect(organizationId, installationId));
  }

  @DeleteMapping("/{installationId}")
  public Res<Void> disconnect(
      @RequestHeader(ApiHeaders.ORGANIZATION_ID) String organizationId,
      @PathVariable String installationId) {
    integrations.disconnect(organizationId, installationId);
    return Res.ok();
  }
}
