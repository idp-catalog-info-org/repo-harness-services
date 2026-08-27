/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.VARIABLE_ENTITY;
import static io.harness.ng.core.events.VariableCreateEvent.VARIABLE_CREATED;
import static io.harness.ng.core.events.VariableDeleteEvent.VARIABLE_DELETED;
import static io.harness.ng.core.events.VariableUpdateEvent.VARIABLE_UPDATED;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.events.VariableCreateEvent;
import io.harness.ng.core.events.VariableDeleteEvent;
import io.harness.ng.core.events.VariableUpdateEvent;
import io.harness.ng.core.variable.dto.VariableRequestDTO;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class VariableEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;
  private final Producer eventProducer;
  @Inject
  public VariableEventHandler(
      AuditClientService auditClientService, @Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
    this.eventProducer = eventProducer;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case VARIABLE_CREATED:
          return handleVariableCreateEvent(outboxEvent);
        case VARIABLE_UPDATED:
          return handleVariableUpdateEvent(outboxEvent);
        case VARIABLE_DELETED:
          return handleVariableDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      return false;
    }
  }

  private boolean handleVariableCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    VariableCreateEvent variableCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), VariableCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(VariableRequestDTO.builder().variable(variableCreateEvent.getVariableDTO()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleVariableUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    VariableUpdateEvent variableUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), VariableUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .oldYaml(
                getYamlString(VariableRequestDTO.builder().variable(variableUpdateEvent.getOldVariableDTO()).build()))
            .newYaml(
                getYamlString(VariableRequestDTO.builder().variable(variableUpdateEvent.getNewVariableDTO()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
  private boolean handleVariableDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    VariableDeleteEvent variableDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), VariableDeleteEvent.class);
    boolean publishedToRedis = publishEvent(DELETE_ACTION, outboxEvent);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CORE)
            .oldYaml(getYamlString(VariableRequestDTO.builder().variable(variableDeleteEvent.getVariableDTO()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  boolean publishEvent(String action, OutboxEvent outboxEvent) {
    EntityChangeDTO entityChangeDTO = buildEntityForRedisMessage(outboxEvent);
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", entityChangeDTO.getAccountIdentifier().getValue(),
                  ENTITY_TYPE, VARIABLE_ENTITY, ACTION, action))
              .setData(entityChangeDTO.toByteString())
              .build());
      return true;
    } catch (EventsFrameworkDownException e) {
      String variableIdentifier = entityChangeDTO.hasIdentifier() ? entityChangeDTO.getIdentifier().getValue() : "";
      String accountIdentifier =
          entityChangeDTO.hasAccountIdentifier() ? entityChangeDTO.getAccountIdentifier().getValue() : "";
      String orgIdentifier = entityChangeDTO.hasOrgIdentifier() ? entityChangeDTO.getOrgIdentifier().getValue() : "";
      String projectIdentifier =
          entityChangeDTO.hasProjectIdentifier() ? entityChangeDTO.getProjectIdentifier().getValue() : "";
      log.error(
          "Failed to send event to events framework variableIdentifier [{}] and scope (account: [{}], org: [{}], project: [{}])",
          variableIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, e);
      return false;
    }
  }

  public EntityChangeDTO buildEntityForRedisMessage(OutboxEvent outboxEvent) {
    EntityChangeDTO.Builder entityBuilder =
        EntityChangeDTO.newBuilder().setIdentifier(StringValue.of(outboxEvent.getResource().getIdentifier()));

    ResourceScope scope = outboxEvent.getResourceScope();

    if (scope instanceof AccountScope) {
      entityBuilder.setAccountIdentifier(StringValue.of(((AccountScope) scope).getAccountIdentifier()));
    } else if (scope instanceof OrgScope) {
      OrgScope orgScope = (OrgScope) scope;
      entityBuilder.setAccountIdentifier(StringValue.of(orgScope.getAccountIdentifier()));
      entityBuilder.setOrgIdentifier(StringValue.of(orgScope.getOrgIdentifier()));
    } else if (scope instanceof ProjectScope) {
      ProjectScope projectScope = (ProjectScope) scope;
      entityBuilder.setAccountIdentifier(StringValue.of(projectScope.getAccountIdentifier()));
      entityBuilder.setOrgIdentifier(StringValue.of(projectScope.getOrgIdentifier()));
      entityBuilder.setProjectIdentifier(StringValue.of(projectScope.getProjectIdentifier()));
    }

    return entityBuilder.build();
  }
}
