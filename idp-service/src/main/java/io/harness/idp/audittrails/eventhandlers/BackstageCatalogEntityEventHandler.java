/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.backstage.events.BackstageCatalogEntityCreateEvent.BACKSTAGE_CATALOG_ENTITY_CREATED;
import static io.harness.idp.backstage.events.BackstageCatalogEntityDeleteEvent.BACKSTAGE_CATALOG_ENTITY_DELETED;
import static io.harness.idp.backstage.events.BackstageCatalogEntityUpdateEvent.BACKSTAGE_CATALOG_ENTITY_UPDATED;

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
import io.harness.idp.audittrails.eventhandlers.dtos.BackstageCatalogEntityDTO;
import io.harness.idp.backstage.events.BackstageCatalogEntityCreateEvent;
import io.harness.idp.backstage.events.BackstageCatalogEntityDeleteEvent;
import io.harness.idp.backstage.events.BackstageCatalogEntityUpdateEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class BackstageCatalogEntityEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public BackstageCatalogEntityEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case BACKSTAGE_CATALOG_ENTITY_CREATED:
          return handleBackstageCatalogEntityCreateEvent(outboxEvent);
        case BACKSTAGE_CATALOG_ENTITY_UPDATED:
          return handleBackstageCatalogEntityUpdateEvent(outboxEvent);
        case BACKSTAGE_CATALOG_ENTITY_DELETED:
          return handleBackstageCatalogEntityDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleBackstageCatalogEntityCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    BackstageCatalogEntityCreateEvent backstageCatalogEntityCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), BackstageCatalogEntityCreateEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.IDP)
            .newYaml(NGYamlUtils.getYamlString(
                BackstageCatalogEntityDTO.builder().yaml(backstageCatalogEntityCreateEvent.getNewYaml()).build(),
                objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleBackstageCatalogEntityUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    BackstageCatalogEntityUpdateEvent backstageCatalogEntityUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), BackstageCatalogEntityUpdateEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.IDP)
            .newYaml(NGYamlUtils.getYamlString(
                BackstageCatalogEntityDTO.builder().yaml(backstageCatalogEntityUpdateEvent.getNewYaml()).build(),
                objectMapper))
            .oldYaml(NGYamlUtils.getYamlString(
                BackstageCatalogEntityDTO.builder().yaml(backstageCatalogEntityUpdateEvent.getOldYaml()).build(),
                objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleBackstageCatalogEntityDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    BackstageCatalogEntityDeleteEvent backstageCatalogEntityDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), BackstageCatalogEntityDeleteEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.IDP)
            .oldYaml(NGYamlUtils.getYamlString(
                BackstageCatalogEntityDTO.builder().yaml(backstageCatalogEntityDeleteEvent.getOldYaml()).build(),
                objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
