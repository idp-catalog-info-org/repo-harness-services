/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.service.GraphGenerationService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralises the logic for enriching a {@link PipelineExecutionSummaryEntity} with data from
 * the normalised PostgreSQL graph store (CDC pipeline).
 *
 * <p>All enrichment is effectively gated by the {@code PIPE_USE_NORMALIZED_POSTGRES_GRAPH} feature
 * flag because {@link GraphGenerationService#getStageLayoutNodesFromPostgres} returns {@code null}
 * when the flag is disabled.
 */
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class GraphLayoutNodeMergeHelper {
  GraphGenerationService graphGenerationService;

  /**
   * Fetches stage layout nodes from PostgreSQL and merges them with the MongoDB layout node map
   * already stored on the entity.
   *
   * <p>Merge rules:
   * <ul>
   *   <li>Nodes in both PG and MongoDB: PG node is used; {@code nodeType} is copied from MongoDB
   *       (nodeType is written to the plan's {@code GraphLayoutInfo} during plan creation and is
   *       not stored in {@code NodeExecution}, so only MongoDB has it).
   *   <li>Nodes only in MongoDB: preserved in the merged map (CDC may not have processed them yet).
   *   <li>Nodes only in PG: kept as-is.
   * </ul>
   *
   * <p>No-op when the feature flag is disabled ({@code getStageLayoutNodesFromPostgres} returns
   * {@code null}).
   */
  public void mergeLayoutNodes(String accountId, String planExecutionId, PipelineExecutionSummaryEntity entity) {
    Map<String, GraphLayoutNodeDTO> pgLayoutNodeMap =
        graphGenerationService.getStageLayoutNodesFromPostgres(accountId, planExecutionId);
    if (pgLayoutNodeMap == null || pgLayoutNodeMap.isEmpty()) {
      return;
    }

    Map<String, GraphLayoutNodeDTO> mongoLayoutNodeMap = entity.getLayoutNodeMap();
    if (mongoLayoutNodeMap != null) {
      // Copy nodeType from MongoDB for nodes that exist in both maps
      for (Map.Entry<String, GraphLayoutNodeDTO> entry : pgLayoutNodeMap.entrySet()) {
        GraphLayoutNodeDTO pgNode = entry.getValue();
        GraphLayoutNodeDTO mongoNode = mongoLayoutNodeMap.get(entry.getKey());
        if (mongoNode != null && mongoNode.getNodeType() != null && pgNode != null) {
          pgNode.setNodeType(mongoNode.getNodeType());
        }
      }
      // Preserve MongoDB-only nodes (CDC may not have processed them yet)
      for (Map.Entry<String, GraphLayoutNodeDTO> entry : mongoLayoutNodeMap.entrySet()) {
        pgLayoutNodeMap.putIfAbsent(entry.getKey(), entry.getValue());
      }
    }

    entity.setLayoutNodeMap(pgLayoutNodeMap);
    log.debug("[NORMALIZED-PG] Merged {} layout nodes from PostgreSQL for planExecutionId: {}", pgLayoutNodeMap.size(),
        planExecutionId);
  }

  /**
   * Fetches pipeline-level module info from PostgreSQL and sets it on the entity.
   *
   * <p>Should only be called when {@link #mergeLayoutNodes} has already confirmed that the PG
   * store is active (i.e. the layout node map was non-null), so the feature flag is guaranteed
   * to be enabled.
   */
  public void mergeModuleInfo(String planExecutionId, PipelineExecutionSummaryEntity entity) {
    Map<String, Object> pgModuleInfo = graphGenerationService.getPipelineModuleInfoFromPostgres(planExecutionId);
    if (pgModuleInfo == null || pgModuleInfo.isEmpty()) {
      return;
    }
    Map<String, org.bson.Document> moduleInfoMap = new HashMap<>();
    for (Map.Entry<String, Object> entry : pgModuleInfo.entrySet()) {
      if (entry.getValue() instanceof Map) {
        moduleInfoMap.put(entry.getKey(), new org.bson.Document((Map<String, Object>) entry.getValue()));
      }
    }
    entity.setModuleInfo(moduleInfoMap);
    log.debug("[NORMALIZED-PG] Merged module info from PostgreSQL for planExecutionId: {}", planExecutionId);
  }
}
