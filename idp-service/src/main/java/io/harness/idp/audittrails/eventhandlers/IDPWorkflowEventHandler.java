/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers;
import static io.harness.idp.catalog.events.WorkflowCreateEvent.IDP_WORKFLOW_CREATED;
import static io.harness.idp.catalog.events.WorkflowDeleteEvent.IDP_WORKFLOW_DELETED;
import static io.harness.idp.catalog.events.WorkflowUpdateEvent.IDP_WORKFLOW_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.catalog.events.WorkflowCreateEvent;
import io.harness.idp.catalog.events.WorkflowDeleteEvent;
import io.harness.idp.catalog.events.WorkflowUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IDPWorkflowEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public IDPWorkflowEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case IDP_WORKFLOW_CREATED:
          return handleWorkflowCreateEvent(outboxEvent);
        case IDP_WORKFLOW_UPDATED:
          return handleWorkflowUpdateEvent(outboxEvent);
        case IDP_WORKFLOW_DELETED:
          return handleWorkflowDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleWorkflowCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    WorkflowCreateEvent workflowCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), WorkflowCreateEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.IDP)
                                .newYaml(workflowCreateEvent.getNewInlineCatalogEntityYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleWorkflowUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    WorkflowUpdateEvent workflowUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), WorkflowUpdateEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.IDP)
                                .newYaml(workflowUpdateEvent.getNewInlineCatalogEntityYaml())
                                .oldYaml(workflowUpdateEvent.getOldInlineCatalogEntityYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleWorkflowDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    WorkflowDeleteEvent workflowDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), WorkflowDeleteEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.IDP)
                                .oldYaml(workflowDeleteEvent.getOldInlineCatalogEntityYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
