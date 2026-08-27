/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.audit.ResourceTypeConstants.GITX_WEBHOOK;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.ANKIT_TIWARI;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.AuthenticationInfoDTO;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookBaseAuditEvent;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEntityRequest;
import io.harness.gitsync.gitxwebhooks.entity.audit.GitXWebhookAutoCreateEvent;
import io.harness.gitsync.gitxwebhooks.entity.audit.GitXWebhookCreateEvent;
import io.harness.gitsync.gitxwebhooks.entity.audit.GitXWebhookDeleteEvent;
import io.harness.gitsync.gitxwebhooks.entity.audit.GitXWebhookUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

public class GitXWebhookOutboxEventHandlerTest {
  private ObjectMapper mapper;
  private AuditClientService auditClientService;

  private GlobalContext globalContext = buildMockGlobalContext();
  private GitXWebhookOutboxEventHandler gitXWebhookOutboxEventHandler;

  @Before
  public void setup() {
    this.mapper = NG_DEFAULT_OBJECT_MAPPER;
    auditClientService = mock(AuditClientService.class);
    gitXWebhookOutboxEventHandler = spy(new GitXWebhookOutboxEventHandler(auditClientService));
  }

  @Test
  @Owner(developers = ANKIT_TIWARI)
  @Category(UnitTests.class)
  public void handleDelete() throws JsonProcessingException {
    ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);

    GitXWebhook gitXWebhook = gitxWebhookEvent("identifier");
    GitXWebhookDeleteEvent event = GitXWebhookDeleteEvent.builder()
                                       .accountIdentifier("accountId")
                                       .orgIdentifier("orgId")
                                       .projectIdentifier("project_id")
                                       .gitXWebhook(new GitXWebhookBaseAuditEvent(gitXWebhook))
                                       .build();

    String eventData = mapper.writeValueAsString(event);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(event.getEventType())
                                  .resourceScope(event.getResourceScope())
                                  .resource(event.getResource())
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .build();

    gitXWebhookOutboxEventHandler.handle(outboxEvent);

    verify(auditClientService, times(1))
        .publishAudit(captor.capture(), any(AuthenticationInfoDTO.class), any(GlobalContext.class));

    AuditEntry entry = captor.getValue();

    assertThat(entry.getAction()).isEqualTo(Action.DELETE);
    assertThat(entry.getModule()).isEqualTo(ModuleType.CORE);
    assertThat(entry.getResource())
        .isEqualTo(ResourceDTO.builder().identifier("identifier").type(GITX_WEBHOOK).labels(new HashMap<>()).build());

    assertThat(entry.getNewYaml())
        .isEqualTo(getYamlString(
            GitXWebhookEntityRequest.builder().gitXWebhook(new GitXWebhookBaseAuditEvent(gitXWebhook)).build()));
  }

  @Test
  @Owner(developers = ANKIT_TIWARI)
  @Category(UnitTests.class)
  public void handleCreate() throws JsonProcessingException {
    ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);

    GitXWebhook gitXWebhook = gitxWebhookEvent("identifier");

    GitXWebhookCreateEvent event = GitXWebhookCreateEvent.builder()
                                       .accountIdentifier("accountId")
                                       .orgIdentifier("orgId")
                                       .projectIdentifier("project_id")
                                       .gitXWebhook(new GitXWebhookBaseAuditEvent(gitXWebhook))
                                       .build();

    String eventData = mapper.writeValueAsString(event);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(event.getEventType())
                                  .resourceScope(event.getResourceScope())
                                  .resource(event.getResource())
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .build();

    gitXWebhookOutboxEventHandler.handle(outboxEvent);

    verify(auditClientService, times(1))
        .publishAudit(captor.capture(), any(AuthenticationInfoDTO.class), any(GlobalContext.class));

    AuditEntry entry = captor.getValue();

    assertThat(entry.getAction()).isEqualTo(Action.CREATE);
    assertThat(entry.getModule()).isEqualTo(ModuleType.CORE);
    assertThat(entry.getResource())
        .isEqualTo(ResourceDTO.builder().identifier("identifier").type(GITX_WEBHOOK).labels(new HashMap<>()).build());

    assertThat(entry.getNewYaml())
        .isEqualTo(getYamlString(
            GitXWebhookEntityRequest.builder().gitXWebhook(new GitXWebhookBaseAuditEvent(gitXWebhook)).build()));
  }

  @Test
  @Owner(developers = ANKIT_TIWARI)
  @Category(UnitTests.class)
  public void handleUpdate() throws JsonProcessingException {
    ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);

    GitXWebhook gitXWebhook = gitxWebhookEvent("identifier");
    gitXWebhook.setIsEnabled(false);

    GitXWebhookUpdateEvent event = GitXWebhookUpdateEvent.builder()
                                       .accountIdentifier("accountId")
                                       .orgIdentifier("orgId")
                                       .projectIdentifier("project_id")
                                       .gitXWebhook(new GitXWebhookBaseAuditEvent(gitxWebhookEvent("identifier")))
                                       .newGitXWebhook(new GitXWebhookBaseAuditEvent(gitXWebhook))
                                       .build();

    String eventData = mapper.writeValueAsString(event);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(event.getEventType())
                                  .resourceScope(event.getResourceScope())
                                  .resource(event.getResource())
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .build();

    gitXWebhookOutboxEventHandler.handle(outboxEvent);

    verify(auditClientService, times(1))
        .publishAudit(captor.capture(), any(AuthenticationInfoDTO.class), any(GlobalContext.class));

    AuditEntry entry = captor.getValue();

    assertThat(entry.getAction()).isEqualTo(Action.UPDATE);
    assertThat(entry.getModule()).isEqualTo(ModuleType.CORE);
    assertThat(entry.getResource())
        .isEqualTo(ResourceDTO.builder().identifier("identifier").type(GITX_WEBHOOK).labels(new HashMap<>()).build());

    assertThat(entry.getNewYaml())
        .isEqualTo(getYamlString(
            GitXWebhookEntityRequest.builder().gitXWebhook(new GitXWebhookBaseAuditEvent(gitXWebhook)).build()));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void handleAutoCreate() throws JsonProcessingException {
    ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);

    GitXWebhook gitXWebhook = gitxWebhookEvent("identifier");

    GitXWebhookAutoCreateEvent event = GitXWebhookAutoCreateEvent.builder()
                                           .accountIdentifier("accountId")
                                           .orgIdentifier("orgId")
                                           .projectIdentifier("project_id")
                                           .gitXWebhook(new GitXWebhookBaseAuditEvent(gitXWebhook))
                                           .build();

    String eventData = mapper.writeValueAsString(event);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(event.getEventType())
                                  .resourceScope(event.getResourceScope())
                                  .resource(event.getResource())
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .build();

    gitXWebhookOutboxEventHandler.handle(outboxEvent);

    verify(auditClientService, times(1))
        .publishAudit(captor.capture(), any(AuthenticationInfoDTO.class), any(GlobalContext.class));

    AuditEntry entry = captor.getValue();

    assertThat(entry.getAction()).isEqualTo(Action.CREATE);
    assertThat(entry.getModule()).isEqualTo(ModuleType.CORE);
    assertThat(entry.getResource())
        .isEqualTo(ResourceDTO.builder().identifier("identifier").type(GITX_WEBHOOK).labels(new HashMap<>()).build());

    assertThat(entry.getNewYaml())
        .isEqualTo(getYamlString(
            GitXWebhookEntityRequest.builder().gitXWebhook(new GitXWebhookBaseAuditEvent(gitXWebhook)).build()));
  }

  private GlobalContext buildMockGlobalContext() {
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    return globalContext;
  }

  private GitXWebhook gitxWebhookEvent(String identifier) {
    return GitXWebhook.builder()
        .accountIdentifier("accountId")
        .orgIdentifier("orgId")
        .isEnabled(true)
        .projectIdentifier("project_id")
        .folderPaths(new ArrayList<>(Arrays.asList(".harness/pipeline")))
        .identifier(identifier)
        .build();
  }
}
