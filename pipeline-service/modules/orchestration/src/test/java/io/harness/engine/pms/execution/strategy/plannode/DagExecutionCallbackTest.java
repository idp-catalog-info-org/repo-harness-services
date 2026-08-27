/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.plannode;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.execution.helpers.InitiateNodeHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class DagExecutionCallbackTest extends CategoryTest {
  @Mock private OrchestrationEngine orchestrationEngine;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private InitiateNodeHelper initiateNodeHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  private String planExecutionId;
  private String targetStageNodeId;
  private String prevNodeId;
  private String parentRuntimeId;
  private Ambiance ambiance;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    planExecutionId = generateUuid();
    targetStageNodeId = generateUuid();
    prevNodeId = generateUuid();
    parentRuntimeId = generateUuid();

    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(planExecutionId)
                   .setPlanId(generateUuid())
                   .addLevels(Level.newBuilder().setRuntimeId("pipeline-level").build())
                   .addLevels(Level.newBuilder().setRuntimeId(parentRuntimeId).build())
                   .addLevels(Level.newBuilder().setRuntimeId("current-runtime").build())
                   .build();
  }

  private DagExecutionCallback buildCallback() {
    DagExecutionCallback callback = DagExecutionCallback.builder()
                                        .ambiance(ambiance)
                                        .prevPlanExecutionId(parentRuntimeId)
                                        .targetStageNodeId(targetStageNodeId)
                                        .prevNodeId(prevNodeId)
                                        .build();
    // Inject mocks manually since @Builder doesn't use @Inject
    callback.setOrchestrationEngine(orchestrationEngine);
    callback.setNodeExecutionService(nodeExecutionService);
    callback.setInitiateNodeHelper(initiateNodeHelper);
    callback.setExecutionSweepingOutputService(executionSweepingOutputService);
    return callback;
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testNotify_NormalFlow_InitiatesNode() {
    DagExecutionCallback callback = buildCallback();

    // No rollback sweeping output found
    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    Map<String, ResponseData> response = new HashMap<>();
    callback.notify(response);

    // Verify initiateNode is called
    verify(orchestrationEngine)
        .initiateNode(eq(ambiance), eq(targetStageNodeId), any(), any(), any(), eq(InitiateMode.CREATE_AND_START));
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testNotify_WithRollbackTriggered_SkipsInitiation() {
    DagExecutionCallback callback = buildCallback();

    // Rollback sweeping output found
    doReturn(OptionalSweepingOutput.builder().found(true).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    Map<String, ResponseData> response = new HashMap<>();
    callback.notify(response);

    // Verify initiateNode is NOT called
    verify(orchestrationEngine, never()).initiateNode(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testNotifyError_JustLogs_NoCallbacksFired() {
    DagExecutionCallback callback = buildCallback();

    Map<String, ResponseData> response = new HashMap<>();
    callback.notifyError(response);

    // notifyError only logs a warning — no initiation should occur
    verify(orchestrationEngine, never()).initiateNode(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testNotify_RollbackModeExecution_SweepingOutputBypassed_InitiatesNode() {
    // When ambiance is already in rollback mode (PIPELINE_ROLLBACK), shouldSkipDueToRollback returns false
    // even if sweeping output exists — the callback should proceed and initiate the node
    Ambiance rollbackAmbiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setPlanId(generateUuid())
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.PIPELINE_ROLLBACK).build())
            .addLevels(Level.newBuilder().setRuntimeId("pipeline-level").build())
            .addLevels(Level.newBuilder().setRuntimeId(parentRuntimeId).build())
            .addLevels(Level.newBuilder().setRuntimeId("current-runtime").build())
            .build();

    DagExecutionCallback callback = DagExecutionCallback.builder()
                                        .ambiance(rollbackAmbiance)
                                        .prevPlanExecutionId(parentRuntimeId)
                                        .targetStageNodeId(targetStageNodeId)
                                        .prevNodeId(prevNodeId)
                                        .build();
    callback.setOrchestrationEngine(orchestrationEngine);
    callback.setNodeExecutionService(nodeExecutionService);
    callback.setInitiateNodeHelper(initiateNodeHelper);
    callback.setExecutionSweepingOutputService(executionSweepingOutputService);

    Map<String, ResponseData> response = new HashMap<>();
    callback.notify(response);

    // isRollbackModeExecution returns true -> shouldSkipDueToRollback returns false -> initiateNode IS called
    verify(orchestrationEngine)
        .initiateNode(
            eq(rollbackAmbiance), eq(targetStageNodeId), any(), any(), any(), eq(InitiateMode.CREATE_AND_START));
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testNotify_SweepingOutputCheckThrowsException_ProceedsWithInitiation() {
    // When sweeping output resolution throws, shouldSkipDueToRollback returns false → initiation proceeds
    DagExecutionCallback callback = buildCallback();

    doThrow(new RuntimeException("sweeping output service unavailable"))
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    Map<String, ResponseData> response = new HashMap<>();
    callback.notify(response);

    // Exception is caught → shouldSkipDueToRollback returns false → initiateNode IS called
    verify(orchestrationEngine)
        .initiateNode(eq(ambiance), eq(targetStageNodeId), any(), any(), any(), eq(InitiateMode.CREATE_AND_START));
  }
}
