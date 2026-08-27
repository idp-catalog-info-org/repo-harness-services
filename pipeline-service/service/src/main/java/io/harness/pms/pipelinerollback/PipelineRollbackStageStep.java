/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinerollback;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.constants.OrchestrationStepTypes;
import io.harness.engine.execution.PipelineStageResponseData;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.helper.PipelineExecutor;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.sdk.core.steps.io.EmptyStepParameters;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.security.PmsSecurityContextGuardUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(PIPELINE)
public class PipelineRollbackStageStep implements AsyncExecutableWithRbac<EmptyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(OrchestrationStepTypes.PIPELINE_ROLLBACK_STAGE)
                                               .setStepCategory(StepCategory.STAGE)
                                               .build();

  @Inject private PipelineExecutor pipelineExecutor;
  @Inject private PmsExecutionSummaryService executionSummaryService;
  @Inject private InterruptService interruptService;
  @Inject private PMSExecutionService executionService;

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String parentUniqueId = AmbianceUtils.getParentUniqueIdentifier(ambiance);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountId)
                              .orgIdentifier(orgId)
                              .projectIdentifier(projectId)
                              .uniqueId(parentUniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    String currentPlanExecutionId = ambiance.getPlanExecutionId();
    log.info("Starting Pipeline Rollback");
    if (AmbianceUtils.isRollbackModeExecution(ambiance)) {
      throw new InvalidRequestException(
          "Failed to start Pipeline Rollback, as the current execution is also a pipeline rollback");
    }
    PipelineStageInfo parentStageInfo = buildParentStageInfo(ambiance);
    PlanExecution rollbackPlanExecution = pipelineExecutor.startPipelineRollback(
        accountId, orgId, projectId, currentPlanExecutionId, parentStageInfo, scopeInfo);
    if (rollbackPlanExecution == null) {
      throw new InvalidRequestException("Failed to start Pipeline Rollback");
    }
    Update update = new Update();
    update.set(PlanExecutionSummaryKeys.rollbackModeExecutionId, rollbackPlanExecution.getUuid());
    executionSummaryService.update(currentPlanExecutionId, update);
    return AsyncExecutableResponse.newBuilder().addCallbackIds(rollbackPlanExecution.getUuid()).build();
  }

  public PipelineStageInfo buildParentStageInfo(Ambiance ambiance) {
    String currentSetupId = AmbianceUtils.obtainCurrentSetupId(ambiance);
    return PipelineStageInfo.newBuilder().setStageNodeId(currentSetupId).setHasParentPipeline(false).build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, EmptyStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // Only one callback is registered in executeAsyncAfterRbac (the rollback plan execution id), so the map has a
    // single entry carrying the rollback execution's final status. When the rollback execution EXPIRED, propagate
    // EXPIRED so the parent node (and plan) is not falsely marked as successful. For all other statuses we retain
    // the previous behaviour of marking the rollback stage as SUCCEEDED.
    if (isNotEmpty(responseDataMap)) {
      ResponseData responseData = responseDataMap.values().iterator().next();
      if (responseData instanceof PipelineStageResponseData
          && ((PipelineStageResponseData) responseData).getStatus() == Status.EXPIRED) {
        return StepResponse.builder().status(Status.EXPIRED).build();
      }
    }
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  @Override
  public void handleAbort(Ambiance ambiance, EmptyStepParameters stepParameters,
      AsyncExecutableResponse executableResponse, boolean userMarked) {
    Principal principal = PmsSecurityContextGuardUtils.getPrincipalFromAmbiance(ambiance);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    SecurityContextBuilder.setContext(principal);
    if (userMarked) {
      handleInterrupt(ambiance, executableResponse, PlanExecutionInterruptType.UserMarkedFailure,
          EnumSet.of(InterruptType.USER_MARKED_FAIL_ALL, InterruptType.MARK_FAILED));
    } else {
      handleInterrupt(ambiance, executableResponse, PlanExecutionInterruptType.ABORTALL,
          EnumSet.of(InterruptType.ABORT_ALL, InterruptType.ABORT));
    }
  }

  private void handleInterrupt(Ambiance ambiance, AsyncExecutableResponse executableResponse,
      PlanExecutionInterruptType planExecutionInterruptType, EnumSet<InterruptType> types) {
    // Setting the same config for rollback execution as the main execution
    List<Interrupt> interrupts = interruptService.fetchPlanLevelInterrupt(ambiance.getPlanExecutionId(), types);
    if (isNotEmpty(interrupts)) {
      Interrupt interrupt = interrupts.get(0);
      if (executableResponse != null && isNotEmpty(executableResponse.getCallbackIdsList())) {
        executionService.registerInterrupt(
            planExecutionInterruptType, executableResponse.getCallbackIds(0), null, interrupt.getInterruptConfig());
      }
    }
  }

  @Override
  public void validateResources(Ambiance ambiance, EmptyStepParameters stepParameters) {
    // do nothing
  }
}
