/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.catalog.events.EnvironmentCreateEvent.IDP_ENVIRONMENT_CREATED;
import static io.harness.idp.catalog.events.EnvironmentDeleteEvent.IDP_ENVIRONMENT_DELETED;
import static io.harness.idp.catalog.events.EnvironmentUpdateEvent.IDP_ENVIRONMENT_UPDATED;
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
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.idp.catalog.events.EnvironmentCreateEvent;
import io.harness.idp.catalog.events.EnvironmentDeleteEvent;
import io.harness.idp.catalog.events.EnvironmentUpdateEvent;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IDPEnvironmentEventHandlerTest {
  @Mock private AuditClientService auditClientService;
  @InjectMocks private IDPEnvironmentEventHandler idpEnvironmentEventHandler;

  private static final String ACCOUNT_ID = "accountId";
  private static final String YAML_CONTENT =
      "apiVersion: backstage.io/v1alpha1\nkind: Resource\nmetadata:\n  name: environment";
  private static final String KIND = "Resource";
  private static final String IDENTIFIER = "environment";
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
  public void testHandleEnvironmentCreateEvent() throws Exception {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .uniqueId(IDENTIFIER)
                              .scopeType(io.harness.beans.ScopeLevel.ACCOUNT)
                              .build();
    EnvironmentCreateEvent createEvent = new EnvironmentCreateEvent(scopeInfo, YAML_CONTENT, KIND, IDENTIFIER);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(IDP_ENVIRONMENT_CREATED)
                                  .eventData(objectMapper.writeValueAsString(createEvent))
                                  .resourceScope(createEvent.getResourceScope())
                                  .resource(createEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = idpEnvironmentEventHandler.handle(outboxEvent);

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
  public void testHandleEnvironmentUpdateEvent() throws Exception {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .uniqueId(IDENTIFIER)
                              .scopeType(io.harness.beans.ScopeLevel.ACCOUNT)
                              .build();
    String newYaml = "apiVersion: backstage.io/v1alpha1\nkind: Resource\nmetadata:\n  name: new-environment";
    String oldYaml = "apiVersion: backstage.io/v1alpha1\nkind: Resource\nmetadata:\n  name: old-environment";
    EnvironmentUpdateEvent updateEvent = new EnvironmentUpdateEvent(scopeInfo, newYaml, oldYaml, KIND, IDENTIFIER);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(IDP_ENVIRONMENT_UPDATED)
                                  .eventData(objectMapper.writeValueAsString(updateEvent))
                                  .resourceScope(updateEvent.getResourceScope())
                                  .resource(updateEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = idpEnvironmentEventHandler.handle(outboxEvent);

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
  public void testHandleEnvironmentDeleteEvent() throws Exception {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .uniqueId(IDENTIFIER)
                              .scopeType(io.harness.beans.ScopeLevel.ACCOUNT)
                              .build();
    EnvironmentDeleteEvent deleteEvent = new EnvironmentDeleteEvent(scopeInfo, YAML_CONTENT, KIND, IDENTIFIER);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(IDP_ENVIRONMENT_DELETED)
                                  .eventData(objectMapper.writeValueAsString(deleteEvent))
                                  .resourceScope(deleteEvent.getResourceScope())
                                  .resource(deleteEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = idpEnvironmentEventHandler.handle(outboxEvent);

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

    idpEnvironmentEventHandler.handle(outboxEvent);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleEventWithIOException() throws Exception {
    Resource resource = Resource.builder().type("IDP_ENVIRONMENT").identifier(IDENTIFIER).build();
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(IDP_ENVIRONMENT_CREATED)
                                  .eventData("invalid json")
                                  .resourceScope(new AccountScope(ACCOUNT_ID))
                                  .resource(resource)
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    boolean result = idpEnvironmentEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
  }
}
