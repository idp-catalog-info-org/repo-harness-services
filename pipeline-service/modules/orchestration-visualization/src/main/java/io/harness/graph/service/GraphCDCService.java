/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.service;

import io.harness.beans.GraphVertex;
import io.harness.beans.OrchestrationGraph;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for normalized PostgreSQL graph storage.
 * Uses individual columns instead of JSONB blobs for efficient queries and pagination.
 * The graph structure is derived from parent_id relationships in graph_vertex table.
 */
public interface GraphCDCService {
  // ============================================
  // Write Operations (called by GraphCDCConsumer)
  // ============================================

  /**
   * Batch update multiple vertices in a single database operation.
   */
  void batchUpdateVertexFields(List<GraphBatchUpdateDTOs.VertexUpdate> vertexUpdates);

  /**
   * Batch append/update outcomes.
   * CREATE events use nodeExecutionId to find the vertex and store documentId in outcome_instance_ids.
   * UPDATE events use documentId to find the vertex via outcome_instance_ids array.
   */
  void batchAppendOutcomes(List<GraphBatchUpdateDTOs.OutcomeUpdate> outcomeUpdates);

  /**
   * Batch update step details.
   * CREATE events use nodeExecutionId and store documentId in node_executions_info_id.
   * UPDATE events use documentId to find the vertex via node_executions_info_id.
   */
  void batchUpdateStepDetails(List<GraphBatchUpdateDTOs.StepDetailsUpdate> stepDetailsUpdates);

  /**
   * Batch update module info.
   * CREATE events use stageUuid/planExecutionId and store documentId in graph_update_info_ids.
   * UPDATE events use documentId to find the vertex via graph_update_info_ids array.
   */
  void batchUpdateModuleInfo(List<GraphBatchUpdateDTOs.ModuleInfoUpdate> moduleInfoUpdates);

  /**
   * Mark parent stages as having barrier children when barrier steps are detected.
   * Called after vertex batch is complete to update has_barrier_child flag on parent stage rows.
   * Stage IDs are extracted directly from ambiance.levels in the CDC consumer, eliminating the need
   * for database queries to walk up the parent chain.
   *
   * @param stageNodeExecutionIds list of stage node execution IDs that contain barrier steps
   */
  void markBarrierParents(List<String> stageNodeExecutionIds);

  // ============================================
  // Read Operations (called by GraphGenerationServiceImpl)
  // ============================================

  Optional<OrchestrationGraph> getOrchestrationGraph(String planExecutionId);

  List<GraphVertex> getChildrenPaginated(String planExecutionId, String parentId, long cursorCreatedAt, int limit);

  int countChildren(String planExecutionId, String parentId);

  Optional<OrchestrationGraph> getPartialOrchestrationGraph(String planExecutionId, String rootNodeId);

  /**
   * Fetches a subtree from graph_vertex including old_retry nodes.
   * Used for the retry step group subgraph API where the root node itself is old_retry=true.
   */
  Optional<OrchestrationGraph> getOldRetrySubGraph(String planExecutionId, String rootNodeId);

  Optional<String> findNodeExecutionId(String planExecutionId, String planNodeId, String nodeExecutionId);

  Map<String, Object> getPipelineModuleInfo(String planExecutionId);

  Map<String, GraphLayoutNodeDTO> getStageLayoutNodes(String planExecutionId);
}
