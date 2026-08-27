/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.service.impl;

import static io.harness.graph.service.impl.GraphVertexFields.CREATED_AT;
import static io.harness.graph.service.impl.GraphVertexFields.END_TS;
import static io.harness.graph.service.impl.GraphVertexFields.EXECUTION_INPUT_CONFIGURED;
import static io.harness.graph.service.impl.GraphVertexFields.FAILURE_INFO;
import static io.harness.graph.service.impl.GraphVertexFields.HAS_BARRIER_CHILD;
import static io.harness.graph.service.impl.GraphVertexFields.IDENTIFIER;
import static io.harness.graph.service.impl.GraphVertexFields.MODULE;
import static io.harness.graph.service.impl.GraphVertexFields.MODULE_INFO;
import static io.harness.graph.service.impl.GraphVertexFields.NAME;
import static io.harness.graph.service.impl.GraphVertexFields.NODE_EXECUTION_ID;
import static io.harness.graph.service.impl.GraphVertexFields.NODE_GROUP;
import static io.harness.graph.service.impl.GraphVertexFields.NODE_RUN_INFO;
import static io.harness.graph.service.impl.GraphVertexFields.PLAN_NODE_ID;
import static io.harness.graph.service.impl.GraphVertexFields.SKIP_INFO;
import static io.harness.graph.service.impl.GraphVertexFields.START_TS;
import static io.harness.graph.service.impl.GraphVertexFields.STATUS;
import static io.harness.graph.service.impl.GraphVertexFields.STEP_DETAILS;
import static io.harness.graph.service.impl.GraphVertexFields.STEP_PARAMETERS;
import static io.harness.graph.service.impl.GraphVertexFields.STEP_TYPE;
import static io.harness.graph.service.impl.GraphVertexFields.STRATEGY_METADATA;
import static io.harness.graph.service.impl.GraphVertexFields.STRATEGY_TYPE;
import static io.harness.graph.service.impl.JsonbParserUtils.parseProto;
import static io.harness.graph.service.impl.JsonbParserUtils.parseStepDetails;
import static io.harness.graph.service.impl.StrategyTypeExtractor.extractFromJsonb;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dto.converter.FailureInfoDTOConverter;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.creation.lookup.intfc.NodeTypeLookupService;
import io.harness.pms.plan.execution.beans.dto.EdgeLayoutListDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Record;

/**
 * Mapper for converting JOOQ Records to GraphLayoutNodeDTO objects.
 * Used for building layoutNodeMap in pipeline execution graph.
 */
@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class GraphLayoutNodeMapper {
  private static final String LOG_PREFIX = "[LAYOUT-MAPPER]";

  /**
   * Convert a JOOQ Record to a GraphLayoutNodeDTO.
   *
   * @param record the database record from graph_vertex table
   * @param layoutKey the key that will be used for this node in the layoutNodeMap
   * @param containerChildren map of container (STRATEGY/FORK) nodeExecutionId to list of child layout keys
   * @param siblingNextIds map of node layoutKey to list containing next sibling's layout key (for nextIds)
   * @param nodeTypeLookupService service to derive module from stepType
   * @param stepTypeToNodeTypeMapper function to map stepType to nodeType
   * @return the mapped GraphLayoutNodeDTO
   */
  public static GraphLayoutNodeDTO fromRecord(Record record, String layoutKey,
      Map<String, List<String>> containerChildren, Map<String, List<String>> siblingNextIds,
      NodeTypeLookupService nodeTypeLookupService,
      java.util.function.Function<String, String> stepTypeToNodeTypeMapper) {
    String nodeExecutionId = record.get(NODE_EXECUTION_ID);
    String statusStr = record.get(STATUS);
    String nodeGroup = record.get(NODE_GROUP);
    String stepType = record.get(STEP_TYPE);

    // Parse failureInfo for failureInfoDTO
    FailureInfo failureInfo = parseProto(record.get(FAILURE_INFO), FailureInfo.getDefaultInstance());

    // Derive module from stepType
    String module = deriveModule(record, stepType, nodeTypeLookupService);

    // Determine nodeType
    String nodeType = determineNodeType(record, nodeGroup, stepType, nodeExecutionId, stepTypeToNodeTypeMapper);

    // Build edge layout based on node type
    EdgeLayoutListDTO edgeLayoutList =
        buildEdgeLayout(layoutKey, nodeExecutionId, nodeGroup, containerChildren, siblingNextIds);

    // Normalize nodeGroup for display - FORK should be displayed as STAGE
    String displayNodeGroup = normalizeNodeGroup(nodeGroup);

    // Parse moduleInfo from JSONB column
    Map<String, LinkedHashMap<String, Object>> moduleInfo = parseModuleInfo(record.get(MODULE_INFO));

    // Derive isRollbackStageNode from plan_node_id (matches legacy ExecutionSummaryUpdateUtils logic)
    String planNodeId = record.get(PLAN_NODE_ID);
    boolean isRollbackStageNode = planNodeId != null && planNodeId.endsWith("_rollbackStage");

    // Get barrierFound from has_barrier_child column
    Boolean hasBarrierChild = record.get(HAS_BARRIER_CHILD);
    boolean barrierFound = hasBarrierChild != null && hasBarrierChild;

    return GraphLayoutNodeDTO.builder()
        .nodeType(nodeType)
        .nodeGroup(displayNodeGroup)
        .nodeIdentifier(record.get(IDENTIFIER))
        .name(record.get(NAME))
        .nodeUuid(planNodeId)
        .status(statusStr != null ? ExecutionStatus.getExecutionStatus(Status.valueOf(statusStr)) : null)
        .module(module)
        .moduleInfo(moduleInfo)
        .createdAt(record.get(CREATED_AT))
        .startTs(record.get(START_TS))
        .endTs(record.get(END_TS))
        .edgeLayoutList(edgeLayoutList)
        .skipInfo(parseProto(record.get(SKIP_INFO), SkipInfo.getDefaultInstance()))
        .nodeRunInfo(parseProto(record.get(NODE_RUN_INFO), NodeRunInfo.getDefaultInstance()))
        .failureInfo(failureInfo != null ? io.harness.pms.contracts.execution.ExecutionErrorInfo.newBuilder()
                                               .setMessage(failureInfo.getErrorMessage())
                                               .build()
                                         : null)
        .failureInfoDTO(failureInfo != null ? FailureInfoDTOConverter.toFailureInfoDTO(failureInfo) : null)
        .stepDetails(parseStepDetails(record.get(STEP_DETAILS)))
        .nodeExecutionId(nodeExecutionId)
        .strategyMetadata(parseProto(record.get(STRATEGY_METADATA), StrategyMetadata.getDefaultInstance()))
        .executionInputConfigured(record.get(EXECUTION_INPUT_CONFIGURED))
        .isRollbackStageNode(isRollbackStageNode)
        .barrierFound(barrierFound)
        .build();
  }

  /**
   * Derive module from stepType using NodeTypeLookupService.
   */
  private static String deriveModule(Record record, String stepType, NodeTypeLookupService nodeTypeLookupService) {
    String module = record.get(MODULE);
    if ((module == null || module.isEmpty()) && stepType != null && !stepType.isEmpty()) {
      try {
        module = nodeTypeLookupService.findNodeTypeServiceName(stepType);
      } catch (Exception e) {
        log.debug("{} Could not derive module from stepType {}: {}", LOG_PREFIX, stepType, e.getMessage());
      }
    }
    return module;
  }

  /**
   * Determine nodeType based on node group and stepType.
   * For STRATEGY nodes, uses strategy_type column or extracts from step_parameters.
   * For FORK nodes (parallel stages), returns "parallel" to match legacy GraphLayoutNode.
   * For other nodes, maps stepType to YAML-style nodeType.
   */
  private static String determineNodeType(Record record, String nodeGroup, String stepType, String nodeExecutionId,
      java.util.function.Function<String, String> stepTypeToNodeTypeMapper) {
    String nodeType = stepTypeToNodeTypeMapper.apply(stepType);

    if ("FORK".equals(nodeGroup)) {
      // FORK nodes (parallel stages wrapper) should have nodeType "parallel" to match legacy
      return "parallel";
    }

    if ("STRATEGY".equals(nodeGroup)) {
      // First try the dedicated strategy_type column
      String strategyType = record.get(STRATEGY_TYPE);
      if (strategyType == null || strategyType.isEmpty()) {
        // Fall back to parsing step_parameters
        strategyType = extractFromJsonb(record.get(STEP_PARAMETERS)).orElse(null);
      }
      if (strategyType != null && !strategyType.isEmpty()) {
        nodeType = strategyType;
        log.debug("{} Using strategyType {} for STRATEGY node {}", LOG_PREFIX, strategyType, nodeExecutionId);
      } else {
        log.warn("{} strategyType not found for STRATEGY node {}, nodeType will remain STRATEGY", LOG_PREFIX,
            nodeExecutionId);
      }
    }

    return nodeType;
  }

  /**
   * Build edge layout based on node type.
   * For STRATEGY nodes: currentNodeChildren contains child stage keys, nextIds contains next sibling.
   * For FORK nodes (parallel stages): currentNodeChildren contains parallel stage keys, nextIds contains next sibling.
   * For STAGE nodes: currentNodeChildren is empty, nextIds contains next sequential sibling stage.
   *
   * @param layoutKey the key used for this node in the layoutNodeMap
   * @param containerChildren map of container nodeExecutionId -> list of child keys (for currentNodeChildren)
   * @param siblingNextIds map of node layoutKey -> list containing next sibling's key (for nextIds)
   */
  private static EdgeLayoutListDTO buildEdgeLayout(String layoutKey, String nodeExecutionId, String nodeGroup,
      Map<String, List<String>> containerChildren, Map<String, List<String>> siblingNextIds) {
    List<String> nextIds = siblingNextIds.getOrDefault(layoutKey, Collections.emptyList());

    if ("STRATEGY".equals(nodeGroup) || "FORK".equals(nodeGroup)) {
      // STRATEGY and FORK nodes have children and may have a next sibling.
      List<String> children = containerChildren.getOrDefault(nodeExecutionId, Collections.emptyList());
      return EdgeLayoutListDTO.builder().currentNodeChildren(children).nextIds(nextIds).build();
    } else {
      // STAGE nodes have no children in layout context, but have next sibling
      return EdgeLayoutListDTO.builder().currentNodeChildren(Collections.emptyList()).nextIds(nextIds).build();
    }
  }

  /**
   * Normalize nodeGroup for display purposes.
   * FORK nodes should be displayed as STAGE to match legacy behavior.
   */
  private static String normalizeNodeGroup(String nodeGroup) {
    if ("FORK".equals(nodeGroup)) {
      // FORK is displayed as STAGE in the legacy implementation
      return "STAGE";
    }
    return nodeGroup;
  }

  /**
   * Parse moduleInfo from JSONB column.
   * Returns a map of module name to module-specific metadata.
   *
   * Example structure:
   * {
   *   "cd": {
   *     "serviceIdentifiers": ["svc1", "svc2"],
   *     "envIdentifiers": ["prod"]
   *   }
   * }
   */
  @SuppressWarnings("unchecked")
  private static Map<String, LinkedHashMap<String, Object>> parseModuleInfo(org.jooq.JSONB jsonb) {
    if (jsonb == null || jsonb.data() == null || jsonb.data().isEmpty()) {
      return null;
    }
    try {
      // Parse as Map<String, LinkedHashMap<String, Object>> to match GraphLayoutNodeDTO.moduleInfo type
      return JsonUtils.asObject(jsonb.data(), new TypeReference<Map<String, LinkedHashMap<String, Object>>>() {});
    } catch (Exception e) {
      log.debug("{} Failed to parse moduleInfo: {}", LOG_PREFIX, e.getMessage());
      return null;
    }
  }
}
