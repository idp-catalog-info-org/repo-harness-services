/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cleanup;

import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dataretention.config.DataRetentionConfig;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.executionretention.ExecutionRetentionCleanupEvent;
import io.harness.rule.Owner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionRetentionCleanupEventPublisherTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "testAccountId";
  private static final String PLAN_EXECUTION_ID_1 = "planExecId1";
  private static final String PLAN_EXECUTION_ID_2 = "planExecId2";
  private static final int RETENTION_PERIOD_MONTHS = 6;

  @Mock private Producer eventProducer;
  @Mock private DataRetentionConfig dataRetentionConfig;

  private ExecutionRetentionCleanupEventPublisher publisher;
  private AutoCloseable mocks;

  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    publisher = new ExecutionRetentionCleanupEventPublisher();
    ReflectionTestUtils.setField(publisher, "eventProducer", eventProducer);
    ReflectionTestUtils.setField(publisher, "dataRetentionConfig", dataRetentionConfig);

    // Default config: cleanup events enabled with batch size 500
    when(dataRetentionConfig.isCleanupEventEnabled()).thenReturn(true);
    when(dataRetentionConfig.getCleanupEventBatchSize()).thenReturn(500);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishCleanupEvent_NullPlanExecutionIds() {
    publisher.publishCleanupEvent(TEST_ACCOUNT_ID, null, RETENTION_PERIOD_MONTHS, Instant.now());

    verify(eventProducer, never()).send(any(Message.class));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishCleanupEvent_EmptyPlanExecutionIds() {
    publisher.publishCleanupEvent(TEST_ACCOUNT_ID, Collections.emptyList(), RETENTION_PERIOD_MONTHS, Instant.now());

    verify(eventProducer, never()).send(any(Message.class));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishCleanupEvent_NullProducer() {
    // Set producer to null
    ReflectionTestUtils.setField(publisher, "eventProducer", null);

    // Should not throw exception
    publisher.publishCleanupEvent(
        TEST_ACCOUNT_ID, Arrays.asList(PLAN_EXECUTION_ID_1), RETENTION_PERIOD_MONTHS, Instant.now());

    // No way to verify, but test passes if no exception thrown
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishCleanupEvent_CleanupEventDisabled() {
    // Disable cleanup event publishing via config
    when(dataRetentionConfig.isCleanupEventEnabled()).thenReturn(false);

    publisher.publishCleanupEvent(
        TEST_ACCOUNT_ID, Collections.singletonList(PLAN_EXECUTION_ID_1), RETENTION_PERIOD_MONTHS, Instant.now());

    // Should not send any events when disabled
    verify(eventProducer, never()).send(any(Message.class));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishCleanupEvent_SinglePlanExecutionId() {
    Instant cleanupTimestamp = Instant.now();
    publisher.publishCleanupEvent(
        TEST_ACCOUNT_ID, Collections.singletonList(PLAN_EXECUTION_ID_1), RETENTION_PERIOD_MONTHS, cleanupTimestamp);

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(1)).send(messageCaptor.capture());

    Message sentMessage = messageCaptor.getValue();
    assert sentMessage.getMetadataMap().get("accountId").equals(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishCleanupEvent_MultiplePlanExecutionIds() {
    List<String> planExecutionIds = Arrays.asList(PLAN_EXECUTION_ID_1, PLAN_EXECUTION_ID_2);
    Instant cleanupTimestamp = Instant.now();

    publisher.publishCleanupEvent(TEST_ACCOUNT_ID, planExecutionIds, RETENTION_PERIOD_MONTHS, cleanupTimestamp);

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(1)).send(messageCaptor.capture());

    Message sentMessage = messageCaptor.getValue();
    assert sentMessage.getMetadataMap().get("accountId").equals(TEST_ACCOUNT_ID);

    // Verify the event data
    try {
      ExecutionRetentionCleanupEvent event = ExecutionRetentionCleanupEvent.parseFrom(sentMessage.getData());
      assert event.getAccountIdentifier().equals(TEST_ACCOUNT_ID);
      assert event.getPlanExecutionIdsList().size() == 2;
      assert event.getRetentionPeriodInMonths() == RETENTION_PERIOD_MONTHS;
      assert event.getCleanupTimestampMillis() == cleanupTimestamp.toEpochMilli();
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse event", e);
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishCleanupEvent_BatchingLargeLists() {
    // Create list with more than default batch size (500) elements
    List<String> planExecutionIds = new ArrayList<>();
    for (int i = 0; i < 1200; i++) {
      planExecutionIds.add("planExecId" + i);
    }

    publisher.publishCleanupEvent(TEST_ACCOUNT_ID, planExecutionIds, RETENTION_PERIOD_MONTHS, Instant.now());

    // Should be split into 3 batches: 500 + 500 + 200
    verify(eventProducer, times(3)).send(any(Message.class));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishCleanupEvent_CustomBatchSize() {
    // Set custom batch size via config
    when(dataRetentionConfig.getCleanupEventBatchSize()).thenReturn(200);

    // Create list with 500 elements
    List<String> planExecutionIds = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
      planExecutionIds.add("planExecId" + i);
    }

    publisher.publishCleanupEvent(TEST_ACCOUNT_ID, planExecutionIds, RETENTION_PERIOD_MONTHS, Instant.now());

    // With batch size 200, should be split into 3 batches: 200 + 200 + 100
    verify(eventProducer, times(3)).send(any(Message.class));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishCleanupEvent_BatchSizeBelowMinimum() {
    // Set batch size below minimum (100), should be clamped to 100
    when(dataRetentionConfig.getCleanupEventBatchSize()).thenReturn(50);

    List<String> planExecutionIds = new ArrayList<>();
    for (int i = 0; i < 250; i++) {
      planExecutionIds.add("planExecId" + i);
    }

    publisher.publishCleanupEvent(TEST_ACCOUNT_ID, planExecutionIds, RETENTION_PERIOD_MONTHS, Instant.now());

    // With minimum batch size 100, should be 3 batches: 100 + 100 + 50
    verify(eventProducer, times(3)).send(any(Message.class));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishCleanupEvent_BatchSizeAboveMaximum() {
    // Set batch size above maximum (1000), should be clamped to 1000
    when(dataRetentionConfig.getCleanupEventBatchSize()).thenReturn(2000);

    List<String> planExecutionIds = new ArrayList<>();
    for (int i = 0; i < 2500; i++) {
      planExecutionIds.add("planExecId" + i);
    }

    publisher.publishCleanupEvent(TEST_ACCOUNT_ID, planExecutionIds, RETENTION_PERIOD_MONTHS, Instant.now());

    // With max batch size 1000, should be 3 batches: 1000 + 1000 + 500
    verify(eventProducer, times(3)).send(any(Message.class));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishCleanupEvent_ExactBatchSize() {
    // Create list with exactly default batch size (500) elements
    List<String> planExecutionIds = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
      planExecutionIds.add("planExecId" + i);
    }

    publisher.publishCleanupEvent(TEST_ACCOUNT_ID, planExecutionIds, RETENTION_PERIOD_MONTHS, Instant.now());

    // Should be sent in exactly 1 batch
    verify(eventProducer, times(1)).send(any(Message.class));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishCleanupEvent_EventsFrameworkDown() {
    when(eventProducer.send(any(Message.class)))
        .thenThrow(new EventsFrameworkDownException("Events framework is down"));

    // Should not throw exception - cleanup should continue
    publisher.publishCleanupEvent(
        TEST_ACCOUNT_ID, Collections.singletonList(PLAN_EXECUTION_ID_1), RETENTION_PERIOD_MONTHS, Instant.now());

    verify(eventProducer, times(1)).send(any(Message.class));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishCleanupEvent_EventsFrameworkDownWithBatching() {
    // Create list with more than default batch size
    List<String> planExecutionIds = new ArrayList<>();
    for (int i = 0; i < 600; i++) {
      planExecutionIds.add("planExecId" + i);
    }

    // First batch succeeds, second fails
    when(eventProducer.send(any(Message.class)))
        .thenReturn("messageId1")
        .thenThrow(new EventsFrameworkDownException("Events framework is down"));

    // Should not throw exception
    publisher.publishCleanupEvent(TEST_ACCOUNT_ID, planExecutionIds, RETENTION_PERIOD_MONTHS, Instant.now());

    // Should have tried to send 2 batches
    verify(eventProducer, times(2)).send(any(Message.class));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishCleanupEvent_VerifyEventContent() {
    Instant cleanupTimestamp = Instant.ofEpochMilli(1704067200000L); // Fixed timestamp for verification
    List<String> planExecutionIds = Arrays.asList(PLAN_EXECUTION_ID_1, PLAN_EXECUTION_ID_2);

    publisher.publishCleanupEvent(TEST_ACCOUNT_ID, planExecutionIds, RETENTION_PERIOD_MONTHS, cleanupTimestamp);

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer).send(messageCaptor.capture());

    Message sentMessage = messageCaptor.getValue();

    try {
      ExecutionRetentionCleanupEvent event = ExecutionRetentionCleanupEvent.parseFrom(sentMessage.getData());

      assert event.getAccountIdentifier().equals(TEST_ACCOUNT_ID);
      assert event.getPlanExecutionIdsList().containsAll(planExecutionIds);
      assert event.getRetentionPeriodInMonths() == RETENTION_PERIOD_MONTHS;
      assert event.getCleanupTimestampMillis() == 1704067200000L;
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse event", e);
    }
  }
}
