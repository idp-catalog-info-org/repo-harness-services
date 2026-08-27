/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.app.beans.dto.CITaskDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.eventsframework.consumer.Message;
import io.harness.observer.Informant;
import io.harness.observer.Informant5;
import io.harness.repositories.CIExecutionRepository;
import io.harness.repositories.CITaskDetailsRepository;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class DelegateTaskEventListenerTest extends CIExecutionTestBase {
  private static final String ACCOUNT_ID = "test-account-id";
  private static final String TASK_ID = "test-task-id";
  private static final String DELEGATE_ID = "test-delegate-id";
  private static final String STAGE_ID = "test-stage-id";
  private static final String TASK_TYPE = "DLITE_CI_VM_INITIALIZE_TASK";
  private static final String OBSERVER_CLASS_NAME_KEY = "observer_class_name";
  private static final String OBSERVER_CLASS_NAME_VALUE = "software.wings.service.impl.CIDelegateTaskObserver";

  @Mock private KryoSerializer kryoSerializer;
  @Mock private CITaskDetailsRepository ciTaskDetailsRepository;
  @Mock private CIExecutionRepository ciExecutionRepository;

  private DelegateTaskEventListener delegateTaskEventListener;

  @Before
  public void setUp() {
    delegateTaskEventListener = new DelegateTaskEventListener(kryoSerializer);
    delegateTaskEventListener.ciTaskDetailsRepository = ciTaskDetailsRepository;
    delegateTaskEventListener.ciExecutionRepository = ciExecutionRepository;
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleMessage_whenMessageHasNoInnerMessage_shouldReturnFalse() {
    Message message = Message.newBuilder().build();

    boolean result = delegateTaskEventListener.handleMessage(message);

    assertThat(result).as("Should return false when message has no inner message").isFalse();
    verifyNoInteractions(kryoSerializer);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleMessage_whenMetadataDoesNotContainObserverKey_shouldReturnFalse() {
    Message message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder().putAllMetadata(new HashMap<>()).build())
            .build();

    boolean result = delegateTaskEventListener.handleMessage(message);

    assertThat(result).as("Should return false when metadata does not contain observer key").isFalse();
    verifyNoInteractions(kryoSerializer);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleMessage_whenObserverClassNameDoesNotMatch_shouldReturnFalse() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(OBSERVER_CLASS_NAME_KEY, "some.other.Observer");

    Message message =
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder().putAllMetadata(metadata).build())
            .build();

    boolean result = delegateTaskEventListener.handleMessage(message);

    assertThat(result).as("Should return false when observer class name does not match").isFalse();
    verifyNoInteractions(kryoSerializer);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleMessage_whenValidMessage_shouldSaveTaskDetailsAndReturnTrue() {
    byte[] accountIdBytes = "accountIdBytes".getBytes();
    byte[] taskIdBytes = "taskIdBytes".getBytes();
    byte[] delegateIdBytes = "delegateIdBytes".getBytes();
    byte[] stageIdBytes = "stageIdBytes".getBytes();
    byte[] taskTypeBytes = "taskTypeBytes".getBytes();

    Informant5 informant5 = Informant5.newBuilder()
                                .setParam1(com.google.protobuf.ByteString.copyFrom(accountIdBytes))
                                .setParam2(com.google.protobuf.ByteString.copyFrom(taskIdBytes))
                                .setParam3(com.google.protobuf.ByteString.copyFrom(delegateIdBytes))
                                .setParam4(com.google.protobuf.ByteString.copyFrom(stageIdBytes))
                                .setParam5(com.google.protobuf.ByteString.copyFrom(taskTypeBytes))
                                .build();
    Informant informant = Informant.newBuilder().setInformant5(informant5).build();

    Map<String, String> metadata = new HashMap<>();
    metadata.put(OBSERVER_CLASS_NAME_KEY, OBSERVER_CLASS_NAME_VALUE);

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(metadata)
                                          .setData(informant.toByteString())
                                          .build())
                          .build();

    when(kryoSerializer.asObject(eq(accountIdBytes))).thenReturn(ACCOUNT_ID);
    when(kryoSerializer.asObject(eq(taskIdBytes))).thenReturn(TASK_ID);
    when(kryoSerializer.asObject(eq(delegateIdBytes))).thenReturn(DELEGATE_ID);
    when(kryoSerializer.asObject(eq(stageIdBytes))).thenReturn(STAGE_ID);
    when(kryoSerializer.asObject(eq(taskTypeBytes))).thenReturn(TASK_TYPE);
    when(ciExecutionRepository.findByStageExecutionId(eq(STAGE_ID))).thenReturn(CIExecutionMetadata.builder().build());

    boolean result = delegateTaskEventListener.handleMessage(message);

    assertThat(result).as("Should return true when valid message is processed and saved").isTrue();
    ArgumentCaptor<CITaskDetails> captor = ArgumentCaptor.forClass(CITaskDetails.class);
    verify(ciTaskDetailsRepository).save(captor.capture());
    CITaskDetails savedDetails = captor.getValue();
    assertThat(savedDetails.getStageExecutionId()).as("Stage execution ID should match").isEqualTo(STAGE_ID);
    assertThat(savedDetails.getDelegateId()).as("Delegate ID should match").isEqualTo(DELEGATE_ID);
    assertThat(savedDetails.getTaskId()).as("Task ID should match").isEqualTo(TASK_ID);
    assertThat(savedDetails.getTaskType()).as("Task type should match").isEqualTo(TASK_TYPE);
    assertThat(savedDetails.getAccountId()).as("Account ID should match").isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleMessage_whenStageIdIsEmpty_shouldReturnFalse() {
    byte[] accountIdBytes = "accountIdBytes".getBytes();
    byte[] taskIdBytes = "taskIdBytes".getBytes();
    byte[] delegateIdBytes = "delegateIdBytes".getBytes();
    byte[] stageIdBytes = "stageIdBytes".getBytes();
    byte[] taskTypeBytes = "taskTypeBytes".getBytes();

    Informant5 informant5 = Informant5.newBuilder()
                                .setParam1(com.google.protobuf.ByteString.copyFrom(accountIdBytes))
                                .setParam2(com.google.protobuf.ByteString.copyFrom(taskIdBytes))
                                .setParam3(com.google.protobuf.ByteString.copyFrom(delegateIdBytes))
                                .setParam4(com.google.protobuf.ByteString.copyFrom(stageIdBytes))
                                .setParam5(com.google.protobuf.ByteString.copyFrom(taskTypeBytes))
                                .build();
    Informant informant = Informant.newBuilder().setInformant5(informant5).build();

    Map<String, String> metadata = new HashMap<>();
    metadata.put(OBSERVER_CLASS_NAME_KEY, OBSERVER_CLASS_NAME_VALUE);

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(metadata)
                                          .setData(informant.toByteString())
                                          .build())
                          .build();

    when(kryoSerializer.asObject(eq(accountIdBytes))).thenReturn(ACCOUNT_ID);
    when(kryoSerializer.asObject(eq(taskIdBytes))).thenReturn(TASK_ID);
    when(kryoSerializer.asObject(eq(delegateIdBytes))).thenReturn(DELEGATE_ID);
    when(kryoSerializer.asObject(eq(stageIdBytes))).thenReturn("");
    when(kryoSerializer.asObject(eq(taskTypeBytes))).thenReturn(TASK_TYPE);

    boolean result = delegateTaskEventListener.handleMessage(message);

    assertThat(result).as("Should return false when stageId is empty").isFalse();
    verifyNoInteractions(ciTaskDetailsRepository);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleMessage_whenTaskTypeDoesNotMatch_shouldReturnFalse() {
    byte[] accountIdBytes = "accountIdBytes".getBytes();
    byte[] taskIdBytes = "taskIdBytes".getBytes();
    byte[] delegateIdBytes = "delegateIdBytes".getBytes();
    byte[] stageIdBytes = "stageIdBytes".getBytes();
    byte[] taskTypeBytes = "taskTypeBytes".getBytes();

    Informant5 informant5 = Informant5.newBuilder()
                                .setParam1(com.google.protobuf.ByteString.copyFrom(accountIdBytes))
                                .setParam2(com.google.protobuf.ByteString.copyFrom(taskIdBytes))
                                .setParam3(com.google.protobuf.ByteString.copyFrom(delegateIdBytes))
                                .setParam4(com.google.protobuf.ByteString.copyFrom(stageIdBytes))
                                .setParam5(com.google.protobuf.ByteString.copyFrom(taskTypeBytes))
                                .build();
    Informant informant = Informant.newBuilder().setInformant5(informant5).build();

    Map<String, String> metadata = new HashMap<>();
    metadata.put(OBSERVER_CLASS_NAME_KEY, OBSERVER_CLASS_NAME_VALUE);

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(metadata)
                                          .setData(informant.toByteString())
                                          .build())
                          .build();

    when(kryoSerializer.asObject(eq(accountIdBytes))).thenReturn(ACCOUNT_ID);
    when(kryoSerializer.asObject(eq(taskIdBytes))).thenReturn(TASK_ID);
    when(kryoSerializer.asObject(eq(delegateIdBytes))).thenReturn(DELEGATE_ID);
    when(kryoSerializer.asObject(eq(stageIdBytes))).thenReturn(STAGE_ID);
    when(kryoSerializer.asObject(eq(taskTypeBytes))).thenReturn("SOME_OTHER_TASK_TYPE");

    boolean result = delegateTaskEventListener.handleMessage(message);

    assertThat(result).as("Should return false when task type does not match").isFalse();
    verifyNoInteractions(ciTaskDetailsRepository);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleMessage_whenExceptionOccurs_shouldReturnFalse() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(OBSERVER_CLASS_NAME_KEY, OBSERVER_CLASS_NAME_VALUE);

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(metadata)
                                          .setData(com.google.protobuf.ByteString.copyFrom("invalid-data".getBytes()))
                                          .build())
                          .build();

    boolean result = delegateTaskEventListener.handleMessage(message);

    assertThat(result).as("Should return false when exception occurs during processing").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleMessage_whenStageIdUnknown_shouldSkipSaveAndReturnTrue() {
    byte[] accountIdBytes = "accountIdBytes".getBytes();
    byte[] taskIdBytes = "taskIdBytes".getBytes();
    byte[] delegateIdBytes = "delegateIdBytes".getBytes();
    byte[] stageIdBytes = "stageIdBytes".getBytes();
    byte[] taskTypeBytes = "taskTypeBytes".getBytes();

    Informant5 informant5 = Informant5.newBuilder()
                                .setParam1(com.google.protobuf.ByteString.copyFrom(accountIdBytes))
                                .setParam2(com.google.protobuf.ByteString.copyFrom(taskIdBytes))
                                .setParam3(com.google.protobuf.ByteString.copyFrom(delegateIdBytes))
                                .setParam4(com.google.protobuf.ByteString.copyFrom(stageIdBytes))
                                .setParam5(com.google.protobuf.ByteString.copyFrom(taskTypeBytes))
                                .build();
    Informant informant = Informant.newBuilder().setInformant5(informant5).build();

    Map<String, String> metadata = new HashMap<>();
    metadata.put(OBSERVER_CLASS_NAME_KEY, OBSERVER_CLASS_NAME_VALUE);

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(metadata)
                                          .setData(informant.toByteString())
                                          .build())
                          .build();

    when(kryoSerializer.asObject(eq(accountIdBytes))).thenReturn(ACCOUNT_ID);
    when(kryoSerializer.asObject(eq(taskIdBytes))).thenReturn(TASK_ID);
    when(kryoSerializer.asObject(eq(delegateIdBytes))).thenReturn(DELEGATE_ID);
    when(kryoSerializer.asObject(eq(stageIdBytes))).thenReturn(STAGE_ID);
    when(kryoSerializer.asObject(eq(taskTypeBytes))).thenReturn(TASK_TYPE);
    when(ciExecutionRepository.findByStageExecutionId(eq(STAGE_ID))).thenReturn(null);

    boolean result = delegateTaskEventListener.handleMessage(message);

    assertThat(result).as("Should return true when stageId is unknown and skip saving").isTrue();
    verifyNoInteractions(ciTaskDetailsRepository);
  }
}
