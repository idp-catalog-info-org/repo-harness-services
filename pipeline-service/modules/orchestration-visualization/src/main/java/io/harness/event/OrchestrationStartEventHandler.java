/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationAdjacencyListInternal;
import io.harness.beans.OrchestrationGraph;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.observers.OrchestrationStartObserver;
import io.harness.engine.observers.beans.OrchestrationQueueInfo;
import io.harness.engine.observers.beans.OrchestrationStartInfo;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.service.GraphGenerationService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@Singleton
public class OrchestrationStartEventHandler implements OrchestrationStartObserver {
  @Inject PlanExecutionService planExecutionService;
  @Inject GraphGenerationService graphGenerationService;

  @Override
  public void onStart(OrchestrationStartInfo orchestrationStartInfo) {
    Ambiance ambiance = orchestrationStartInfo.getAmbiance();
    if (isCdcGraphEnabled(ambiance)) {
      return;
    }
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        orchestrationStartInfo.getPlanExecutionMetadataWithContext();
    if (OrchestrationUtils.checkAsyncPlanCreation(
            AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION.name()),
            AmbianceUtils.checkIfFeatureFlagEnabled(
                ambiance, FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS.name()),
            ambiance.getMetadata().getTriggerInfo(), planExecutionMetadataWithContext.getIsAsyncPlanCreation())) {
      return;
    }
    cacheOrchestrationGraph(ambiance);
  }

  private void cacheOrchestrationGraph(Ambiance ambiance) {
    OrchestrationGraph orchestrationGraph = handleEventFromLog(ambiance);
    if (orchestrationGraph != null) {
      graphGenerationService.cacheOrchestrationGraphInDB(orchestrationGraph, AmbianceUtils.getAccountId(ambiance));
    }
  }

  @Override
  public void onQueue(OrchestrationQueueInfo orchestrationQueueInfo) {
    Ambiance ambiance = orchestrationQueueInfo.getPlanExecution().getAmbiance();
    if (isCdcGraphEnabled(ambiance)) {
      return;
    }
    cacheOrchestrationGraph(ambiance);
  }

  public OrchestrationGraph handleEventFromLog(Ambiance ambiance) {
    try {
      PlanExecution planExecution = planExecutionService.getPlanExecutionMetadata(ambiance.getPlanExecutionId());

      log.info("Starting Execution for planExecutionId [{}] with status [{}].", planExecution.getUuid(),
          planExecution.getStatus());

      return OrchestrationGraph.builder()
          .cacheKey(planExecution.getUuid())
          .cacheParams(null)
          .cacheContextOrder(System.currentTimeMillis())
          .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                             .graphVertexMap(new HashMap<>())
                             .adjacencyMap(new HashMap<>())
                             .build())
          .planExecutionId(planExecution.getUuid())
          .rootNodeIds(new ArrayList<>())
          .startTs(planExecution.getStartTs())
          .endTs(planExecution.getEndTs())
          .status(planExecution.getStatus())
          .workflowType(AmbianceUtils.getWorkflowType(ambiance))
          .build();

    } catch (Exception e) {
      log.error("Failed to handle event from log for [{}] planExecutionId", ambiance.getPlanExecutionId(), e);
      throw e;
    }
  }

  private boolean isCdcGraphEnabled(Ambiance ambiance) {
    return ambiance.getMetadata().getFeatureFlagToValueMapOrDefault(FeatureName.PIPE_USE_CDC_BASED_GRAPH.name(), false);
  }

  @Override
  public boolean shouldIgnore(OrchestrationStartInfo orchestrationStartInfo) {
    return false;
  }
}
