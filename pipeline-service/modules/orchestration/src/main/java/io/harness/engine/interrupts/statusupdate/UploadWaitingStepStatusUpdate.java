/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.statusupdate;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.execution.Status.UPLOAD_WAITING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.helpers.PipelineStageStatusHelper;
import io.harness.engine.observers.NodeStatusUpdateHandler;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Inject;

@OwnedBy(PIPELINE)
public class UploadWaitingStepStatusUpdate implements NodeStatusUpdateHandler {
  @Inject private PlanExecutionService planExecutionService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject PipelineStageStatusHelper pipelineStageStatusHelper;

  @Override
  public void handleNodeStatusUpdate(NodeUpdateInfo nodeStatusUpdateInfo) {
    String stageNodeExecutionId = NodeExecutionContextUtils.getStageRuntimeId(nodeStatusUpdateInfo.getNodeExecution());
    nodeExecutionService.updateStatusWithOps(
        stageNodeExecutionId, UPLOAD_WAITING, null, StatusUtils.planAllowedStartSet(UPLOAD_WAITING));
    planExecutionService.updateStatus(nodeStatusUpdateInfo.getPlanExecutionId(), UPLOAD_WAITING);
    pipelineStageStatusHelper.updatePipelineAndStageWaitingStatus(nodeStatusUpdateInfo, UPLOAD_WAITING);
  }
}
