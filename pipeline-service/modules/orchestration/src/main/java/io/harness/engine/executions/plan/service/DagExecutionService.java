/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;

import java.util.List;
import java.util.Map;

/**
 * Service for managing DAG execution in pipeline orchestration.
 * Handles any specific functionality for dependency-based node execution.
 */
@OwnedBy(PIPELINE)
public interface DagExecutionService {
  /**
   * Registers wait instances for node dependencies.
   * Each node waits for its dependencies to complete before starting.
   *
   * @param dependencyGraph Map of stage UUID to list of dependency node UUIDs
   * @param nodeExecutionId Parent node execution ID
   * @param ambiance Execution context
   * @param nodeId Parent node ID
   */
  void registerDependencyWaitInstances(
      Map<String, List<String>> dependencyGraph, String nodeExecutionId, Ambiance ambiance, String nodeId);

  /**
   * Registers wait instances for parent node to wait on leaf nodes completion.
   * Leaf nodes are nodes that no other node depend on.
   *
   * @param ambiance Execution context
   * @param leafNodeIds List of leaf node UUIDs
   * @param nodeExecutionId Parent node execution ID
   */
  void registerLeafNodesWaitInstances(Ambiance ambiance, List<String> leafNodeIds, String nodeExecutionId);

  /**
   * Generates callback ID for DAG wait instances.
   *
   * @param parentNodeExecutionId Parent stages node execution ID
   * @param currentNodeId Current stage node ID
   * @param isLeafNode True for leaf nodes (adds "leaf" prefix)
   * @return Generated callback ID
   */
  String generateCallbackId(String parentNodeExecutionId, String currentNodeId, boolean isLeafNode);

  /**
   * Fires DAG callbacks for stages that complete without any advisers.
   * This ensures the parent stages node is notified of the stage completion,
   * allowing dependent stages to be initiated or the pipeline to complete.
   *
   * @param ambiance the execution context
   * @param node the completed stage node (can be PlanNode or IdentityPlanNode)
   * @param status the completion status of the stage
   */
  void fireDagCallbacksForNoAdviserStage(Ambiance ambiance, Node node, Status status);
}
