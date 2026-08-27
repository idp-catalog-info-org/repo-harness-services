/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.contracts.execution.Status.APPROVAL_WAITING;
import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;
import static io.harness.pms.contracts.execution.Status.WAIT_STEP_RUNNING;

import io.harness.DelegateInfoHelper;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.GraphVertex;
import io.harness.beans.GraphVertex.GraphVertexBuilder;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.beans.stepDetail.NodeExecutionsInfo.NodeExecutionsInfoKeys;
import io.harness.data.structure.CollectionUtils;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.generator.OrchestrationAdjacencyListGenerator;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.resolver.outcome.mapper.PmsOutcomeMapper;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class GraphStatusUpdateHelper {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsOutcomeService pmsOutcomeService;
  @Inject private OrchestrationAdjacencyListGenerator orchestrationAdjacencyListGenerator;
  @Inject private DelegateInfoHelper delegateInfoHelper;

  @Inject private NodeExecutionInfoService pmsGraphStepDetailsService;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  private static final Set<String> nodeExecutionsInfoProjections = Set.of(NodeExecutionsInfoKeys.retryNodeMetadata);

  public OrchestrationGraph handleEvent(
      String planExecutionId, String nodeExecutionId, OrchestrationGraph orchestrationGraph) {
    if (isEmpty(nodeExecutionId)) {
      return orchestrationGraph;
    }
    NodeExecution nodeExecution = nodeExecutionService.get(nodeExecutionId);
    return handleEventV2(planExecutionId, nodeExecution, orchestrationGraph);
  }

  public OrchestrationGraph handleEventV2(
      String planExecutionId, NodeExecution nodeExecution, OrchestrationGraph orchestrationGraph) {
    if (nodeExecution == null) {
      return orchestrationGraph;
    }
    String nodeExecutionId = nodeExecution.getUuid();
    try {
      if (orchestrationGraph.getRootNodeIds().isEmpty()) {
        orchestrationGraph.getRootNodeIds().add(nodeExecutionId);
      }

      Map<String, GraphVertex> graphVertexMap = orchestrationGraph.getAdjacencyList().getGraphVertexMap();
      if (graphVertexMap.containsKey(nodeExecutionId) && nodeExecution.getOldRetry()) {
        log.info("[PMS_GRAPH]  Removing graph vertex with id [{}] and status [{}]. PlanExecutionId: [{}]",
            nodeExecutionId, nodeExecution.getStatus(), planExecutionId);
        orchestrationAdjacencyListGenerator.removeVertex(orchestrationGraph.getAdjacencyList(), nodeExecution);
      } else if (!nodeExecution.getOldRetry()) {
        if (!graphVertexMap.containsKey(nodeExecutionId)) {
          orchestrationAdjacencyListGenerator.addVertex(orchestrationGraph.getAdjacencyList(), nodeExecution);
        }
        updateGraphVertex(graphVertexMap, nodeExecution, planExecutionId);
      }
    } catch (Exception e) {
      log.error(
          String.format("[GRAPH_ERROR] event failed for [%s] for plan [%s]", nodeExecutionId, planExecutionId), e);
      throw e;
    }
    return orchestrationGraph;
  }

  private void updateGraphVertex(
      Map<String, GraphVertex> graphVertexMap, NodeExecution nodeExecution, String planExecutionId) {
    String nodeExecutionId = nodeExecution.getUuid();
    graphVertexMap.computeIfPresent(nodeExecutionId, (key, prevValue) -> {
      GraphVertex newValue = convertFromNodeExecution(prevValue, nodeExecution);
      if (isOutcomeUpdateGraphStatus(newValue.getStatus())) {
        newValue.setOutcomeDocuments(PmsOutcomeMapper.convertJsonToOrchestrationMap(
            pmsOutcomeService.findAllOutcomesMapByRuntimeId(planExecutionId, nodeExecutionId)));
        newValue.setGraphDelegateSelectionLogParams(
            delegateInfoHelper.getDelegateInformationForGivenTask(nodeExecution.getExecutableResponses(),
                nodeExecution.getMode(), NodeExecutionContextUtils.getAccountId(nodeExecution)));
      }
      return newValue;
    });
  }

  // Todo: Update only properties that will be changed. No need to construct full
  public GraphVertex convertFromNodeExecution(GraphVertex prevValue, NodeExecution nodeExecution) {
    Level level = Objects.requireNonNull(NodeExecutionContextUtils.obtainCurrentLevel(nodeExecution));
    NodeExecutionsInfo nodeExecutionsInfo = pmsGraphStepDetailsService.getNodeExecutionsInfoWithProjections(
        nodeExecution.getUuid(), nodeExecutionsInfoProjections);
    GraphVertexBuilder prevValueBuilder =
        prevValue.toBuilder()
            .uuid(nodeExecution.getUuid())
            .currentLevel(NodeExecutionContextUtils.obtainCurrentLevel(nodeExecution))
            .planNodeId(level.getSetupId())
            .identifier(level.getIdentifier())
            .name(nodeExecution.getName())
            .startTs(nodeExecution.getStartTs())
            .endTs(nodeExecution.getEndTs())
            .initialWaitDuration(nodeExecution.getInitialWaitDuration())
            .lastUpdatedAt(nodeExecution.getLastUpdatedAt())
            .stepType(level.getStepType().getType())
            .status(nodeExecution.getStatus())
            .failureInfo(nodeExecution.getFailureInfo())
            .nodeRunInfo(nodeExecution.getNodeRunInfo())
            .mode(nodeExecution.getMode())
            .executableResponses(CollectionUtils.emptyIfNull(nodeExecution.getExecutableResponses()))
            .interruptHistories(nodeExecution.getInterruptHistories())
            .retryIds(nodeExecution.getRetryIds())
            .skipType(nodeExecution.getSkipGraphType())
            .unitProgresses(nodeExecution.getUnitProgresses())
            .progressData(nodeExecution.getPmsProgressData())
            .manualInterventionAvailableActions(
                NodeExecutionContextUtils.getManualInterventionAvailableActions(nodeExecution))
            .baseFqn(AmbianceUtils.getFQNUsingLevels(NodeExecutionContextUtils.getLevelList(nodeExecution)))
            .childrenCount(nodeExecution.getChildrenCount());
    if (prevValue.getStepParameters() == null || shouldPopulateStepParams(prevValue, nodeExecution)) {
      prevValueBuilder.stepParameters(nodeExecution.getExcludedKeysFromStepInputs() != null
              ? nodeExecutionService.getResolvedStepInputs(
                    nodeExecution.getExcludedKeysFromStepInputs(), nodeExecution.getResolvedParams())
              : nodeExecution.getResolvedParams());
      prevValueBuilder.stepParametersVersion(nodeExecution.getResolvedParamsVersion());
    }
    if (nodeExecutionsInfo != null && nodeExecutionsInfo.getRetryNodeMetadata() != null) {
      prevValueBuilder.retryNodeMetadata(nodeExecutionsInfo.getRetryNodeMetadata());
    }
    if (AmbianceUtils.hasStrategyMetadata(level) && prevValue.getStrategyMetadata() == null) {
      prevValueBuilder.strategyMetadata(pmsGraphStepDetailsService.getStrategyMetadata(nodeExecution));
    }
    return prevValueBuilder.build();
  }

  private static boolean shouldPopulateStepParams(GraphVertex prevValue, NodeExecution nodeExecution) {
    if (nodeExecution.getResolvedParamsVersion() == null || prevValue.getStepParametersVersion() == null) {
      return false;
    }
    return nodeExecution.getResolvedParamsVersion() > prevValue.getStepParametersVersion();
  }

  @VisibleForTesting
  boolean isOutcomeUpdateGraphStatus(Status status) {
    return StatusUtils.isFinalStatus(status) || status.equals(INTERVENTION_WAITING) || status.equals(APPROVAL_WAITING)
        || status.equals(WAIT_STEP_RUNNING);
  }

  /*
  check if the node is old and retried; Note: old means current execution of the node is not the latest retry
   */
  public boolean isOldRetriedStepGroupNode(NodeExecution nodeExecution, OrchestrationGraph orchestrationGraph) {
    if (nodeExecution == null) {
      return false;
    }
    // returning early for other node types available
    if (nodeExecution.getGroup() != null
        && !(StepCategory.STEP_GROUP.name().equals(nodeExecution.getGroup())
            || NGCommonUtilPlanCreationConstants.GROUP.equals(nodeExecution.getGroup()))) {
      return false;
    }
    Map<String, GraphVertex> graphVertexMap = orchestrationGraph.getAdjacencyList().getGraphVertexMap();
    return graphVertexMap.containsKey(nodeExecution.getUuid()) && nodeExecution.getOldRetry();
  }
}
