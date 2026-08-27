/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.consumers.graph;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.visualisation.log.OrchestrationLogEvent;
import io.harness.pms.sdk.execution.events.PmsCommonsBaseEventHandler;
import io.harness.service.GraphGenerationService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka message handler for processing OrchestrationLogEvents.
 * Processes events and triggers graph updates using GraphUpdateDispatcher.
 * Follows the same pattern as InitiateNodeEventKafkaConsumer for consistency.
 */
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = HarnessModuleComponent.CDS_PIPELINE)
public class GraphUpdateKafkaMessageHandler implements PmsCommonsBaseEventHandler<OrchestrationLogEvent> {
  private final GraphGenerationService graphGenerationService;

  @Inject
  public GraphUpdateKafkaMessageHandler(GraphGenerationService graphGenerationService) {
    this.graphGenerationService = graphGenerationService;
  }

  @Override
  public void handleEvent(
      OrchestrationLogEvent event, Map<String, String> metadataMap, Map<String, Object> metricInfo) {
    if (event == null || EmptyPredicate.isEmpty(event.getPlanExecutionId())) {
      log.debug(
          "Skipping null or empty event for planExecutionId: {}", event != null ? event.getPlanExecutionId() : "null");
      return;
    }

    try (AutoLogContext ignore = new AutoLogContext(ImmutableMap.of("planExecutionId", event.getPlanExecutionId()),
             AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      // no need to submit to executor service again
      GraphUpdateDispatcher.builder()
          .planExecutionId(event.getPlanExecutionId())
          .startTs(System.currentTimeMillis())
          .graphGenerationService(graphGenerationService)
          .build()
          .runInternal();
    } catch (Exception ex) {
      log.error("Error processing OrchestrationLogEvent for plan execution: {}", event.getPlanExecutionId(), ex);
    }
  }
}
