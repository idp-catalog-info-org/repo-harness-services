/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.outbox.branding.handler;

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
import io.harness.branding.dtos.BrandingYamlDTO;
import io.harness.branding.mapper.BrandingMapper;
import io.harness.branding.outbox.branding.events.BrandingCreateEvent;
import io.harness.branding.outbox.branding.events.BrandingEvent;
import io.harness.branding.outbox.branding.events.BrandingUpdateEvent;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.spec.server.ng.v1.model.BrandingSettingsDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class BrandingEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;
  private final BrandingMapper brandingMapper;

  @Inject
  public BrandingEventHandler(AuditClientService auditClientService, BrandingMapper brandingMapper) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
    this.brandingMapper = brandingMapper;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case BrandingEvent.BRANDING_SETTINGS_CREATED:
          return handleBrandingCreateEvent(outboxEvent);
        case BrandingEvent.BRANDING_SETTINGS_UPDATED:
          return handleBrandingUpdateEvent(outboxEvent);
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

  private boolean handleBrandingCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    BrandingCreateEvent brandingCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), BrandingCreateEvent.class);
    BrandingSettingsDTO brandingSettingsDTO = brandingMapper.toBrandingSettingsDTO(brandingCreateEvent.getBranding());
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(BrandingYamlDTO.builder().brandingSettingsDTO(brandingSettingsDTO).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleBrandingUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    BrandingUpdateEvent brandingUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), BrandingUpdateEvent.class);
    BrandingSettingsDTO brandingSettingsDTO = brandingMapper.toBrandingSettingsDTO(brandingUpdateEvent.getBranding());
    BrandingSettingsDTO oldBrandingSettingsDTO =
        brandingMapper.toBrandingSettingsDTO(brandingUpdateEvent.getOldBranding());
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(BrandingYamlDTO.builder().brandingSettingsDTO(brandingSettingsDTO).build()))
            .oldYaml(getYamlString(BrandingYamlDTO.builder().brandingSettingsDTO(oldBrandingSettingsDTO).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
