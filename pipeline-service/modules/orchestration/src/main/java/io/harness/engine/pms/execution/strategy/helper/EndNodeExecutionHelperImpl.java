/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.helper;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.springdata.SpringDataMongoUtils.setUnset;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.execution.strategy.helper.intfc.EndNodeExecutionHelper;
import io.harness.engine.pms.execution.strategy.plannode.PlanNodeExecutionStrategy;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.expression.common.ExpressionMode;
import io.harness.plan.PlanNode;
import io.harness.plancreator.exports.ExportConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.io.StepOutcomeProto;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class EndNodeExecutionHelperImpl implements EndNodeExecutionHelper {
  @Inject private PmsOutcomeService pmsOutcomeService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanNodeExecutionStrategy executionStrategy;
  @Inject private PlanService planService;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;

  @Override
  public void endNodeExecutionWithNoAdvisers(
      @NonNull Ambiance ambiance, @NonNull StepResponseProto stepResponse, PlanNode planNode) {
    // Start a transaction here to fetch StepOutcomeRefs and pass to endNodeExecutions to avoid fetch OutcomeRefs twice
    List<StepOutcomeRef> outcomeRefs =
        handleOutcomes(ambiance, stepResponse.getStepOutcomesList(), stepResponse.getGraphOutcomesList(), planNode);
    // End transaction here
    NodeExecution updatedNodeExecution = finalizeNodeWithStepResponse(ambiance, stepResponse, true);
    if (updatedNodeExecution == null) {
      executionStrategy.startQueuedExecutionIfAny(ambiance);
      log.warn("Cannot process step response for nodeExecution {}", AmbianceUtils.obtainCurrentRuntimeId(ambiance));
      return;
    }
    executionStrategy.endNodeExecution(
        nodeExecutionService.getAmbiance(updatedNodeExecution), updatedNodeExecution, outcomeRefs);
  }

  @VisibleForTesting
  NodeExecution processStepResponseWithNoAdvisers(Ambiance ambiance, StepResponseProto stepResponse) {
    // Start a transaction here
    handleOutcomes(ambiance, stepResponse.getStepOutcomesList(), stepResponse.getGraphOutcomesList(), null);
    // End transaction here

    return finalizeNodeWithStepResponse(ambiance, stepResponse, true);
  }

  @VisibleForTesting
  NodeExecution finalizeNodeWithStepResponse(
      Ambiance ambiance, StepResponseProto stepResponse, boolean setAdvisorsProcessed) {
    boolean isOptimizationWriteDisabled = AmbianceUtils.checkIfFeatureFlagEnabled(
        ambiance, FeatureName.PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION.name());

    boolean shouldSetAdvisorsProcessed = !isOptimizationWriteDisabled && setAdvisorsProcessed;

    Consumer<Update> opsConsumer = getUpdateConsumer(stepResponse, shouldSetAdvisorsProcessed);

    return nodeExecutionService.updateStatusWithOps(AmbianceUtils.obtainCurrentRuntimeId(ambiance),
        stepResponse.getStatus(), opsConsumer, EnumSet.noneOf(Status.class));
  }

  private Consumer<Update> getUpdateConsumer(StepResponseProto stepResponse, boolean shouldSetAdvisorsProcessed) {
    Consumer<Update> baseOps = ops -> {
      setUnset(ops, NodeExecutionKeys.failureInfo, AmbianceUtils.truncateFailureInfo(stepResponse.getFailureInfo()));
      setUnset(ops, NodeExecutionKeys.unitProgresses, stepResponse.getUnitProgressList());
      setUnset(ops, NodeExecutionKeys.progressData + "." + NodeExecutionKeys.unitProgresses,
          stepResponse.getUnitProgressList());
    };

    return shouldSetAdvisorsProcessed ? baseOps.andThen(ops -> {
      ops.set(NodeExecutionKeys.advisorsProcessed, true);
      ops.set(NodeExecutionKeys.processingEvent, false);
    })
                                      : baseOps.andThen(ops -> { ops.set(NodeExecutionKeys.processingEvent, false); });
  }

  @VisibleForTesting
  List<StepOutcomeRef> handleOutcomes(Ambiance ambiance, List<StepOutcomeProto> stepOutcomeProtos,
      List<StepOutcomeProto> graphOutcomesList, PlanNode planNode) {
    List<StepOutcomeRef> outcomeRefs = new ArrayList<>();

    if (EmptyPredicate.isNotEmpty(stepOutcomeProtos)) {
      stepOutcomeProtos.forEach(proto -> {
        if (isNotEmpty(proto.getOutcome())) {
          String instanceId =
              pmsOutcomeService.consume(ambiance, proto.getName(), proto.getOutcome(), proto.getGroup());
          outcomeRefs.add(StepOutcomeRef.newBuilder().setName(proto.getName()).setInstanceId(instanceId).build());
        }
      });
      graphOutcomesList.forEach(proto -> {
        if (isNotEmpty(proto.getOutcome())) {
          String instanceId =
              pmsOutcomeService.consume(ambiance, proto.getName(), proto.getOutcome(), proto.getGroup());
          outcomeRefs.add(StepOutcomeRef.newBuilder().setName(proto.getName()).setInstanceId(instanceId).build());
        }
      });
    }

    Level currentLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
    // Since we will have the exports at only Stage and stepGroup level. So fetching the planNode and checking if
    // exports is empty or not is unnecessary for step and other nodes.
    if (currentLevel.getStepType().getStepCategory() == StepCategory.STAGE
        || currentLevel.getStepType().getStepCategory() == StepCategory.STEP_GROUP) {
      boolean isOptimizationDisabled = AmbianceUtils.checkIfFeatureFlagEnabled(
          ambiance, FeatureName.PIPE_DISABLE_SKIP_FETCH_ON_END_NODE_EXECUTION.name());
      boolean canUseOptimizedPlanNode = !isOptimizationDisabled && planNode != null;
      PlanNode effectivePlanNode = canUseOptimizedPlanNode
          ? planNode
          : planService.fetchNode(ambiance.getPlanId(), AmbianceUtils.obtainCurrentSetupId(ambiance));
      if (EmptyPredicate.isNotEmpty(effectivePlanNode.getExports())) {
        String instanceId = pmsOutcomeService.consume(ambiance, YAMLFieldNameConstants.EXPORTS,
            getResolvedJsonForExports(ambiance, effectivePlanNode.getExports()), "");
        outcomeRefs.add(
            StepOutcomeRef.newBuilder().setName(YAMLFieldNameConstants.EXPORTS).setInstanceId(instanceId).build());
      }
    }
    return outcomeRefs;
  }

  private String getResolvedJsonForExports(Ambiance ambiance, Map<String, ExportConfig> exportConfigMap) {
    Map<String, Object> exportsValueMap = new HashMap<>();
    // Using only the value field of ExportConfig.
    exportConfigMap.forEach((key, value) -> exportsValueMap.put(key, value.getValue()));
    pmsEngineExpressionService.resolve(
        ambiance, exportsValueMap, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    return RecastOrchestrationUtils.toJson(exportsValueMap);
  }

  @Override
  public NodeExecution handleStepResponsePreAdviser(
      Ambiance ambiance, StepResponseProto stepResponse, PlanNode planNode) {
    log.info("Handling Step response before calling advisers");
    return processStepResponsePreAdvisers(ambiance, stepResponse, planNode);
  }

  @VisibleForTesting
  NodeExecution processStepResponsePreAdvisers(Ambiance ambiance, StepResponseProto stepResponse, PlanNode planNode) {
    handleOutcomes(ambiance, stepResponse.getStepOutcomesList(), stepResponse.getGraphOutcomesList(), planNode);
    // Do NOT set advisorsProcessed here; advisers will be processed next and will set it
    return finalizeNodeWithStepResponse(ambiance, stepResponse, false);
  }

  @Override
  public FailureData decorateFailureData(Ambiance ambiance, String errorMessage, FailureData failureData) {
    String stageIdentifier = AmbianceUtils.getStageIdentifierFromAmbiance(ambiance);
    String stepIdentifier = AmbianceUtils.getStepIdentifierFromAmbiance(ambiance);
    if (failureData != null) {
      FailureData.Builder failureDataBuilder = failureData.toBuilder();
      if (EmptyPredicate.isEmpty(failureDataBuilder.getStepIdentifier())) {
        failureDataBuilder.setStepIdentifier(stepIdentifier);
      }
      if (EmptyPredicate.isEmpty(failureDataBuilder.getStageIdentifier())) {
        failureDataBuilder.setStageIdentifier(stageIdentifier);
      }
      return failureDataBuilder.build();
    } else {
      return FailureData.newBuilder()
          .setMessage(errorMessage)
          .setStepIdentifier(stepIdentifier)
          .setStageIdentifier(stageIdentifier)
          .build();
    }
  }
}
