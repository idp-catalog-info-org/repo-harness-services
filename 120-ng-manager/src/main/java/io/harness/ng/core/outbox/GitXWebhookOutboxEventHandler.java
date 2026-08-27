/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.audit.beans.AuthenticationInfoDTO.fromSecurityPrincipal;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.security.PrincipalContextData.PRINCIPAL_CONTEXT;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEntityRequest;
import io.harness.gitsync.gitxwebhooks.entity.audit.GitXWebhookAutoCreateEvent;
import io.harness.gitsync.gitxwebhooks.entity.audit.GitXWebhookCreateEvent;
import io.harness.gitsync.gitxwebhooks.entity.audit.GitXWebhookDeleteEvent;
import io.harness.gitsync.gitxwebhooks.entity.audit.GitXWebhookDisableEvent;
import io.harness.gitsync.gitxwebhooks.entity.audit.GitXWebhookEnableEvent;
import io.harness.gitsync.gitxwebhooks.entity.audit.GitXWebhookEventConstants;
import io.harness.gitsync.gitxwebhooks.entity.audit.GitXWebhookUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitXWebhookOutboxEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  @Inject
  GitXWebhookOutboxEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  }
  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case GitXWebhookEventConstants.WEBHOOK_CREATED:
          return handleWebhookCreateEvent(outboxEvent);
        case GitXWebhookEventConstants.WEBHOOK_AUTO_CREATED:
          return handleWebhookAutoCreateEvent(outboxEvent);
        case GitXWebhookEventConstants.WEBHOOK_UPDATED:
          return handleWebhookUpdateEvent(outboxEvent);
        case GitXWebhookEventConstants.WEBHOOK_DELETED:
          return handleWebhookDeleteEvent(outboxEvent);
        case GitXWebhookEventConstants.WEBHOOK_ENABLED:
          return handleWebhookEnableEvent(outboxEvent);
        case GitXWebhookEventConstants.WEBHOOK_DISABLED:
          return handleWebhookDisableEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    } catch (Exception e) {
      log.info(String.format("Exception while handling webhook outbox flow for audits with outbox event id: [%s], "
              + "resource type: [%s], resource id: [%s]}",
          outboxEvent.getId(), outboxEvent.getResource().getType(), outboxEvent.getResource().getIdentifier()));
      throw e;
    }
  }

  // not being used as of now. Update is used for enable - disable also
  private boolean handleWebhookDisableEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    GitXWebhookDisableEvent webhookEvent =
        objectMapper.readValue(outboxEvent.getEventData(), GitXWebhookDisableEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DISABLED)
            .module(ModuleType.CORE)
            .insertId(outboxEvent.getId())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .timestamp(outboxEvent.getCreatedAt())
            .newYaml(
                getYamlString(GitXWebhookEntityRequest.builder().gitXWebhook(webhookEvent.getGitXWebhook()).build()))
            .build();

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  // not being used as of now. Update is used for enable - disable also
  private boolean handleWebhookEnableEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    GitXWebhookEnableEvent webhookEvent =
        objectMapper.readValue(outboxEvent.getEventData(), GitXWebhookEnableEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.ENABLED)
            .module(ModuleType.CORE)
            .insertId(outboxEvent.getId())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .timestamp(outboxEvent.getCreatedAt())
            .newYaml(
                getYamlString(GitXWebhookEntityRequest.builder().gitXWebhook(webhookEvent.getGitXWebhook()).build()))
            .build();

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  private boolean handleWebhookDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    GitXWebhookDeleteEvent webhookEvent =
        objectMapper.readValue(outboxEvent.getEventData(), GitXWebhookDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CORE)
            .insertId(outboxEvent.getId())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .timestamp(outboxEvent.getCreatedAt())
            .newYaml(
                getYamlString(GitXWebhookEntityRequest.builder().gitXWebhook(webhookEvent.getGitXWebhook()).build()))
            .build();

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  private boolean handleWebhookUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    GitXWebhookUpdateEvent webhookEvent =
        objectMapper.readValue(outboxEvent.getEventData(), GitXWebhookUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .insertId(outboxEvent.getId())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .timestamp(outboxEvent.getCreatedAt())
            .oldYaml(
                getYamlString(GitXWebhookEntityRequest.builder().gitXWebhook(webhookEvent.getGitXWebhook()).build()))
            .newYaml(
                getYamlString(GitXWebhookEntityRequest.builder().gitXWebhook(webhookEvent.getNewGitXWebhook()).build()))
            .build();

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  private boolean handleWebhookCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    GitXWebhookCreateEvent webhookEvent =
        objectMapper.readValue(outboxEvent.getEventData(), GitXWebhookCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .insertId(outboxEvent.getId())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .timestamp(outboxEvent.getCreatedAt())
            .newYaml(
                getYamlString(GitXWebhookEntityRequest.builder().gitXWebhook(webhookEvent.getGitXWebhook()).build()))
            .build();

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  private boolean handleWebhookAutoCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    GitXWebhookAutoCreateEvent webhookEvent =
        objectMapper.readValue(outboxEvent.getEventData(), GitXWebhookAutoCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .insertId(outboxEvent.getId())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .timestamp(outboxEvent.getCreatedAt())
            .newYaml(
                getYamlString(GitXWebhookEntityRequest.builder().gitXWebhook(webhookEvent.getGitXWebhook()).build()))
            .build();

    Principal principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }
}
