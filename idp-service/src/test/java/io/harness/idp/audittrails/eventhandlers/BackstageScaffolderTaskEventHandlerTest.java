/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.backstage.events.BackstageScaffolderTaskStartEvent.BACKSTAGE_SCAFFOLDER_TASK_START;
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
import io.harness.idp.backstage.events.BackstageScaffolderTaskStartEvent;
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
public class BackstageScaffolderTaskEventHandlerTest {
  @Mock private AuditClientService auditClientService;
  @InjectMocks private BackstageScaffolderTaskEventHandler backstageScaffolderTaskEventHandler;

  private static final String ACCOUNT_ID = "accountId";
  private static final String TASK_ID = "taskId";
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
  public void testHandleBackstageScaffolderTaskStartEvent() throws Exception {
    BackstageScaffolderTaskStartEvent startEvent = new BackstageScaffolderTaskStartEvent(ACCOUNT_ID, TASK_ID);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(BACKSTAGE_SCAFFOLDER_TASK_START)
                                  .eventData(objectMapper.writeValueAsString(startEvent))
                                  .resourceScope(new AccountScope(ACCOUNT_ID))
                                  .resource(startEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = backstageScaffolderTaskEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    ArgumentCaptor<AuditEntry> auditEntryCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClientService).publishAudit(auditEntryCaptor.capture(), any(GlobalContext.class));

    AuditEntry capturedEntry = auditEntryCaptor.getValue();
    assertThat(capturedEntry.getAction()).isEqualTo(Action.START);
    assertThat(capturedEntry.getInsertId()).isEqualTo(EVENT_ID);
    assertThat(capturedEntry.getTimestamp()).isEqualTo(CREATED_AT);
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

    backstageScaffolderTaskEventHandler.handle(outboxEvent);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleEventWithIOException() throws Exception {
    Resource resource = Resource.builder().type("BACKSTAGE_SCAFFOLDER_TASK").identifier(TASK_ID).build();
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(BACKSTAGE_SCAFFOLDER_TASK_START)
                                  .eventData("invalid json")
                                  .resourceScope(new AccountScope(ACCOUNT_ID))
                                  .resource(resource)
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    boolean result = backstageScaffolderTaskEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
  }
}
