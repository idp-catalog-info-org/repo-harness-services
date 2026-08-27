/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.WorkflowGraph;
import io.harness.cache.EntityWithAccountId;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.dto.SimplifiedOrchestrationGraphDTO;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public interface GraphGenerationService {
  OrchestrationGraph constructOldRetryGraph(String planExecutionId, NodeExecution nodeExecution, String accountId);

  OrchestrationGraph getCachedOrchestrationGraphFromDB(String planExecutionId, String accountId);
  EntityWithAccountId getCachedOrchestrationGraphWithAccountIdFromDB(String planExecutionId);

  OrchestrationGraph getCachedOrchestrationGraphFromSecondary(String accountIdentifier, String planExecutionId);

  OrchestrationGraph getCachedOrchestrationGraphFromSecondary(
      String accountIdentifier, String planExecutionId, String nodeExecutionId);

  EntityWithAccountId getCachedOrchestrationGraphFromSecondaryWithAccountId(
      String planExecutionId, String accountIdentifier);

  void cacheOrchestrationGraphInDB(OrchestrationGraph adjacencyListInternal, String accountIdentifier);

  OrchestrationGraphDTO generateOrchestrationGraphV2(String accountIdentifier, String planExecutionId);

  SimplifiedOrchestrationGraphDTO generateSimplifiedOrchestrationGraphV2(
      String accountIdentifier, String planExecutionId);

  OrchestrationGraphDTO generatePartialOrchestrationGraphFromSetupNodeIdAndExecutionId(
      String accountIdentifier, String startingSetupNodeId, String planExecutionId, String startingExecutionId);

  void sendUpdateEventIfAny(PipelineExecutionSummaryEntity executionSummaryEntity);
  OrchestrationGraph buildOrchestrationGraph(String planExecutionId);

  OrchestrationGraph buildOrchestrationGraphForNodeExecution(
      String planExecutionId, String nodeExecutionId, List<NodeExecution> nodeExecutions);

  boolean updateGraph(String planExecutionId);

  boolean updateGraphWithWaitLock(String planExecutionId);

  void validateAndUpdateFromNodeExecution(String planExecutionId, OrchestrationGraph orchestrationGraph);

  /**
   * Delete all GraphMetadata for given planExecutionIds
   * It will delete all related OrchestrationEventLog, cacheEntities
   * @param planExecutionIds
   */
  void deleteAllGraphMetadataForGivenExecutionIds(
      Set<String> planExecutionIds, boolean retainPipelineExecutionDetailsAfterDelete, String accountId);

  void deleteOutputsForStepInGraph(
      String accountIdentifier, String planExecutionId, String stepType, Long endTs, Status status);

  /**
   * Maps an OrchestrationGraph to a WorkflowGraph for visualization purposes.
   *
   * @param accountIdentifier The account identifier
   * @param planExecutionId The plan execution ID
   * @param nodeExecutionId The starting node execution ID for traversal (optional)
   * @param depth The maximum depth to traverse from the starting node
   * @return A WorkflowGraph containing nodes and relations up to the specified depth
   */
  WorkflowGraph generateWorkflowGraph(
      String accountIdentifier, String planExecutionId, String nodeExecutionId, int depth);

  /**
   * Get stage layout nodes from PostgreSQL normalized storage.
   * This returns the layoutNodeMap derived from graph_vertex table instead of the stored
   * value in PipelineExecutionSummaryEntity.
   *
   * @param accountIdentifier The account identifier for feature flag evaluation
   * @param planExecutionId The plan execution ID
   * @return Map of stage node IDs to GraphLayoutNodeDTO, or null if feature flag is disabled or no data
   */
  Map<String, GraphLayoutNodeDTO> getStageLayoutNodesFromPostgres(String accountIdentifier, String planExecutionId);

  /**
   * Get pipeline-level module info from PostgreSQL normalized storage.
   * This retrieves the module_info from the PIPELINE_SECTION vertex.
   *
   * @param planExecutionId The plan execution ID
   * @return Map of module info, or null if not found
   */
  Map<String, Object> getPipelineModuleInfoFromPostgres(String planExecutionId);

  /**
   * Get a partial orchestration graph (subtree) from CDC PostgreSQL storage for old retried nodes.
   * Returns null if CDC is not enabled for this execution or the graph is not found.
   *
   * @param accountId The account identifier
   * @param planExecutionId The plan execution ID
   * @param nodeExecutionId The root node execution ID for the subtree (old_retry=true node)
   * @return OrchestrationGraph for the subtree, or null if not available via CDC
   */
  OrchestrationGraph getCdcSubGraph(String accountId, String planExecutionId, String nodeExecutionId);
}
