/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.listener;

import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.executionretention.ExecutionRetentionCleanupEvent;
import io.harness.rule.Owner;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.tables.records.PipelineExecutionSummaryCdRecord;

import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.Collections;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.DeleteConditionStep;
import org.jooq.DeleteUsingStep;
import org.jooq.exception.DataAccessException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ExecutionRetentionCleanupListenerTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "testAccountId";
  private static final String PLAN_EXECUTION_ID_1 = "planExecId1";
  private static final String PLAN_EXECUTION_ID_2 = "planExecId2";
  private static final String PLAN_EXECUTION_ID_3 = "planExecId3";

  @Mock private DSLContext dsl;
  @Mock private DeleteUsingStep<PipelineExecutionSummaryCdRecord> deleteUsingStep;
  @Mock private DeleteConditionStep<PipelineExecutionSummaryCdRecord> deleteConditionStep;

  private ExecutionRetentionCleanupListener listener;
  private AutoCloseable mocks;

  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    listener = new ExecutionRetentionCleanupListener(dsl);

    // Setup JOOQ mock chain
    when(dsl.delete(Tables.PIPELINE_EXECUTION_SUMMARY_CD)).thenReturn(deleteUsingStep);
    when(deleteUsingStep.where(any(Condition.class))).thenReturn(deleteConditionStep);
    when(deleteConditionStep.execute()).thenReturn(1);
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
  public void testHandleMessage_NullMessage() {
    boolean result = listener.handleMessage(null);
    assertTrue("Should return true for null message", result);
    verify(dsl, never()).delete(any());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testHandleMessage_MessageWithoutInnerMessage() {
    Message message =
        Message.newBuilder().setId("testId").setTimestamp(com.google.protobuf.Timestamp.newBuilder().build()).build();

    boolean result = listener.handleMessage(message);
    assertTrue("Should return true for message without inner message", result);
    verify(dsl, never()).delete(any());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testHandleMessage_InvalidProtobufData() {
    Message message = Message.newBuilder()
                          .setId("testId")
                          .setTimestamp(com.google.protobuf.Timestamp.newBuilder().build())
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .setData(ByteString.copyFromUtf8("invalid data"))
                                          .build())
                          .build();

    boolean result = listener.handleMessage(message);
    assertFalse("Should return false for invalid protobuf data", result);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testHandleMessage_EmptyPlanExecutionIds() {
    ExecutionRetentionCleanupEvent event = ExecutionRetentionCleanupEvent.newBuilder()
                                               .setAccountIdentifier(TEST_ACCOUNT_ID)
                                               .setRetentionPeriodInMonths(6)
                                               .setCleanupTimestampMillis(System.currentTimeMillis())
                                               .build();

    Message message = createMessage(event);

    boolean result = listener.handleMessage(message);
    assertTrue("Should return true for empty planExecutionIds", result);
    verify(dsl, never()).delete(any());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testHandleMessage_SuccessfulCleanup() {
    ExecutionRetentionCleanupEvent event =
        ExecutionRetentionCleanupEvent.newBuilder()
            .setAccountIdentifier(TEST_ACCOUNT_ID)
            .addAllPlanExecutionIds(Arrays.asList(PLAN_EXECUTION_ID_1, PLAN_EXECUTION_ID_2, PLAN_EXECUTION_ID_3))
            .setRetentionPeriodInMonths(6)
            .setCleanupTimestampMillis(System.currentTimeMillis())
            .build();

    Message message = createMessage(event);

    boolean result = listener.handleMessage(message);

    assertTrue("Should return true for successful cleanup", result);
    verify(dsl, times(1)).delete(Tables.PIPELINE_EXECUTION_SUMMARY_CD);
    verify(deleteUsingStep, times(1)).where(any(Condition.class));
    verify(deleteConditionStep, times(1)).execute();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testHandleMessage_DataAccessExceptionAfterMaxRetries() {
    when(deleteConditionStep.execute()).thenThrow(new DataAccessException("Database error"));

    ExecutionRetentionCleanupEvent event = ExecutionRetentionCleanupEvent.newBuilder()
                                               .setAccountIdentifier(TEST_ACCOUNT_ID)
                                               .addAllPlanExecutionIds(Collections.singletonList(PLAN_EXECUTION_ID_1))
                                               .setRetentionPeriodInMonths(6)
                                               .setCleanupTimestampMillis(System.currentTimeMillis())
                                               .build();

    Message message = createMessage(event);

    boolean result = listener.handleMessage(message);

    assertFalse("Should return false after max retries exceeded", result);
    // Should have retried 3 times
    verify(deleteConditionStep, times(3)).execute();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testHandleMessage_DataAccessExceptionRetrySuccess() {
    // First two calls fail, third succeeds
    when(deleteConditionStep.execute())
        .thenThrow(new DataAccessException("Database error"))
        .thenThrow(new DataAccessException("Database error"))
        .thenReturn(1);

    ExecutionRetentionCleanupEvent event = ExecutionRetentionCleanupEvent.newBuilder()
                                               .setAccountIdentifier(TEST_ACCOUNT_ID)
                                               .addAllPlanExecutionIds(Collections.singletonList(PLAN_EXECUTION_ID_1))
                                               .setRetentionPeriodInMonths(6)
                                               .setCleanupTimestampMillis(System.currentTimeMillis())
                                               .build();

    Message message = createMessage(event);

    boolean result = listener.handleMessage(message);

    assertTrue("Should return true when retry succeeds", result);
    verify(deleteConditionStep, times(3)).execute();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testHandleMessage_MultiplePlanExecutionIds() {
    ExecutionRetentionCleanupEvent event =
        ExecutionRetentionCleanupEvent.newBuilder()
            .setAccountIdentifier(TEST_ACCOUNT_ID)
            .addAllPlanExecutionIds(Arrays.asList(PLAN_EXECUTION_ID_1, PLAN_EXECUTION_ID_2, PLAN_EXECUTION_ID_3))
            .setRetentionPeriodInMonths(12)
            .setCleanupTimestampMillis(System.currentTimeMillis())
            .build();

    when(deleteConditionStep.execute()).thenReturn(3);

    Message message = createMessage(event);

    boolean result = listener.handleMessage(message);

    assertTrue("Should return true for successful cleanup", result);
    verify(dsl, times(1)).delete(Tables.PIPELINE_EXECUTION_SUMMARY_CD);
    verify(deleteConditionStep, times(1)).execute();
  }

  private Message createMessage(ExecutionRetentionCleanupEvent event) {
    return Message.newBuilder()
        .setId("testMessageId")
        .setTimestamp(com.google.protobuf.Timestamp.newBuilder().build())
        .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                        .setData(event.toByteString())
                        .putMetadata("accountId", TEST_ACCOUNT_ID)
                        .build())
        .build();
  }
}
