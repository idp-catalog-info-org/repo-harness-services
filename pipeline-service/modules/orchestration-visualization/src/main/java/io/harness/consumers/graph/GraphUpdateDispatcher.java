/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.consumers.graph;

import io.harness.logging.AutoLogContext;
import io.harness.service.GraphGenerationService;

import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GraphUpdateDispatcher implements Runnable {
  private static final Duration THRESHOLD_PROCESS_DURATION = Duration.ofMillis(100);
  private static final Duration THRESHOLD_PROCESSING_DURATION = Duration.ofSeconds(10);

  private final String planExecutionId;
  private final GraphGenerationService graphGenerationService;
  private final long startTs;

  @Builder
  public GraphUpdateDispatcher(String planExecutionId, GraphGenerationService graphGenerationService, long startTs) {
    this.planExecutionId = planExecutionId;
    this.graphGenerationService = graphGenerationService;
    this.startTs = startTs;
  }

  @Override
  public void run() {
    runInternal();
  }

  public void runInternal() {
    try (AutoLogContext ignore = new AutoLogContext(
             ImmutableMap.of("planExecutionId", planExecutionId), AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      log.info("Start processing graph update via dispatcher for planExecutionId: {}", planExecutionId);
      checkAndLogSchedulingDelays(planExecutionId, startTs);
      boolean success = graphGenerationService.updateGraph(planExecutionId);
      checkAndLogProcessingDelays(planExecutionId, startTs);
      if (!success) {
        log.info("Graph update failed for planExecutionId {}", planExecutionId);
      }
    }
  }

  private void checkAndLogSchedulingDelays(String planExecutionId, long startTs) {
    Duration scheduleDuration = Duration.ofMillis(System.currentTimeMillis() - startTs);
    if (THRESHOLD_PROCESS_DURATION.compareTo(scheduleDuration) < 0) {
      log.warn("[PMS_MESSAGE_LISTENER] Handler for graphUpdate event with planExecutionId {} called after {} delay",
          planExecutionId, scheduleDuration);
    }
  }

  private void checkAndLogProcessingDelays(String planExecutionId, long startTs) {
    Duration scheduleDuration = Duration.ofMillis(System.currentTimeMillis() - startTs);
    if (THRESHOLD_PROCESSING_DURATION.compareTo(scheduleDuration) < 0) {
      log.warn("[PMS_MESSAGE_LISTENER] Handler for graphUpdate event with planExecutionId {} processed in {}",
          planExecutionId, scheduleDuration);
    }
  }
}
