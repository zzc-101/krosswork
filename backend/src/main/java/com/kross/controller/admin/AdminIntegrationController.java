package com.kross.controller.admin;

import com.kross.api.ApiHeaders;
import com.kross.api.ItemList;
import com.kross.api.Res;
import com.kross.integration.IntegrationService;
import com.kross.integration.dto.IntegrationViews;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/integrations")
public class AdminIntegrationController {
  private final IntegrationService integrations;

  @GetMapping
  public Res<ItemList<IntegrationViews.CatalogItem>> list(
      @RequestHeader(ApiHeaders.ORGANIZATION_ID) String organizationId) {
    return Res.ok(new ItemList<>(integrations.listCatalog(organizationId)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Res<IntegrationViews.CatalogItem> install(
      @RequestHeader(ApiHeaders.ORGANIZATION_ID) String organizationId,
      @RequestBody IntegrationViews.InstallRequest request) {
    return Res.ok(integrations.install(organizationId, request));
  }

  @PatchMapping("/{installationId}")
  public Res<IntegrationViews.CatalogItem> patch(
      @RequestHeader(ApiHeaders.ORGANIZATION_ID) String organizationId,
      @PathVariable String installationId,
      @RequestBody IntegrationViews.PatchRequest request) {
    return Res.ok(integrations.patch(organizationId, installationId, request));
  }

  @DeleteMapping("/{installationId}")
  public Res<Void> uninstall(
      @RequestHeader(ApiHeaders.ORGANIZATION_ID) String organizationId,
      @PathVariable String installationId) {
    integrations.uninstall(organizationId, installationId);
    return Res.ok();
  }
}
