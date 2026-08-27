/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.integrations.events.GitIntegrationCreateEvent.GIT_INTEGRATION_CREATED;
import static io.harness.idp.integrations.events.GitIntegrationDeleteEvent.GIT_INTEGRATION_DELETED;
import static io.harness.idp.integrations.events.GitIntegrationUpdateEvent.GIT_INTEGRATION_UPDATED;

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
import io.harness.idp.audittrails.eventhandlers.dtos.GitIntegrationDTO;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.events.GitIntegrationCreateEvent;
import io.harness.idp.integrations.events.GitIntegrationDeleteEvent;
import io.harness.idp.integrations.events.GitIntegrationUpdateEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class GitIntegrationsEventHandler implements OutboxEventHandler {
  private static final ObjectMapper OBJECT_MAPPER = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public GitIntegrationsEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      return switch (outboxEvent.getEventType()) {
                case GIT_INTEGRATION_CREATED -> handleGitIntegrationCreateEvent(outboxEvent);
                case GIT_INTEGRATION_UPDATED -> handleGitIntegrationUpdateEvent(outboxEvent);
                case GIT_INTEGRATION_DELETED -> handleGitIntegrationDeleteEvent(outboxEvent);
                default -> throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
            };
        } catch (IOException exception) {
            log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
            return false;
        }
    }

    private boolean handleGitIntegrationCreateEvent(OutboxEvent outboxEvent) throws IOException {
        GlobalContext globalContext = outboxEvent.getGlobalContext();
        GitIntegrationCreateEvent gitIntegrationCreateEvent =
                OBJECT_MAPPER.readValue(outboxEvent.getEventData(), GitIntegrationCreateEvent.class);

        AuditEntry auditEntry = AuditEntry.builder()
                .action(Action.CREATE)
                .module(ModuleType.IDP)
                .newYaml((getYaml(gitIntegrationCreateEvent.getEntity())))
                .timestamp(outboxEvent.getCreatedAt())
                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                .insertId(outboxEvent.getId())
                .build();
        return auditClientService.publishAudit(auditEntry, globalContext);
    }

    private boolean handleGitIntegrationUpdateEvent(OutboxEvent outboxEvent) throws IOException {
        GlobalContext globalContext = outboxEvent.getGlobalContext();
        GitIntegrationUpdateEvent gitIntegrationUpdateEvent =
                OBJECT_MAPPER.readValue(outboxEvent.getEventData(), GitIntegrationUpdateEvent.class);

        String oldYaml = getYaml(gitIntegrationUpdateEvent.getOldEntity());
        String newYaml = getYaml(gitIntegrationUpdateEvent.getNewEntity());

        if (oldYaml.equals(newYaml)) {
            return true;
        }

        AuditEntry auditEntry =
                AuditEntry.builder()
                        .action(Action.UPDATE)
                        .module(ModuleType.IDP)
                        .oldYaml(oldYaml)
                        .newYaml(newYaml)
                        .timestamp(outboxEvent.getCreatedAt())
                        .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                        .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                        .insertId(outboxEvent.getId())
                        .build();
        return auditClientService.publishAudit(auditEntry, globalContext);
    }

    private boolean handleGitIntegrationDeleteEvent(OutboxEvent outboxEvent) throws IOException {
        GlobalContext globalContext = outboxEvent.getGlobalContext();
        GitIntegrationDeleteEvent gitIntegrationDeleteEvent =
                OBJECT_MAPPER.readValue(outboxEvent.getEventData(), GitIntegrationDeleteEvent.class);

        AuditEntry auditEntry = AuditEntry.builder()
                .action(Action.DELETE)
                .module(ModuleType.IDP)
                .oldYaml((getYaml(gitIntegrationDeleteEvent.getEntity())))
                .timestamp(outboxEvent.getCreatedAt())
                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                .insertId(outboxEvent.getId())
                .build();
        return auditClientService.publishAudit(auditEntry, globalContext);
    }

    private String getYaml(GitIntegrationEntity entity) {
        return NGYamlUtils.getYamlString(GitIntegrationDTO.builder()
                        .connectorIdentifier(entity.getConnectorIdentifier())
                        .parentType(entity.getParentType().name())
                        .subType(Objects.nonNull(entity.getSubType()) ? entity.getSubType().name() : "")
                        .host(entity.getHost())
                        .authType(entity.getAuthMode().name())
                        .executeOnDelegate(entity.isExecuteOnDelegate())
                        .delegateSelectors(entity.getDelegateSelectors())
                        .build(),
                OBJECT_MAPPER);
      }
    }
