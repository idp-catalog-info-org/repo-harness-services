/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.observers.NodeExecutionStartObserver;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.timeout.TimeoutInstance;
import io.harness.timeout.TimeoutTracker;
import io.harness.timeout.engine.TimeoutEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Synchronous observer that stamps the pipeline-level timeout expiry on the {@code planExecutionsSummary} record
 * (and mirrors it to Elasticsearch) at the moment the pipeline node starts and its {@link TimeoutInstance}(s) are
 * registered.
 *
 * <p>Anchoring the write at node-start rather than summary-creation is intentional: a pipeline can sit in QUEUED after
 * the summary is created, so only once the pipeline node actually starts does the timeout clock begin. The absolute
 * epoch-millis expiry is read from the {@link TimeoutInstance} tracker, which is the source of truth.
 *
 * <p>This observer is registered as a plain (synchronous) {@link NodeExecutionStartObserver} - it deliberately does NOT
 * implement {@code AsyncInformObserver}, so the write happens inline on node-start. All work is wrapped in a
 * try/catch so a failure here can never break the actual pipeline execution.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PipelineTimeoutUpdateHandler implements NodeExecutionStartObserver {
  @Inject private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject private TimeoutEngine timeoutEngine;

  @Override
  public void onNodeStart(NodeStartInfo nodeStartInfo) {
    NodeExecution nodeExecution = nodeStartInfo.getNodeExecution();
    if (nodeExecution == null || !OrchestrationUtils.isPipelineNode(nodeExecution)) {
      return;
    }
    List<String> timeoutInstanceIds = nodeExecution.getTimeoutInstanceIds();
    if (isEmpty(timeoutInstanceIds)) {
      return;
    }
    try {
      Long pipelineTimeoutTs = computeEarliestExpiry(timeoutInstanceIds);
      if (pipelineTimeoutTs == null) {
        return;
      }
      String planExecutionId = NodeExecutionContextUtils.getPlanExecutionId(nodeExecution);
      pmsExecutionSummaryService.update(
          planExecutionId, new Update().set(PlanExecutionSummaryKeys.pipelineTimeoutTs, pipelineTimeoutTs));
      log.info("Stamped pipelineTimeoutTs {} on planExecutionsSummary for planExecutionId {}", pipelineTimeoutTs,
          planExecutionId);
    } catch (Exception ex) {
      // Timeout tracking is best-effort - never fail node start because of it. The TimeoutInstance remains the source
      // of truth for actual expiry.
      log.warn("Failed to stamp pipelineTimeoutTs on planExecutionsSummary for nodeExecution {}",
          nodeExecution.getUuid(), ex);
    }
  }

  /**
   * Returns the earliest (soonest-firing) absolute expiry across all registered timeout instances for the pipeline
   * node, or {@code null} if none of them expose an expiry time.
   */
  private Long computeEarliestExpiry(List<String> timeoutInstanceIds) {
    Long earliestExpiry = null;
    for (TimeoutInstance timeoutInstance : timeoutEngine.getTimeoutInstances(timeoutInstanceIds)) {
      TimeoutTracker tracker = timeoutInstance.getTracker();
      if (tracker == null) {
        continue;
      }
      Long expiryTime = tracker.getExpiryTime();
      if (expiryTime == null) {
        continue;
      }
      if (earliestExpiry == null || expiryTime < earliestExpiry) {
        earliestExpiry = expiryTime;
      }
    }
    return earliestExpiry;
  }
}