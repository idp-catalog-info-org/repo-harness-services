/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.start;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.springdata.SpringDataMongoUtils.setUnset;

import static java.lang.String.format;

import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionTimeoutCallback;
import io.harness.engine.executions.node.exception.NodeExecutionUpdateFailedException;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.observers.NodeExecutionStartObserver;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.observer.Subject;
import io.harness.plan.PlanNode;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.start.NodeStartEvent;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.serializer.KryoSerializer;
import io.harness.springdata.TransactionHelper;
import io.harness.timeout.TimeoutCallback;
import io.harness.timeout.TimeoutInstance;
import io.harness.timeout.TimeoutParameters;
import io.harness.timeout.contracts.TimeoutObtainment;
import io.harness.timeout.engine.TimeoutEngine;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class NodeStartHelper {
  @Inject private PmsEventSender eventSender;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private TimeoutEngine timeoutEngine;
  @Inject private PlanService planService;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private PipelineSettingsService pipelineSettingsService;
  @Inject private PipelineRetentionService pipelineRetentionService;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private TransactionHelper transactionHelper;
  @Getter private final Subject<NodeExecutionStartObserver> nodeExecutionStartSubject = new Subject<>();

  public void startNode(Ambiance ambiance, FacilitatorResponseProto facilitatorResponse) {
    startNode(ambiance, facilitatorResponse, java.util.Collections.emptyMap());
  }

  public void startNode(Ambiance ambiance, FacilitatorResponseProto facilitatorResponse, Map<String, Object> updates) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    Status targetStatus = calculateStatusFromMode(facilitatorResponse.getExecutionMode());
    PlanNode node = planService.fetchNode(ambiance.getPlanId(), AmbianceUtils.obtainCurrentSetupId(ambiance));
    NodeExecution nodeExecution =
        prepareNodeExecutionForInvocation(ambiance, targetStatus, node, facilitatorResponse, updates);
    if (nodeExecution == null) {
      nodeExecution = nodeExecutionService.get(nodeExecutionId);
      // We can mark the nodeExecution as either discontinuing, aborted or expired if nodeExecution is in queued state.
      // If the nodeExecution is in that state then we should do no-op
      if (StatusUtils.abortInProgressStatuses().contains(nodeExecution.getStatus())) {
        return;
      }
      // This is just for debugging if this is happening then the node status has changed from QUEUED
      // This should never happen
      log.warn("Not Starting node execution. Cannot transition from {} to {}", nodeExecution.getStatus(), targetStatus);
      throw new NodeExecutionUpdateFailedException("Cannot Start node Execution");
    }
    nodeExecutionStartSubject.fireInform(
        NodeExecutionStartObserver::onNodeStart, NodeStartInfo.builder().nodeExecution(nodeExecution).build());

    // Update childrenCount on parent when this node starts (only for V1 pipelines)
    if (HarnessYamlVersion.isV1(NodeExecutionContextUtils.getHarnessYamlVersion(nodeExecution))) {
      updateParentChildrenCount(nodeExecution);
    }
    log.info("Sending NodeExecution START event");

    sendEvent(nodeExecution, node, facilitatorResponse.getPassThroughDataBytes());
  }

  private void sendEvent(NodeExecution nodeExecution, PlanNode planNode, ByteString passThroughData) {
    String resolvedStepParametersBytes = nodeExecution.getResolvedStepParametersString();
    Ambiance ambiance = nodeExecutionService.getAmbiance(nodeExecution);
    // Check whether the input parameters size is within the imposed limits
    if (!pipelineSettingsService.isStepInputSizeWithinLimit(
            planNode.getAccountIdentifier(), resolvedStepParametersBytes)) {
      log.info("[INPUT_PARAMETER_SIZE_EXCEEDED] for nodeId : {}", nodeExecution.getNodeId());
      if (pmsFeatureFlagService.isEnabled(
              planNode.getAccountIdentifier(), FeatureName.PIPE_HARD_IMPOSE_EXECUTION_LIMITS)) {
        throw new LimitExceededException("You have exceeded the step input parameter size allowed on the account. "
            + "Please upgrade your plan or contact Harness Support.");
      }
      try {
        pipelineRetentionService.updateMaxStepInputSize(
            planNode.getAccountIdentifier(), Long.valueOf(resolvedStepParametersBytes.length()));
      } catch (Exception ex) {
        log.warn(
            String.format("Error in overriding the input parameter size limit for account id: {%s}, to size: {%d}:",
                planNode.getAccountIdentifier(), resolvedStepParametersBytes.length()),
            ex);
      }
    }
    NodeStartEvent nodeStartEvent = NodeStartEvent.newBuilder()
                                        .setAmbiance(ambiance)
                                        .addAllRefObjects(planNode.getRefObjects())
                                        .setFacilitatorPassThoroughData(passThroughData)
                                        .setStepParameters(ByteString.copyFromUtf8(resolvedStepParametersBytes))
                                        .setMode(nodeExecution.getMode())
                                        .build();
    // markNodesProcessing already retries once on transient Mongo failures. If it is still exhausted, fail the node
    // explicitly instead of publishing the start event, so the node errors out fast rather than hanging until the
    // pipeline timeout. See PIPE-35791.
    try {
      nodeExecutionService.markNodesProcessing(Collections.singletonList(nodeExecution.getUuid()), true);
    } catch (Exception ex) {
      log.error("Failed to mark node {} as processing before publishing start event. Failing the node.",
          nodeExecution.getUuid(), ex);
      orchestrationEngine.handleError(ambiance, ex);
      return;
    }
    eventSender.sendEvent(ambiance, nodeStartEvent, PmsEventCategory.NODE_START, nodeExecution.getModule(), true, true);
  }

  private NodeExecution prepareNodeExecutionForInvocation(Ambiance ambiance, Status targetStatus, PlanNode planNode,
      FacilitatorResponseProto facilitatorResponse, Map<String, Object> updates) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    boolean isOptimizationWriteDisabled = AmbianceUtils.checkIfFeatureFlagEnabled(
        ambiance, FeatureName.PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION.name());
    return transactionHelper.performTransaction(() -> {
      List<String> timeoutInstanceIds = registerTimeouts(ambiance, planNode.getTimeoutObtainments());
      return nodeExecutionService.updateStatusWithOps(nodeExecutionId, targetStatus, ops -> {
        setUnset(ops, NodeExecutionKeys.timeoutInstanceIds, timeoutInstanceIds);
        updateStartTsInNodeExecution(ops, ambiance, planNode);
        // Apply facilitation-mode and any pre-facilitation updates in the same atomic write
        if (!isOptimizationWriteDisabled) {
          ops.set(NodeExecutionKeys.mode, facilitatorResponse.getExecutionMode());
          if (updates != null && !updates.isEmpty()) {
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
              ops.set(entry.getKey(), entry.getValue());
            }
          }
        }
      }, EnumSet.noneOf(Status.class));
    });
  }

  private Status calculateStatusFromMode(ExecutionMode executionMode) {
    switch (executionMode) {
      case CONSTRAINT:
        return Status.RESOURCE_WAITING;
      case APPROVAL:
        return Status.APPROVAL_WAITING;
      case WAIT_STEP:
        return Status.WAIT_STEP_RUNNING;
      case ASYNC:
        return Status.ASYNC_WAITING;
      default:
        return Status.RUNNING;
    }
  }

  private List<String> registerTimeouts(Ambiance ambiance, List<TimeoutObtainment> timeoutObtainments) {
    if (isEmpty(timeoutObtainments)) {
      return Collections.emptyList();
    }
    List<String> timeoutInstanceIds = new ArrayList<>();
    TimeoutCallback timeoutCallback =
        new NodeExecutionTimeoutCallback(ambiance.getPlanExecutionId(), AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    EngineExpressionEvaluator evaluator = pmsEngineExpressionService.prepareExpressionEvaluator(ambiance);
    for (TimeoutObtainment timeoutObtainment : timeoutObtainments) {
      TimeoutParameters timeoutParameters =
          OrchestrationUtils.buildTimeoutParameters(kryoSerializer, evaluator, timeoutObtainment);
      TimeoutInstance instance =
          timeoutEngine.registerTimeout(timeoutObtainment.getDimension(), timeoutParameters, timeoutCallback);
      timeoutInstanceIds.add(instance.getUuid());
    }
    log.info(format("Registered node execution timeouts: %s", timeoutInstanceIds.toString()));
    return timeoutInstanceIds;
  }

  @VisibleForTesting
  protected void updateStartTsInNodeExecution(Update ops, Ambiance ambiance, PlanNode planNode) {
    long currentTimeMillis = System.currentTimeMillis();
    Level updatedLevel =
        ambiance.toBuilder().getLevelsBuilder(ambiance.getLevelsCount() - 1).setStartTs(currentTimeMillis).build();
    ambiance = ambiance.toBuilder().setLevels(ambiance.getLevelsCount() - 1, updatedLevel).build();
    if (shouldSetStartTimestamp(ambiance, planNode)) {
      /*
       This check is added because in case of manual execution, we set the startts in facilitator itself
       So that we can record the duration of waiting time for user input as well in the node execution duration
       */
      ops.set(NodeExecutionKeys.startTs, currentTimeMillis);
    }
    Ambiance finalAmbiance = ambiance;
    ops.set(NodeExecutionKeys.executionContext, AmbianceUtils.getExecutionContextFromAmbiance(ambiance));
    if (pmsFeatureFlagService.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.PIPE_REMOVE_AMBIANCE_POPULATION_IN_NODE_EXECUTION)) {
      finalAmbiance = Ambiance.newBuilder()
                          .setPlanExecutionId(ambiance.getPlanExecutionId())
                          .putSetupAbstractions(SetupAbstractionKeys.accountId, AmbianceUtils.getAccountId(ambiance))
                          .build();
    }
    ops.set(NodeExecutionKeys.ambiance, finalAmbiance);
  }

  private boolean shouldSetStartTimestamp(Ambiance ambiance, PlanNode planNode) {
    if (!AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_ENABLE_MANUAL_STAGE_RUN.name())
        || planNode == null) {
      return true;
    }
    return !planNode.isManualExecution();
  }

  /**
   * Increments childrenCount on parent wrapper nodes (NG_FORK, STRATEGY_V1, GROUP).
   * For GROUP nodes, we increment on the GROUP node (grandparent of the actual child).
   * This is called when a child node starts executing - increments by 1 for live counting.
   */
  private void updateParentChildrenCount(NodeExecution nodeExecution) {
    String parentId = nodeExecution.getParentId();
    if (isEmpty(parentId)) {
      return;
    }

    try {
      NodeExecution parentNodeExecution = nodeExecutionService.getWithFieldsIncluded(
          parentId, Set.of(NodeExecutionKeys.uuid, NodeExecutionKeys.stepType, NodeExecutionKeys.parentId));

      if (parentNodeExecution == null || parentNodeExecution.getStepType() == null) {
        return;
      }

      // Check if parent is a wrapper type (NG_FORK, STRATEGY_V1)
      String parentStepType = parentNodeExecution.getStepType().getType();
      if (NGCommonUtilPlanCreationConstants.NG_FORK.equals(parentStepType)
          || NGCommonUtilPlanCreationConstants.STRATEGY_V1.equals(parentStepType)) {
        incrementChildrenCount(parentNodeExecution);
        return;
      }

      // Special handling for GROUP/UNIFIED STAGE:
      // - Step groups: GROUP -> NG_SECTION (steps) -> actual steps
      // - Stage groups: GROUP -> STAGES_STEP (stages) -> actual stages
      // - IntegrationStageStepPMS -> steps -> actual steps
      // If parent is an intermediate node inside GROUP, increment on grandparent (GROUP)
      if (isGroupStepsOrStagesNode(parentNodeExecution) && isNotEmpty(parentNodeExecution.getParentId())) {
        NodeExecution grandparentNodeExecution = nodeExecutionService.getWithFieldsIncluded(
            parentNodeExecution.getParentId(), Set.of(NodeExecutionKeys.uuid, NodeExecutionKeys.stepType));

        if (grandparentNodeExecution != null && grandparentNodeExecution.getStepType() != null
            && (NGCommonUtilPlanCreationConstants.GROUP.equals(grandparentNodeExecution.getStepType().getType())
                || NGCommonUtilPlanCreationConstants.UNIFIED_STAGE.equals(
                    grandparentNodeExecution.getStepType().getType()))) {
          // Increment childrenCount on the GROUP node
          incrementChildrenCount(grandparentNodeExecution);
        }
      }
    } catch (Exception e) {
      // Log and continue - don't fail the main execution flow
      log.warn("Failed to update childrenCount for parent of node {}: {}", nodeExecution.getUuid(), e.getMessage());
    }
  }

  /**
   * Checks if the node is an intermediate node inside a GROUP (steps/stages node).
   * - For step groups step type: NG_SECTION_WITH_ROLLBACK_INFO
   * - For stage groups stage type: STAGES_STEP
   */
  private boolean isGroupStepsOrStagesNode(NodeExecution nodeExecution) {
    if (nodeExecution == null || nodeExecution.getStepType() == null) {
      return false;
    }
    String stepType = nodeExecution.getStepType().getType();
    return NGCommonUtilPlanCreationConstants.STAGES_STEP.equals(stepType)
        || NGCommonUtilPlanCreationConstants.NG_SECTION_WITH_ROLLBACK_INFO.equals(stepType);
  }

  /**
   * Increments childrenCount by 1 for wrapper nodes (NG_FORK, STRATEGY_V1, GROUP).
   * This is a live count - each time a child starts, we increment by 1.
   * Uses atomic $inc operation (works because childrenCount is initialized to 0 in NodeExecution).
   */
  private void incrementChildrenCount(NodeExecution wrapperNodeExecution) {
    nodeExecutionService.updateV2(wrapperNodeExecution.getUuid(), ops -> ops.inc(NodeExecutionKeys.childrenCount, 1));
  }
}
