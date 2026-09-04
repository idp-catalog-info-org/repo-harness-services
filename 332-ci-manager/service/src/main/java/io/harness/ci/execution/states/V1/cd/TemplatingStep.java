/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.beans.steps.CIStepInfoType.TEMPLATING_STEP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.cd.beans.outcomes.OutputVarsOutcome;
import io.harness.cd.beans.outcomes.ServiceHookMetadata;
import io.harness.cd.beans.outcomes.ServiceHooksSweepingOutput;
import io.harness.cd.beans.outcomes.UnifiedServiceOutcome;
import io.harness.ci.execution.integrationstage.ci.CIStepGroupUtils;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.ci.states.V1.cd.TemplatingStepPassThroughData.ChainLink;
import io.harness.ci.states.V1.cd.TemplatingStepPassThroughData.TemplatingStepPassThroughDataBuilder;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncChainExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.runner.request.builder.RunnerRequestBuilder;
import io.harness.runner.request.utils.RunnerSubmitTaskUtils;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.steps.executable.AsyncChainExecutableWithRbac;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.unified.cd.service.spec.ServiceType;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.DeployTemplateFetchHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * Two ways to make service hooks run sequentially:
 * Method 1: Make TemplatingStep of AsyncChain type and have all the children run sequentially, one after the other
 * Method 2: Keep TemplatingStep of Async type and introduce a new dummy node like TemplatingSection of AsyncChain type
 * (like we have ManifestSection under Service node), as child of TemplatingStep. Now, all the hooks and the templating
 * logic will run sequentially one after the other.
 *
 * We've moved ahead with Method 1 because, besides service hook, the only possible logic that can run inside
 * TemplatingStep is the templating logic itself so introducing a dummy node would be more of an overhead.
 *
 * If later we do plan to introduce new steps under TemplatingStep which can run independently of the service hooks and
 * the templating logic, then moving to Method 2 would be the more optimal approach.
 */
@OwnedBy(HarnessTeam.CI)
@Singleton
@Slf4j
public class TemplatingStep implements AsyncChainExecutableWithRbac<TemplatingStepParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(TEMPLATING_STEP.getDisplayName()).setStepCategory(StepCategory.STEP).build();
  public static final String COMMAND_UNIT_TEMPLATING = "manifest-templating";
  public static final String SERVICE_OUTPUT_FILES_TO_TEMPLATIZED_EXP = "${{serviceOutput.manifests.toTemplate}}";
  public static final String SERVICE_OUTPUT_MANIFESTS_PRIMARY_TYPE_EXP = "${{serviceOutput.manifests.primary.uses}}";

  /**
   * Manifest types for which the templating step always runs regardless of whether
   * values/override files are present. For these types the plugin operates directly
   * on the primary manifest file, so there is always something to template.
   * Add new types here as they gain templating support.
   */
  public static final Set<String> MANIFEST_TYPES_ALWAYS_TEMPLATE =
      Set.of("serverless", "aws-sam", "kustomize", "openshift");

  @Inject private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Inject RunnerSubmitTaskUtils runnerSubmitTaskUtils;
  @Inject private CommonAbstractStepUtils commonAbstractStepUtils;
  @Inject private DeployTemplateFetchHelper deployTemplateFetchHelper;
  @Inject SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private ResponseHandlerUtils responseHandlerUtils;
  @Inject private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Inject private ServiceHookTaskHelper serviceHookTaskHelper;

  @Override
  public void validateResources(Ambiance ambiance, TemplatingStepParameters stepParameters) {}

  @Override
  public Class<TemplatingStepParameters> getStepParametersClass() {
    return TemplatingStepParameters.class;
  }

  // Chain link 1: submit the first pre-template hook (or skip to templating/post-hooks if none).
  // All logKeys (pre-hooks, templating, post-hooks) are declared upfront so the UI renders all
  // log tabs from the start. Hook logKeys are computed from RunnerRequestBuilder.generateLogKey
  // against the TemplatingStep's ambiance — this matches what the runner actually uses, regardless
  // of whatever base path was computed when hooks were registered in UnifiedServiceStep.
  @Override
  public AsyncChainExecutableResponse startChainLinkAfterRbac(
      Ambiance ambiance, TemplatingStepParameters stepParameters, StepInputPackage inputPackage) {
    List<String> logKeys = new ArrayList<>();
    List<String> commandUnits = new ArrayList<>();
    Map<String, String> postHookLogKeys = new LinkedHashMap<>();

    boolean hooksEnabled = serviceHookTaskHelper.isServiceHooksEnabled(ambiance);

    ServiceHooksSweepingOutput preHooks = hooksEnabled
        ? resolveHooksOutput(serviceStepSweepingOutputHelper.fetchPreTemplateHooksSweepingOutput(ambiance))
        : null;
    ServiceHooksSweepingOutput postHooks = hooksEnabled
        ? resolveHooksOutput(serviceStepSweepingOutputHelper.fetchPostTemplateHooksSweepingOutput(ambiance))
        : null;

    List<ServiceHookMetadata> preHookList = toHookList(preHooks);
    List<ServiceHookMetadata> postHookList = toHookList(postHooks);
    boolean templatingWillRun = willRunTemplating(ambiance);

    // Nothing to do — signal chain end immediately (finalizeExecution gets ptd==null → SKIPPED).
    if (preHookList.isEmpty() && postHookList.isEmpty() && !templatingWillRun) {
      return AsyncChainExecutableResponse.newBuilder().setChainEnd(true).build();
    }

    // Register all log keys/command units upfront so the UI renders tabs in the correct order.
    for (ServiceHookMetadata hookMetadata : preHookList) {
      String logKey = RunnerRequestBuilder.generateLogKey(ambiance, hookMetadata.getStepId());
      logKeys.add(logKey);
      commandUnits.add(hookMetadata.getStepId());
    }

    // Only register the templating tab when the plugin will actually run.
    if (templatingWillRun) {
      String templatingLogKey = RunnerRequestBuilder.generateLogKey(ambiance, COMMAND_UNIT_TEMPLATING);
      logKeys.add(templatingLogKey);
      commandUnits.add(COMMAND_UNIT_TEMPLATING);
    }

    for (ServiceHookMetadata hookMetadata : postHookList) {
      String logKey = RunnerRequestBuilder.generateLogKey(ambiance, hookMetadata.getStepId());
      postHookLogKeys.put(hookMetadata.getStepId(), logKey);
      logKeys.add(logKey);
      commandUnits.add(hookMetadata.getStepId());
    }

    AsyncChainExecutableResponse.Builder builder =
        AsyncChainExecutableResponse.newBuilder().addAllLogKeys(logKeys).addAllUnits(commandUnits);

    if (isEmpty(preHookList)) {
      // No pre-hooks: go straight to templating or post-hooks.
      return submitTemplatingOrPostHooks(ambiance, stepParameters, postHookList, postHooks, postHookLogKeys, builder);
    }

    // Submit only the first pre-hook; carry the rest as pendingPreHooks.
    TemplatingStepPassThroughDataBuilder ptdBuilder =
        TemplatingStepPassThroughData.builder()
            .postHookLogKeys(isNotEmpty(postHookLogKeys) ? postHookLogKeys : null)
            .pendingPostHooks(new ArrayList<>(postHookList));

    return submitNextHook(ambiance, preHookList, preHooks.getEnvVars(),
        stepId -> RunnerRequestBuilder.generateLogKey(ambiance, stepId), ptdBuilder, true, builder);
  }

  // executeNextLink state machine:
  //  completedLink==null, pendingPreHooks non-empty → another pre-hook just finished; submit next
  //  completedLink==null, pendingPreHooks empty     → last pre-hook done; submit templating (or skip to post-hooks)
  //  completedLink==PRE_HOOKS, pendingPostHooks non-empty → templating done; submit first post-hook
  //  completedLink==PRE_HOOKS, pendingPostHooks empty     → templating done, no post-hooks; chainEnd=true
  //  completedLink==TEMPLATING, pendingPostHooks non-empty → a post-hook just finished; submit next
  //  completedLink==TEMPLATING, pendingPostHooks empty     → last post-hook done; chainEnd=true
  @Override
  public AsyncChainExecutableResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      TemplatingStepParameters stepParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<Map<String, ResponseData>> responseSupplier) throws Exception {
    TemplatingStepPassThroughData ptd = (TemplatingStepPassThroughData) passThroughData;
    Map<String, ResponseData> responseDataMap = responseSupplier.get();

    // A pre-hook just finished.
    if (ptd == null || ptd.getCompletedLink() == null) {
      return onPreHookLinkCompleted(ambiance, stepParameters, ptd, responseDataMap);
    }

    // Templating just finished.
    if (ChainLink.PRE_HOOKS.equals(ptd.getCompletedLink())) {
      return onTemplatingLinkCompleted(ambiance, ptd, responseDataMap);
    }

    // completedLink == TEMPLATING: a post-hook just finished.
    return onPostHookLinkCompleted(ambiance, ptd, responseDataMap);
  }

  // Chain end: finalize after all links complete.
  // ptd == null means startChainLinkAfterRbac returned chainEnd=true immediately (nothing to do).
  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, TemplatingStepParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    TemplatingStepPassThroughData ptd = (TemplatingStepPassThroughData) passThroughData;

    if (ptd == null) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    OutputVarsOutcome outcome = new OutputVarsOutcome();
    if (isNotEmpty(ptd.getOutputVars())) {
      outcome.putAll(ptd.getOutputVars());
    }
    return StepResponse.builder()
        .stepOutcome(StepResponse.StepOutcome.builder().name("output").outcome(outcome).build())
        .status(Status.SUCCEEDED)
        .build();
  }

  private boolean willRunTemplating(Ambiance ambiance) {
    OptionalSweepingOutput serviceConfigOutput = serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(ambiance);
    if (serviceConfigOutput.isFound()) {
      UnifiedServiceOutcome serviceOutcome = (UnifiedServiceOutcome) serviceConfigOutput.getOutput();
      if (ServiceType.HELM.getDisplayName().equals(serviceOutcome.getType())) {
        return false;
      }
    }
    String manifestType =
        cdStepsExpressionResolver.renderValue(ambiance, SERVICE_OUTPUT_MANIFESTS_PRIMARY_TYPE_EXP, true);
    if (!MANIFEST_TYPES_ALWAYS_TEMPLATE.contains(manifestType)) {
      String overridesFiles =
          cdStepsExpressionResolver.renderValue(ambiance, SERVICE_OUTPUT_FILES_TO_TEMPLATIZED_EXP, true);
      if (isEmpty(overridesFiles) || SERVICE_OUTPUT_FILES_TO_TEMPLATIZED_EXP.equals(overridesFiles)) {
        return false;
      }
    }
    return isNotEmpty(deployTemplateFetchHelper.getTemplatingTemplateYamlContent(manifestType, ambiance));
  }

  private ServiceHooksSweepingOutput resolveHooksOutput(OptionalSweepingOutput opt) {
    return opt.isFound() ? (ServiceHooksSweepingOutput) opt.getOutput() : null;
  }

  private List<ServiceHookMetadata> toHookList(ServiceHooksSweepingOutput hooks) {
    if (hooks == null || isEmpty(hooks.getHookMetadataMap())) {
      return Collections.emptyList();
    }
    return new ArrayList<>(hooks.getHookMetadataMap().values());
  }

  private Map<String, String> mergeOutputVars(List<Map<String, String>> outputVarsList) {
    Map<String, String> merged = new HashMap<>();
    if (isNotEmpty(outputVarsList)) {
      outputVarsList.forEach(merged::putAll);
    }
    return merged;
  }

  private void handleHookResponses(Ambiance ambiance, Map<String, ResponseData> responseDataMap, String phase) {
    if (isEmpty(responseDataMap)) {
      return;
    }
    String stepId = AmbianceUtils.obtainStepIdentifier(ambiance);
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      ResponseData responseData = serializedResponseDataHelper.deserialize(entry.getValue());
      if (responseData instanceof ErrorNotifyResponseData errorData) {
        log.error("Error in {} hook for step {}: {}", phase, stepId, errorData.getErrorMessage());
        throw new InvalidRequestException(
            "Service hook execution failed for phase " + phase + ": " + errorData.getErrorMessage());
      }
      if (responseData instanceof StepStatusTaskResponseData statusData) {
        if (statusData.getStepStatus() != null
            && !StepExecutionStatus.SUCCESS.equals(statusData.getStepStatus().getStepExecutionStatus())) {
          throw new InvalidRequestException("Service hook execution failed for phase " + phase);
        }
      } else if (responseData instanceof VmTaskExecutionResponse vmResponse) {
        if (CommandExecutionStatus.FAILURE.equals(vmResponse.getCommandExecutionStatus())) {
          log.error("Hook {} failed for step {}: {}", phase, stepId, vmResponse.getErrorMessage());
          throw new InvalidRequestException(
              "Service hook execution failed for phase " + phase + ": " + vmResponse.getErrorMessage());
        }
      }
    }
  }

  private List<Map<String, String>> handleTemplatingResponse(
      Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    if (isEmpty(responseDataMap)) {
      return new ArrayList<>();
    }

    // If any of the responses are in serialized format, deserialize them
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      entry.setValue(serializedResponseDataHelper.deserialize(entry.getValue()));
    }

    StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();

    List<Map<String, String>> outputVars = new ArrayList<>();
    if (stageInfraType == StageInfraDetails.Type.K8) {
      StepResponse failureResponse = handleK8AsyncFailureResponse(ambiance, responseDataMap);
      if (!Status.SUCCEEDED.equals(failureResponse.getStatus())) {
        String detail =
            failureResponse.getFailureInfo() != null ? failureResponse.getFailureInfo().getErrorMessage() : "";
        throw new InvalidRequestException("Failed to execute manifest templating: " + detail);
      }
      outputVars = handleK8AsyncSuccessResponse(ambiance, responseDataMap);
    } else if (stageInfraType == StageInfraDetails.Type.VM || stageInfraType == StageInfraDetails.Type.DLITE_VM) {
      StepResponse failureResponse = handleVmAsyncFailureResponses(ambiance, responseDataMap);
      if (!Status.SUCCEEDED.equals(failureResponse.getStatus())) {
        String detail =
            failureResponse.getFailureInfo() != null ? failureResponse.getFailureInfo().getErrorMessage() : "";
        throw new InvalidRequestException("Failed to execute manifest templating: " + detail);
      }
      outputVars = handleVmAsyncSuccessResponses(responseDataMap);
    }

    return outputVars;
  }

  private StepResponse handleK8AsyncFailureResponse(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    log.info("Received response for step {}", stepIdentifier);

    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      ResponseData responseData = entry.getValue();
      if (responseData instanceof ErrorNotifyResponseData) {
        log.error("Received error response for step {}, error: {}", stepIdentifier,
            ((ErrorNotifyResponseData) responseData).getErrorMessage());
        return responseHandlerUtils.getGenericFailedStepResponse(
            ambiance, "Failed to execute manifest templating", "Failed to execute manifest templating");
      }

      StepStatusTaskResponseData stepStatusTaskResponseData = (StepStatusTaskResponseData) entry.getValue();
      if (stepStatusTaskResponseData == null) {
        log.error("stepStatusTaskResponseData should not be null for step {}", stepIdentifier);
        return responseHandlerUtils.getGenericFailedStepResponse(
            ambiance, "Failed to fetch execute manifest templating", "Failed to execute manifest templating");
      }

      if (stepStatusTaskResponseData.getStepStatus() != null
          && !StepExecutionStatus.SUCCESS.equals(stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus())) {
        // Todo: check if output vars can be accessed here form stepStatusTaskResponseData
        return responseHandlerUtils.getGenericFailedStepResponse(
            ambiance, "Failed to execute manifest templating", "Failed to fetch execute manifest templating");
      }
    }

    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private StepResponse handleVmAsyncFailureResponses(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      if (entry.getValue() instanceof VmTaskExecutionResponse vmTaskExecutionResponse) {
        if (CommandExecutionStatus.FAILURE.equals(vmTaskExecutionResponse.getCommandExecutionStatus())) {
          log.error("Failed to execute manifest templating for taskId: " + entry.getKey()
              + " reason: " + vmTaskExecutionResponse.getErrorMessage());

          Map<String, String> outputVariables = new HashMap<>();
          if (isNotEmpty(vmTaskExecutionResponse.getOutputs())) {
            outputVariables = responseHandlerUtils.getOutputVariables(vmTaskExecutionResponse.getOutputs());
          }
          String failureErrorMsg = responseHandlerUtils.getFailureErrorMsg(
              outputVariables, "Failed to execute manifest templating: ", vmTaskExecutionResponse.getErrorMessage());
          return responseHandlerUtils.getGenericFailedStepResponse(ambiance,
              "Failed to execute manifest templating: " + vmTaskExecutionResponse.getErrorMessage(), failureErrorMsg);
        }
      }
    }
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private List<Map<String, String>> handleK8AsyncSuccessResponse(
      Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    log.info("Received response for step {}", stepIdentifier);
    List<Map<String, String>> outputVars = new ArrayList<>();

    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      StepStatusTaskResponseData stepStatusTaskResponseData = (StepStatusTaskResponseData) entry.getValue();
      Map<String, String> resolvedOutputVariables = new HashMap<>();

      if (isNotEmpty(stepStatusTaskResponseData.getStepStatus().getOutputV2())) {
        stepStatusTaskResponseData.getStepStatus().getOutputV2().forEach(
            outputVariable -> resolvedOutputVariables.put(outputVariable.getKey(), outputVariable.getValue()));
      }

      VmTaskExecutionResponse vmTaskExecutionResponse =
          VmTaskExecutionResponse.builder()
              .outputVars(resolvedOutputVariables)
              .outputs(stepStatusTaskResponseData.getStepStatus().getOutputV2())
              .build();
      if (isNotEmpty(vmTaskExecutionResponse.getOutputVars())) {
        outputVars.add(vmTaskExecutionResponse.getOutputVars());
      }
    }
    return outputVars;
  }

  private List<Map<String, String>> handleVmAsyncSuccessResponses(Map<String, ResponseData> responseDataMap) {
    List<Map<String, String>> outputVars = new ArrayList<>();

    if (isNotEmpty(responseDataMap)) {
      for (Map.Entry<String, ResponseData> responseDataEntry : responseDataMap.entrySet()) {
        ResponseData responseData = responseDataEntry.getValue();
        if (responseData instanceof VmTaskExecutionResponse vmTaskExecutionResponse) {
          if (isNotEmpty(vmTaskExecutionResponse.getOutputVars())) {
            outputVars.add(vmTaskExecutionResponse.getOutputVars());
          }
        }
      }
    }
    return outputVars;
  }

  /**
   * Called when all pre-hooks are done (or there were none). Submits templating if it will run,
   * otherwise submits the first post-hook (or signals chain end if no post-hooks either).
   * When called from startChainLinkAfterRbac, logKeys/units were already added to the builder.
   */
  private AsyncChainExecutableResponse submitTemplatingOrPostHooks(Ambiance ambiance,
      TemplatingStepParameters stepParameters, List<ServiceHookMetadata> postHookList,
      ServiceHooksSweepingOutput postHooks, Map<String, String> postHookLogKeys,
      AsyncChainExecutableResponse.Builder builder) {
    if (willRunTemplating(ambiance)) {
      String manifestType =
          cdStepsExpressionResolver.renderValue(ambiance, SERVICE_OUTPUT_MANIFESTS_PRIMARY_TYPE_EXP, true);
      String templateYaml = deployTemplateFetchHelper.getTemplatingTemplateYamlContent(manifestType, ambiance);
      String logKey = RunnerRequestBuilder.generateLogKey(ambiance, COMMAND_UNIT_TEMPLATING);

      StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
      StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();

      String callbackId;
      if (stageInfraType == StageInfraDetails.Type.K8 || stageInfraType.name().equals("K8")) {
        String completeStepId =
            CIStepGroupUtils.getUniqueStepIdentifier(ambiance.getLevelsList(), stepParameters.getId());
        callbackId = runnerSubmitTaskUtils.submitK8sTask(ambiance, completeStepId, stepParameters.getEnvVars(),
            templateYaml, (K8StageInfraDetails) stageInfraDetails, logKey, new HashMap<>(), new ArrayList<>());
      } else {
        callbackId = runnerSubmitTaskUtils.submitTaskByTemplate(ambiance, COMMAND_UNIT_TEMPLATING,
            stepParameters.getEnvVars(), templateYaml, new ArrayList<>(), new HashMap<>());
      }

      TemplatingStepPassThroughData ptd = TemplatingStepPassThroughData.builder()
                                              .completedLink(ChainLink.PRE_HOOKS)
                                              .templatingSkipped(false)
                                              .pendingPostHooks(new ArrayList<>(postHookList))
                                              .postHookLogKeys(isNotEmpty(postHookLogKeys) ? postHookLogKeys : null)
                                              .build();
      builder.setChainEnd(false).setPassThroughData(ByteString.copyFrom(RecastOrchestrationUtils.toBytes(ptd)));
      if (isNotEmpty(callbackId)) {
        builder.addCallbackIds(callbackId);
      }
      return builder.build();
    }

    // Templating skipped — go directly to post-hooks if any.
    if (isNotEmpty(postHookList)) {
      ServiceHookMetadata first = postHookList.get(0);
      List<ServiceHookMetadata> remaining = postHookList.subList(1, postHookList.size());
      String logKey = postHookLogKeys != null ? postHookLogKeys.get(first.getStepId()) : null;

      ServiceHooksSweepingOutput resolvedPostHooks = postHooks != null
          ? postHooks
          : (ServiceHooksSweepingOutput) serviceStepSweepingOutputHelper.fetchPostTemplateHooksSweepingOutput(ambiance)
                .getOutput();
      String callbackId = serviceHookTaskHelper.submitHookTask(
          ambiance, first, resolvedPostHooks.getEnvVars(), new HashMap<>(), logKey);

      TemplatingStepPassThroughData ptd = TemplatingStepPassThroughData.builder()
                                              .completedLink(ChainLink.TEMPLATING)
                                              .templatingSkipped(true)
                                              .pendingPostHooks(new ArrayList<>(remaining))
                                              .postHookLogKeys(isNotEmpty(postHookLogKeys) ? postHookLogKeys : null)
                                              .build();
      builder.setChainEnd(false).setPassThroughData(ByteString.copyFrom(RecastOrchestrationUtils.toBytes(ptd)));
      if (isNotEmpty(callbackId)) {
        builder.addCallbackIds(callbackId);
      }
      return builder.build();
    }

    // Templating skipped, no post-hooks — chain end.
    TemplatingStepPassThroughData ptd =
        TemplatingStepPassThroughData.builder().completedLink(ChainLink.POST_HOOKS).templatingSkipped(true).build();
    return builder.setChainEnd(true)
        .setPassThroughData(ByteString.copyFrom(RecastOrchestrationUtils.toBytes(ptd)))
        .build();
  }

  /**
   * A pre-hook (either the initial one, or one submitted from a previous call to this method) just
   * finished. Submits the next pending pre-hook if any; otherwise moves on to templating, or
   * straight to post-hooks if templating is skipped.
   */
  private AsyncChainExecutableResponse onPreHookLinkCompleted(Ambiance ambiance,
      TemplatingStepParameters stepParameters, TemplatingStepPassThroughData ptd,
      Map<String, ResponseData> responseDataMap) {
    handleHookResponses(ambiance, responseDataMap, "pre-template");
    List<ServiceHookMetadata> pendingPreHooks = ptd != null ? ptd.getPendingPreHooks() : null;
    List<ServiceHookMetadata> pendingPostHooks = ptd != null ? ptd.getPendingPostHooks() : null;
    Map<String, String> savedPostHookLogKeys = ptd != null ? ptd.getPostHookLogKeys() : null;

    if (isNotEmpty(pendingPreHooks)) {
      // More pre-hooks to run — submit the next one.
      ServiceHooksSweepingOutput preHooks =
          resolveHooksOutput(serviceStepSweepingOutputHelper.fetchPreTemplateHooksSweepingOutput(ambiance));

      TemplatingStepPassThroughDataBuilder ptdBuilder = TemplatingStepPassThroughData.builder()
                                                            .pendingPostHooks(pendingPostHooks)
                                                            .postHookLogKeys(savedPostHookLogKeys);

      return submitNextHook(ambiance, pendingPreHooks, preHooks.getEnvVars(),
          stepId
          -> RunnerRequestBuilder.generateLogKey(ambiance, stepId),
          ptdBuilder, true, AsyncChainExecutableResponse.newBuilder());
    }

    // All pre-hooks done — move to templating (or post-hooks if templating is skipped).
    ServiceHooksSweepingOutput postHooks =
        resolveHooksOutput(serviceStepSweepingOutputHelper.fetchPostTemplateHooksSweepingOutput(ambiance));
    List<ServiceHookMetadata> resolvedPostHooks = pendingPostHooks != null ? pendingPostHooks : toHookList(postHooks);
    return submitTemplatingOrPostHooks(ambiance, stepParameters, resolvedPostHooks, postHooks, savedPostHookLogKeys,
        AsyncChainExecutableResponse.newBuilder());
  }

  /**
   * Templating just finished. Parses its output vars (unless templating was skipped), then submits
   * the first post-hook if any; otherwise signals chain end.
   */
  private AsyncChainExecutableResponse onTemplatingLinkCompleted(
      Ambiance ambiance, TemplatingStepPassThroughData ptd, Map<String, ResponseData> responseDataMap) {
    List<Map<String, String>> outputVars = new ArrayList<>();
    if (!ptd.isTemplatingSkipped()) {
      outputVars = handleTemplatingResponse(ambiance, responseDataMap);
    }
    Map<String, String> mergedOutputVars = mergeOutputVars(outputVars);
    List<ServiceHookMetadata> pendingPostHooks = ptd.getPendingPostHooks();

    if (isNotEmpty(pendingPostHooks)) {
      ServiceHooksSweepingOutput postHooks =
          resolveHooksOutput(serviceStepSweepingOutputHelper.fetchPostTemplateHooksSweepingOutput(ambiance));

      TemplatingStepPassThroughDataBuilder ptdBuilder = TemplatingStepPassThroughData.builder()
                                                            .completedLink(ChainLink.TEMPLATING)
                                                            .templatingSkipped(ptd.isTemplatingSkipped())
                                                            .outputVars(mergedOutputVars)
                                                            .postHookLogKeys(ptd.getPostHookLogKeys());

      return submitNextHook(ambiance, pendingPostHooks, postHooks.getEnvVars(),
          stepId
          -> ptd.getPostHookLogKeys() != null ? ptd.getPostHookLogKeys().get(stepId) : null,
          ptdBuilder, false, AsyncChainExecutableResponse.newBuilder());
    }

    // No post-hooks — signal chain end.
    TemplatingStepPassThroughData nextPtd = TemplatingStepPassThroughData.builder()
                                                .completedLink(ChainLink.POST_HOOKS)
                                                .templatingSkipped(ptd.isTemplatingSkipped())
                                                .outputVars(mergedOutputVars)
                                                .build();
    return AsyncChainExecutableResponse.newBuilder()
        .setChainEnd(true)
        .setPassThroughData(ByteString.copyFrom(RecastOrchestrationUtils.toBytes(nextPtd)))
        .build();
  }

  /**
   * A post-hook just finished. Submits the next pending post-hook if any; otherwise signals chain end.
   */
  private AsyncChainExecutableResponse onPostHookLinkCompleted(
      Ambiance ambiance, TemplatingStepPassThroughData ptd, Map<String, ResponseData> responseDataMap) {
    handleHookResponses(ambiance, responseDataMap, "post-template");
    List<ServiceHookMetadata> pendingPostHooks = ptd.getPendingPostHooks();

    if (isNotEmpty(pendingPostHooks)) {
      ServiceHooksSweepingOutput postHooks =
          (ServiceHooksSweepingOutput) serviceStepSweepingOutputHelper.fetchPostTemplateHooksSweepingOutput(ambiance)
              .getOutput();
      TemplatingStepPassThroughDataBuilder ptdBuilder = TemplatingStepPassThroughData.builder()
                                                            .completedLink(ChainLink.TEMPLATING)
                                                            .templatingSkipped(ptd.isTemplatingSkipped())
                                                            .outputVars(ptd.getOutputVars())
                                                            .postHookLogKeys(ptd.getPostHookLogKeys());

      return submitNextHook(ambiance, pendingPostHooks, postHooks.getEnvVars(),
          stepId
          -> ptd.getPostHookLogKeys() != null ? ptd.getPostHookLogKeys().get(stepId) : null,
          ptdBuilder, false, AsyncChainExecutableResponse.newBuilder());
    }

    // All post-hooks done — signal chain end.
    TemplatingStepPassThroughData nextPtd = TemplatingStepPassThroughData.builder()
                                                .completedLink(ChainLink.POST_HOOKS)
                                                .templatingSkipped(ptd.isTemplatingSkipped())
                                                .outputVars(ptd.getOutputVars())
                                                .build();

    return AsyncChainExecutableResponse.newBuilder()
        .setChainEnd(true)
        .setPassThroughData(ByteString.copyFrom(RecastOrchestrationUtils.toBytes(nextPtd)))
        .build();
  }

  /**
   * Submits the head of a hook queue, pops it off, stamps the remaining queue onto the supplied
   * (partially built) TemplatingStepPassThroughData builder, and wraps everything into an
   * AsyncChainExecutableResponse with the callback ID attached if present.
   *
   * @param isPreHook true to stash the remaining queue as pendingPreHooks, false for pendingPostHooks
   * @param responseBuilder an existing builder to reuse (e.g. one that already has logKeys/units set),
   *                        or a fresh AsyncChainExecutableResponse.newBuilder()
   */
  private AsyncChainExecutableResponse submitNextHook(Ambiance ambiance, List<ServiceHookMetadata> pending,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars, Function<String, String> logKeyResolver,
      TemplatingStepPassThroughDataBuilder ptdBuilder, boolean isPreHook,
      AsyncChainExecutableResponse.Builder responseBuilder) {
    ServiceHookMetadata next = pending.get(0);
    List<ServiceHookMetadata> remaining = pending.subList(1, pending.size());
    String logKey = logKeyResolver.apply(next.getStepId());
    String callbackId = serviceHookTaskHelper.submitHookTask(ambiance, next, envVars, new HashMap<>(), logKey);

    if (isPreHook) {
      ptdBuilder.pendingPreHooks(new ArrayList<>(remaining));
    } else {
      ptdBuilder.pendingPostHooks(new ArrayList<>(remaining));
    }

    responseBuilder.setChainEnd(false).setPassThroughData(
        ByteString.copyFrom(RecastOrchestrationUtils.toBytes(ptdBuilder.build())));
    if (isNotEmpty(callbackId)) {
      responseBuilder.addCallbackIds(callbackId);
    }
    return responseBuilder.build();
  }
}
