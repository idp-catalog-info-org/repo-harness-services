/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrency.MaxConcurrentChildCallback;
import io.harness.constants.OrchestrationPublisherName;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.helpers.InitiateNodeHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.InitiateNodeBatchEvent;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class InitiateNodeBatchHandler extends PmsBaseEventHandler<InitiateNodeBatchEvent> {
  @Inject private OrchestrationEngine engine;
  @Inject BlockExecutionMetadataService blockExecutionMetadataService;
  @Inject NodeExecutionService nodeExecutionService;
  @Inject WaitNotifyEngine waitNotifyEngine;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;
  @Inject InitiateNodeHelper initiateNodeHelper;

  @Override
  protected String getEventType(InitiateNodeBatchEvent message) {
    return "trigger_node_event";
  }

  @Override
  protected Map<String, String> extraLogProperties(InitiateNodeBatchEvent event) {
    return ImmutableMap.of();
  }

  @Override
  protected Ambiance extractAmbiance(InitiateNodeBatchEvent event) {
    return event.getAmbiance();
  }

  @Override
  protected void handleEventWithContext(InitiateNodeBatchEvent event) {
    if (event.getChildrenCount() == 0 || blockExecutionMetadataService.validate(event.getAmbiance())) {
      return;
    }
    Ambiance parentAmbiance = event.getAmbiance();
    if (event.getShouldRegisterCallback()) {
      // TODO: Make this also batched
      for (InitiateNodeBatchEvent.Child child : event.getChildrenList()) {
        MaxConcurrentChildCallback maxConcurrentChildCallback =
            MaxConcurrentChildCallback.builder()
                .parentNodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(parentAmbiance))
                .planExecutionId(parentAmbiance.getPlanExecutionId())
                .maxConcurrency(event.getMaxConcurrency())
                .proceedIfFailed(event.getShouldProceedIfFailed())
                .build();
        String waitInstanceId =
            waitNotifyEngine.waitForAllOn(publisherName, maxConcurrentChildCallback, child.getRuntimeId());
        log.info("SpawnChildrenRequestProcessor registered a waitInstance for maxConcurrency with waitInstanceId: {}",
            waitInstanceId);
      }
    }

    List<InitiateNodeRequest> requestList =
        event.getChildrenList()
            .stream()
            .map(o
                -> InitiateNodeRequest.builder()
                       .setupId(o.getSetupId())
                       .runtimeId(o.getRuntimeId())
                       .strategyMetadata(o.hasStrategyMetadata() ? o.getStrategyMetadata() : null)
                       .build())
            .toList();
    InitiateNodeBatchRequest initiateNodeBatchRequest = InitiateNodeBatchRequest.builder()
                                                            .nodes(requestList)
                                                            .parentAmbiance(parentAmbiance)
                                                            .childCount(requestList.size())
                                                            .build();

    switch (event.getInitiateMode()) {
      case CREATE -> engine.initiateNodes(initiateNodeBatchRequest, event.getInitiateMode());
      case CREATE_AND_START -> {
        List<NodeExecution> nodeExecutions = engine.initiateNodes(initiateNodeBatchRequest, InitiateMode.CREATE);
        nodeExecutions.forEach(nodeExecution -> initiateNodeHelper.publishEvent(nodeExecutionService.getAmbiance(nodeExecution), InitiateMode.START));
      }
      default ->
              throw new InvalidRequestException(String.format("Invalid mode %s not supported in InitiateNode batch request", event.getInitiateMode()));
    }
  }
}
