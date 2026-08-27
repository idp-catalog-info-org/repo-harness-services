/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ExecutionErrorInfo;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.converter.FailureInfoDTOConverter;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plan.NodeType;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO.GraphLayoutNodeDTOKeys;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.execution.ExecutionModeUtils;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Update;

/**
 * A utility to generate updates for the layout graph used in the list api for stage layout
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionSummaryUpdateUtils {
  @Inject NodeExecutionInfoService nodeExecutionInfoService;
  @Inject PmsFeatureFlagService pmsFeatureFlagService;

  private boolean isBarrierNode(Level level) {
    return Objects.equals(level.getStepType().getType(), StepSpecTypeConstants.BARRIER);
  }

  private boolean performUpdatesOnBarrierNode(Update update, NodeExecution nodeExecution) {
    Optional<Level> stage = NodeExecutionContextUtils.getStageLevelFromExecutionContext(nodeExecution);
    if (stage.isPresent()) {
      Level stageNode = stage.get();
      String graphNodeId = stageNode.getSetupId();
      if (AmbianceUtils.hasStrategyMetadata(stageNode)) {
        graphNodeId = stageNode.getRuntimeId();
      }
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + graphNodeId + ".barrierFound", true);
      return true;
    }
    return false;
  }

  /**
   * This function adds some information at the stage layoutNodeMap level.
   * Performs the following operation:
   * 1. Updates barrier related information on a stage node
   * 2. Updates strategy node with status and step parameters
   * 3. Updates stage node with generic updates and strategy information.
   *
   * @param update
   * @param nodeExecution
   * @param postExecutionRollbackInfos
   * @return
   */
  public boolean addStageUpdateCriteria(
      Update update, NodeExecution nodeExecution, List<PostExecutionRollbackInfo> postExecutionRollbackInfos) {
    Level level = Objects.requireNonNull(NodeExecutionContextUtils.obtainCurrentLevel(nodeExecution));
    boolean updated = false;
    if (isBarrierNode(level)) {
      updated = performUpdatesOnBarrierNode(update, nodeExecution);
    }
    boolean isStageNode = HarnessYamlVersion.isV1(NodeExecutionContextUtils.getHarnessYamlVersion(nodeExecution))
        ? OrchestrationUtils.isStageOrParallelStageNode(nodeExecution)
        : OrchestrationUtils.isStageNode(nodeExecution);
    if (ExecutionModeUtils.isPostExecutionRollbackMode(NodeExecutionContextUtils.getExecutionMode(nodeExecution))) {
      if (isStageNode) {
        if (isDagPipelinePostExecutionRollback(nodeExecution)) {
          // DAG post-prod rollback: pruned layout must not be repopulated by upstream identity stages.
          // Rollback stages are identity nodes — force layout status so the UI shows terminal state.
          if (isPostExecutionRollbackTargetStage(nodeExecution, postExecutionRollbackInfos)) {
            ExecutionStatus status = ExecutionStatus.getExecutionStatus(nodeExecution.getStatus());
            updated = updateStageNode(update, nodeExecution, status, level, true) || updated;
          }
        } else {
          // Sequential post-prod rollback — unchanged.
          String startingNodeId = postExecutionRollbackInfos.get(0).getPostExecutionRollbackStageId();
          for (Level nodeLevel : NodeExecutionContextUtils.getLevelList(nodeExecution)) {
            if (Objects.equals(nodeLevel.getSetupId(), startingNodeId)) {
              ExecutionStatus status = ExecutionStatus.getExecutionStatus(nodeExecution.getStatus());
              updated = updateStageNode(update, nodeExecution, status, level, false) || updated;
            }
          }
        }
      }
      return updated;
    }
    if (isStageNode) {
      ExecutionStatus status = ExecutionStatus.getExecutionStatus(nodeExecution.getStatus());
      updated = updateStageNode(update, nodeExecution, status, level, false) || updated;
    }

    return updated;
  }

  /**
   * Aligns with create/end handlers: enableDAG + PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION.
   * Post-prod mode is already gated by the caller.
   */
  private boolean isDagPipelinePostExecutionRollback(NodeExecution nodeExecution) {
    if (nodeExecution.getAmbiance() == null || !nodeExecution.getAmbiance().hasMetadata()
        || !nodeExecution.getAmbiance().getMetadata().getEnableDAG()) {
      return false;
    }
    String accountId = AmbianceUtils.getAccountId(nodeExecution.getAmbiance());
    return accountId != null
        && pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION);
  }

  private boolean isPostExecutionRollbackTargetStage(
      NodeExecution nodeExecution, List<PostExecutionRollbackInfo> postExecutionRollbackInfos) {
    if (EmptyPredicate.isEmpty(postExecutionRollbackInfos)) {
      return false;
    }
    String rollbackStageId = postExecutionRollbackInfos.get(0).getPostExecutionRollbackStageId();
    if (rollbackStageId.equals(nodeExecution.getNodeId())) {
      return true;
    }
    return NodeExecutionContextUtils.getLevelList(nodeExecution)
        .stream()
        .anyMatch(nodeLevel -> rollbackStageId.equals(nodeLevel.getSetupId()));
  }

  /**
   * Updates layout node fields for a stage.
   *
   * @param writeStatusForIdentity when true (DAG post-prod rollback target), always write layout status —
   *     rollback stages are IdentityPlanNodes and would otherwise skip status updates.
   */
  private boolean updateStageNode(
      Update update, NodeExecution nodeExecution, ExecutionStatus status, Level level, boolean writeStatusForIdentity) {
    String stageUuid = nodeExecution.getNodeId();
    if (NodeExecutionContextUtils.getStrategyLevelFromExecutionContext(nodeExecution).isPresent()) {
      // If nodeExecution is under strategy then we use nodeExecution.getUuid rather than the planNodeId
      stageUuid = nodeExecution.getUuid();
      updateStrategyBasedData(update, nodeExecution);
    }
    // Normally identity nodes skip status (PlanNode-driven). DAG post-prod rollback targets must still show status.
    if (writeStatusForIdentity || !level.getNodeType().equals(NodeType.IDENTITY_PLAN_NODE.toString())) {
      update.set(String.format(LayoutNodeGraphConstants.STATUS, stageUuid), status);
    }
    updateGenericData(update, stageUuid, nodeExecution);
    return true;
  }

  private void updateStrategyBasedData(Update update, NodeExecution nodeExecution) {
    String stageUuid = nodeExecution.getUuid();

    update.set(String.format(LayoutNodeGraphConstants.NODE_IDENTIFIER, stageUuid), nodeExecution.getIdentifier());
    update.set(String.format(LayoutNodeGraphConstants.NAME, stageUuid), nodeExecution.getName());
    update.set(String.format(LayoutNodeGraphConstants.STRATEGY_METADATA, stageUuid),
        nodeExecutionInfoService.getStrategyMetadata(nodeExecution));
  }

  private void updateGenericData(Update update, String stageUuid, NodeExecution nodeExecution) {
    update.set(String.format(LayoutNodeGraphConstants.CREATED_AT, stageUuid), nodeExecution.getCreatedAt());
    update.set(String.format(LayoutNodeGraphConstants.START_TS, stageUuid), nodeExecution.getStartTs());
    update.set(String.format(LayoutNodeGraphConstants.NODE_EXECUTION_ID, stageUuid), nodeExecution.getUuid());
    update.set(String.format(LayoutNodeGraphConstants.NODE_RUN_INFO, stageUuid), nodeExecution.getNodeRunInfo());
    if (nodeExecution.getEndTs() != null) {
      update.set(String.format(LayoutNodeGraphConstants.END_TS, stageUuid), nodeExecution.getEndTs());
    }
    if (nodeExecution.getFailureInfo() != null) {
      update.set(String.format(LayoutNodeGraphConstants.FAILURE_INFO, stageUuid),
          ExecutionErrorInfo.builder().message(nodeExecution.getFailureInfo().getErrorMessage()).build());
      update.set(String.format(LayoutNodeGraphConstants.FAILURE_INFO_DTO, stageUuid),
          FailureInfoDTOConverter.toFailureInfoDTO(nodeExecution.getFailureInfo()));
    }

    update.set(String.format(LayoutNodeGraphConstants.EXECUTION_INPUT_CONFIGURED, stageUuid),
        nodeExecution.getExecutionInputConfigured());
    update.set(String.format(LayoutNodeGraphConstants.NODE_IDENTIFIER, stageUuid), nodeExecution.getIdentifier());
    update.set(String.format(LayoutNodeGraphConstants.NAME, stageUuid), nodeExecution.getName());

    boolean isRollbackStageNode =
        nodeExecution.getNodeId().endsWith(NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX);
    update.set(
        String.format(LayoutNodeGraphConstants.BASE_KEY + "." + GraphLayoutNodeDTOKeys.isRollbackStageNode, stageUuid),
        isRollbackStageNode);

    if (nodeExecution.getNodeRunInfo() != null) {
      // This is required because strategy nodes generate new stage nodes using this function, so it's not populated via
      // the normal plan creator flow with the basic graph layout info
      boolean isManualExecution = nodeExecution.getNodeRunInfo().getIsManualExecution();
      update.set(
          String.format(LayoutNodeGraphConstants.BASE_KEY + "." + GraphLayoutNodeDTOKeys.isManualExecution, stageUuid),
          isManualExecution);
    }

    // Update childrenCount for wrapper nodes (NG_FORK, STRATEGY_V1, GROUP)
    if (nodeExecution.getChildrenCount() != null && nodeExecution.getChildrenCount() > 0) {
      update.set(String.format(LayoutNodeGraphConstants.CHILDREN_COUNT, stageUuid), nodeExecution.getChildrenCount());
    }
  }

  public void updateNextIdOfStageBeforePipelineRollback(
      Update update, String pipelineRollbackStagePlanNodeId, String previousStagePlanNodeId) {
    update.set(String.format(LayoutNodeGraphConstants.NEXT_IDS, previousStagePlanNodeId),
        Collections.singletonList(pipelineRollbackStagePlanNodeId));
  }

  public void updateDependencyGraphForPipelineRollback(
      Update update, String pipelineRollbackStagePlanNodeId, String triggeringStageNodeId) {
    update.set(PlanExecutionSummaryKeys.dependencyGraph + "." + pipelineRollbackStagePlanNodeId,
        Collections.singletonList(triggeringStageNodeId));
  }
}
