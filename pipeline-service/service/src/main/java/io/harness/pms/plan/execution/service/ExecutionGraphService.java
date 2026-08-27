/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WorkflowGraph;
import io.harness.pms.plan.execution.beans.dto.NodeExecutionSubGraphResponse;

@OwnedBy(PIPELINE)
public interface ExecutionGraphService {
  NodeExecutionSubGraphResponse getNodeExecutionSubGraph(
      String nodeExecutionId, String planExecutionId, String accountId, Long executionStartTs);

  /**
   * Gets a workflow graph for visualization purposes.
   *
   * @param planExecutionId The plan execution ID
   * @param nodeExecutionId The starting node execution ID for traversal (optional)
   * @param depth The maximum depth to traverse from the starting node
   * @param accountId The account identifier
   * @return A WorkflowGraph containing nodes and relations up to the specified depth
   */
  WorkflowGraph getWorkflowGraph(String planExecutionId, String nodeExecutionId, int depth, String accountId);
}
