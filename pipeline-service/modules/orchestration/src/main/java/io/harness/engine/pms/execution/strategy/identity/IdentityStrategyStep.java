/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildrenResponseForStrategy;

import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMigrationHelper;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.pms.steps.identity.IdentityStepParameters;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.persistence.UuidAccess;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.PlanNode;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.PlanExecutionProjectionConstants;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;
import io.harness.utils.execution.ExecutionModeUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort.Direction;

@Slf4j
public class IdentityStrategyStep implements ChildrenExecutable<IdentityStepParameters> {
  @Inject NodeExecutionService nodeExecutionService;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private PlanExecutionService planExecutionService;
  @Inject PlanService planService;
  @Inject private NodeExecutionInfoService nodeExecutionInfoService;
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(NGCommonUtilPlanCreationConstants.IDENTITY_STRATEGY)
                                               .setStepCategory(StepCategory.STRATEGY)
                                               .build();

  @Override
  public ChildrenExecutableResponse obtainChildren(
      Ambiance ambiance, IdentityStepParameters stepParameters, StepInputPackage inputPackage) {
    NodeExecution originalStrategyNodeExecution = nodeExecutionService.getWithFieldsIncluded(
        stepParameters.getOriginalNodeExecutionId(), NodeProjectionUtils.fieldsForIdentityStrategyStep);
    List<NodeExecution> childrenNodeExecutions = new ArrayList<>();
    // Use original planExecutionId that belongs to the originalNodeExecutionId and not current
    // planExecutionId(ambiance.getPlanExecutionId)
    try (Stream<NodeExecution> stream = nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             originalStrategyNodeExecution.getPlanExecutionId(), stepParameters.getOriginalNodeExecutionId(),
             Direction.ASC, NodeProjectionUtils.fieldsForIdentityStrategyStep)) {
      stream.forEach(nodeExecution -> {
        // Don't want to include retried nodeIds
        if (Boolean.FALSE.equals(nodeExecution.getOldRetry())) {
          childrenNodeExecutions.add(nodeExecution);
        }
      });
    }

    List<ChildrenExecutableResponse.Child> children = getChildrenFromNodeExecutions(childrenNodeExecutions, ambiance);
    long maxConcurrency =
        originalStrategyNodeExecution.getExecutableResponses().get(0).getChildren().getMaxConcurrency();

    boolean shouldProceedIfFailed =
        originalStrategyNodeExecution.getExecutableResponses().get(0).getChildren().getShouldProceedIfFailed();
    return ChildrenExecutableResponse.newBuilder()
        .addAllChildren(children)
        .setShouldProceedIfFailed(shouldProceedIfFailed)
        .setMaxConcurrency(maxConcurrency)
        .build();
  }

  @Override
  public Class<IdentityStepParameters> getStepParametersClass() {
    return IdentityStepParameters.class;
  }

  @Override
  public StepResponse handleChildrenResponse(
      Ambiance ambiance, IdentityStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Completed  execution for Strategy Identity Step [{}]", stepParameters);
    return createStepResponseFromChildrenResponseForStrategy(responseDataMap);
  }

  private List<ChildrenExecutableResponse.Child> getChildrenFromNodeExecutions(
      List<NodeExecution> childrenNodeExecutions, Ambiance ambiance) {
    String planId = ambiance.getPlanId();
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    List<Node> identityNodesToBeCreated = new ArrayList<>();
    if (EmptyPredicate.isEmpty(childrenNodeExecutions)) {
      return children;
    }
    for (NodeExecution nodeExecution : childrenNodeExecutions) {
      // Current node (if failed) needs to be added into children as execution node only if its part of the retry stage.
      // If we see a failed step that isn't part of the retry stage, it should be added as an identity stage.
      // This allows us to create an identity node for all such executions and not just use the same IdentityPlanNode
      // pointing to one of the executions (hence copying the status).
      Node node = planService.fetchNode(planId, nodeExecution.getNodeId());

      /*
      If nodeExecution status is broken then the nodeExecution will be retried by actually executing it in the below
      conditions.
      If rollbackMode and not stage node: In rollback-mode, Stage node should be run via IdentityNode so that we can
      copy the execution steps and only execute the rollback-steps. So will not go into this If.
      If node is of type PlanNode except Rollback mode and StageNode.
       */

      if ((StatusUtils.brokeAndAbortedStatuses().contains(nodeExecution.getStatus())
              || Status.SKIPPED.equals(nodeExecution.getStatus()))
          && (checkIfRollbackModeButNotStageNode(ambiance, node)
              || checkIfPlanNodeTypeButNotRollbackModeAndStageNode(ambiance, node))) {
        children.add(
            ChildrenExecutableResponse.Child.newBuilder()
                .setChildNodeId(nodeExecution.getNodeId())
                .setStrategyMetadata(nodeExecutionInfoService.getStrategyMetadata(nodeExecution.getCurrentLevel()))
                .build());
      } else {
        Node identityNode = IdentityPlanNode.mapPlanNodeToIdentityNode(UUIDGenerator.generateUuid(), node,
            nodeExecution.getIdentifier(), nodeExecution.getName(), nodeExecution.getStepType(),
            nodeExecution.getUuid());
        children.add(
            ChildrenExecutableResponse.Child.newBuilder()
                .setChildNodeId(identityNode.getUuid())
                .setStrategyMetadata(nodeExecutionInfoService.getStrategyMetadata(nodeExecution.getCurrentLevel()))
                .build());
        identityNodesToBeCreated.add(identityNode);
      }
    }
    return filterChildrenAndSaveIdPlanNodes(ambiance, children, identityNodesToBeCreated, childrenNodeExecutions);
  }

  // Checking if the executionMode is rollback and node is not a stage node. In case of rollback only the stage strategy
  // children should run via IdentityPlanNode.
  private boolean checkIfRollbackModeButNotStageNode(Ambiance ambiance, Node node) {
    return ExecutionModeUtils.isRollbackMode(ambiance.getMetadata().getExecutionMode())
        && node.getStepCategory() != StepCategory.STAGE;
  }

  // Checking if node is of type PlanNode and its not a rollbackMode and stageNode. if its true then the node will be
  // executed.
  // Actually it should be sufficient condition to execute a node when its of type planNode. But there is an exception
  // when its in rollbackMode and its a stageNode. In that case we run it via IdentityPlanNode so that we can skip the
  // execution steps and run only the rollback steps.
  private boolean checkIfPlanNodeTypeButNotRollbackModeAndStageNode(Ambiance ambiance, Node node) {
    return !(ExecutionModeUtils.isRollbackMode(ambiance.getMetadata().getExecutionMode())
               && node.getStepCategory() == StepCategory.STAGE)
        && node instanceof PlanNode;
  }

  // Filter the children for PostExecutionRollback. Also filter the identityPlanNode to be created. Then save the
  // filtered the identityPlanNodes.
  private List<ChildrenExecutableResponse.Child> filterChildrenAndSaveIdPlanNodes(Ambiance ambiance,
      List<ChildrenExecutableResponse.Child> children, List<Node> identityNodesToBeCreated,
      List<NodeExecution> childrenNodeExecutions) {
    // Filter the children when the executionMode is PostExecutionRollback and nodeExecutions are of stage. Only those
    // children should be returned corresponding to nodeExecutionIds to rollback
    if (AmbianceUtils.checkIfFeatureFlagEnabled(
            ambiance, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK.name())
        && ExecutionMode.POST_EXECUTION_ROLLBACK.equals(ambiance.getMetadata().getExecutionMode())
        && childrenNodeExecutions.get(0).getStepType().getStepCategory() == StepCategory.STAGE) {
      PlanExecutionMetadata planExecutionMetadata =
          planExecutionMetadataService.getWithFieldsIncludedFromSecondary(AmbianceUtils.getAccountId(ambiance),
              ambiance.getPlanExecutionId(), PlanExecutionProjectionConstants.fieldsForPostProdRollback);
      boolean readSwitchEnabled =
          AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
      PlanExecution planExecution = null;
      if (readSwitchEnabled) {
        Optional<PlanExecution> planExecutionOptional = planExecutionService.getWithFieldsIncludedOptional(
            ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.postExecutionRollbackInfos));
        if (planExecutionOptional.isPresent()) {
          planExecution = planExecutionOptional.get();
        }
      }
      List<PostExecutionRollbackInfo> postExecutionRollbackInfos =
          PlanExecutionMigrationHelper.readPostExecutionRollbackInfoWithFallbackOnMetadata(
              planExecutionMetadata, planExecution);

      // Stage NodeExecutionIds that are to be rollback by PostExecution rollback.
      List<String> stageExecutionIdsToBeRollback =
          postExecutionRollbackInfos.stream().map(PostExecutionRollbackInfo::getOriginalStageExecutionId).toList();

      // Filter only those nodes that belong to the nodeExecutionId to rollback.
      identityNodesToBeCreated =
          identityNodesToBeCreated.stream()
              .filter(idNode
                  -> stageExecutionIdsToBeRollback.contains(((IdentityPlanNode) idNode).getOriginalNodeExecutionId()))
              .collect(Collectors.toList());
      List<String> identityNodeUuids = identityNodesToBeCreated.stream().map(UuidAccess::getUuid).toList();
      // Filter the children such that only children should be returned that is going to be rollback by
      // PostExecutionRollback.
      children = children.stream()
                     .filter(child -> identityNodeUuids.contains(child.getChildNodeId()))
                     .collect(Collectors.toList());
    }
    planService.saveIdentityNodesForMatrix(identityNodesToBeCreated, ambiance.getPlanId());
    return children;
  }
}
