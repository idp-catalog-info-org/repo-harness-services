/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * DTOs for batch update operations in GraphCDCService.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public final class GraphBatchUpdateDTOs {
  private GraphBatchUpdateDTOs() {
    // Utility class
  }

  /**
   * DTO for vertex field updates in batch operations.
   */
  @Value
  @Builder
  public static class VertexUpdate {
    String planExecutionId;
    String nodeExecutionId;
    String accountIdentifier;
    Map<String, Object> updatedFields;
  }

  /**
   * DTO for outcome updates in batch operations.
   * When isCreate=true, nodeExecutionId is used to find the vertex row.
   * When isCreate=false (UPDATE), documentId is used to find the vertex via outcome_instance_ids array.
   */
  @Value
  @Builder
  public static class OutcomeUpdate {
    String planExecutionId;
    String nodeExecutionId;
    String documentId;
    String outcomeName;
    String outcomeJson;
    boolean isCreate;
  }

  /**
   * DTO for step details updates in batch operations.
   * When isCreate=true, nodeExecutionId is used to find the vertex row.
   * When isCreate=false (UPDATE), documentId is used to find the vertex via node_executions_info_id.
   *
   * strategyMetadataJson/retryNodeMetadataJson are sourced from the same nodeExecutionsInfo CDC event
   * and written to graph_vertex.strategy_metadata / retry_node_metadata when present.
   */
  @Value
  @Builder
  public static class StepDetailsUpdate {
    String nodeExecutionId;
    String documentId;
    String stepDetailsJson;
    Map<String, String> stepDetailsElementsByName;
    String strategyMetadataJson;
    String retryNodeMetadataJson;
    boolean isCreate;
  }

  /**
   * DTO for module info updates in batch operations.
   * When isCreate=true, stageUuid/planExecutionId are used to find the vertex.
   * When isCreate=false (UPDATE), documentId is used to find the vertex via graph_update_info_ids array.
   */
  @Value
  @Builder
  public static class ModuleInfoUpdate {
    String planExecutionId;
    String documentId;
    String stageUuid; // Can be planNodeId or nodeExecutionId
    Map<String, Object> moduleInfo;
    boolean isPipelineLevel; // true for PIPELINE level, false for STAGE level
    boolean isCreate;
  }
}
