/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.execution.utils.NodeProjectionUtils.fieldsForRollbackTransformer;

import io.harness.AbortInfoHelper;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.constants.OrchestrationStepTypes;
import io.harness.engine.execution.PipelineStageResponseData;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.interrupts.Interrupt;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.pipelinestage.outcome.PipelineStageOutcome;
import io.harness.pms.pipelinestage.output.PipelineStageSweepingOutput;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.PlanExecutionResponseDto;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.security.PmsSecurityContextGuardUtils;
import io.harness.pms.utils.GitxBranchContextUtils;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.steps.pipelinestage.ChildPipelineExecutionDetails;
import io.harness.tasks.ResponseData;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineStageStep implements AsyncExecutableWithRbac<PipelineStageStepParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(OrchestrationStepTypes.PIPELINE_STAGE).setStepCategory(StepCategory.STAGE).build();

  public static String NESTED_CHAINING_HINT = "Unable to run pipeline [%s]";
  public static String NESTED_CHAINING_ERROR =
      "The referred pipeline [%s] invokes a child pipeline, so it cannot be included within another pipeline. Nested "
      + "Pipeline Chaining is not supported";

  @Inject private PipelineExecutor pipelineExecutor;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  @Inject private PipelineStageHelper pipelineStageHelper;

  @Inject private AccessControlClient client;

  @Inject private PMSExecutionService pmsExecutionService;
  @Inject private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject InterruptService interruptService;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @Inject private ExecutionHelper executionHelper;
  @Inject private AbortInfoHelper abortInfoHelper;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;

  @Override
  public Class<PipelineStageStepParameters> getStepParametersClass() {
    return PipelineStageStepParameters.class;
  }

  @Override
  public void handleAbort(Ambiance ambiance, PipelineStageStepParameters stepParameters,
      AsyncExecutableResponse executableResponse, boolean userMarked) {
    if (userMarked) {
      handleInterrupt(ambiance, executableResponse, PlanExecutionInterruptType.UserMarkedFailure,
          EnumSet.of(InterruptType.USER_MARKED_FAIL_ALL, InterruptType.MARK_FAILED));
    } else {
      handleInterrupt(ambiance, executableResponse, PlanExecutionInterruptType.ABORTALL,
          EnumSet.of(InterruptType.ABORT_ALL, InterruptType.ABORT));
    }
  }

  @Override
  public void handleExpire(
      Ambiance ambiance, PipelineStageStepParameters stepParameters, AsyncExecutableResponse executableResponse) {
    handleInterrupt(ambiance, executableResponse, PlanExecutionInterruptType.EXPIREALL,
        EnumSet.of(InterruptType.EXPIRE_ALL, InterruptType.MARK_EXPIRED));
  }

  public void handleInterrupt(Ambiance ambiance, AsyncExecutableResponse executableResponse,
      PlanExecutionInterruptType planExecutionInterruptType, EnumSet<InterruptType> types) {
    setSourcePrincipal(ambiance);
    // Setting interrupt config of parent pipeline while registering interrupt for child pipeline
    List<Interrupt> interrupts = interruptService.fetchPlanLevelInterrupt(ambiance.getPlanExecutionId(), types);
    if (isNotEmpty(interrupts)) {
      Interrupt interrupt = interrupts.get(0);
      if (executableResponse != null && isNotEmpty(executableResponse.getCallbackIdsList())) {
        pmsExecutionService.registerInterrupt(
            planExecutionInterruptType, executableResponse.getCallbackIds(0), null, interrupt.getInterruptConfig());
      }
    }
  }

  public void setSourcePrincipal(Ambiance ambiance) {
    Principal principal = PmsSecurityContextGuardUtils.getPrincipalFromAmbiance(ambiance);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    SecurityContextBuilder.setContext(principal);
  }

  @Override
  public void validateResources(Ambiance ambiance, PipelineStageStepParameters stepParameters) {
    if (ambiance.getMetadata().getPipelineStageInfo().getHasParentPipeline()) {
      throw NestedExceptionUtils.hintWithExplanationException(
          String.format(NESTED_CHAINING_HINT, ambiance.getMetadata().getPipelineStageInfo().getIdentifier()),
          String.format(NESTED_CHAINING_ERROR, ambiance.getMetadata().getPipelineIdentifier()));
    }
    pipelineStageHelper.validateResource(client, ambiance, stepParameters);
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, PipelineStageStepParameters stepParameters, StepInputPackage inputPackage) {
    log.info(String.format("Starting Pipeline Stage with child pipeline %s", stepParameters.getPipeline()));

    PlanExecutionResponseDto responseDto = null;

    // set the principal for child
    setSourcePrincipal(ambiance);

    String childBranch = stepParameters.getGitBranch() != null ? stepParameters.getGitBranch() : null;

    // Check if this is a rerun with original definition
    String accountId = AmbianceUtils.getAccountId(ambiance);
    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(accountId, stepParameters.getOrg(), stepParameters.getProject());

    boolean isRerunWithOriginalDefinition = false;
    String originalChildExecutionId = null;

    boolean settingEnabled = AmbianceUtils.checkIfSettingEnabled(
        ambiance, NGPipelineSettingsConstant.ALLOW_ORIGINAL_YAML_ON_RERUN.getName());

    if (settingEnabled) {
      // Get parent execution summary to check if it's a rerun
      PipelineExecutionSummaryEntity summary = getParentExecutionSummary(accountId, ambiance.getPlanExecutionId());

      // Check if this is a rerun
      if (summary != null && summary.getExecutionTriggerInfo() != null) {
        String originalParentExecId = null;
        try {
          originalParentExecId = summary.getExecutionTriggerInfo().getRerunInfo().getPrevExecutionId();
        } catch (Exception e) {
          log.warn("Error retrieving original parent execution ID", e);
        }
        if (originalParentExecId != null && !originalParentExecId.isEmpty()) {
          // Check if the parent pipeline was rerun with original YAML
          isRerunWithOriginalDefinition =
              isParentPipelineUsingOriginalDefinition(accountId, ambiance.getPlanExecutionId(), originalParentExecId);

          if (isRerunWithOriginalDefinition) {
            try {
              // Find the original child execution ID from the original parent execution
              originalChildExecutionId = findOriginalChildExecutionId(originalParentExecId, ambiance);
              if (originalChildExecutionId == null) {
                throw new InvalidRequestException(String.format(
                    "Failed to find original child execution ID for parent execution: %s", originalParentExecId));
              }
            } catch (Exception e) {
              log.warn("Failed to find original child execution ID", e);
            }
          }
        }
      }
    }

    PipelineStageInfo info = prepareParentStageInfo(ambiance, stepParameters);
    try (ResponseTimeRecorder ignore1 = new ResponseTimeRecorder("[PMS_PIPELINE_STAGE_STEP]")) {
      GitEntityInfo requestInfo = GitAwareContextHelper.getGitRequestParamsInfo();
      if (isRerunWithOriginalDefinition && originalChildExecutionId != null) {
        log.info("Rerunning child pipeline with original definition from execution: {}", originalChildExecutionId);

        String finalOriginalChildExecutionId = originalChildExecutionId;
        responseDto = GitxBranchContextUtils.withBranch(requestInfo, childBranch,
            ()
                -> pipelineExecutor.runPipelineAsChildPipelineWithJsonNode(accountId, stepParameters.getOrg(),
                    stepParameters.getProject(), stepParameters.getPipeline(), ambiance.getMetadata().getModuleType(),
                    stepParameters.getPipelineInputsJsonNode(), false, false, stepParameters.getInputSetReferences(),
                    info, ambiance.getMetadata().getIsDebug(), finalOriginalChildExecutionId, true, scopeInfo));
      } else {
        // Normal child pipeline execution
        responseDto = GitxBranchContextUtils.withBranch(requestInfo, childBranch,
            ()
                -> pipelineExecutor.runPipelineAsChildPipelineWithJsonNode(accountId, stepParameters.getOrg(),
                    stepParameters.getProject(), stepParameters.getPipeline(), ambiance.getMetadata().getModuleType(),
                    stepParameters.getPipelineInputsJsonNode(), false, false, stepParameters.getInputSetReferences(),
                    info, ambiance.getMetadata().getIsDebug(), null, false, scopeInfo));
      }
    }

    if (responseDto == null) {
      throw new InvalidRequestException(
          String.format("Failed to execute child pipeline %s", stepParameters.getPipeline()));
    }
    sdkGraphVisualizationDataService.publishStepDetailInformation(ambiance,
        ChildPipelineExecutionDetails.builder()
            .planExecutionId(responseDto.getPlanExecution().getUuid())
            .projectId(stepParameters.getProject())
            .orgId(stepParameters.getOrg())
            .build(),
        "childPipelineExecutionDetails");
    // saving output for handleAsyncResponse
    sweepingOutputService.consume(ambiance, PipelineStageSweepingOutput.OUTPUT_NAME,
        PipelineStageSweepingOutput.builder().childExecutionId(responseDto.getPlanExecution().getUuid()).build(),
        StepCategory.STAGE.name());

    return AsyncExecutableResponse.newBuilder().addCallbackIds(responseDto.getPlanExecution().getUuid()).build();
  }

  public PipelineStageInfo prepareParentStageInfo(Ambiance ambiance, PipelineStageStepParameters stepParameters) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(accountIdentifier,
            ambiance.getPlanExecutionId(),
            Sets.newHashSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.name));

    return PipelineStageInfo.newBuilder()
        .setExecutionId(ambiance.getPlanExecutionId())
        .setStageNodeId(stepParameters.getStageNodeId())
        .setHasParentPipeline(true)
        .setPipelineName(pipelineExecutionSummaryEntity.getName())
        .setRunSequence(ambiance.getMetadata().getRunSequence())
        .setIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .setProjectId(ambiance.getSetupAbstractions().get("projectIdentifier"))
        .setOrgId(ambiance.getSetupAbstractions().get("orgIdentifier"))
        .build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, PipelineStageStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Handling Pipeline Stage Response");
    OptionalSweepingOutput sweepingOutput = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(PipelineStageSweepingOutput.OUTPUT_NAME));
    if (!sweepingOutput.isFound() || !(sweepingOutput.getOutput() instanceof PipelineStageSweepingOutput)) {
      log.error("Child Pipeline details were not saved");
      return StepResponse.builder().status(Status.FAILED).build();
    }

    PipelineStageSweepingOutput pipelineStageSweepingOutput = (PipelineStageSweepingOutput) sweepingOutput.getOutput();

    NodeExecution nodeExecution = null;
    Ambiance childNodeAmbiance = null;
    Optional<NodeExecution> nodeExecutionOptional = nodeExecutionService.getPipelineNodeExecutionWithProjections(
        pipelineStageSweepingOutput.getChildExecutionId(), NodeProjectionUtils.WithAmbianceAndFailureInfo);

    PipelineStageOutcome resolvedOutcome;
    // NodeExecutionOptional can be empty when no node was executed in child execution
    if (nodeExecutionOptional.isPresent()) {
      nodeExecution = nodeExecutionOptional.get();
      childNodeAmbiance = nodeExecutionService.getAmbiance(nodeExecution);
      resolvedOutcome =
          pipelineStageHelper.resolveOutputVariables(stepParameters.getOutputs().getValue(), childNodeAmbiance);
    } else {
      resolvedOutcome =
          new PipelineStageOutcome(pipelineStageHelper.resolveOutputVariables(stepParameters.getOutputs().getValue()));
    }

    PipelineStageResponseData pipelineStageResponseData =
        (PipelineStageResponseData) responseDataMap.get(pipelineStageSweepingOutput.getChildExecutionId());
    if (pipelineStageResponseData.getStatus() == Status.ABORTED) {
      registerDummyAbortInterruptForParent(ambiance, pipelineStageSweepingOutput.getChildExecutionId());
    }
    return StepResponse.builder()
        .status(pipelineStageResponseData.getStatus())
        .failureInfo(nodeExecution != null ? nodeExecution.getFailureInfo() : FailureInfo.newBuilder().build())
        .stepOutcome(
            StepResponse.StepOutcome.builder().name(OutputExpressionConstants.OUTPUT).outcome(resolvedOutcome).build())
        .build();
  }

  public void registerDummyAbortInterruptForParent(Ambiance ambiance, String childExecutionId) {
    Interrupt abortInterrupt = fetchAbortInterrupt(childExecutionId);
    if (abortInterrupt != null && fetchAbortInterrupt(ambiance.getPlanExecutionId()) == null) {
      interruptService.save(Interrupt.builder()
                                .planExecutionId(ambiance.getPlanExecutionId())
                                .nodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                                .interruptConfig(abortInterrupt.getInterruptConfig())
                                .type(InterruptType.ABORT)
                                .createdAt(abortInterrupt.getCreatedAt())
                                .state(Interrupt.State.PROCESSED_SUCCESSFULLY)
                                .build());
    }
  }

  private Interrupt fetchAbortInterrupt(String planExecutionId) {
    List<Interrupt> interruptsList = interruptService.fetchAbortAllPlanLevelInterrupt(planExecutionId);
    if (isNotEmpty(interruptsList)) {
      return interruptsList.get(0);
    }
    return null;
  }

  /**
   * Gets the parent execution summary with necessary fields for checking rerun status.
   */
  private PipelineExecutionSummaryEntity getParentExecutionSummary(String accountId, String executionId) {
    Set<String> summaryFields = Set.of(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.executionTriggerInfo,
        PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.orgIdentifier,
        PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.projectIdentifier,
        PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.pipelineIdentifier);

    return pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(accountId, executionId, summaryFields);
  }

  /**
   * Checks if the parent pipeline is being rerun with its original definition by comparing YAMLs.
   */
  public boolean isParentPipelineUsingOriginalDefinition(
      String accountId, String currentExecutionId, String originalExecutionId) {
    try {
      Set<String> fields = Collections.singleton(PlanExecutionMetadataKeys.yaml);

      PlanExecutionMetadata metadata =
          planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(accountId, currentExecutionId, fields);

      // If the parent execution has the original YAML from the previous execution, it means
      // it was rerun with the original definition
      if (metadata != null && metadata.getYaml() != null) {
        PlanExecutionMetadata originalMetadata = planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(
            accountId, originalExecutionId, fields);

        if (originalMetadata != null && originalMetadata.getYaml() != null
            && metadata.getYaml().equals(originalMetadata.getYaml())) {
          return true;
        }
      }
    } catch (Exception e) {
      log.warn("Error checking if parent pipeline is using original definition", e);
    }
    return false;
  }

  /**
   * Finds the original child execution ID from the original parent execution.
   */
  public String findOriginalChildExecutionId(String originalParentExecId, Ambiance ambiance) {
    Optional<NodeExecution> originalNodeExecution =
        nodeExecutionService
            .fetchNodeExecutionsForGivenStageFQNs(originalParentExecId,
                Collections.singletonList(
                    nodeExecutionService.get(AmbianceUtils.obtainCurrentRuntimeId(ambiance)).getStageFqn()),
                fieldsForRollbackTransformer)
            .findFirst();

    if (originalNodeExecution.isPresent()) {
      try {
        PipelineStageSweepingOutput output = (PipelineStageSweepingOutput) sweepingOutputService.resolve(
            nodeExecutionService.getAmbiance(originalNodeExecution.get()),
            RefObject.newBuilder().setName(PipelineStageSweepingOutput.OUTPUT_NAME).build());

        if (output != null && output.getChildExecutionId() != null) {
          return output.getChildExecutionId();
        }
      } catch (Exception e) {
        log.warn("Error retrieving sweeping output for original execution", e);
      }
    }
    return null;
  }
}
