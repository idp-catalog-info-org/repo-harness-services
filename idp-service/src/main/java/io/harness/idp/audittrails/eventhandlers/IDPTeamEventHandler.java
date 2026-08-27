/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.catalog.events.TeamCreateEvent.IDP_TEAM_CREATED;
import static io.harness.idp.catalog.events.TeamDeleteEvent.IDP_TEAM_DELETED;
import static io.harness.idp.catalog.events.TeamUpdateEvent.IDP_TEAM_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.catalog.events.TeamCreateEvent;
import io.harness.idp.catalog.events.TeamDeleteEvent;
import io.harness.idp.catalog.events.TeamUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IDPTeamEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public IDPTeamEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case IDP_TEAM_CREATED:
          return handleTeamCreateEvent(outboxEvent);
        case IDP_TEAM_UPDATED:
          return handleTeamUpdateEvent(outboxEvent);
        case IDP_TEAM_DELETED:
          return handleTeamDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleTeamCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    TeamCreateEvent teamCreateEvent = objectMapper.readValue(outboxEvent.getEventData(), TeamCreateEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.IDP)
                                .newYaml(teamCreateEvent.getNewInlineCatalogEntityYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleTeamUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    TeamUpdateEvent teamUpdateEvent = objectMapper.readValue(outboxEvent.getEventData(), TeamUpdateEvent.class);

    String oldYaml = teamUpdateEvent.getOldInlineCatalogEntityYaml();
    String newYaml = teamUpdateEvent.getNewInlineCatalogEntityYaml();
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

  private boolean handleTeamDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    TeamDeleteEvent teamDeleteEvent = objectMapper.readValue(outboxEvent.getEventData(), TeamDeleteEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.IDP)
                                .oldYaml(teamDeleteEvent.getOldInlineCatalogEntityYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
