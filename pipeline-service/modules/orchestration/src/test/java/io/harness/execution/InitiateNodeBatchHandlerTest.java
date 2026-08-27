/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.concurrency.MaxConcurrentChildCallback;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.helpers.InitiateNodeHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.InitiateNodeBatchEvent;
import io.harness.pms.contracts.execution.events.InitiateNodeBatchEvent.Child;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class InitiateNodeBatchHandlerTest extends OrchestrationTestBase {
  @Mock private OrchestrationEngine engine;
  @Mock private BlockExecutionMetadataService blockExecutionMetadataService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private InitiateNodeHelper initiateNodeHelper;

  @InjectMocks private InitiateNodeBatchHandler initiateNodeBatchHandler;

  private static final String NODE_EXECUTION_ID = "nodeExecutionId";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";
  private static final String SETUP_ID = "setupId";
  private static final String RUNTIME_ID = "runtimeId";
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  private Ambiance createTestAmbiance() {
    return Ambiance.newBuilder()
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .addLevels(Level.newBuilder()
                       .setRuntimeId(NODE_EXECUTION_ID)
                       .setSetupId(SETUP_ID)
                       .setStepType(StepType.newBuilder().setType("TEST").setStepCategory(StepCategory.STEP).build())
                       .build())
        .build();
  }

  private InitiateNodeBatchEvent createTestEvent(InitiateMode initiateMode, boolean registerCallback) {
    Child child = Child.newBuilder().setRuntimeId(RUNTIME_ID).setSetupId(SETUP_ID).build();
    return InitiateNodeBatchEvent.newBuilder()
        .setAmbiance(createTestAmbiance())
        .addChildren(child)
        .setInitiateMode(initiateMode)
        .setShouldRegisterCallback(registerCallback)
        .setMaxConcurrency(1)
        .setShouldProceedIfFailed(true)
        .build();
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void testHandleEventWithContext_CreateMode() {
    InitiateNodeBatchEvent event = createTestEvent(InitiateMode.CREATE, false);
    when(blockExecutionMetadataService.validate(any())).thenReturn(false);

    initiateNodeBatchHandler.handleEventWithContext(event);

    ArgumentCaptor<InitiateNodeBatchRequest> requestCaptor = ArgumentCaptor.forClass(InitiateNodeBatchRequest.class);
    verify(engine, times(1)).initiateNodes(requestCaptor.capture(), eq(InitiateMode.CREATE));

    InitiateNodeBatchRequest request = requestCaptor.getValue();
    assertThat(request.getChildCount()).isEqualTo(1);
    assertThat(request.getNodes().get(0).getRuntimeId()).isEqualTo(RUNTIME_ID);
    assertThat(request.getNodes().get(0).getSetupId()).isEqualTo(SETUP_ID);
    assertThat(request.getNodes().get(0).getStrategyMetadata()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void testHandleEventWithContext_StrategyMetadataPresence() {
    StrategyMetadata strategyMetadata =
        StrategyMetadata.newBuilder().setCurrentIteration(1).setTotalIterations(3).build();
    Child child1 =
        Child.newBuilder().setRuntimeId(RUNTIME_ID).setSetupId(SETUP_ID).setStrategyMetadata(strategyMetadata).build();

    Child child2 = Child.newBuilder().setRuntimeId("runtimeId2").setSetupId("setupId2").build();
    InitiateNodeBatchEvent event = InitiateNodeBatchEvent.newBuilder()
                                       .setAmbiance(createTestAmbiance())
                                       .addChildren(child1)
                                       .addChildren(child2)
                                       .setInitiateMode(InitiateMode.CREATE)
                                       .setShouldRegisterCallback(false)
                                       .build();
    when(blockExecutionMetadataService.validate(any())).thenReturn(false);

    initiateNodeBatchHandler.handleEventWithContext(event);

    ArgumentCaptor<InitiateNodeBatchRequest> requestCaptor = ArgumentCaptor.forClass(InitiateNodeBatchRequest.class);
    verify(engine, times(1)).initiateNodes(requestCaptor.capture(), eq(InitiateMode.CREATE));

    InitiateNodeBatchRequest request = requestCaptor.getValue();
    assertThat(request.getChildCount()).isEqualTo(2);
    assertThat(request.getNodes().get(0).getRuntimeId()).isEqualTo(RUNTIME_ID);
    assertThat(request.getNodes().get(0).getSetupId()).isEqualTo(SETUP_ID);
    assertThat(request.getNodes().get(0).getStrategyMetadata()).isEqualTo(strategyMetadata);

    assertThat(request.getNodes().get(1).getRuntimeId()).isEqualTo("runtimeId2");
    assertThat(request.getNodes().get(1).getSetupId()).isEqualTo("setupId2");
    assertThat(request.getNodes().get(1).getStrategyMetadata()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void testHandleEventWithContext_CreateAndStartMode() {
    InitiateNodeBatchEvent event = createTestEvent(InitiateMode.CREATE_AND_START, false);
    when(blockExecutionMetadataService.validate(any())).thenReturn(false);

    NodeExecution nodeExecution =
        NodeExecution.builder().uuid("nodeExecutionUuid").ambiance(createTestAmbiance()).status(Status.QUEUED).build();
    when(engine.initiateNodes(any(InitiateNodeBatchRequest.class), eq(InitiateMode.CREATE)))
        .thenReturn(ImmutableList.of(nodeExecution));
    when(nodeExecutionService.getAmbiance(any())).thenReturn(createTestAmbiance());

    initiateNodeBatchHandler.handleEventWithContext(event);

    verify(engine, times(1)).initiateNodes(any(InitiateNodeBatchRequest.class), eq(InitiateMode.CREATE));
    verify(initiateNodeHelper, times(1)).publishEvent(any(), eq(InitiateMode.START));
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void testHandleEventWithContext_WithCallbackRegistration() {
    InitiateNodeBatchEvent event = createTestEvent(InitiateMode.CREATE, true);
    when(blockExecutionMetadataService.validate(any())).thenReturn(false);

    initiateNodeBatchHandler.handleEventWithContext(event);

    ArgumentCaptor<MaxConcurrentChildCallback> callbackCaptor =
        ArgumentCaptor.forClass(MaxConcurrentChildCallback.class);
    verify(waitNotifyEngine, times(1)).waitForAllOn(any(), callbackCaptor.capture(), eq(RUNTIME_ID));

    MaxConcurrentChildCallback callback = callbackCaptor.getValue();
    assertThat(callback.getParentNodeExecutionId()).isEqualTo(NODE_EXECUTION_ID);
    assertThat(callback.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(callback.getMaxConcurrency()).isEqualTo(1);
    assertThat(callback.getProceedIfFailed()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void testHandleEventWithContext_BlockExecutionValidation() {
    InitiateNodeBatchEvent event = createTestEvent(InitiateMode.CREATE, false);
    when(blockExecutionMetadataService.validate(any())).thenReturn(true);

    initiateNodeBatchHandler.handleEventWithContext(event);

    verify(engine, times(0)).initiateNodes(any(InitiateNodeBatchRequest.class), any());
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void testHandleEventWithContext_EmptyChildren() {
    InitiateNodeBatchEvent event = InitiateNodeBatchEvent.newBuilder()
                                       .setAmbiance(createTestAmbiance())
                                       .setInitiateMode(InitiateMode.CREATE)
                                       .build();
    when(blockExecutionMetadataService.validate(any())).thenReturn(false);

    initiateNodeBatchHandler.handleEventWithContext(event);

    verify(engine, times(0)).initiateNodes(any(InitiateNodeBatchRequest.class), any());
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void testHandleEventWithContext_InvalidInitiateMode() {
    InitiateNodeBatchEvent event = createTestEvent(InitiateMode.UNKNOWN_MODE, false);
    when(blockExecutionMetadataService.validate(any())).thenReturn(false);

    assertThatThrownBy(() -> initiateNodeBatchHandler.handleEventWithContext(event))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid mode UNKNOWN_MODE not supported in InitiateNode batch request");
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void testHandleEventWithContext_MultipleChildren() {
    // Arrange: create two children
    Child child1 = Child.newBuilder().setRuntimeId("runtimeId1").setSetupId("setupId1").build();
    Child child2 = Child.newBuilder().setRuntimeId("runtimeId2").setSetupId("setupId2").build();
    InitiateNodeBatchEvent event = InitiateNodeBatchEvent.newBuilder()
                                       .setAmbiance(createTestAmbiance())
                                       .addChildren(child1)
                                       .addChildren(child2)
                                       .setInitiateMode(InitiateMode.CREATE_AND_START)
                                       .setShouldRegisterCallback(false)
                                       .build();
    when(blockExecutionMetadataService.validate(any())).thenReturn(false);

    // Mock two node executions
    NodeExecution nodeExecution1 =
        NodeExecution.builder().uuid("nodeExecutionUuid1").ambiance(createTestAmbiance()).status(Status.QUEUED).build();
    NodeExecution nodeExecution2 =
        NodeExecution.builder().uuid("nodeExecutionUuid2").ambiance(createTestAmbiance()).status(Status.QUEUED).build();
    when(engine.initiateNodes(any(InitiateNodeBatchRequest.class), eq(InitiateMode.CREATE)))
        .thenReturn(ImmutableList.of(nodeExecution1, nodeExecution2));
    when(nodeExecutionService.getAmbiance(any())).thenReturn(createTestAmbiance());

    // Act
    initiateNodeBatchHandler.handleEventWithContext(event);

    // Assert
    ArgumentCaptor<InitiateNodeBatchRequest> requestCaptor = ArgumentCaptor.forClass(InitiateNodeBatchRequest.class);
    verify(engine, times(1)).initiateNodes(requestCaptor.capture(), eq(InitiateMode.CREATE));
    InitiateNodeBatchRequest batchRequest = requestCaptor.getValue();
    assertThat(batchRequest.getNodes()).hasSize(2);
    assertThat(batchRequest.getNodes().get(0).getRuntimeId()).isEqualTo("runtimeId1");
    assertThat(batchRequest.getNodes().get(1).getRuntimeId()).isEqualTo("runtimeId2");
    assertThat(batchRequest.getChildCount()).isEqualTo(2);
    // Verify publishEvent called for both
    verify(initiateNodeHelper, times(2)).publishEvent(any(), eq(InitiateMode.START));
  }
}
