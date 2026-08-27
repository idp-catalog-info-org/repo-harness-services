/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class StepDetailsUpdateEventHandler {
  @Inject NodeExecutionInfoService pmsGraphStepDetailsService;
  @Inject PmsFeatureFlagService pmsFeatureFlagService;

  public OrchestrationGraph handleEvent(String planExecutionId, String nodeExecutionId,
      OrchestrationGraph orchestrationGraph, Update summaryEntityUpdate, String accountIdentifier) {
    try {
      if (orchestrationGraph.getAdjacencyList().getGraphVertexMap().containsKey(nodeExecutionId)) {
        Map<String, PmsStepDetails> stepDetails =
            pmsGraphStepDetailsService.getStepDetails(planExecutionId, nodeExecutionId);
        updateGraphAndSummaryEntity(
            orchestrationGraph, nodeExecutionId, stepDetails, summaryEntityUpdate, accountIdentifier);
      }
    } catch (Exception e) {
      log.error(String.format(
                    "[GRAPH_ERROR] Graph update for Step Details update event failed for node [%s]", nodeExecutionId),
          e);
      throw e;
    }
    return orchestrationGraph;
  }

  public OrchestrationGraph handleEventV2(String nodeExecutionId, OrchestrationGraph orchestrationGraph,
      Update summaryEntityUpdate, Map<String, PmsStepDetails> stepDetails, String accountIdentifier) {
    try {
      if (!stepDetails.isEmpty()
          && orchestrationGraph.getAdjacencyList().getGraphVertexMap().containsKey(nodeExecutionId)) {
        updateGraphAndSummaryEntity(
            orchestrationGraph, nodeExecutionId, stepDetails, summaryEntityUpdate, accountIdentifier);
      }
    } catch (Exception e) {
      log.error(String.format(
                    "[GRAPH_ERROR] Graph update for Step Details update event failed for node [%s]", nodeExecutionId),
          e);
      throw e;
    }
    return orchestrationGraph;
  }

  private void updateGraphAndSummaryEntity(OrchestrationGraph orchestrationGraph, String nodeExecutionId,
      Map<String, PmsStepDetails> stepDetails, Update summaryEntityUpdate, String accountIdentifier) {
    orchestrationGraph.getAdjacencyList().getGraphVertexMap().get(nodeExecutionId).setStepDetails(stepDetails);
    // Add stepCategory in graphVertex
    Level currentLevel =
        orchestrationGraph.getAdjacencyList().getGraphVertexMap().get(nodeExecutionId).getCurrentLevel();
    if (Objects.equals(currentLevel.getStepType().getStepCategory(), StepCategory.STAGE)
        || Objects.equals(currentLevel.getStepType().getStepCategory(), StepCategory.STRATEGY)) {
      String stageUuid = currentLevel.getSetupId();
      if (accountIdentifier != null
          && pmsFeatureFlagService.isEnabled(
              accountIdentifier, FeatureName.PIPE_POPULATE_STEP_DETAILS_IN_RUNTIME_ID_FOR_STRATEGY_CHILD_NODES)) {
        if ((currentLevel.hasStrategyMetadata() || currentLevel.hasStrategyInfo())
            && isNotEmpty(currentLevel.getRuntimeId())) {
          stageUuid = currentLevel.getRuntimeId();
        }
      }
      summaryEntityUpdate.set(
          PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".stepDetails",
          stepDetails);
    }
  }

  public OrchestrationGraph handleStepInputEvent(
      String planExecutionId, String nodeExecutionId, OrchestrationGraph orchestrationGraph) {
    try {
      if (orchestrationGraph.getAdjacencyList().getGraphVertexMap().containsKey(nodeExecutionId)) {
        PmsStepParameters stepDetails =
            pmsGraphStepDetailsService.getStepInputsRecasterPruned(planExecutionId, nodeExecutionId);
        if (stepDetails != null) {
          orchestrationGraph.getAdjacencyList().getGraphVertexMap().get(nodeExecutionId).setStepParameters(stepDetails);
        }
      }
    } catch (Exception e) {
      log.error(
          String.format("[GRAPH_ERROR] Graph update for Step Input event failed for node [%s]", nodeExecutionId), e);
      throw e;
    }
    return orchestrationGraph;
  }

  public OrchestrationGraph handleStepInputEventV2(
      NodeExecutionsInfo nodeExecutionsInfo, OrchestrationGraph orchestrationGraph) {
    try {
      if (nodeExecutionsInfo.getResolvedInputs() != null
          && orchestrationGraph.getAdjacencyList().getGraphVertexMap().containsKey(
              nodeExecutionsInfo.getNodeExecutionId())) {
        PmsStepParameters stepDetails =
            pmsGraphStepDetailsService.getStepInputsRecasterPruned(nodeExecutionsInfo.getResolvedInputs());
        orchestrationGraph.getAdjacencyList()
            .getGraphVertexMap()
            .get(nodeExecutionsInfo.getNodeExecutionId())
            .setStepParameters(stepDetails);
      }
    } catch (Exception e) {
      log.error(String.format("[GRAPH_ERROR] Graph update for Step Input event failed for node [%s]",
                    nodeExecutionsInfo.getNodeExecutionId()),
          e);
      throw e;
    }
    return orchestrationGraph;
  }
}
