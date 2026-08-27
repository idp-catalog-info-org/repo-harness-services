/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.barriers.event;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.YUVRAJ;
import static io.harness.steps.barriers.service.BarrierService.BARRIER_UPDATE_LOCK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.steps.approval.stage.ApprovalStageStep;
import io.harness.steps.barriers.BarrierSpecParameters;
import io.harness.steps.barriers.BarrierStep;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierPositionInfo;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition.BarrierPositionType;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.utils.PmsFeatureFlagHelper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierEventHandlerTest extends OrchestrationStepsTestBase {
  @Mock BarrierService barrierService;
  @Mock PersistentLocker persistentLocker;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @InjectMocks BarrierEventHandler barrierEventHandler;

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdateBarrier() {
    String accountId = "accountId";
    String executionId = "executionId";
    String planExecutionId = "planExecutionId";
    String barrierRef = "barrierRef";
    BarrierExecutionInstance barrierExecutionInstance = BarrierExecutionInstance.builder().build();
    when(nodeExecutionService.getAmbiance(any(NodeExecution.class)))
        .thenReturn(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build());
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(NodeExecution.builder()
                               .uuid(executionId)
                               .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                               .status(Status.ASYNC_WAITING)
                               .build())
            .build();
    when(barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, "setupId"))
        .thenReturn(List.of(barrierExecutionInstance));
    doNothing()
        .when(barrierService)
        .updatePosition(BarrierPositionType.STEP, "setupId", executionId, "stageId", "stepGroupId",
            List.of(barrierExecutionInstance), true, false);
    when(barrierService.findByIdentifierAndPlanExecutionId(barrierRef, planExecutionId))
        .thenReturn(barrierExecutionInstance);
    try (MockedStatic<AmbianceUtils> mockAmbianceUtils = mockStatic(AmbianceUtils.class, RETURNS_MOCKS);
         MockedStatic<RecastOrchestrationUtils> mockRecastOrchestrationUtils =
             mockStatic(RecastOrchestrationUtils.class, RETURNS_MOCKS)) {
      when(AmbianceUtils.getAccountId(any())).thenReturn(accountId);
      when(AmbianceUtils.obtainCurrentLevel(any()))
          .thenReturn(Level.newBuilder().setGroup(BarrierPositionType.STEP.name()).setSetupId("setupId").build());
      when(AmbianceUtils.getStageLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("stageId").build()));
      when(AmbianceUtils.getStepGroupLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("stepGroupId").build()));
      when(AmbianceUtils.getCurrentStepType(any())).thenReturn(BarrierStep.STEP_TYPE);
      when(RecastOrchestrationUtils.fromMap(any(), any()))
          .thenReturn(StepElementParameters.builder()
                          .spec(BarrierSpecParameters.builder().barrierRef(barrierRef).build())
                          .build());
      barrierEventHandler.onNodeStatusUpdate(nodeUpdateInfo);
      verify(persistentLocker, times(0))
          .waitToAcquireLock(BARRIER_UPDATE_LOCK + planExecutionId, Duration.ofSeconds(20), Duration.ofSeconds(60));
      verify(barrierService, times(1))
          .updatePosition(BarrierPositionType.STEP, "setupId", executionId, "stageId", "stepGroupId",
              List.of(barrierExecutionInstance), true, false);
    }
    verify(barrierService, times(1)).findByIdentifierAndPlanExecutionId(barrierRef, planExecutionId);
    verify(barrierService, times(1)).update(barrierExecutionInstance);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdateBarrierWithFeatureFlagEnabled() {
    String accountId = "accountId";
    String executionId = "executionId";
    String planExecutionId = "planExecutionId";
    String barrierRef = "barrierRef";
    BarrierExecutionInstance barrierExecutionInstance = BarrierExecutionInstance.builder().build();
    when(nodeExecutionService.getAmbiance(any(NodeExecution.class)))
        .thenReturn(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build());
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(NodeExecution.builder()
                               .uuid(executionId)
                               .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                               .status(Status.ASYNC_WAITING)
                               .build())
            .build();
    when(barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, "setupId"))
        .thenReturn(List.of(barrierExecutionInstance));
    doNothing()
        .when(barrierService)
        .updatePosition(BarrierPositionType.STEP, "setupId", executionId, "stageId", "stepGroupId",
            List.of(barrierExecutionInstance), true, false);
    when(barrierService.findByIdentifierAndPlanExecutionId(barrierRef, planExecutionId))
        .thenReturn(barrierExecutionInstance);
    try (MockedStatic<AmbianceUtils> mockAmbianceUtils = mockStatic(AmbianceUtils.class, RETURNS_MOCKS);
         MockedStatic<RecastOrchestrationUtils> mockRecastOrchestrationUtils =
             mockStatic(RecastOrchestrationUtils.class, RETURNS_MOCKS)) {
      when(AmbianceUtils.getAccountId(any())).thenReturn(accountId);
      when(AmbianceUtils.obtainCurrentLevel(any()))
          .thenReturn(Level.newBuilder().setGroup(BarrierPositionType.STEP.name()).setSetupId("setupId").build());
      when(AmbianceUtils.getStageLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("stageId").build()));
      when(AmbianceUtils.getStepGroupLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("stepGroupId").build()));
      when(AmbianceUtils.getCurrentStepType(any())).thenReturn(BarrierStep.STEP_TYPE);
      when(RecastOrchestrationUtils.fromMap(any(), any()))
          .thenReturn(StepElementParameters.builder()
                          .spec(BarrierSpecParameters.builder().barrierRef(barrierRef).build())
                          .build());
      barrierEventHandler.onNodeStatusUpdate(nodeUpdateInfo);
      verify(persistentLocker, times(0))
          .waitToAcquireLock(BARRIER_UPDATE_LOCK + planExecutionId, Duration.ofSeconds(20), Duration.ofSeconds(60));
      verify(barrierService, times(1))
          .updatePosition(BarrierPositionType.STEP, "setupId", executionId, "stageId", "stepGroupId",
              List.of(barrierExecutionInstance), true, false);
    }
    verify(barrierService, times(1)).findByIdentifierAndPlanExecutionId(barrierRef, planExecutionId);
    verify(barrierService, times(1)).update(barrierExecutionInstance);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdateBarrierWithFeatureFlagEnabledStageLevel() {
    String accountId = "accountId";
    String executionId = "executionId";
    String planExecutionId = "planExecutionId";
    BarrierExecutionInstance barrierExecutionInstance = BarrierExecutionInstance.builder().build();
    when(nodeExecutionService.getAmbiance(any(NodeExecution.class)))
        .thenReturn(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build());
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(NodeExecution.builder()
                               .uuid(executionId)
                               .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                               .status(Status.ASYNC_WAITING)
                               .build())
            .build();
    when(barrierService.findByPosition(planExecutionId, BarrierPositionType.STAGE, "setupId"))
        .thenReturn(List.of(barrierExecutionInstance));
    try (MockedStatic<AmbianceUtils> mockAmbianceUtils = mockStatic(AmbianceUtils.class, RETURNS_MOCKS)) {
      when(AmbianceUtils.getAccountId(any())).thenReturn(accountId);
      when(AmbianceUtils.obtainCurrentLevel(any()))
          .thenReturn(Level.newBuilder().setGroup(BarrierPositionType.STAGE.name()).setSetupId("setupId").build());
      when(AmbianceUtils.getStageLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("stageId").build()));
      when(AmbianceUtils.getStepGroupLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("stepGroupId").build()));
      when(AmbianceUtils.getCurrentStepType(any())).thenReturn(ApprovalStageStep.STEP_TYPE);
      barrierEventHandler.onNodeStatusUpdate(nodeUpdateInfo);
      verify(persistentLocker, times(0))
          .waitToAcquireLock(BARRIER_UPDATE_LOCK + planExecutionId, Duration.ofSeconds(20), Duration.ofSeconds(60));
      verify(barrierService, times(0))
          .updatePosition(any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
    }
    verify(barrierService, times(0)).findByIdentifierAndPlanExecutionId(any(), any());
    verify(barrierService, times(0)).update(any());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdateBarrierWithStageStrategyWithFeatureFlagEnabled() {
    String accountId = "accountId";
    String executionId = "executionId";
    String planExecutionId = "planExecutionId";
    String barrierRef = "barrierRef";
    BarrierExecutionInstance barrierExecutionInstance = BarrierExecutionInstance.builder().build();
    when(nodeExecutionService.getAmbiance(any(NodeExecution.class)))
        .thenReturn(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build());
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(NodeExecution.builder()
                               .uuid(executionId)
                               .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                               .status(Status.ASYNC_WAITING)
                               .build())
            .build();
    when(barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, "setupId"))
        .thenReturn(List.of(barrierExecutionInstance));
    doNothing()
        .when(barrierService)
        .updatePosition(BarrierPositionType.STEP, "setupId", executionId, "stageId", "stepGroupId",
            List.of(barrierExecutionInstance), true, false);
    when(barrierService.findByIdentifierAndPlanExecutionId(barrierRef, planExecutionId))
        .thenReturn(barrierExecutionInstance);
    try (MockedStatic<AmbianceUtils> mockAmbianceUtils = mockStatic(AmbianceUtils.class, RETURNS_MOCKS);
         MockedStatic<RecastOrchestrationUtils> mockRecastOrchestrationUtils =
             mockStatic(RecastOrchestrationUtils.class, RETURNS_MOCKS)) {
      when(AmbianceUtils.getAccountId(any())).thenReturn(accountId);
      when(AmbianceUtils.obtainCurrentLevel(any()))
          .thenReturn(Level.newBuilder().setGroup(BarrierPositionType.STEP.name()).setSetupId("setupId").build());
      when(AmbianceUtils.getStageLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("stageId").build()));
      when(AmbianceUtils.getStrategyLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("strategyId").build()));
      when(AmbianceUtils.getStepGroupLevelFromAmbiance(any())).thenReturn(Optional.empty());
      when(AmbianceUtils.getCurrentStepType(any())).thenReturn(BarrierStep.STEP_TYPE);
      when(RecastOrchestrationUtils.fromMap(any(), any()))
          .thenReturn(StepElementParameters.builder()
                          .spec(BarrierSpecParameters.builder().barrierRef(barrierRef).build())
                          .build());
      barrierEventHandler.onNodeStatusUpdate(nodeUpdateInfo);
      verify(persistentLocker, times(0))
          .waitToAcquireLock(BARRIER_UPDATE_LOCK + planExecutionId, Duration.ofSeconds(20), Duration.ofSeconds(60));
      verify(barrierService, times(1))
          .updatePosition(BarrierPositionType.STEP, "setupId", executionId, "stageId", null,
              List.of(barrierExecutionInstance), true, false);
    }
    verify(barrierService, times(1)).findByIdentifierAndPlanExecutionId(barrierRef, planExecutionId);
    verify(barrierService, times(1)).update(barrierExecutionInstance);
  }
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdateBarrierWithStepGroupStrategyWithFeatureFlagEnabled() {
    String accountId = "accountId";
    String executionId = "executionId";
    String planExecutionId = "planExecutionId";
    String barrierRef = "barrierRef";
    BarrierExecutionInstance barrierExecutionInstance = BarrierExecutionInstance.builder().build();
    when(nodeExecutionService.getAmbiance(any(NodeExecution.class)))
        .thenReturn(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build());
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(NodeExecution.builder()
                               .uuid(executionId)
                               .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                               .status(Status.ASYNC_WAITING)
                               .build())
            .build();
    when(barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, "setupId"))
        .thenReturn(List.of(barrierExecutionInstance));
    doNothing()
        .when(barrierService)
        .updatePosition(BarrierPositionType.STEP, "setupId", executionId, "stageId", "stepGroupId",
            List.of(barrierExecutionInstance), true, false);
    when(barrierService.findByIdentifierAndPlanExecutionId(barrierRef, planExecutionId))
        .thenReturn(barrierExecutionInstance);
    try (MockedStatic<AmbianceUtils> mockAmbianceUtils = mockStatic(AmbianceUtils.class, RETURNS_MOCKS);
         MockedStatic<RecastOrchestrationUtils> mockRecastOrchestrationUtils =
             mockStatic(RecastOrchestrationUtils.class, RETURNS_MOCKS)) {
      when(AmbianceUtils.getAccountId(any())).thenReturn(accountId);
      when(AmbianceUtils.obtainCurrentLevel(any()))
          .thenReturn(Level.newBuilder().setGroup(BarrierPositionType.STEP.name()).setSetupId("setupId").build());
      when(AmbianceUtils.getStageLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("stageId").build()));
      when(AmbianceUtils.getStrategyLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("strategyId").build()));
      when(AmbianceUtils.getStepGroupLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("stepGroupId").build()));
      when(AmbianceUtils.getCurrentStepType(any())).thenReturn(BarrierStep.STEP_TYPE);
      when(RecastOrchestrationUtils.fromMap(any(), any()))
          .thenReturn(StepElementParameters.builder()
                          .spec(BarrierSpecParameters.builder().barrierRef(barrierRef).build())
                          .build());
      when(AmbianceUtils.getNearestStepGroupLevelWithStrategyFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("stepGroupIdWithStrategy").build()));
      barrierEventHandler.onNodeStatusUpdate(nodeUpdateInfo);
      verify(persistentLocker, times(0))
          .waitToAcquireLock(BARRIER_UPDATE_LOCK + planExecutionId, Duration.ofSeconds(20), Duration.ofSeconds(60));
      verify(barrierService, times(1))
          .updatePosition(BarrierPositionType.STEP, "setupId", executionId, "stageId", "stepGroupIdWithStrategy",
              List.of(barrierExecutionInstance), true, false);
    }
    verify(barrierService, times(1)).findByIdentifierAndPlanExecutionId(barrierRef, planExecutionId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdateBarrierWithoutBarrier() {
    String accountId = "accountId";
    String executionId = "executionId";
    String planExecutionId = "planExecutionId";
    String barrierRef = "barrierRef";
    when(nodeExecutionService.getAmbiance(any(NodeExecution.class)))
        .thenReturn(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build());
    BarrierExecutionInstance barrierExecutionInstance = BarrierExecutionInstance.builder().build();
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(NodeExecution.builder()
                               .uuid(executionId)
                               .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                               .status(Status.ASYNC_WAITING)
                               .build())
            .build();
    when(barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, "setupId"))
        .thenReturn(Collections.emptyList());
    try (MockedStatic<AmbianceUtils> mockAmbianceUtils = mockStatic(AmbianceUtils.class, RETURNS_MOCKS);
         MockedStatic<RecastOrchestrationUtils> mockRecastOrchestrationUtils =
             mockStatic(RecastOrchestrationUtils.class, RETURNS_MOCKS)) {
      when(AmbianceUtils.getAccountId(any())).thenReturn(accountId);
      when(AmbianceUtils.obtainCurrentLevel(any()))
          .thenReturn(Level.newBuilder().setGroup("group1").setSetupId("setupId").build());
      when(AmbianceUtils.getStageLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("stageId").build()));
      when(AmbianceUtils.getStepGroupLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("stepGroupId").build()));
      when(AmbianceUtils.getCurrentStepType(any())).thenReturn(ApprovalStageStep.STEP_TYPE);
      barrierEventHandler.onNodeStatusUpdate(nodeUpdateInfo);
      verify(persistentLocker, times(0))
          .waitToAcquireLock(BARRIER_UPDATE_LOCK + planExecutionId, Duration.ofSeconds(20), Duration.ofSeconds(60));
      verify(barrierService, times(0))
          .updatePosition(BarrierPositionType.STEP, "setupId", executionId, "stageId", "stepGroupId",
              List.of(barrierExecutionInstance), false, false);
    }
    verify(barrierService, times(0)).findByIdentifierAndPlanExecutionId(barrierRef, planExecutionId);
    verify(barrierService, times(0)).update(barrierExecutionInstance);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testShouldTryToDropBarrier_WhenFeatureFlagDisabled_AndNoSiblings() {
    String nodeExecutionId = "nodeExec1";
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).status(Status.ASYNC_WAITING).build();

    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .identifier("barrier1")
            .positionInfo(BarrierPositionInfo.builder().barrierPositionList(new ArrayList<>()).build())
            .build();

    boolean result = barrierEventHandler.shouldTryToDropBarrier(nodeExecution, barrierInstance, false);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testShouldTryToDropBarrier_WhenFeatureFlagEnabled_SkipsSiblingCheck() {
    String nodeExecutionId = "nodeExec1";
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).status(Status.ASYNC_WAITING).build();

    BarrierExecutionInstance barrierInstance = BarrierExecutionInstance.builder().identifier("barrier1").build();

    // When feature flag is enabled (inverted - fix disabled), should return true immediately
    boolean result = barrierEventHandler.shouldTryToDropBarrier(nodeExecution, barrierInstance, true);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testShouldTryToDropBarrier_WhenBarrierNotInAsyncWaiting_ReturnsFalse() {
    String nodeExecutionId = "nodeExec1";
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).status(Status.RUNNING).build();

    BarrierExecutionInstance barrierInstance = BarrierExecutionInstance.builder().identifier("barrier1").build();

    boolean result = barrierEventHandler.shouldTryToDropBarrier(nodeExecution, barrierInstance, false);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testShouldTryToDropBarrier_WhenSiblingStageHasFailed_ReturnsFalse() {
    String nodeExecutionId = "nodeExec1";
    String siblingStageId = "siblingStage1";
    String siblingStepId = "siblingStep1";

    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).status(Status.ASYNC_WAITING).build();

    List<BarrierPosition> positions = new ArrayList<>();
    positions.add(BarrierPosition.builder().stepRuntimeId(nodeExecutionId).stageRuntimeId("currentStage").build());
    positions.add(BarrierPosition.builder().stepRuntimeId(siblingStepId).stageRuntimeId(siblingStageId).build());

    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .identifier("barrier1")
            .positionInfo(BarrierPositionInfo.builder().barrierPositionList(positions).build())
            .build();

    NodeExecution siblingStage = NodeExecution.builder().uuid(siblingStageId).status(Status.FAILED).build();

    NodeExecution siblingStep = NodeExecution.builder().uuid(siblingStepId).status(Status.SKIPPED).build();

    Set<String> nodeIds = new HashSet<>();
    nodeIds.add(siblingStageId);
    nodeIds.add(siblingStepId);

    when(nodeExecutionService.getAllWithFieldIncluded(eq(nodeIds), any()))
        .thenReturn(List.of(siblingStage, siblingStep));

    boolean result = barrierEventHandler.shouldTryToDropBarrier(nodeExecution, barrierInstance, false);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testShouldTryToDropBarrier_WhenSiblingStageRunningAndStepSkipped_ReturnsFalse() {
    String nodeExecutionId = "nodeExec1";
    String siblingStageId = "siblingStage1";
    String siblingStepId = "siblingStep1";

    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).status(Status.ASYNC_WAITING).build();

    List<BarrierPosition> positions = new ArrayList<>();
    positions.add(BarrierPosition.builder().stepRuntimeId(nodeExecutionId).stageRuntimeId("currentStage").build());
    positions.add(BarrierPosition.builder().stepRuntimeId(siblingStepId).stageRuntimeId(siblingStageId).build());

    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .identifier("barrier1")
            .positionInfo(BarrierPositionInfo.builder().barrierPositionList(positions).build())
            .build();

    NodeExecution siblingStage = NodeExecution.builder().uuid(siblingStageId).status(Status.RUNNING).build();

    NodeExecution siblingStep = NodeExecution.builder().uuid(siblingStepId).status(Status.SKIPPED).build();

    Set<String> nodeIds = new HashSet<>();
    nodeIds.add(siblingStageId);
    nodeIds.add(siblingStepId);

    when(nodeExecutionService.getAllWithFieldIncluded(eq(nodeIds), any()))
        .thenReturn(List.of(siblingStage, siblingStep));

    boolean result = barrierEventHandler.shouldTryToDropBarrier(nodeExecution, barrierInstance, false);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testShouldTryToDropBarrier_WhenAllSiblingsSucceeded_ReturnsTrue() {
    String nodeExecutionId = "nodeExec1";
    String siblingStageId = "siblingStage1";
    String siblingStepId = "siblingStep1";

    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).status(Status.ASYNC_WAITING).build();

    List<BarrierPosition> positions = new ArrayList<>();
    positions.add(BarrierPosition.builder().stepRuntimeId(nodeExecutionId).stageRuntimeId("currentStage").build());
    positions.add(BarrierPosition.builder().stepRuntimeId(siblingStepId).stageRuntimeId(siblingStageId).build());

    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .identifier("barrier1")
            .positionInfo(BarrierPositionInfo.builder().barrierPositionList(positions).build())
            .build();

    NodeExecution siblingStage = NodeExecution.builder().uuid(siblingStageId).status(Status.SUCCEEDED).build();

    NodeExecution siblingStep = NodeExecution.builder().uuid(siblingStepId).status(Status.SUCCEEDED).build();

    Set<String> nodeIds = new HashSet<>();
    nodeIds.add(siblingStageId);
    nodeIds.add(siblingStepId);

    when(nodeExecutionService.getAllWithFieldIncluded(eq(nodeIds), any()))
        .thenReturn(List.of(siblingStage, siblingStep));

    boolean result = barrierEventHandler.shouldTryToDropBarrier(nodeExecution, barrierInstance, false);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testShouldTryToDropBarrier_WhenPositionHasNullStageId_SkipsPosition() {
    String nodeExecutionId = "nodeExec1";
    String siblingStepId = "siblingStep1";

    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).status(Status.ASYNC_WAITING).build();

    List<BarrierPosition> positions = new ArrayList<>();
    positions.add(BarrierPosition.builder().stepRuntimeId(nodeExecutionId).stageRuntimeId("currentStage").build());
    // Position with null stageRuntimeId should be skipped
    positions.add(BarrierPosition.builder().stepRuntimeId(siblingStepId).stageRuntimeId(null).build());

    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .identifier("barrier1")
            .positionInfo(BarrierPositionInfo.builder().barrierPositionList(positions).build())
            .build();

    boolean result = barrierEventHandler.shouldTryToDropBarrier(nodeExecution, barrierInstance, false);

    // Should return true because no valid sibling positions to check
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testShouldTryToDropBarrier_WhenMultipleSiblingsWithMixedStatuses_ReturnsFalse() {
    String nodeExecutionId = "nodeExec1";
    String siblingStage1Id = "siblingStage1";
    String siblingStep1Id = "siblingStep1";
    String siblingStage2Id = "siblingStage2";
    String siblingStep2Id = "siblingStep2";

    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).status(Status.ASYNC_WAITING).build();

    List<BarrierPosition> positions = new ArrayList<>();
    positions.add(BarrierPosition.builder().stepRuntimeId(nodeExecutionId).stageRuntimeId("currentStage").build());
    positions.add(BarrierPosition.builder().stepRuntimeId(siblingStep1Id).stageRuntimeId(siblingStage1Id).build());
    positions.add(BarrierPosition.builder().stepRuntimeId(siblingStep2Id).stageRuntimeId(siblingStage2Id).build());

    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .identifier("barrier1")
            .positionInfo(BarrierPositionInfo.builder().barrierPositionList(positions).build())
            .build();

    // First sibling succeeded
    NodeExecution siblingStage1 = NodeExecution.builder().uuid(siblingStage1Id).status(Status.SUCCEEDED).build();
    NodeExecution siblingStep1 = NodeExecution.builder().uuid(siblingStep1Id).status(Status.SUCCEEDED).build();

    // Second sibling failed
    NodeExecution siblingStage2 = NodeExecution.builder().uuid(siblingStage2Id).status(Status.FAILED).build();
    NodeExecution siblingStep2 = NodeExecution.builder().uuid(siblingStep2Id).status(Status.SKIPPED).build();

    Set<String> nodeIds = new HashSet<>();
    nodeIds.add(siblingStage1Id);
    nodeIds.add(siblingStep1Id);
    nodeIds.add(siblingStage2Id);
    nodeIds.add(siblingStep2Id);

    when(nodeExecutionService.getAllWithFieldIncluded(eq(nodeIds), any()))
        .thenReturn(List.of(siblingStage1, siblingStep1, siblingStage2, siblingStep2));

    boolean result = barrierEventHandler.shouldTryToDropBarrier(nodeExecution, barrierInstance, false);

    // Should return false because one sibling has failed
    assertThat(result).isFalse();
  }
}
