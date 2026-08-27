/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EphemeralOrchestrationGraph;
import io.harness.beans.ExecutionGraph;
import io.harness.beans.GraphVertex;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.WorkflowGraph;
import io.harness.beans.converter.EphemeralOrchestrationGraphConverter;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.dto.converter.OrchestrationGraphDTOConverter;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.PmsCommonConstants;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.pipeline.mappers.ExecutionGraphMapper;
import io.harness.pms.plan.execution.beans.dto.NodeExecutionSubGraphResponse;
import io.harness.service.GraphGenerationService;
import io.harness.skip.service.VertexSkipperService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class ExecutionGraphServiceImpl implements ExecutionGraphService {
  NodeExecutionService nodeExecutionService;
  GraphGenerationService graphGenerationService;
  VertexSkipperService vertexSkipperService;
  private static final long EXECUTION_EXPIRATION_THRESHOLD_MILLIS =
      PmsCommonConstants.EXECUTION_TTL_IN_DAYS * 24L * 60 * 60 * 1000;

  private static final String STEP_GROUP_GRAPH_CANNOT_BE_CONSTRUCTED =
      "Node execution not found for planExecutionId %s nodeExecutionId %s. Step group graph cannot be constructed for "
      + "old failed step group node";

  @Override
  public NodeExecutionSubGraphResponse getNodeExecutionSubGraph(
      String nodeExecutionId, String planExecutionId, String accountId, Long executionStartTs) {
    return getCachedSubGraph(nodeExecutionId, planExecutionId, accountId, executionStartTs);
  }

  private NodeExecutionSubGraphResponse getCachedSubGraph(
      String nodeExecutionId, String planExecutionId, String accountId, Long executionStartTs) {
    // CDC path: fetch subtree directly from normalized PostgreSQL via recursive CTE
    OrchestrationGraph cdcGraph = graphGenerationService.getCdcSubGraph(accountId, planExecutionId, nodeExecutionId);
    if (cdcGraph != null) {
      return processGraphAndReturnResponse(cdcGraph);
    }

    // get the old retried step group graph from cacheEntities
    OrchestrationGraph graph =
        graphGenerationService.getCachedOrchestrationGraphFromSecondary(accountId, planExecutionId, nodeExecutionId);
    if (graph != null) {
      return processGraphAndReturnResponse(graph);
    }
    NodeExecution parentNodeExecution = null;
    try {
      parentNodeExecution = nodeExecutionService.get(nodeExecutionId);
    } catch (Exception ignored) {
    }
    // if unavailable, fallback to generating the graph from nodeExecutions - same as current behavior
    if (parentNodeExecution != null) {
      checkRequestedNodeTypeInNodeExecution(parentNodeExecution, planExecutionId, nodeExecutionId, accountId);
      graph = graphGenerationService.constructOldRetryGraph(planExecutionId, parentNodeExecution, accountId);
      return processGraphAndReturnResponse(graph);
    }

    // if unavailable, it cannot be constructed; just log the requested node if it is found in cacheEntities and proceed
    // this is only for knowing the node type for which the API is used to notify the existing customers, we do not want
    // to return the graph from here
    graph = graphGenerationService.getCachedOrchestrationGraphFromSecondary(accountId, planExecutionId);
    if (graph != null) {
      checkRequestedNodeTypeInOrchestrationGraph(graph, planExecutionId, nodeExecutionId, accountId);
    } else if (nodeExecutionExpired(executionStartTs)) {
      // likely to happen for old retried step groups older than 37 days
      log.info("Node execution expired: account {}, planExecutionId {}, nodeExecutionId {}", accountId, planExecutionId,
          nodeExecutionId);
    }

    throw new EntityNotFoundException(
        String.format(STEP_GROUP_GRAPH_CANNOT_BE_CONSTRUCTED, planExecutionId, nodeExecutionId));
  }

  @VisibleForTesting
  protected void checkRequestedNodeTypeInNodeExecution(
      NodeExecution nodeExecution, String planExecutionId, String nodeExecutionId, String accountId) {
    if (!(StepCategory.STEP_GROUP.name().equals(nodeExecution.getGroup())
            || NGCommonUtilPlanCreationConstants.GROUP.equals(nodeExecution.getGroup()))) {
      handleIfNodeIsNotStepGroup(accountId, planExecutionId, nodeExecutionId, nodeExecution.getGroup());
    }
    if (!Boolean.TRUE.equals(nodeExecution.getOldRetry())) {
      handleIfNodeIsNotOldFailedStepGroup(accountId, planExecutionId, nodeExecutionId);
    }
  }

  private void handleIfNodeIsNotStepGroup(
      String accountId, String planExecutionId, String nodeExecutionId, String nodeType) {
    log.info("Execution graph generated in runtime for account {}, planExecutionId {}, nodeExecutionId {}, nodeType {}",
        accountId, planExecutionId, nodeExecutionId, nodeType);
    throw new InvalidRequestException("Input nodeExecutionId does not belong to step group");
  }

  private void handleIfNodeIsNotOldFailedStepGroup(String accountId, String planExecutionId, String nodeExecutionId) {
    log.info("Execution graph generated in runtime for latest retried step group for account {}, planExecutionId {}, "
            + "nodeExecutionId {}",
        accountId, planExecutionId, nodeExecutionId);
    throw new InvalidRequestException("Input nodeExecutionId does not belong to old failed step group");
  }

  private void checkRequestedNodeTypeInOrchestrationGraph(
      OrchestrationGraph graph, String planExecutionId, String nodeExecutionId, String accountId) {
    Map<String, GraphVertex> executionNodeMap = graph.getAdjacencyList().getGraphVertexMap();
    if (executionNodeMap.containsKey(nodeExecutionId)) {
      GraphVertex parentNode = executionNodeMap.get(nodeExecutionId);
      if (!StepCategory.STEP_GROUP.toString().equals(parentNode.getStepType())) {
        handleIfNodeIsNotStepGroup(accountId, planExecutionId, nodeExecutionId, parentNode.getStepType());
      } else {
        // if it's a step group node and found in orchestration graph, it's not old retried node and hence this API
        // should not be used
        handleIfNodeIsNotOldFailedStepGroup(accountId, planExecutionId, nodeExecutionId);
      }
    }
  }

  private boolean nodeExecutionExpired(Long executionStartTs) {
    long differenceMillis = System.currentTimeMillis() - executionStartTs;

    return differenceMillis > EXECUTION_EXPIRATION_THRESHOLD_MILLIS;
  }

  private NodeExecutionSubGraphResponse processGraphAndReturnResponse(OrchestrationGraph graph) {
    EphemeralOrchestrationGraph ephemeralOrchestrationGraph = EphemeralOrchestrationGraphConverter.convertFrom(graph);
    vertexSkipperService.removeSkippedVertices(ephemeralOrchestrationGraph);
    OrchestrationGraphDTO orchestrationGraphDTO =
        OrchestrationGraphDTOConverter.convertFrom(ephemeralOrchestrationGraph);
    ExecutionGraph executionGraph = ExecutionGraphMapper.toExecutionGraph(orchestrationGraphDTO);
    return NodeExecutionSubGraphResponse.builder().executionGraph(executionGraph).build();
  }

  /**
   * Gets a workflow graph for visualization purposes.
   *
   * @param planExecutionId The plan execution ID
   * @param nodeExecutionId The starting node execution ID for traversal (optional)
   * @param depth The maximum depth to traverse from the starting node
   * @param accountId The account identifier
   * @return A WorkflowGraph containing nodes and relations up to the specified depth
   */
  @Override
  public WorkflowGraph getWorkflowGraph(String planExecutionId, String nodeExecutionId, int depth, String accountId) {
    // Validate parameters
    if (planExecutionId == null || planExecutionId.isEmpty()) {
      throw new InvalidRequestException("Plan execution ID cannot be null or empty");
    }

    // Apply a reasonable default and limit for depth
    int effectiveDepth = depth;
    if (effectiveDepth <= 0) {
      effectiveDepth = 10; // Default depth
    } else if (effectiveDepth > 100) {
      effectiveDepth = 100; // Maximum allowed depth to prevent excessive processing
    }

    // Delegate to the GraphGenerationService to generate the workflow graph
    return graphGenerationService.generateWorkflowGraph(accountId, planExecutionId, nodeExecutionId, effectiveDepth);
  }
}
