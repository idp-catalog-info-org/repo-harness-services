/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.statusupdate;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.pms.contracts.execution.Status.RESOURCE_WAITING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.helpers.PipelineStageStatusHelper;
import io.harness.engine.observers.NodeStatusUpdateHandler;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;

@OwnedBy(CDC)
public class ResourceWaitingStepStatusUpdate implements NodeStatusUpdateHandler {
  @Inject private PlanExecutionService planExecutionService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject PipelineStageStatusHelper pipelineStageStatusHelper;

  @Override
  public void handleNodeStatusUpdate(NodeUpdateInfo nodeStatusUpdateInfo) {
    if (!pmsFeatureFlagService.isEnabled(
            NodeExecutionContextUtils.getAccountId(nodeStatusUpdateInfo.getNodeExecution()),
            FeatureName.CDS_MARK_PIPELINE_AND_STAGE_AS_RESOURCE_WAITING)) {
      return;
    }
    String stageNodeExecutionId = NodeExecutionContextUtils.getStageRuntimeId(nodeStatusUpdateInfo.getNodeExecution());
    nodeExecutionService.updateStatusWithOps(
        stageNodeExecutionId, RESOURCE_WAITING, null, StatusUtils.planAllowedStartSet(RESOURCE_WAITING));
    planExecutionService.updateStatus(nodeStatusUpdateInfo.getPlanExecutionId(), RESOURCE_WAITING);
    pipelineStageStatusHelper.updatePipelineAndStageWaitingStatus(nodeStatusUpdateInfo, RESOURCE_WAITING);
  }
}
