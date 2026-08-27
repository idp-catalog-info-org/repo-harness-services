/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.proxy.layout.events.LayoutCreateEvent.LAYOUT_CREATED;
import static io.harness.idp.proxy.layout.events.LayoutDeleteEvent.LAYOUT_DELETED;
import static io.harness.idp.proxy.layout.events.LayoutUpdateEvent.LAYOUT_UPDATED;

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
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.proxy.layout.events.LayoutCreateEvent;
import io.harness.idp.proxy.layout.events.LayoutDeleteEvent;
import io.harness.idp.proxy.layout.events.LayoutUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class LayoutEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public LayoutEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case LAYOUT_CREATED:
          return handleLayoutCreateEvent(outboxEvent);
        case LAYOUT_UPDATED:
          return handleLayoutUpdateEvent(outboxEvent);
        case LAYOUT_DELETED:
          return handleLayoutDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleLayoutUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    LayoutUpdateEvent layoutUpdateEvent = objectMapper.readValue(outboxEvent.getEventData(), LayoutUpdateEvent.class);

    String newLayoutYaml;
    String oldLayoutYaml;
    try {
      JsonNode newLayoutNode = ConfigManagerUtils.asJsonNode(layoutUpdateEvent.getNewLayout().getYaml());
      newLayoutYaml = ConfigManagerUtils.asYaml(newLayoutNode.toString());
      newLayoutYaml = removeUnwantedCharactersFromYaml(newLayoutYaml);

      JsonNode oldLayoutNode = ConfigManagerUtils.asJsonNode(layoutUpdateEvent.getOldLayout().getYaml());
      oldLayoutYaml = ConfigManagerUtils.asYaml(oldLayoutNode.toString());
      oldLayoutYaml = removeUnwantedCharactersFromYaml(oldLayoutYaml);
    } catch (Exception e) {
      log.error("Error in parsing the yaml", e.getMessage());
      return true;
    }

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.IDP)
                                .newYaml(newLayoutYaml)
                                .oldYaml(oldLayoutYaml)
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleLayoutCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    LayoutCreateEvent layoutCreateEvent = objectMapper.readValue(outboxEvent.getEventData(), LayoutCreateEvent.class);
    String newLayoutYaml;
    try {
      JsonNode newLayoutNode = ConfigManagerUtils.asJsonNode(layoutCreateEvent.getNewLayout().getYaml());
      newLayoutYaml = ConfigManagerUtils.asYaml(newLayoutNode.toString());
      newLayoutYaml = removeUnwantedCharactersFromYaml(newLayoutYaml);
    } catch (Exception e) {
      log.error("Error in parsing the yaml", e.getMessage());
      return true;
    }

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.IDP)
                                .newYaml(newLayoutYaml)
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleLayoutDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    LayoutDeleteEvent layoutDeleteEvent = objectMapper.readValue(outboxEvent.getEventData(), LayoutDeleteEvent.class);
    String oldLayoutYaml;
    try {
      JsonNode oldLayoutNode = ConfigManagerUtils.asJsonNode(layoutDeleteEvent.getOldLayout().getYaml());
      oldLayoutYaml = ConfigManagerUtils.asYaml(oldLayoutNode.toString());
      oldLayoutYaml = removeUnwantedCharactersFromYaml(oldLayoutYaml);
    } catch (Exception e) {
      log.error("Error in parsing the yaml", e.getMessage());
      return true;
    }

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.IDP)
                                .oldYaml(oldLayoutYaml)
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private String removeUnwantedCharactersFromYaml(String yamlString) {
    yamlString = yamlString.replaceFirst("---\n", "");
    return yamlString;
  }
}