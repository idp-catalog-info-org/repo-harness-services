/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.constants.OrchestrationPublisherName;
import io.harness.constants.OrchestrationStepTypes;
import io.harness.engine.executions.plan.service.DagExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.pms.execution.strategy.plannode.DagExecutionCallback;
import io.harness.engine.pms.resume.callback.resume.EngineResumeCallback;
import io.harness.exception.InvalidRequestException;
import io.harness.plan.Node;
import io.harness.plan.PlanNode;
import io.harness.plancreator.common.dependencyUtils.DependencyUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.DependencyGraphProto;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class DagExecutionServiceImpl implements DagExecutionService {
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;
  @Inject private PlanService planService;

  private final String DAG_CALLBACK_ID_PREFIX = "dag";
  private final String DAG_LEAF_CALLBACK_PREFIX = "leaf";
  private final String UNDERSCORE_DELIMITER = "_";
  /**
   * Register wait instances for stage dependencies
   * Each stage waits for its dependencies to complete before it can start
   * Pattern: Multiple callback registrations per dependency → Each dependency gets its own callback registration
   */
  @Override
  public void registerDependencyWaitInstances(
      Map<String, List<String>> dependencyGraph, String nodeExecutionId, Ambiance ambiance, String nodeId) {
    for (Map.Entry<String, List<String>> entry : dependencyGraph.entrySet()) {
      String targetNodeId = entry.getKey();
      List<String> dependencies = entry.getValue();

      if (!dependencies.isEmpty()) {
        log.info("Registering wait instances for node: {} to wait on dependencies: {}", targetNodeId, dependencies);
        List<String> callbackIds = dependencies.stream()
                                       .map(dependencyId -> generateCallbackId(nodeExecutionId, dependencyId, false))
                                       .toList();
        // Create DAG callback for this specific dependency completion
        DagExecutionCallback nodeDependencyCallback = DagExecutionCallback.builder()
                                                          .ambiance(ambiance)
                                                          .prevPlanExecutionId(ambiance.getPlanExecutionId())
                                                          .targetStageNodeId(targetNodeId)
                                                          .prevNodeId(nodeId)
                                                          .build();

        // Register wait instance for this node to wait on its specific dependency
        waitNotifyEngine.waitForAllOn(publisherName, nodeDependencyCallback, callbackIds.toArray(new String[0]));
        log.info("Successfully registered {} wait instances for node: {}", dependencies.size(), targetNodeId);
      } else {
        log.info("Stage: {} has no dependencies, will node immediately", targetNodeId);
      }
    }
  }

  /**
   * Register wait instances for the parent node(in the current case, it's the stages node) to wait on leaf nodes
   * This ensures the parent node completes only when all leaf nodes reach terminal state
   */
  @VisibleForTesting
  public void registerLeafNodesWaitInstances(Ambiance ambiance, List<String> leafNodeIds, String nodeExecutionId) {
    if (!leafNodeIds.isEmpty()) {
      List<String> callbackIds = generateCallbackIds(nodeExecutionId, leafNodeIds);

      log.info("Registering node wait instance for nodeExecutionId: {} to wait on leaf nodes: {} with callbackId: {}",
          nodeExecutionId, leafNodeIds, callbackIds);

      // Create callback for stages completion
      EngineResumeCallback callback = EngineResumeCallback.builder().ambiance(ambiance).build();
      String waitInstanceId =
          waitNotifyEngine.waitForAllOn(publisherName, callback, callbackIds.toArray(new String[0]));
      log.info("Parent node registered a waitInstance with id: {}", waitInstanceId);
    } else {
      log.warn("No leaf nodes found for the parent node: {}, this might indicate an issue with dependency graph",
          nodeExecutionId);
      throw new InvalidRequestException(
          "A dependency Graph is defined without leaf nodes. Please check the configuration");
    }
  }

  @VisibleForTesting
  List<String> generateCallbackIds(String nodeExecutionId, List<String> dependencyIds) {
    return dependencyIds.stream()
        .map(dependencyId -> generateCallbackId(nodeExecutionId, dependencyId, true))
        .collect(Collectors.toList());
  }

  @VisibleForTesting
  @Override
  public String generateCallbackId(String parentNodeExecutionId, String currentNodeId, boolean isLeafNode) {
    if (parentNodeExecutionId != null) {
      if (isLeafNode) {
        return new StringJoiner(UNDERSCORE_DELIMITER)
            .add(DAG_CALLBACK_ID_PREFIX)
            .add(DAG_LEAF_CALLBACK_PREFIX)
            .add(parentNodeExecutionId)
            .add(currentNodeId)
            .toString();
      }
      return new StringJoiner(UNDERSCORE_DELIMITER)
          .add(DAG_CALLBACK_ID_PREFIX)
          .add(parentNodeExecutionId)
          .add(currentNodeId)
          .toString();
    }
    return null;
  }

  @Override
  public void fireDagCallbacksForNoAdviserStage(Ambiance ambiance, Node node, Status status) {
    try {
      if (!isStageNode(node)) {
        return;
      }

      PlanNode parentNode = findParentWithDependencyGraph(ambiance);
      if (parentNode == null) {
        return;
      }

      String parentNodeExecutionId = AmbianceUtils.getParentNodeExecutionId(ambiance);
      String nodeId = node.getUuid();
      String runtimeId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
      Level currentLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
      String identifier = currentLevel != null ? currentLevel.getIdentifier() : node.getIdentifier();
      DependencyGraphProto dependencyGraph = parentNode.getDependencyGraph();

      if (OrchestrationStepTypes.PIPELINE_ROLLBACK_STAGE.equals(node.getStepType().getType())) {
        fireAllLeafCallbacksForCompletedRollback(
            nodeId, identifier, runtimeId, status, parentNodeExecutionId, dependencyGraph);
        return;
      }

      StepResponseNotifyData responseData = StepResponseNotifyData.builder()
                                                .nodeUuid(nodeId)
                                                .identifier(identifier)
                                                .nodeExecutionId(runtimeId)
                                                .status(status)
                                                .build();

      List<String> dependentNodeIds = DependencyUtils.findDependentNodes(nodeId, dependencyGraph);
      boolean isLeaf = dependentNodeIds.isEmpty();
      String callbackId = generateCallbackId(parentNodeExecutionId, nodeId, isLeaf);
      waitNotifyEngine.doneWith(callbackId, responseData);
    } catch (Exception e) {
      log.warn("Error firing DAG callbacks for no-adviser stage: {}", node != null ? node.getUuid() : "null", e);
    }
  }

  private boolean isStageNode(Node node) {
    return node != null && node.getStepType() != null && node.getStepType().getStepCategory() == StepCategory.STAGE;
  }

  private void fireAllLeafCallbacksForCompletedRollback(String nodeId, String identifier, String runtimeId,
      Status status, String parentNodeExecutionId, DependencyGraphProto dependencyGraph) {
    try {
      Map<String, List<String>> depMap = DependencyUtils.convertDependencyGraphToMap(dependencyGraph);
      List<String> leafNodeIds = DependencyUtils.calculateLeafNodes(depMap);

      StepResponseNotifyData responseData = StepResponseNotifyData.builder()
                                                .nodeUuid(nodeId)
                                                .identifier(identifier)
                                                .nodeExecutionId(runtimeId)
                                                .status(status)
                                                .build();

      for (String leafNodeId : leafNodeIds) {
        String leafCallbackId = generateCallbackId(parentNodeExecutionId, leafNodeId, true);
        waitNotifyEngine.doneWith(leafCallbackId, responseData);
      }
    } catch (Exception e) {
      log.warn("Error firing leaf callbacks for rollback completion in no-adviser path", e);
    }
  }

  private PlanNode findParentWithDependencyGraph(Ambiance ambiance) {
    String parentNodeId = ambiance.getLevels(ambiance.getLevelsCount() - 2).getSetupId();
    Node parentNode = planService.fetchNode(ambiance.getPlanId(), parentNodeId);
    if (parentNode instanceof PlanNode && ((PlanNode) parentNode).hasDependencyGraph()) {
      return (PlanNode) parentNode;
    }
    return null;
  }
}
