/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Inject;

@OwnedBy(PIPELINE)
public class PipelineStageStatusHelper {
  @Inject private PlanExecutionService planExecutionService;
  @Inject private NodeExecutionService nodeExecutionService;

  public void updatePipelineAndStageWaitingStatus(NodeUpdateInfo nodeStatusUpdateInfo, Status waitingStatus) {
    if (nodeStatusUpdateInfo == null || nodeStatusUpdateInfo.getNodeExecution() == null
        || (nodeStatusUpdateInfo.getNodeExecution().getExecutionContext() == null
            && nodeStatusUpdateInfo.getNodeExecution().getAmbiance() == null)
        || !StatusUtils.waitingStatuses().contains(waitingStatus)) {
      return;
    }
    updatePipelineAndStageStatus(
        nodeExecutionService.getAmbiance(nodeStatusUpdateInfo.getNodeExecution()), waitingStatus);
  }

  public void updatePipelineAndStageRunningStatus(Ambiance ambiance, Status runningStatus) {
    if (ambiance.getMetadata() == null || !StatusUtils.activeStatuses().contains(runningStatus)) {
      return;
    }
    updatePipelineAndStageStatus(ambiance, runningStatus);
  }

  private void updatePipelineAndStageStatus(Ambiance ambiance, Status status) {
    PipelineStageInfo pipelineStageInfo = ambiance.getMetadata().getPipelineStageInfo();
    if (pipelineStageInfo != null && pipelineStageInfo.getHasParentPipeline()) {
      NodeExecution nodeExecution = nodeExecutionService.getByPlanNodeUuid(
          pipelineStageInfo.getStageNodeId(), pipelineStageInfo.getExecutionId());
      if (nodeExecution != null) {
        nodeExecutionService.updateStatusWithOps(
            nodeExecution.getStageExecutionId(), status, null, StatusUtils.planAllowedStartSet(status));
        planExecutionService.updateStatus(pipelineStageInfo.getExecutionId(), status);
      }
    }
  }
}
