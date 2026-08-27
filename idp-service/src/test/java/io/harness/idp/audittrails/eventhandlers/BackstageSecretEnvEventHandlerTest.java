/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretCreateEvent.ENV_VARIABLE_CREATED;
import static io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretDeleteEvent.ENV_VARIABLE_DELETED;
import static io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretUpdateEvent.ENV_VARIABLE_UPDATED;
import static io.harness.rule.OwnerRule.DEVESH;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
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
import io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretCreateEvent;
import io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretDeleteEvent;
import io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretUpdateEvent;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class BackstageSecretEnvEventHandlerTest {
  @Mock private AuditClientService auditClientService;
  @InjectMocks private BackstageSecretEnvEventHandler backstageSecretEnvEventHandler;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ENV_NAME = "MY_SECRET";
  private static final String SECRET_IDENTIFIER = "secretId";
  private static final String EVENT_ID = "eventId";
  private static final Long CREATED_AT = 1234567890L;
  private ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleBackstageEnvSecretCreateEvent() throws Exception {
    BackstageEnvSecretVariable variable = getBackstageEnvSecretVariable();
    BackstageEnvSecretCreateEvent createEvent = new BackstageEnvSecretCreateEvent(ACCOUNT_ID, variable);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(ENV_VARIABLE_CREATED)
                                  .eventData(objectMapper.writeValueAsString(createEvent))
                                  .resourceScope(createEvent.getResourceScope())
                                  .resource(createEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = backstageSecretEnvEventHandler.handle(outboxEvent);

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
  public void testHandleBackstageEnvSecretUpdateEvent() throws Exception {
    BackstageEnvSecretVariable newVariable = getBackstageEnvSecretVariable();
    BackstageEnvSecretVariable oldVariable = getBackstageEnvSecretVariable();
    oldVariable.setHarnessSecretIdentifier("oldSecretId");

    BackstageEnvSecretUpdateEvent updateEvent = new BackstageEnvSecretUpdateEvent(ACCOUNT_ID, newVariable, oldVariable);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(ENV_VARIABLE_UPDATED)
                                  .eventData(objectMapper.writeValueAsString(updateEvent))
                                  .resourceScope(updateEvent.getResourceScope())
                                  .resource(updateEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = backstageSecretEnvEventHandler.handle(outboxEvent);

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
  public void testHandleBackstageEnvSecretDeleteEvent() throws Exception {
    BackstageEnvSecretVariable variable = getBackstageEnvSecretVariable();
    BackstageEnvSecretDeleteEvent deleteEvent = new BackstageEnvSecretDeleteEvent(ACCOUNT_ID, variable);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(ENV_VARIABLE_DELETED)
                                  .eventData(objectMapper.writeValueAsString(deleteEvent))
                                  .resourceScope(deleteEvent.getResourceScope())
                                  .resource(deleteEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = backstageSecretEnvEventHandler.handle(outboxEvent);

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

    backstageSecretEnvEventHandler.handle(outboxEvent);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleEventWithIOException() throws Exception {
    Resource resource = Resource.builder().type("BACKSTAGE_ENV_SECRET").identifier(ENV_NAME).build();
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(ENV_VARIABLE_CREATED)
                                  .eventData("invalid json")
                                  .resourceScope(new AccountScope(ACCOUNT_ID))
                                  .resource(resource)
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    boolean result = backstageSecretEnvEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
  }

  private BackstageEnvSecretVariable getBackstageEnvSecretVariable() {
    BackstageEnvSecretVariable variable = new BackstageEnvSecretVariable();
    variable.setEnvName(ENV_NAME);
    variable.setHarnessSecretIdentifier(SECRET_IDENTIFIER);
    variable.setType(io.harness.spec.server.idp.v1.model.BackstageEnvVariable.TypeEnum.SECRET);
    return variable;
  }
}
