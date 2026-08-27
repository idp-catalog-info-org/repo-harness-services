/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationGraph;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.execution.PlanExecution;
import io.harness.logging.AutoLogContext;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.service.GraphGenerationService;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class OrchestrationEndGraphHandler implements AsyncInformObserver, OrchestrationEndObserver {
  private final ExecutorService executorService;
  private final PlanExecutionService planExecutionService;
  private final GraphGenerationService graphGenerationService;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Inject
  public OrchestrationEndGraphHandler(@Named("OrchestrationVisualizationExecutorService")
                                      ExecutorService executorService, PlanExecutionService planExecutionService,
      GraphGenerationService graphGenerationService, PmsFeatureFlagHelper pmsFeatureFlagHelper) {
    this.executorService = executorService;
    this.planExecutionService = planExecutionService;
    this.graphGenerationService = graphGenerationService;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
  }

  @Override
  public void onEnd(Ambiance ambiance, Status endStatus) {
    if (pmsFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.PIPE_USE_CDC_BASED_GRAPH)) {
      // No-op: graph state is kept up-to-date by the CDC consumer pipeline
      // (Debezium → Kafka → GraphCDCConsumer → PostgreSQL).
      return;
    }

    OrchestrationGraph orchestrationGraph = graphGenerationService.getCachedOrchestrationGraphFromDB(
        ambiance.getPlanExecutionId(), AmbianceUtils.getAccountId(ambiance));

    try (AutoLogContext autoLogContext = AmbianceUtils.autoLogContext(ambiance)) {
      PlanExecution planExecution = planExecutionService.getPlanExecutionMetadata(ambiance.getPlanExecutionId());
      // One last time try to update the graph to process any unprocessed logs
      graphGenerationService.updateGraphWithWaitLock(planExecution.getUuid());
      orchestrationGraph = orchestrationGraph.withStatus(planExecution.getStatus()).withEndTs(planExecution.getEndTs());
    } catch (Exception e) {
      log.error("[GRAPH_ERROR] Cannot update Orchestration graph for ORCHESTRATION_END", e);
      throw e;
    } finally {
      // check status of all graph vertex if marked in terminal state. If not, fetch corresponding
      try {
        graphGenerationService.validateAndUpdateFromNodeExecution(ambiance.getPlanExecutionId(), orchestrationGraph);
      } catch (Exception e) {
        log.error("[GRAPH_ERROR] Cannot update Orchestration graph from Node Execution", e);
      }
      graphGenerationService.cacheOrchestrationGraphInDB(orchestrationGraph, AmbianceUtils.getAccountId(ambiance));
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
