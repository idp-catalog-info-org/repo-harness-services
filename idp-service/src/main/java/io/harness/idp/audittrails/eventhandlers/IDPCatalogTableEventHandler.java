/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.catalog.events.CatalogTableCreateEvent.IDP_CATALOG_TABLE_CREATED;
import static io.harness.idp.catalog.events.CatalogTableUpdateEvent.IDP_CATALOG_TABLE_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.audittrails.eventhandlers.dtos.CatalogTableDTO;
import io.harness.idp.catalog.events.CatalogTableCreateEvent;
import io.harness.idp.catalog.events.CatalogTableUpdateEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.spec.server.idp.v1.model.EntityTableResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IDPCatalogTableEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public IDPCatalogTableEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      return switch (outboxEvent.getEventType()) {
                case IDP_CATALOG_TABLE_CREATED -> handleIdpCatalogCreateEvent(outboxEvent);
                case IDP_CATALOG_TABLE_UPDATED -> handleIdpCatalogUpdateEvent(outboxEvent);
                default ->
                        throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
            };
        } catch (IOException exception) {
            log.error("Failed to handle {} event", outboxEvent.getEventType(), exception);
            return false;
        }
    }

    private boolean handleIdpCatalogCreateEvent(OutboxEvent outboxEvent) throws IOException {
        GlobalContext globalContext = outboxEvent.getGlobalContext();

        CatalogTableCreateEvent catalogTableCreateEvent =
                objectMapper.readValue(outboxEvent.getEventData(), CatalogTableCreateEvent.class);

        AuditEntry auditEntry = AuditEntry.builder()
                .action(Action.CREATE)
                .module(ModuleType.IDP)
                .newYaml(NGYamlUtils.getYamlString(getCatalogTableDTO(catalogTableCreateEvent.getEntityTableResponse()), objectMapper))
                .timestamp(outboxEvent.getCreatedAt())
                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                .insertId(outboxEvent.getId())
                .build();
        return auditClientService.publishAudit(auditEntry, globalContext);
    }

    private boolean handleIdpCatalogUpdateEvent(OutboxEvent outboxEvent) throws IOException {
        GlobalContext globalContext = outboxEvent.getGlobalContext();

        CatalogTableUpdateEvent catalogTableUpdateEvent =
                objectMapper.readValue(outboxEvent.getEventData(), CatalogTableUpdateEvent.class);

        AuditEntry auditEntry =
                AuditEntry.builder()
                        .action(Action.UPDATE)
                        .module(ModuleType.IDP)
                        .newYaml(NGYamlUtils.getYamlString(
                                getCatalogTableDTO(catalogTableUpdateEvent.getNewEntityTableResponse()),
                                objectMapper))
                        .oldYaml(NGYamlUtils.getYamlString(
                                getCatalogTableDTO(catalogTableUpdateEvent.getOldEntityTableResponse()),
                                objectMapper))
                        .timestamp(outboxEvent.getCreatedAt())
                        .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                        .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                        .insertId(outboxEvent.getId())
                        .build();
        return auditClientService.publishAudit(auditEntry, globalContext);
    }

    private CatalogTableDTO getCatalogTableDTO(EntityTableResponse entityTableResponse) {
        return CatalogTableDTO.builder().entityTableResponse(entityTableResponse).build();
    }
}
