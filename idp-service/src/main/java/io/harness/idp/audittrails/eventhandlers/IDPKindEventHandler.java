/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.catalog.events.KindCreateEvent.IDP_KIND_CREATED;
import static io.harness.idp.catalog.events.KindDeleteEvent.IDP_KIND_DELETED;
import static io.harness.idp.catalog.events.KindUpdateEvent.IDP_KIND_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.audittrails.eventhandlers.dtos.KindDTO;
import io.harness.idp.catalog.events.KindCreateEvent;
import io.harness.idp.catalog.events.KindDeleteEvent;
import io.harness.idp.catalog.events.KindUpdateEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IDPKindEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public IDPKindEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case IDP_KIND_CREATED:
          return handleKindCreateEvent(outboxEvent);
        case IDP_KIND_UPDATED:
          return handleKindUpdateEvent(outboxEvent);
        case IDP_KIND_DELETED:
          return handleKindDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleKindCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    KindCreateEvent kindCreateEvent = objectMapper.readValue(outboxEvent.getEventData(), KindCreateEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.IDP)
            .newYaml(NGYamlUtils.getYamlString(KindDTO.builder()
                                                   .name(kindCreateEvent.getKindEntity().getName())
                                                   .description(kindCreateEvent.getKindEntity().getDescription())
                                                   .icon(kindCreateEvent.getKindEntity().getIcon())
                                                   .schema(kindCreateEvent.getKindEntity().getSchema())
                                                   .groupingKind(kindCreateEvent.getKindEntity().isGroupingKind())
                                                   .build(),
                objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleKindUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    KindUpdateEvent kindUpdateEvent = objectMapper.readValue(outboxEvent.getEventData(), KindUpdateEvent.class);

    String oldYaml = NGYamlUtils.getYamlString(KindDTO.builder()
                                                   .name(kindUpdateEvent.getOldKindEntity().getName())
                                                   .description(kindUpdateEvent.getOldKindEntity().getDescription())
                                                   .icon(kindUpdateEvent.getOldKindEntity().getIcon())
                                                   .schema(kindUpdateEvent.getOldKindEntity().getSchema())
                                                   .build(),
        objectMapper);
    String newYaml = NGYamlUtils.getYamlString(KindDTO.builder()
                                                   .name(kindUpdateEvent.getNewKindEntity().getName())
                                                   .description(kindUpdateEvent.getNewKindEntity().getDescription())
                                                   .icon(kindUpdateEvent.getNewKindEntity().getIcon())
                                                   .schema(kindUpdateEvent.getNewKindEntity().getSchema())
                                                   .build(),
        objectMapper);
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

  private boolean handleKindDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    KindDeleteEvent kindDeleteEvent = objectMapper.readValue(outboxEvent.getEventData(), KindDeleteEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.IDP)
            .oldYaml(NGYamlUtils.getYamlString(KindDTO.builder()
                                                   .name(kindDeleteEvent.getKindEntity().getName())
                                                   .description(kindDeleteEvent.getKindEntity().getDescription())
                                                   .icon(kindDeleteEvent.getKindEntity().getIcon())
                                                   .schema(kindDeleteEvent.getKindEntity().getSchema())
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
