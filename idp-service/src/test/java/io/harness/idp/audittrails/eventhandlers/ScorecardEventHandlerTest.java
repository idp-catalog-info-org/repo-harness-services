/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.scorecard.scorecards.events.ScorecardCheckFailureEvent.SCORECARD_CHECK_FAILED;
import static io.harness.idp.scorecard.scorecards.events.ScorecardCreateEvent.SCORECARD_CREATED;
import static io.harness.idp.scorecard.scorecards.events.ScorecardDeleteEvent.SCORECARD_DELETED;
import static io.harness.idp.scorecard.scorecards.events.ScorecardRecalibrateEvent.SCORECARD_RECALIBRATED;
import static io.harness.idp.scorecard.scorecards.events.ScorecardUpdateEvent.SCORECARD_UPDATED;
import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.idp.scorecard.scorecards.events.ScorecardCheckFailureEvent;
import io.harness.idp.scorecard.scorecards.events.ScorecardCreateEvent;
import io.harness.idp.scorecard.scorecards.events.ScorecardDeleteEvent;
import io.harness.idp.scorecard.scorecards.events.ScorecardRecalibrateEvent;
import io.harness.idp.scorecard.scorecards.events.ScorecardUpdateEvent;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ScorecardDetails;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class ScorecardEventHandlerTest {
  @Mock private AuditClientService auditClientService;
  @Mock private ScoreService scoreService;
  @InjectMocks private ScorecardEventHandler scorecardEventHandler;

  private static final String ACCOUNT_ID = "accountId";
  private static final String SCORECARD_ID = "scorecardId";
  private static final String SCORECARD_NAME = "scorecardName";
  private static final String ENTITY_ID = "entityId";
  private static final Long TRIGGERED_AT = 1234567890L;
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
  public void testHandleScorecardCreateEvent() throws Exception {
    ScorecardDetailsResponse scorecardDetails = getScorecardDetailsResponse();
    ScorecardCreateEvent createEvent = new ScorecardCreateEvent(ACCOUNT_ID, scorecardDetails);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(SCORECARD_CREATED)
                                  .eventData(objectMapper.writeValueAsString(createEvent))
                                  .resourceScope(createEvent.getResourceScope())
                                  .resource(createEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = scorecardEventHandler.handle(outboxEvent);

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
  public void testHandleScorecardUpdateEvent() throws Exception {
    ScorecardDetailsResponse newScorecardDetails = getScorecardDetailsResponse();

    ScorecardDetails oldScorecard = new ScorecardDetails();
    oldScorecard.setIdentifier(SCORECARD_ID);
    oldScorecard.setName("oldScorecardName");
    ScorecardDetailsResponse oldScorecardDetails = new ScorecardDetailsResponse();
    oldScorecardDetails.setScorecard(oldScorecard);

    ScorecardUpdateEvent updateEvent = new ScorecardUpdateEvent(ACCOUNT_ID, newScorecardDetails, oldScorecardDetails);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(SCORECARD_UPDATED)
                                  .eventData(objectMapper.writeValueAsString(updateEvent))
                                  .resourceScope(updateEvent.getResourceScope())
                                  .resource(updateEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = scorecardEventHandler.handle(outboxEvent);

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
  public void testHandleScorecardDeleteEvent() throws Exception {
    ScorecardDetailsResponse scorecardDetails = getScorecardDetailsResponse();
    ScorecardDeleteEvent deleteEvent = new ScorecardDeleteEvent(ACCOUNT_ID, scorecardDetails);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(SCORECARD_DELETED)
                                  .eventData(objectMapper.writeValueAsString(deleteEvent))
                                  .resourceScope(deleteEvent.getResourceScope())
                                  .resource(deleteEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = scorecardEventHandler.handle(outboxEvent);

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

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleScorecardRecalibrateEvent() throws Exception {
    ScorecardRecalibrateEvent recalibrateEvent =
        new ScorecardRecalibrateEvent(ACCOUNT_ID, SCORECARD_ID, SCORECARD_NAME);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(SCORECARD_RECALIBRATED)
                                  .eventData(objectMapper.writeValueAsString(recalibrateEvent))
                                  .resourceScope(recalibrateEvent.getResourceScope())
                                  .resource(recalibrateEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = scorecardEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    ArgumentCaptor<AuditEntry> auditEntryCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClientService).publishAudit(auditEntryCaptor.capture(), any(GlobalContext.class));

    AuditEntry capturedEntry = auditEntryCaptor.getValue();
    assertThat(capturedEntry.getAction()).isEqualTo(Action.RERUN);
    assertThat(capturedEntry.getInsertId()).isEqualTo(EVENT_ID);
    assertThat(capturedEntry.getTimestamp()).isEqualTo(CREATED_AT);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleScorecardCheckFailureEvent() throws Exception {
    ScorecardCheckFailureEvent checkFailureEvent =
        new ScorecardCheckFailureEvent(ACCOUNT_ID, SCORECARD_ID, SCORECARD_NAME, ENTITY_ID, TRIGGERED_AT);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(SCORECARD_CHECK_FAILED)
                                  .eventData(objectMapper.writeValueAsString(checkFailureEvent))
                                  .resourceScope(checkFailureEvent.getResourceScope())
                                  .resource(checkFailureEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    boolean result = scorecardEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    verify(scoreService)
        .generateFailureSummaryForFailedChecksInScore(
            eq(ACCOUNT_ID), eq(SCORECARD_ID), eq(ENTITY_ID), eq(TRIGGERED_AT));
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

    scorecardEventHandler.handle(outboxEvent);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleEventWithIOException() throws Exception {
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(SCORECARD_CREATED)
                                  .eventData("invalid json")
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    boolean result = scorecardEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
  }

  private ScorecardDetailsResponse getScorecardDetailsResponse() {
    ScorecardDetails scorecard = new ScorecardDetails();
    scorecard.setIdentifier(SCORECARD_ID);
    scorecard.setName(SCORECARD_NAME);

    ScorecardDetailsResponse scorecardDetails = new ScorecardDetailsResponse();
    scorecardDetails.setScorecard(scorecard);
    return scorecardDetails;
  }
}
