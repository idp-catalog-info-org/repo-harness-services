/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.catalog.events.CatalogCreateEvent.IDP_CATALOG_CREATED;
import static io.harness.idp.catalog.events.CatalogDeleteEvent.IDP_CATALOG_DELETED;
import static io.harness.idp.catalog.events.CatalogUpdateEvent.IDP_CATALOG_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.catalog.events.CatalogCreateEvent;
import io.harness.idp.catalog.events.CatalogDeleteEvent;
import io.harness.idp.catalog.events.CatalogUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IDPCatalogEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public IDPCatalogEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case IDP_CATALOG_CREATED:
          return handleCatalogCreateEvent(outboxEvent);
        case IDP_CATALOG_UPDATED:
          return handleCatalogUpdateEvent(outboxEvent);
        case IDP_CATALOG_DELETED:
          return handleCatalogDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleCatalogCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    CatalogCreateEvent catalogCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CatalogCreateEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.IDP)
                                .newYaml(catalogCreateEvent.getNewInlineCatalogEntityYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleCatalogUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    CatalogUpdateEvent catalogUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CatalogUpdateEvent.class);

    String oldYaml = catalogUpdateEvent.getOldInlineCatalogEntityYaml();
    String newYaml = catalogUpdateEvent.getNewInlineCatalogEntityYaml();
    if (oldYaml.equals(newYaml)) {
      return true;
    }

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

  private boolean handleCatalogDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    CatalogDeleteEvent catalogDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CatalogDeleteEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.IDP)
                                .oldYaml(catalogDeleteEvent.getOldInlineCatalogEntityYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
