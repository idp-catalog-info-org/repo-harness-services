/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.integrations.events.GitIntegrationCreateEvent.GIT_INTEGRATION_CREATED;
import static io.harness.idp.integrations.events.GitIntegrationDeleteEvent.GIT_INTEGRATION_DELETED;
import static io.harness.idp.integrations.events.GitIntegrationUpdateEvent.GIT_INTEGRATION_UPDATED;
import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GithubIntegrationEntity;
import io.harness.idp.integrations.events.GitIntegrationCreateEvent;
import io.harness.idp.integrations.events.GitIntegrationDeleteEvent;
import io.harness.idp.integrations.events.GitIntegrationUpdateEvent;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class GitIntegrationsEventHandlerTest {
  @Mock private AuditClientService auditClientService;
  @InjectMocks private GitIntegrationsEventHandler gitIntegrationsEventHandler;

  private static final String ACCOUNT_ID = "accountId";
  private static final String CONNECTOR_ID = "connectorId";
  private static final String HOST = "https://github.com";
  private static final String EVENT_ID = "eventId";
  private static final Long CREATED_AT = 1234567890L;
  private ObjectMapper objectMapper = new ObjectMapper();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleGitIntegrationCreateEvent() throws Exception {
    GitIntegrationEntity entity = getGitIntegrationEntity();
    GitIntegrationCreateEvent createEvent = new GitIntegrationCreateEvent(ACCOUNT_ID, entity);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(GIT_INTEGRATION_CREATED)
                                  .eventData(objectMapper.writeValueAsString(createEvent))
                                  .resourceScope(createEvent.getResourceScope())
                                  .resource(createEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = gitIntegrationsEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    ArgumentCaptor<AuditEntry> auditEntryCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClientService).publishAudit(auditEntryCaptor.capture(), any(GlobalContext.class));

    AuditEntry capturedEntry = auditEntryCaptor.getValue();
    assertThat(capturedEntry.getAction()).isEqualTo(Action.CREATE);
    assertThat(capturedEntry.getInsertId()).isEqualTo(EVENT_ID);
    assertThat(capturedEntry.getTimestamp()).isEqualTo(CREATED_AT);
    assertThat(capturedEntry.getNewYaml()).isNotNull();
    assertThat(capturedEntry.getOldYaml()).isNull();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleGitIntegrationUpdateEvent() throws Exception {
    GitIntegrationEntity newEntity = getGitIntegrationEntity();
    GitIntegrationEntity oldEntity = getGitIntegrationEntity();
    oldEntity.setHost("https://gitlab.com");

    GitIntegrationUpdateEvent updateEvent = new GitIntegrationUpdateEvent(ACCOUNT_ID, newEntity, oldEntity);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(GIT_INTEGRATION_UPDATED)
                                  .eventData(objectMapper.writeValueAsString(updateEvent))
                                  .resourceScope(updateEvent.getResourceScope())
                                  .resource(updateEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = gitIntegrationsEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    ArgumentCaptor<AuditEntry> auditEntryCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClientService).publishAudit(auditEntryCaptor.capture(), any(GlobalContext.class));

    AuditEntry capturedEntry = auditEntryCaptor.getValue();
    assertThat(capturedEntry.getAction()).isEqualTo(Action.UPDATE);
    assertThat(capturedEntry.getInsertId()).isEqualTo(EVENT_ID);
    assertThat(capturedEntry.getTimestamp()).isEqualTo(CREATED_AT);
    assertThat(capturedEntry.getNewYaml()).isNotNull();
    assertThat(capturedEntry.getOldYaml()).isNotNull();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleGitIntegrationDeleteEvent() throws Exception {
    GitIntegrationEntity entity = getGitIntegrationEntity();
    GitIntegrationDeleteEvent deleteEvent = new GitIntegrationDeleteEvent(ACCOUNT_ID, entity);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(GIT_INTEGRATION_DELETED)
                                  .eventData(objectMapper.writeValueAsString(deleteEvent))
                                  .resourceScope(deleteEvent.getResourceScope())
                                  .resource(deleteEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = gitIntegrationsEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    ArgumentCaptor<AuditEntry> auditEntryCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClientService).publishAudit(auditEntryCaptor.capture(), any(GlobalContext.class));

    AuditEntry capturedEntry = auditEntryCaptor.getValue();
    assertThat(capturedEntry.getAction()).isEqualTo(Action.DELETE);
    assertThat(capturedEntry.getInsertId()).isEqualTo(EVENT_ID);
    assertThat(capturedEntry.getTimestamp()).isEqualTo(CREATED_AT);
    assertThat(capturedEntry.getNewYaml()).isNull();
    assertThat(capturedEntry.getOldYaml()).isNotNull();
  }

  @Test(expected = io.harness.exception.InvalidArgumentsException.class)
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleUnsupportedEventType() throws Exception {
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("UNSUPPORTED_EVENT")
                                  .eventData("{}")
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    gitIntegrationsEventHandler.handle(outboxEvent);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleEventWithIOException() throws Exception {
    Resource resource = Resource.builder().type("GIT_INTEGRATION").identifier(CONNECTOR_ID).build();
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(GIT_INTEGRATION_CREATED)
                                  .eventData("invalid json")
                                  .resourceScope(new AccountScope(ACCOUNT_ID))
                                  .resource(resource)
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    boolean result = gitIntegrationsEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
  }

  private GitIntegrationEntity getGitIntegrationEntity() {
    Set<String> delegateSelectors = new HashSet<>();
    delegateSelectors.add("delegate1");
    delegateSelectors.add("delegate2");
    return GithubIntegrationEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .connectorIdentifier(CONNECTOR_ID)
        .identifier("github_integration")
        .parentType(io.harness.idp.integrations.entities.IntegrationEntity.ParentType.GITHUB)
        .host(HOST)
        .authMode(GitIntegrationEntity.AuthMode.TOKEN)
        .executeOnDelegate(true)
        .delegateSelectors(delegateSelectors)
        .build();
  }
}
