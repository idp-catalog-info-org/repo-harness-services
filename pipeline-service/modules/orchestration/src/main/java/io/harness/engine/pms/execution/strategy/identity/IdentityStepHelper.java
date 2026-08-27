/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.advisers.rollback.CombinedRollbackSweepingOutput;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.data.RawOptionalSweepingOutput;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.engine.pms.steps.identity.IdentityStepParameters;
import io.harness.execution.NodeExecution;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.tasks.ResponseData;
import io.harness.utils.execution.ExecutionModeUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class IdentityStepHelper {
  @Inject private PmsOutcomeService pmsOutcomeService;
  @Inject private PmsSweepingOutputService pmsSweepingOutputService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanExpansionService planExpansionService;

  public StepResponse handleChildResponse(
      Ambiance ambiance, IdentityStepParameters identityParams, Map<String, ResponseData> responseDataMap) {
    NodeExecution originalNodeExecution = nodeExecutionService.getWithFieldsIncluded(
        identityParams.getOriginalNodeExecutionId(), NodeProjectionUtils.withStatusAndStepTypeAndAmbiance);

    // Copying the outcomes
    pmsOutcomeService.cloneForRetryExecution(ambiance, identityParams.getOriginalNodeExecutionId());
    planExpansionService.updateExpansionForRetriedNode(ambiance, originalNodeExecution.getPlanExecutionId());
    ExecutionMode executionMode = ambiance.getMetadata().getExecutionMode();
    if (AmbianceUtils.checkIfFeatureFlagEnabled(
            ambiance, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK.name())
        && ExecutionModeUtils.isRollbackMode(executionMode)) {
      RawOptionalSweepingOutput sweepingOutput = pmsSweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.COMBINED_ROLLBACK_STATUS));
      if (sweepingOutput.isFound()) {
        CombinedRollbackSweepingOutput combinedRollbackOutput =
            (CombinedRollbackSweepingOutput) RecastOrchestrationUtils.fromJson(
                sweepingOutput.getOutput(), ExecutionSweepingOutput.class);
        // If all steps or stages are in a 'Suspended' status, it indicates that no rollback steps have been executed.
        // Consequently, the process is marked as 'Skipped'.
        if (checkIfAllStepsAreSuspended(combinedRollbackOutput.getResponseDataMap())) {
          return StepResponse.builder().status(Status.SKIPPED).build();
        }
        return createStepResponseFromChildResponse(combinedRollbackOutput.getResponseDataMap());
      } else if (originalNodeExecution.getStepType().getStepCategory().equals(StepCategory.STAGE)) {
        return StepResponse.builder().status(Status.SKIPPED).build();
      }
    }
    return StepResponse.builder().status(originalNodeExecution.getStatus()).build();
  }

  private boolean checkIfAllStepsAreSuspended(Map<String, ResponseData> responseDataMap) {
    if (null == responseDataMap) {
      return true;
    }
    List<StepResponseNotifyData> stepResponseNotifyDataList =
        responseDataMap.values().stream().map(o -> (StepResponseNotifyData) o).toList();
    List<Status> statusList = stepResponseNotifyDataList.stream().map(StepResponseNotifyData::getStatus).toList();
    return statusList.stream().allMatch(status -> status == Status.SUSPENDED);
  }

  public StepResponse handleChildrenResponse(
      Ambiance ambiance, IdentityStepParameters identityParams, Map<String, ResponseData> responseDataMap) {
    NodeExecution originalNodeExecution = nodeExecutionService.getWithFieldsIncluded(
        identityParams.getOriginalNodeExecutionId(), NodeProjectionUtils.withAmbianceAndStatus);
    // copying the outcomes
    pmsOutcomeService.cloneForRetryExecution(ambiance, identityParams.getOriginalNodeExecutionId());
    planExpansionService.updateExpansionForRetriedNode(ambiance, originalNodeExecution.getPlanExecutionId());
    return StepResponse.builder().status(originalNodeExecution.getStatus()).build();
  }
}
