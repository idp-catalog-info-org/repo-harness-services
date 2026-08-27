/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.plannode;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.execution.helpers.InitiateNodeHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import com.google.inject.Inject;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Simple DAG Execution Callback for dependency-based execution.
 *
 * When a dependency completes, this callback initiates the target stage that was waiting.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Data
@Builder
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class DagExecutionCallback implements OldNotifyCallback {
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private InitiateNodeHelper initiateNodeHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  // Callback configuration
  private final Ambiance ambiance; // ambiance of the completed node
  private final String prevPlanExecutionId; // stages node execution ID
  private final String targetStageNodeId;
  private final String prevNodeId; // stage to initiate (s2, s3, s4, s5)

  @Override
  public void notify(Map<String, ResponseData> response) {
    try {
      log.info("DAG Callback triggered - Initiating target stage: {} under parent: {}", targetStageNodeId,
          prevPlanExecutionId);

      if (shouldSkipDueToRollback()) {
        log.info(
            "Pipeline rollback triggered - Skipping initiation of target stage: {}. Stage will remain NOT_STARTED.",
            targetStageNodeId);
        return;
      }
      String targetStageRuntimeId =
          AmbianceUtils.generateNodeExecutionId(ambiance, prevPlanExecutionId, targetStageNodeId);
      // Initiate the target stage
      orchestrationEngine.initiateNode(
          ambiance, targetStageNodeId, targetStageRuntimeId, null, null, InitiateMode.CREATE_AND_START);

    } catch (Exception ex) {
      log.error("Error in DAG Callback for target stage: {}", targetStageNodeId, ex);
    }
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    log.warn("DAG Callback received error for target stage: {}", targetStageNodeId);
  }

  private boolean shouldSkipDueToRollback() {
    try {
      if (AmbianceUtils.isRollbackModeExecution(ambiance)) {
        return false;
      }

      OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.USE_PIPELINE_ROLLBACK_STRATEGY));

      if (optionalSweepingOutput.isFound()) {
        return true;
      }
    } catch (Exception ex) {
      log.warn("Error checking for pipeline rollback sweeping output", ex);
    }
    return false;
  }
}
