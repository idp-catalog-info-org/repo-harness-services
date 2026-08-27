/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.barriers.event;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.observer.AsyncInformObserver;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.steps.barriers.BarrierSpecParameters;
import io.harness.steps.barriers.BarrierStep;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition.BarrierPositionType;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

/**
 * This observer is needed because it is responsible for also lifting the barrier in case of failure.
 * e.g: when a barrier step is skipped, it would not be lifted without an observer pattern.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierEventHandler implements AsyncInformObserver, NodeStatusUpdateObserver {
  @Inject @Named("OrchestrationVisualizationExecutorService") ExecutorService executorService;
  @Inject BarrierService barrierService;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject NodeExecutionService nodeExecutionService;

  @Override
  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    Ambiance ambiance = nodeExecutionService.getAmbiance(nodeUpdateInfo.getNodeExecution());
    boolean hasParentPipeline = false;
    String parentPlanExecutionId = null;

    if (ambiance.hasMetadata() && ambiance.getMetadata().hasPipelineStageInfo()) {
      hasParentPipeline = Boolean.TRUE.equals(ambiance.getMetadata().getPipelineStageInfo().getHasParentPipeline());
      parentPlanExecutionId = ambiance.getMetadata().getPipelineStageInfo().getExecutionId();
    }

    updateBarrierPosition(nodeUpdateInfo.getPlanExecutionId(), nodeUpdateInfo.getNodeExecution());
    if (hasParentPipeline) {
      updateBarrierPosition(parentPlanExecutionId, nodeUpdateInfo.getNodeExecution());
    }

    dropBarrier(nodeUpdateInfo.getPlanExecutionId(), nodeUpdateInfo.getNodeExecution(), ambiance);
    if (hasParentPipeline) {
      dropBarrier(parentPlanExecutionId, nodeUpdateInfo.getNodeExecution(), ambiance);
    }
  }

  private void updateBarrierPosition(String planExecutionId, NodeExecution nodeExecution) {
    try {
      Level level = Objects.requireNonNull(NodeExecutionContextUtils.obtainCurrentLevel(nodeExecution));
      String group = level.getGroup();
      BarrierPositionType positionType = null;
      if (BarrierPositionType.STAGE.name().equals(group)) {
        positionType = BarrierPositionType.STAGE;
      } else if (BarrierPositionType.STEP_GROUP.name().equals(group)) {
        positionType = BarrierPositionType.STEP_GROUP;
      } else if (BarrierPositionType.STEP.name().equals(group)) {
        positionType = BarrierPositionType.STEP;
      }
      if (positionType != null) {
        updateBarrierPositionInternal(planExecutionId, positionType, nodeExecution);
      }
    } catch (Exception ex) {
      log.error(String.format("Failed to update barrier position for planExecutionId: [%s], nodeExecutionId: [%s]",
                    planExecutionId, nodeExecution.getUuid()),
          ex);
      throw ex;
    }
  }

  private void updateBarrierPositionInternal(
      String planExecutionId, BarrierPositionType type, NodeExecution nodeExecution) {
    boolean disableDummyPositionFix = pmsFeatureFlagHelper.isEnabled(
        nodeExecution.getAccountId(), FeatureName.PIPE_DISABLE_BARRIER_DUMMY_POSITION_FIX);

    // Step parents should not update the barrier position; this responsibility is delegated to the step to avoid race
    // conditions and reduce the number of updates. When status is HALTED, we still need to update the barrier position
    // because the step won't be executed anyway.
    if (type != BarrierPositionType.STEP
        && !StatusUtils.stepExecutionHaltStatuses().contains(nodeExecution.getStatus())) {
      return;
    }

    Level level = Objects.requireNonNull(NodeExecutionContextUtils.obtainCurrentLevel(nodeExecution));
    String stageRuntimeId = NodeExecutionContextUtils.getStageLevelFromExecutionContext(nodeExecution)
                                .map(Level::getRuntimeId)
                                .orElse(null);

    String stepGroupRuntimeId =
        NodeExecutionContextUtils.getNearestStepGroupLevelWithStrategyFromExecutionContext(nodeExecution)
            .map(Level::getRuntimeId)
            .orElse(null);
    if (stepGroupRuntimeId == null) {
      stepGroupRuntimeId = NodeExecutionContextUtils.getStepGroupLevelFromExecutionContext(nodeExecution)
                               .map(Level::getRuntimeId)
                               .orElse(null);
    }

    List<BarrierExecutionInstance> barrierExecutionInstances =
        barrierService.findByPosition(planExecutionId, type, level.getSetupId());

    if (isNotEmpty(barrierExecutionInstances)) {
      barrierService.updatePosition(type, level.getSetupId(), nodeExecution.getUuid(), stageRuntimeId,
          stepGroupRuntimeId, barrierExecutionInstances, true, disableDummyPositionFix);
    }
  }

  private void dropBarrier(String planExecutionId, NodeExecution nodeExecution, Ambiance ambiance) {
    try {
      // Only process barrier steps
      if (!BarrierStep.STEP_TYPE.equals(NodeExecutionContextUtils.getCurrentStepType(nodeExecution))) {
        return;
      }

      String barrierRef = getBarrierRef(nodeExecution, ambiance);
      if (barrierRef.startsWith(YAMLFieldNameConstants.PARENT_DOT)) {
        barrierRef = barrierRef.split("\\.")[1];
      }

      BarrierExecutionInstance barrierExecutionInstance =
          barrierService.findByIdentifierAndPlanExecutionId(barrierRef, planExecutionId);

      boolean disableBarrierRaceConditionFix = AmbianceUtils.checkIfFeatureFlagEnabled(
          ambiance, FeatureName.PIPE_DISABLE_BARRIER_RACE_CONDITION_FIX.toString());

      if (shouldTryToDropBarrier(nodeExecution, barrierExecutionInstance, disableBarrierRaceConditionFix)) {
        if (null != barrierExecutionInstance) {
          barrierService.update(barrierExecutionInstance);
        }
      }
    } catch (Exception ex) {
      log.error(String.format("Failed to drop barrier for planExecutionId: [%s], nodeExecutionId: [%s]",
                    planExecutionId, nodeExecution.getUuid()),
          ex);
      throw ex;
    }
  }

  private String getBarrierRef(NodeExecution nodeExecution, Ambiance ambiance) {
    BarrierSpecParameters barrierSpecParameters = HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())
        ? getBarrierSpecParametersV1(nodeExecution)
        : getBarrierSpecParametersV0(nodeExecution);
    return barrierSpecParameters.getBarrierRef();
  }

  private BarrierSpecParameters getBarrierSpecParametersV1(NodeExecution nodeExecution) {
    return (BarrierSpecParameters) RecastOrchestrationUtils
        .fromMap(nodeExecution.getResolvedStepParameters(), StepElementParametersV1.class)
        .getSpec();
  }

  private BarrierSpecParameters getBarrierSpecParametersV0(NodeExecution nodeExecution) {
    return (BarrierSpecParameters) RecastOrchestrationUtils
        .fromMap(nodeExecution.getResolvedStepParameters(), StepElementParameters.class)
        .getSpec();
  }

  boolean shouldTryToDropBarrier(
      NodeExecution nodeExecution, BarrierExecutionInstance barrierInstance, boolean disableRaceConditionFix) {
    if (!isBarrierInAsyncWaitingStatus(nodeExecution)) {
      return false;
    }

    // If race condition fix is disabled (inverted FF enabled), skip the sibling check logic
    if (disableRaceConditionFix) {
      return true;
    }

    if (!hasValidBarrierPositions(barrierInstance)) {
      return true;
    }

    try {
      List<SiblingPosition> siblingPositions = collectSiblingPositions(nodeExecution, barrierInstance);

      if (siblingPositions.isEmpty()) {
        return true;
      }

      Map<String, NodeExecution> nodeStatusMap = fetchSiblingNodeStatuses(siblingPositions);

      if (shouldSkipBarrierEvaluation(siblingPositions, nodeStatusMap)) {
        return false;
      }
    } catch (Exception ex) {
      log.error("Failed to check sibling stage iterations for barrier step [{}], proceeding with barrier evaluation",
          nodeExecution.getUuid(), ex);
    }

    return true;
  }

  private boolean isBarrierInAsyncWaitingStatus(NodeExecution nodeExecution) {
    return Status.ASYNC_WAITING.equals(nodeExecution.getStatus());
  }

  private boolean hasValidBarrierPositions(BarrierExecutionInstance barrierInstance) {
    if (barrierInstance == null || barrierInstance.getPositionInfo() == null
        || !isNotEmpty(barrierInstance.getPositionInfo().getBarrierPositionList())) {
      return false;
    }
    return true;
  }

  private List<SiblingPosition> collectSiblingPositions(
      NodeExecution nodeExecution, BarrierExecutionInstance barrierInstance) {
    List<SiblingPosition> siblingPositions = new ArrayList<>();

    for (var position : barrierInstance.getPositionInfo().getBarrierPositionList()) {
      if (nodeExecution.getUuid().equals(position.getStepRuntimeId())) {
        continue;
      }

      // Only add position if both stage and step runtime IDs are present
      if (isNotEmpty(position.getStageRuntimeId()) && isNotEmpty(position.getStepRuntimeId())) {
        siblingPositions.add(new SiblingPosition(position.getStageRuntimeId(), position.getStepRuntimeId()));
      }
    }

    return siblingPositions;
  }

  private Map<String, NodeExecution> fetchSiblingNodeStatuses(List<SiblingPosition> siblingPositions) {
    Set<String> allSiblingIds = new HashSet<>();
    for (SiblingPosition position : siblingPositions) {
      allSiblingIds.add(position.stageRuntimeId);
      allSiblingIds.add(position.stepRuntimeId);
    }

    List<NodeExecution> siblingNodes =
        nodeExecutionService.getAllWithFieldIncluded(allSiblingIds, NodeProjectionUtils.withStatus);

    Map<String, NodeExecution> nodeMap = new HashMap<>();
    for (NodeExecution node : siblingNodes) {
      nodeMap.put(node.getUuid(), node);
    }
    return nodeMap;
  }

  private boolean shouldSkipBarrierEvaluation(
      List<SiblingPosition> siblingPositions, Map<String, NodeExecution> nodeStatusMap) {
    for (SiblingPosition position : siblingPositions) {
      NodeExecution siblingStage = nodeStatusMap.get(position.stageRuntimeId);
      NodeExecution siblingStep = nodeStatusMap.get(position.stepRuntimeId);

      Status stageStatus = siblingStage != null ? siblingStage.getStatus() : null;
      Status stepStatus = siblingStep != null ? siblingStep.getStatus() : null;

      // Only skip evaluation if there's evidence of failure
      if (isSiblingStageInBrokenState(stageStatus)) {
        return true;
      }

      if (isSiblingBarrierSkippedWhileStageRunning(stageStatus, stepStatus)) {
        return true;
      }
    }

    return false;
  }

  private boolean isSiblingStageInBrokenState(Status stageStatus) {
    if (stageStatus != null && StatusUtils.brokeStatuses().contains(stageStatus)) {
      return true;
    }
    return false;
  }

  private boolean isSiblingBarrierSkippedWhileStageRunning(Status stageStatus, Status stepStatus) {
    if (stageStatus == Status.RUNNING && stepStatus == Status.SKIPPED) {
      return true;
    }
    return false;
  }

  private static class SiblingPosition {
    final String stageRuntimeId;
    final String stepRuntimeId;

    SiblingPosition(String stageRuntimeId, String stepRuntimeId) {
      this.stageRuntimeId = stageRuntimeId;
      this.stepRuntimeId = stepRuntimeId;
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
