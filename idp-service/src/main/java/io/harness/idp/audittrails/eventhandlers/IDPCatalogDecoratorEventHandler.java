/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.catalog.events.CatalogDecoratorUpdateEvent.IDP_CATALOG_DECORATOR_UPDATED;
import static io.harness.idp.common.YamlUtils.writeObjectAsYaml;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.catalog.events.CatalogDecoratorUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IDPCatalogDecoratorEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public IDPCatalogDecoratorEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      if (outboxEvent.getEventType().equals(IDP_CATALOG_DECORATOR_UPDATED)) {
        return handleCatalogDecoratorUpdateEvent(outboxEvent);
      }
      throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
    } catch (IOException exception) {
      log.error("Failed to handle {} event", outboxEvent.getEventType(), exception);
      return false;
    }
  }

  private boolean handleCatalogDecoratorUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    CatalogDecoratorUpdateEvent catalogDecoratorUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CatalogDecoratorUpdateEvent.class);

    Map<String, Object> oldCatalogDecorator = catalogDecoratorUpdateEvent.getOldCatalogDecorator();
    Map<String, Object> newCatalogDecorator = catalogDecoratorUpdateEvent.getNewCatalogDecorator();
    if (oldCatalogDecorator.equals(newCatalogDecorator)) {
      return true;
    }

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.IDP)
                                .newYaml(writeObjectAsYaml(newCatalogDecorator))
                                .oldYaml(writeObjectAsYaml(oldCatalogDecorator))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
