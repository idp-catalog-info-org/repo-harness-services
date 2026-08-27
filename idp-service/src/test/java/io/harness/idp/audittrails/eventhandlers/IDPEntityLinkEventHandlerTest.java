/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.catalog.events.EntityLinkCreateEvent.ENTITY_LINK_CREATED;
import static io.harness.idp.catalog.events.EntityLinkDeleteEvent.ENTITY_LINK_DELETED;
import static io.harness.idp.catalog.events.EntityLinkUpdateEvent.ENTITY_LINK_UPDATED;
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
import io.harness.idp.catalog.events.EntityLinkCreateEvent;
import io.harness.idp.catalog.events.EntityLinkDeleteEvent;
import io.harness.idp.catalog.events.EntityLinkUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IDPEntityLinkEventHandlerTest {
  @Mock private AuditClientService auditClientService;
  @InjectMocks private IDPEntityLinkEventHandler handler;

  AutoCloseable openMocks;

  private static final String ACCOUNT_ID = "test-account-id";
  private static final String ORG_ID = "default";
  private static final String PROJECT_ID = "myproject";
  private static final String ENTITY_REF = "workflow:account/my-workflow";
  private static final String EVENT_ID = "event-id-123";
  private static final Long CREATED_AT = 1700000000000L;
  private static final String NEW_JSON = "{\"entityRef\":\"" + ENTITY_REF + "\"}";
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleCreateEvent_publishesAuditWithCreateAction() throws Exception {
    EntityLinkCreateEvent event =
        new EntityLinkCreateEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENTITY_REF, "{\"entityRef\":\"" + ENTITY_REF + "\"}");
    OutboxEvent outboxEvent = buildOutboxEvent(ENTITY_LINK_CREATED, event);
    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = handler.handle(outboxEvent);

    assertThat(result).isTrue();
    ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClientService).publishAudit(captor.capture(), any(GlobalContext.class));
    assertThat(captor.getValue().getAction()).isEqualTo(Action.CREATE);
    assertThat(captor.getValue().getNewYaml()).contains(ENTITY_REF);
    assertThat(captor.getValue().getOldYaml()).isNull();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleUpdateEvent_publishesAuditWithUpdateAction() throws Exception {
    EntityLinkUpdateEvent event =
        new EntityLinkUpdateEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENTITY_REF, "{\"old\":true}", "{\"new\":true}");
    OutboxEvent outboxEvent = buildOutboxEvent(ENTITY_LINK_UPDATED, event);
    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = handler.handle(outboxEvent);

    assertThat(result).isTrue();
    ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClientService).publishAudit(captor.capture(), any(GlobalContext.class));
    assertThat(captor.getValue().getAction()).isEqualTo(Action.UPDATE);
    assertThat(captor.getValue().getOldYaml()).isNotNull();
    assertThat(captor.getValue().getNewYaml()).isNotNull();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleDeleteEvent_publishesAuditWithDeleteAction() throws Exception {
    EntityLinkDeleteEvent event =
        new EntityLinkDeleteEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENTITY_REF, "{\"entityRef\":\"" + ENTITY_REF + "\"}");
    OutboxEvent outboxEvent = buildOutboxEvent(ENTITY_LINK_DELETED, event);
    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = handler.handle(outboxEvent);

    assertThat(result).isTrue();
    ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClientService).publishAudit(captor.capture(), any(GlobalContext.class));
    assertThat(captor.getValue().getAction()).isEqualTo(Action.DELETE);
    assertThat(captor.getValue().getOldYaml()).contains(ENTITY_REF);
    assertThat(captor.getValue().getNewYaml()).isNull();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleCreateEvent_auditEntryHasCorrectInsertIdAndTimestamp() throws Exception {
    EntityLinkCreateEvent event = new EntityLinkCreateEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENTITY_REF, NEW_JSON);
    OutboxEvent outboxEvent = buildOutboxEvent(ENTITY_LINK_CREATED, event);
    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    handler.handle(outboxEvent);

    ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClientService).publishAudit(captor.capture(), any(GlobalContext.class));
    assertThat(captor.getValue().getInsertId()).isEqualTo(EVENT_ID);
    assertThat(captor.getValue().getTimestamp()).isEqualTo(CREATED_AT);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleCreateEvent_auditServiceReturnsFalse_propagatesFalse() throws Exception {
    EntityLinkCreateEvent event = new EntityLinkCreateEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENTITY_REF, NEW_JSON);
    OutboxEvent outboxEvent = buildOutboxEvent(ENTITY_LINK_CREATED, event);
    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(false);

    boolean result = handler.handle(outboxEvent);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleCreateEvent_accountScopeOnly_publishesAudit() throws Exception {
    EntityLinkCreateEvent event = new EntityLinkCreateEvent(ACCOUNT_ID, null, null, ENTITY_REF, NEW_JSON);
    OutboxEvent outboxEvent = buildOutboxEvent(ENTITY_LINK_CREATED, event);
    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = handler.handle(outboxEvent);

    assertThat(result).isTrue();
  }

  @Test(expected = io.harness.exception.InvalidArgumentsException.class)
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandle_unsupportedEventType_throwsInvalidArgumentsException() {
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("UnknownEvent")
                                  .eventData("{}")
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    handler.handle(outboxEvent);
  }

  private OutboxEvent buildOutboxEvent(String eventType, Object event) throws Exception {
    EntityLinkCreateEvent dummy = new EntityLinkCreateEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENTITY_REF, "{}");
    return OutboxEvent.builder()
        .eventType(eventType)
        .eventData(objectMapper.writeValueAsString(event))
        .resourceScope(dummy.getResourceScope())
        .resource(dummy.getResource())
        .createdAt(CREATED_AT)
        .id(EVENT_ID)
        .globalContext(new GlobalContext())
        .build();
  }
}
