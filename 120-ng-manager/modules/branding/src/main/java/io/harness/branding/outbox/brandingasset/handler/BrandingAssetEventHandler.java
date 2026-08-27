/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.outbox.brandingasset.handler;

import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.branding.dtos.BrandingAssetYamlDTO;
import io.harness.branding.mapper.BrandingMapper;
import io.harness.branding.outbox.brandingasset.events.BrandingAssetDeleteEvent;
import io.harness.branding.outbox.brandingasset.events.BrandingAssetEvent;
import io.harness.branding.outbox.brandingasset.events.BrandingAssetUploadEvent;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.spec.server.ng.v1.model.BrandingAssetsDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class BrandingAssetEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;
  private final BrandingMapper brandingMapper;

  @Inject
  public BrandingAssetEventHandler(AuditClientService auditClientService, BrandingMapper brandingMapper) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
    this.brandingMapper = brandingMapper;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case BrandingAssetEvent.BRANDING_ASSET_UPLOADED:
          return handleBrandingAssetUpdateEvent(outboxEvent);
        case BrandingAssetEvent.BRANDING_ASSET_DELETED:
          return handleBrandingAssetDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error(
          String.format("Failed to audit : [%s] event with id [%s]", outboxEvent.getEventType(), outboxEvent.getId()),
          exception);
      return false;
    }
  }

  private boolean handleBrandingAssetUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    BrandingAssetUploadEvent brandingAssetUploadEvent =
        objectMapper.readValue(outboxEvent.getEventData(), BrandingAssetUploadEvent.class);
    BrandingAssetsDTO brandingAssetDTO =
        brandingMapper.toBrandingAssetsDTO(brandingAssetUploadEvent.getBrandingAsset());
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(BrandingAssetYamlDTO.builder().brandingAssetsDTO(brandingAssetDTO).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleBrandingAssetDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    BrandingAssetDeleteEvent brandingAssetDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), BrandingAssetDeleteEvent.class);
    BrandingAssetsDTO brandingAssetDTO =
        brandingMapper.toBrandingAssetsDTO(brandingAssetDeleteEvent.getBrandingAsset());
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CORE)
            .oldYaml(getYamlString(BrandingAssetYamlDTO.builder().brandingAssetsDTO(brandingAssetDTO).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
