/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.personaview.events.PersonaViewCreateEvent.PERSONA_VIEW_CREATED;
import static io.harness.idp.personaview.events.PersonaViewDeleteEvent.PERSONA_VIEW_DELETED;
import static io.harness.idp.personaview.events.PersonaViewUpdateEvent.PERSONA_VIEW_UPDATED;

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
import io.harness.idp.audittrails.eventhandlers.dtos.PersonaViewDTO;
import io.harness.idp.personaview.events.PersonaViewCreateEvent;
import io.harness.idp.personaview.events.PersonaViewDeleteEvent;
import io.harness.idp.personaview.events.PersonaViewUpdateEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.spec.server.idp.v1.model.PersonaViewResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class PersonaViewEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public PersonaViewEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case PERSONA_VIEW_CREATED:
          return handlePersonaViewCreateEvent(outboxEvent);
        case PERSONA_VIEW_UPDATED:
          return handlePersonaViewUpdateEvent(outboxEvent);
        case PERSONA_VIEW_DELETED:
          return handlePersonaViewDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handlePersonaViewCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    PersonaViewCreateEvent personaViewCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), PersonaViewCreateEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.IDP)
            .newYaml(NGYamlUtils.getYamlString(
                PersonaViewDTO.builder()
                    .personaView(new PersonaViewResponse().personaView(personaViewCreateEvent.getNewPersonaView()))
                    .build(),
                objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handlePersonaViewUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    PersonaViewUpdateEvent personaViewUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), PersonaViewUpdateEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.IDP)
            .newYaml(NGYamlUtils.getYamlString(
                PersonaViewDTO.builder()
                    .personaView(new PersonaViewResponse().personaView(personaViewUpdateEvent.getNewPersonaView()))
                    .build(),
                objectMapper))
            .oldYaml(NGYamlUtils.getYamlString(
                PersonaViewDTO.builder()
                    .personaView(new PersonaViewResponse().personaView(personaViewUpdateEvent.getOldPersonaView()))
                    .build(),
                objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handlePersonaViewDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    PersonaViewDeleteEvent personaViewDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), PersonaViewDeleteEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.IDP)
            .oldYaml(NGYamlUtils.getYamlString(
                PersonaViewDTO.builder()
                    .personaView(new PersonaViewResponse().personaView(personaViewDeleteEvent.getOldPersonaView()))
                    .build(),
                objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
