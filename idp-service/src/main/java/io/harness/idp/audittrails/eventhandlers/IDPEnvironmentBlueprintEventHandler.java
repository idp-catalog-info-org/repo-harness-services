/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.catalog.events.EnvironmentBlueprintCreateEvent.IDP_ENVIRONMENT_BLUEPRINT_CREATED;
import static io.harness.idp.catalog.events.EnvironmentBlueprintDeleteEvent.IDP_ENVIRONMENT_BLUEPRINT_DELETED;
import static io.harness.idp.catalog.events.EnvironmentBlueprintUpdateEvent.IDP_ENVIRONMENT_BLUEPRINT_UPDATED;
import static io.harness.idp.catalog.events.EnvironmentCreateEvent.IDP_ENVIRONMENT_CREATED;
import static io.harness.idp.catalog.events.EnvironmentDeleteEvent.IDP_ENVIRONMENT_DELETED;
import static io.harness.idp.catalog.events.EnvironmentUpdateEvent.IDP_ENVIRONMENT_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.catalog.events.EnvironmentBlueprintCreateEvent;
import io.harness.idp.catalog.events.EnvironmentBlueprintDeleteEvent;
import io.harness.idp.catalog.events.EnvironmentBlueprintUpdateEvent;
import io.harness.idp.catalog.events.EnvironmentCreateEvent;
import io.harness.idp.catalog.events.EnvironmentDeleteEvent;
import io.harness.idp.catalog.events.EnvironmentUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IDPEnvironmentBlueprintEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public IDPEnvironmentBlueprintEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case IDP_ENVIRONMENT_BLUEPRINT_CREATED:
          return handleEnvironmentBlueprintCreateEvent(outboxEvent);
        case IDP_ENVIRONMENT_BLUEPRINT_UPDATED:
          return handleEnvironmentBlueprintUpdateEvent(outboxEvent);
        case IDP_ENVIRONMENT_BLUEPRINT_DELETED:
          return handleEnvironmentBlueprintDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleEnvironmentBlueprintCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    EnvironmentBlueprintCreateEvent environmentBlueprintCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), EnvironmentBlueprintCreateEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.IDP)
                                .newYaml(environmentBlueprintCreateEvent.getNewInlineCatalogEntityYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleEnvironmentBlueprintUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    EnvironmentBlueprintUpdateEvent environmentBlueprintUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), EnvironmentBlueprintUpdateEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.IDP)
                                .newYaml(environmentBlueprintUpdateEvent.getNewInlineCatalogEntityYaml())
                                .oldYaml(environmentBlueprintUpdateEvent.getOldInlineCatalogEntityYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleEnvironmentBlueprintDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    EnvironmentBlueprintDeleteEvent environmentBlueprintDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), EnvironmentBlueprintDeleteEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.IDP)
                                .oldYaml(environmentBlueprintDeleteEvent.getOldInlineCatalogEntityYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
