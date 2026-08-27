/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.execution.NodeExecution.NodeExecutionKeys;

import io.harness.advisers.pipelinerollback.output.OnFailPipelineRollbackOutput;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.DagExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.pms.advise.AdviserResponseHandler;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.plan.PlanNode;
import io.harness.plancreator.common.dependencyUtils.DependencyUtils;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.DependencyGraphProto;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.utils.execution.ExecutionModeUtils;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@Slf4j
public class NextStepHandler implements AdviserResponseHandler {
  @Inject private OrchestrationEngine engine;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanService planService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private FailureStrategyAdviserHandlerUtils failureStrategyAdviserHandlerUtils;
  @Inject DagExecutionService dagExecutionService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public void handleAdvise(NodeExecution prevNodeExecution, AdviserResponse adviserResponse) {
    NextStepAdvise advise = adviserResponse.getNextStepAdvise();
    Ambiance ambiance = nodeExecutionService.getAmbiance(prevNodeExecution);

    if (!ExecutionModeUtils.isRollbackMode(ambiance.getMetadata().getExecutionMode())) {
      failureStrategyAdviserHandlerUtils.interruptPipelineIfFailAll(prevNodeExecution, ambiance, advise.getFailAll());
    }
    // Check if this is a DAG execution scenario
    if (isDagExecution(ambiance, prevNodeExecution)) {
      handleDagNodeCompletion(ambiance, prevNodeExecution, advise.getNextNodeId());
    } else {
      ExecutionMode executionMode = prevNodeExecution.getExecutionMode();
      runNextNode(prevNodeExecution, advise.getNextNodeId(), executionMode);
    }
  }

  public void runNextNode(NodeExecution prevNodeExecution, String nextNodeId, ExecutionMode executionMode) {
    Ambiance ambiance = nodeExecutionService.getAmbiance(prevNodeExecution);
    if (EmptyPredicate.isNotEmpty(nextNodeId)) {
      Node nextNode = Preconditions.checkNotNull(planService.fetchNode(prevNodeExecution.getPlanId(), nextNodeId));
      nextNode = createIdentityNodeIfRequired(nextNode, prevNodeExecution, executionMode);
      String runtimeId = generateUuid();
      // Update NodeExecution nextId and endTs
      nodeExecutionService.updateV2(prevNodeExecution.getUuid(),
          ops -> ops.set(NodeExecutionKeys.nextId, runtimeId).set(NodeExecutionKeys.endTs, System.currentTimeMillis()));
      Ambiance cloned = AmbianceUtils.cloneForFinish(ambiance, PmsLevelUtils.buildLevelFromNode(runtimeId, nextNode));
      // prevNodeExecution will not contain nextId and endTs
      engine.runNextNode(cloned, nextNode, prevNodeExecution, null);
    } else {
      engine.endNodeExecution(ambiance);
    }
  }

  @VisibleForTesting
  Node createIdentityNodeIfRequired(Node nextNode, NodeExecution prevNodeExecution, ExecutionMode executionMode) {
    // if in rollback mode, the plan node received is to be preserved, then return the node as is.
    // For failed nodes, we need to create different identity nodes corresponding to each node executions,
    // in case parent is an identity plan node.
    if (checkIfSameNodeIsRequired(nextNode, executionMode)) {
      return nextNode;
    }
    if (EmptyPredicate.isEmpty(prevNodeExecution.getParentId())) {
      log.error("ParentId is empty for nodeExecution: {} and planExecutionId: {}", prevNodeExecution.getUuid(),
          prevNodeExecution.getPlanExecutionId());
      return nextNode;
    }
    NodeExecution parentNodeExecution =
        nodeExecutionService.getWithFieldsIncluded(prevNodeExecution.getParentId(), NodeProjectionUtils.withAmbiance);
    // Create IdentityNode for nextNode when the parentNodeExecution.node is of type IdentityNode
    if (parentNodeExecution.getNodeType() == NodeType.IDENTITY_PLAN_NODE) {
      NodeExecution originalNodeExecution = nodeExecutionService.getWithFieldsIncluded(
          prevNodeExecution.getOriginalNodeExecutionId(), NodeProjectionUtils.withNextId);

      // Pass the "NextNodeId" of the original node execution as the last parameter in the mapPlanNodeToIdentityNode()
      // function to designate it as the identity node for that NodeExecution.
      String nextNodeId = getNextNodeId(originalNodeExecution);
      Node identityNode = IdentityPlanNode.mapPlanNodeToIdentityNode(UUIDGenerator.generateUuid(), nextNode,
          nextNode.getIdentifier(), nextNode.getName(), nextNode.getStepType(), nextNodeId);
      planService.saveIdentityNodesForMatrix(Collections.singletonList(identityNode), prevNodeExecution.getPlanId());
      return identityNode;
    }
    return nextNode;
  }

  /*
  In retry failure strategy, with single node id, we have multiple node execution.
  Next node id should be equal to last retried node execution id. OriginalNodeExecution NextNodeId will be the  first
  retried node id hence we are fetching latest retried node execution fetchNodeExecutionForPlanNodeAndRetriedId().
   */
  private String getNextNodeId(NodeExecution originalNodeExecution) {
    NodeExecution nextNodeExecution = nodeExecutionService.getWithFieldsIncluded(
        originalNodeExecution.getNextId(), NodeProjectionUtils.fieldsForIdentityStrategyStep);

    // Making a db call only if nextNodeExecution was retried else return originalNodeExecution.getNextId()
    if (nextNodeExecution.getOldRetry()) {
      // Due to multiple combinations of planNodeId and oldRetry as false, we are adding the third parameters
      // (retryListId) eg -> in strategy
      NodeExecution nextNonRetriedNodeExecution =
          nodeExecutionService.fetchNodeExecutionForPlanNodeAndRetriedId(nextNodeExecution.getPlanExecutionId(),
              nextNodeExecution.getNodeId(), false, Collections.singletonList(originalNodeExecution.getNextId()));

      return nextNonRetriedNodeExecution != null ? nextNonRetriedNodeExecution.getUuid()
                                                 : originalNodeExecution.getNextId();
    }
    return originalNodeExecution.getNextId();
  }

  boolean checkIfSameNodeIsRequired(Node nextNode, ExecutionMode executionMode) {
    //  For nodes (before retry stage) without strategy, we would still return IdentityPlanNode because of last
    //  return statement in createIdentityNodeIfRequired.
    boolean isRollbackMode = ExecutionModeUtils.isRollbackMode(executionMode);
    if (nextNode.getNodeType() == NodeType.IDENTITY_PLAN_NODE) {
      return isRollbackMode;
    }
    return isRollbackMode && ((PlanNode) nextNode).isPreserveInRollbackMode();
  }

  private PlanNode getParentNode(Ambiance ambiance, NodeExecution currentNodeExecution) {
    try {
      if (ambiance.getLevelsCount() > 1) {
        String parentNodeId = ambiance.getLevels(ambiance.getLevelsCount() - 2).getSetupId();
        Node parentNode = planService.fetchNode(ambiance.getPlanId(), parentNodeId);
        if (parentNode instanceof PlanNode && ((PlanNode) parentNode).hasDependencyGraph()) {
          return (PlanNode) parentNode;
        }
      }
      log.debug("No parent node with dependency graph found for current node: {}", currentNodeExecution.getNodeId());
    } catch (Exception e) {
      log.warn("Error fetching parent node", e);
    }
    return null;
  }

  private boolean isDagExecution(Ambiance ambiance, NodeExecution currentNodeExecution) {
    try {
      // Check if parent node has dependency graph
      PlanNode parentStagesNode = getParentNode(ambiance, currentNodeExecution);
      return parentStagesNode != null && parentStagesNode.hasDependencyGraph();
    } catch (Exception e) {
      log.warn("Error checking DAG execution status", e);
      return false;
    }
  }

  private void handleDagNodeCompletion(Ambiance ambiance, NodeExecution currentNodeExecution, String nextNodeId) {
    try {
      boolean rollbackTriggered = shouldTriggerPipelineRollback(ambiance);
      if (rollbackTriggered) {
        String parentNodeExecutionId = AmbianceUtils.getParentNodeExecutionId(ambiance);
        long activeCount =
            nodeExecutionService.findCountByParentIdAndStatusIn(parentNodeExecutionId, StatusUtils.activeStatuses());

        if (activeCount > 0) {
          log.info("DAG rollback deferred: {} stages still active under parent {}", activeCount, parentNodeExecutionId);
          return;
        }

        if (!tryClaimDagRollbackInitiation(ambiance)) {
          return;
        }

        runNextNode(currentNodeExecution, nextNodeId, currentNodeExecution.getExecutionMode());
        return;
      }

      // Fetch dependency graph
      DependencyGraphProto dependencyGraph = fetchDependencyGraph(ambiance, currentNodeExecution);
      if (dependencyGraph != null && !dependencyGraph.getEntriesList().isEmpty()) {
        Node currentPlanNode =
            planService.fetchNode(currentNodeExecution.getPlanId(), currentNodeExecution.getNodeId());
        String parentNodeExecutionId = AmbianceUtils.getParentNodeExecutionId(ambiance);
        List<String> nextIds =
            findAndFetchDependentNodeExecutions(currentNodeExecution, currentPlanNode.getUuid(), dependencyGraph);
        StepResponseNotifyData responseData = StepResponseNotifyData.builder()
                                                  .nodeUuid(currentNodeExecution.getNodeId())
                                                  .identifier(currentNodeExecution.getIdentifier())
                                                  .nodeExecutionId(currentNodeExecution.getUuid())
                                                  .status(currentNodeExecution.getStatus())
                                                  .nodeExecutionEndTs(currentNodeExecution.getEndTs())
                                                  .build();
        String callbackId = null;
        if (!nextIds.isEmpty()) {
          // Update nextIds for dependent nodes and notify completion
          updateNextIds(nextIds, currentNodeExecution);
          for (String nextId : nextIds) {
            callbackId =
                dagExecutionService.generateCallbackId(parentNodeExecutionId, currentNodeExecution.getNodeId(), false);
            log.info("Node {} completed, notifying dependent nodes: {}, callbackId: {}",
                currentNodeExecution.getNodeId(), nextId, callbackId);
          }
        } else { // will be a leaf node case
          callbackId =
              dagExecutionService.generateCallbackId(parentNodeExecutionId, currentNodeExecution.getNodeId(), true);
        }
        waitNotifyEngine.doneWith(callbackId, responseData);
        log.info("DAG node completion handled successfully for node: {} with dependent nodes: {}",
            currentNodeExecution.getNodeId(), nextIds);
      } else {
        log.info("No Dependency Graph found in the parent node");
      }
    } catch (Exception e) {
      log.error("Error handling DAG completion", e);
    }
  }

  private boolean shouldTriggerPipelineRollback(Ambiance ambiance) {
    try {
      // Check if already in rollback mode - if so, don't trigger again
      if (AmbianceUtils.isRollbackModeExecution(ambiance)) {
        return false;
      }

      // Check for USE_PIPELINE_ROLLBACK_STRATEGY sweeping output
      OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.USE_PIPELINE_ROLLBACK_STRATEGY));

      return optionalSweepingOutput.isFound();
    } catch (Exception ex) {
      log.warn("Error checking for pipeline rollback sweeping output in DAG execution", ex);
    }
    return false;
  }

  private static final String DAG_PIPELINE_ROLLBACK_INITIATED = "DAG_PIPELINE_ROLLBACK_INITIATED";

  private boolean tryClaimDagRollbackInitiation(Ambiance ambiance) {
    try {
      executionSweepingOutputService.consume(ambiance, DAG_PIPELINE_ROLLBACK_INITIATED,
          OnFailPipelineRollbackOutput.builder().shouldStartPipelineRollback(true).build(),
          StepCategory.PIPELINE.name());
      return true;
    } catch (Exception e) {
      log.info("Failed to claim DAG rollback initiation (likely already claimed): {}", e.getMessage());
      return false;
    }
  }

  private DependencyGraphProto fetchDependencyGraph(Ambiance ambiance, NodeExecution currentNodeExecution) {
    PlanNode parentNode = getParentNode(ambiance, currentNodeExecution);
    if (parentNode != null && parentNode.hasDependencyGraph()) {
      return parentNode.getDependencyGraph();
    }
    return DependencyGraphProto.getDefaultInstance();
  }

  private void updateNextIds(List<String> nextIds, NodeExecution currentNodeExecution) {
    try {
      nodeExecutionService.update(
          currentNodeExecution.getUuid(), ops -> ops.addToSet(NodeExecutionKeys.nextIds, nextIds));
      log.info("Updated nextIds for node: {} with dependent nodes: {}", currentNodeExecution.getUuid(), nextIds);
    } catch (Exception e) {
      log.error("Error updating nextIds for node: " + currentNodeExecution.getUuid(), e);
    }
  }

  /**
   * Find dependent nodes and fetch their NodeExecutionIds
   */
  private List<String> findAndFetchDependentNodeExecutions(
      NodeExecution currentNodeExecution, String currentNodeId, DependencyGraphProto dependencyGraph) {
    List<String> dependentNodeExecutionIds = new ArrayList<>();
    List<String> dependentNodeIds = DependencyUtils.findDependentNodes(currentNodeId, dependencyGraph);

    if (dependentNodeIds.isEmpty()) {
      log.debug("No dependent nodes found for currentNodeId: {}", currentNodeId);
      return dependentNodeExecutionIds;
    }
    Ambiance ambiance = nodeExecutionService.getAmbiance(currentNodeExecution);
    // Fetch NodeExecution objects with projections for each dependent node
    for (String dependentNodeId : dependentNodeIds) {
      String nodeExecutionId =
          AmbianceUtils.generateNodeExecutionId(ambiance, ambiance.getPlanExecutionId(), dependentNodeId);
      dependentNodeExecutionIds.add(nodeExecutionId);
    }
    return dependentNodeExecutionIds;
  }
}
