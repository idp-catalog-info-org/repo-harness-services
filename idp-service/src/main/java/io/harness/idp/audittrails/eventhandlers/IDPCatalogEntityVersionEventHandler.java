/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.catalog.events.CatalogEntityVersionCreateEvent.IDP_CATALOG_ENTITY_VERSION_CREATED;
import static io.harness.idp.catalog.events.CatalogEntityVersionDeleteEvent.IDP_CATALOG_ENTITY_VERSION_DELETED;
import static io.harness.idp.catalog.events.CatalogEntityVersionUpdateEvent.IDP_CATALOG_ENTITY_VERSION_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.audittrails.eventhandlers.dtos.BackstageEnvSecretDTO;
import io.harness.idp.audittrails.eventhandlers.dtos.CatalogueEntityVersionDTO;
import io.harness.idp.catalog.events.CatalogEntityVersionCreateEvent;
import io.harness.idp.catalog.events.CatalogEntityVersionDeleteEvent;
import io.harness.idp.catalog.events.CatalogEntityVersionUpdateEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IDPCatalogEntityVersionEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public IDPCatalogEntityVersionEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case IDP_CATALOG_ENTITY_VERSION_CREATED:
          return handleCatalogEntityVersionCreateEvent(outboxEvent);
        case IDP_CATALOG_ENTITY_VERSION_UPDATED:
          return handleCatalogEntityVersionUpdateEvent(outboxEvent);
        case IDP_CATALOG_ENTITY_VERSION_DELETED:
          return handleCatalogEntityVersionDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleCatalogEntityVersionCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    CatalogEntityVersionCreateEvent catalogEntityVersionCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CatalogEntityVersionCreateEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.IDP)
            .newYaml(
                getYamlStringForEntityVersion(catalogEntityVersionCreateEvent.getNewInlineCatalogEntityVersionYaml(),
                    catalogEntityVersionCreateEvent.getVersion(), catalogEntityVersionCreateEvent.getStable(),
                    catalogEntityVersionCreateEvent.getDeprecated()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleCatalogEntityVersionUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    CatalogEntityVersionUpdateEvent catalogEntityVersionUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CatalogEntityVersionUpdateEvent.class);

    String oldYaml =
        getYamlStringForEntityVersion(catalogEntityVersionUpdateEvent.getOldInlineCatalogEntityVersionYaml(),
            catalogEntityVersionUpdateEvent.getVersion(), catalogEntityVersionUpdateEvent.getOldStable(),
            catalogEntityVersionUpdateEvent.getOldDeprecated());
    String newYaml =
        getYamlStringForEntityVersion(catalogEntityVersionUpdateEvent.getNewInlineCatalogEntityVersionYaml(),
            catalogEntityVersionUpdateEvent.getVersion(), catalogEntityVersionUpdateEvent.getNewStable(),
            catalogEntityVersionUpdateEvent.getNewDeprecated());

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.IDP)
                                .newYaml(newYaml)
                                .oldYaml(oldYaml)
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleCatalogEntityVersionDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    CatalogEntityVersionDeleteEvent catalogEntityVersionDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CatalogEntityVersionDeleteEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.IDP)
            .oldYaml(
                getYamlStringForEntityVersion(catalogEntityVersionDeleteEvent.getOldInlineCatalogEntityVersionYaml(),
                    catalogEntityVersionDeleteEvent.getVersion(), catalogEntityVersionDeleteEvent.getStable(),
                    catalogEntityVersionDeleteEvent.getDeprecated()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
  private String getYamlStringForEntityVersion(
      String catalogueEntityVersionYaml, String version, Boolean stable, Boolean deprecated) {
    return NGYamlUtils.getYamlString(CatalogueEntityVersionDTO.builder()
                                         .yaml(catalogueEntityVersionYaml)
                                         .version(version)
                                         .stable(stable)
                                         .deprecated(deprecated)
                                         .build(),
        objectMapper);
  }
}
