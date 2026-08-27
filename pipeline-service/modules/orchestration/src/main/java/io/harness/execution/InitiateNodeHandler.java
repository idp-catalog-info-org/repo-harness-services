/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.beans.FeatureName.PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2;
import static io.harness.plan.NodeType.PLAN_NODE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.InitiateNodeEvent;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class InitiateNodeHandler extends PmsBaseEventHandler<InitiateNodeEvent> {
  @Inject private OrchestrationEngine engine;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject BlockExecutionMetadataService blockExecutionMetadataService;

  @Override
  protected String getEventType(InitiateNodeEvent message) {
    return "trigger_node_event";
  }

  @Override
  protected Map<String, String> extraLogProperties(InitiateNodeEvent event) {
    return ImmutableMap.of();
  }

  @Override
  protected Ambiance extractAmbiance(InitiateNodeEvent event) {
    return event.getAmbiance();
  }

  @Override
  protected void handleEventWithContext(InitiateNodeEvent event) {
    if (blockExecutionMetadataService.validate(event.getAmbiance())) {
      return;
    }
    if (event.getInitiateMode() == InitiateMode.START) {
      var stuckMonitorV2Enabled = !pmsFeatureFlagHelper.isEnabled(
          AmbianceUtils.getAccountId(event.getAmbiance()), PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2);
      if (stuckMonitorV2Enabled && PLAN_NODE.equals(OrchestrationUtils.currentNodeType(event.getAmbiance()))) {
        var nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(event.getAmbiance());
        // markNodesProcessing already retries once on transient Mongo failures. If it is still exhausted, fail the
        // node explicitly instead of queuing execution, so the node errors out fast rather than hanging until the
        // pipeline timeout. See PIPE-35791.
        try {
          nodeExecutionService.markNodesProcessing(Collections.singletonList(nodeExecutionId), true);
        } catch (Exception ex) {
          log.error(
              "Failed to mark node {} as processing before queuing execution. Failing the node.", nodeExecutionId, ex);
          engine.handleError(event.getAmbiance(), ex);
          return;
        }
      }
      engine.queueOrStartExecution(event.getAmbiance());
    } else {
      engine.initiateNode(event.getAmbiance(), event.getNodeId(), event.getRuntimeId(), null,
          event.hasStrategyMetadata() ? event.getStrategyMetadata() : null, event.getInitiateMode());
    }
  }
}
