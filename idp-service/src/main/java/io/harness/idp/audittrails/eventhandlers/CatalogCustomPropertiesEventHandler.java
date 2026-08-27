/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.ccp.events.CatalogCustomPropertyCreateEvent.CATALOG_CUSTOM_PROPERTY_CREATED;
import static io.harness.idp.ccp.events.CatalogCustomPropertyDeleteEvent.CATALOG_CUSTOM_PROPERTY_DELETED;
import static io.harness.idp.ccp.events.CatalogCustomPropertyDisableEvent.CATALOG_CUSTOM_PROPERTY_DISABLED;
import static io.harness.idp.ccp.events.CatalogCustomPropertyEnableEvent.CATALOG_CUSTOM_PROPERTY_ENABLED;
import static io.harness.idp.ccp.events.CatalogCustomPropertyUpdateEvent.CATALOG_CUSTOM_PROPERTY_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.audittrails.eventhandlers.dtos.CatalogCustomPropertyDTO;
import io.harness.idp.ccp.entities.CatalogCustomPropertyEntity;
import io.harness.idp.ccp.events.CatalogCustomPropertyCreateEvent;
import io.harness.idp.ccp.events.CatalogCustomPropertyDeleteEvent;
import io.harness.idp.ccp.events.CatalogCustomPropertyDisableEvent;
import io.harness.idp.ccp.events.CatalogCustomPropertyEnableEvent;
import io.harness.idp.ccp.events.CatalogCustomPropertyUpdateEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CatalogCustomPropertiesEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public CatalogCustomPropertiesEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case CATALOG_CUSTOM_PROPERTY_CREATED:
          return handleCatalogCustomPropertyCreateEvent(outboxEvent);
        case CATALOG_CUSTOM_PROPERTY_UPDATED:
          return handleCatalogCustomPropertyUpdateEvent(outboxEvent);
        case CATALOG_CUSTOM_PROPERTY_DELETED:
          return handleCatalogCustomPropertyDeleteEvent(outboxEvent);
        case CATALOG_CUSTOM_PROPERTY_ENABLED:
          return handleCatalogCustomPropertyEnableEvent(outboxEvent);
        case CATALOG_CUSTOM_PROPERTY_DISABLED:
          return handleCatalogCustomPropertyDisableEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleCatalogCustomPropertyCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    CatalogCustomPropertyCreateEvent catalogCustomPropertyCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CatalogCustomPropertyCreateEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.IDP)
                                .newYaml((getYamlStringForCustomProperty(catalogCustomPropertyCreateEvent.getEntity())))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleCatalogCustomPropertyUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    CatalogCustomPropertyUpdateEvent catalogCustomPropertyUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CatalogCustomPropertyUpdateEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.IDP)
            .oldYaml((getYamlStringForCustomProperty(catalogCustomPropertyUpdateEvent.getOldEntity())))
            .newYaml((getYamlStringForCustomProperty(catalogCustomPropertyUpdateEvent.getNewEntity())))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleCatalogCustomPropertyDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    CatalogCustomPropertyDeleteEvent catalogCustomPropertyDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CatalogCustomPropertyDeleteEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.IDP)
                                .oldYaml((getYamlStringForCustomProperty(catalogCustomPropertyDeleteEvent.getEntity())))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleCatalogCustomPropertyEnableEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.ENABLED)
                                .module(ModuleType.IDP)
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
  private boolean handleCatalogCustomPropertyDisableEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DISABLED)
                                .module(ModuleType.IDP)
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private String getYamlStringForCustomProperty(CatalogCustomPropertyEntity entity) {
    String value = entity.getValue();
    if (value.startsWith("\"") && value.endsWith("\"")) {
      value = value.substring(1, value.length() - 1);
    }
    return NGYamlUtils.getYamlString(CatalogCustomPropertyDTO.builder()
                                         .entityRef(entity.getEntityRef())
                                         .field(entity.getField())
                                         .value(value)
                                         .build(),
        objectMapper);
  }
}
