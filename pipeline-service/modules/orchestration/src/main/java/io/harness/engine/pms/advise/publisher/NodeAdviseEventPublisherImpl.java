/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.publisher;

import static io.harness.beans.FeatureName.PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.advise.utils.NodeAdviserUtils;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.execution.NodeExecution;
import io.harness.plan.Node;
import io.harness.pms.contracts.advisers.AdviseEvent;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class NodeAdviseEventPublisherImpl implements NodeAdviseEventPublisher {
  @Inject private PmsEventSender eventSender;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsFeatureFlagHelper featureFlagHelper;
  @Inject private OrchestrationEngine orchestrationEngine;

  @Override
  public void publishEvent(NodeExecution nodeExecution, Node planNode, Status fromStatus) {
    publishEvent(nodeExecution, nodeExecution.getFailureInfo(), planNode, fromStatus);
  }

  @Override
  public void publishEvent(NodeExecution nodeExecution, FailureInfo failureInfo, Node planNode, Status fromStatus) {
    Ambiance ambiance = nodeExecutionService.getAmbiance(nodeExecution);
    AdviseEvent adviseEvent =
        NodeAdviserUtils.createAdviseEvent(nodeExecution, failureInfo, planNode, fromStatus, ambiance);
    var stuckMonitorV2Enabled =
        !featureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2);
    if (stuckMonitorV2Enabled) {
      // markNodesProcessing already retries once on transient Mongo failures. If it is still exhausted, fail the node
      // explicitly instead of publishing the advise event, so the node errors out fast rather than hanging until the
      // pipeline timeout. See PIPE-35791.
      try {
        nodeExecutionService.markNodesProcessing(Collections.singletonList(nodeExecution.getUuid()), true);
      } catch (Exception ex) {
        log.error("Failed to mark node {} as processing before publishing advise event. Failing the node.",
            nodeExecution.getUuid(), ex);
        orchestrationEngine.handleError(ambiance, ex);
        return;
      }
    }
    eventSender.sendEvent(ambiance, adviseEvent, PmsEventCategory.NODE_ADVISE, nodeExecution.getModule(), true, false);
  }
}
