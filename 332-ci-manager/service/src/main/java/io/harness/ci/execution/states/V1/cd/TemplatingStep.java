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
import io.harness.runner.request.builder.RunnerRequestBuilder;
import io.harness.runner.request.utils.RunnerSubmitTaskUtils;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.steps.executable.AsyncChainExecutableWithRbac;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.unified.cd.service.spec.ServiceType;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.DeployTemplateFetchHelper;

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
import lombok.extern.slf4j.Slf4j;

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

  // Chain link 1: submit pre-template hook tasks (or skip if none configured).
  // All logKeys (pre-hooks, templating, post-hooks) are declared upfront so the UI renders all
  // log tabs from the start. Hook logKeys are computed from RunnerRequestBuilder.generateLogKey
  // against the TemplatingStep's ambiance — this matches what the runner actually uses, regardless
  // of whatever base path was computed when hooks were registered in UnifiedServiceStep.
  @Override
  public AsyncChainExecutableResponse startChainLinkAfterRbac(
      Ambiance ambiance, TemplatingStepParameters stepParameters, StepInputPackage inputPackage) {
    List<String> logKeys = new ArrayList<>();
    List<String> commandUnits = new ArrayList<>();
    List<String> callbackIds = new ArrayList<>();
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

    // Nothing to do — signal chain end immediately
    if (preHookList.isEmpty() && postHookList.isEmpty() && !templatingWillRun) {
      return AsyncChainExecutableResponse.newBuilder().setChainEnd(true).build();
    }

    // Pre-hooks: register log keys/command units and submit tasks.
    for (ServiceHookMetadata hookMetadata : preHookList) {
      String logKey = RunnerRequestBuilder.generateLogKey(ambiance, hookMetadata.getStepId());
      logKeys.add(logKey);
      commandUnits.add(hookMetadata.getStepId());

      String callbackId =
          serviceHookTaskHelper.submitHookTask(ambiance, hookMetadata, preHooks.getEnvVars(), new HashMap<>(), logKey);
      if (isNotEmpty(callbackId)) {
        callbackIds.add(callbackId);
      }
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

    TemplatingStepPassThroughData ptd = isNotEmpty(postHookLogKeys)
        ? TemplatingStepPassThroughData.builder().postHookLogKeys(postHookLogKeys).build()
        : null;

    AsyncChainExecutableResponse.Builder builder = AsyncChainExecutableResponse.newBuilder()
                                                       .setChainEnd(false)
                                                       .addAllCallbackIds(callbackIds)
                                                       .addAllLogKeys(logKeys)
                                                       .addAllUnits(commandUnits);
    if (ptd != null) {
      builder.setPassThroughData(ByteString.copyFrom(RecastOrchestrationUtils.toBytes(ptd)));
    }
    return builder.build();
  }

  // executeNextLinkWithSecurityContext is called up to three times:
  //   Call 1 (ptd == null or completedLink == null): pre-hooks finished → submit templating
  //   Call 2 (completedLink == PRE_HOOKS)           : templating finished → submit post-hooks (chainEnd=false)
  //   Call 3 (completedLink == TEMPLATING)          : post-hooks finished → emit terminal chainEnd=true (no callbacks)
  @Override
  public AsyncChainExecutableResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      TemplatingStepParameters stepParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<Map<String, ResponseData>> responseSupplier) throws Exception {
    TemplatingStepPassThroughData ptd = (TemplatingStepPassThroughData) passThroughData;

    if (ptd == null || ptd.getCompletedLink() == null) {
      // Pre-hooks just finished; handle their responses and now run actual templating.
      // Carry forward postHookLogKeys that were pre-computed in startChainLinkAfterRbac.
      handleHookResponses(ambiance, responseSupplier.get(), "pre-template");
      Map<String, String> postHookLogKeys = ptd != null ? ptd.getPostHookLogKeys() : null;
      return submitTemplatingOrSkip(ambiance, stepParameters, postHookLogKeys);
    }

    if (ChainLink.PRE_HOOKS.equals(ptd.getCompletedLink())) {
      // Templating just finished; handle its response and now submit post-hooks.
      List<Map<String, String>> outputVars = new ArrayList<>();
      if (!ptd.isTemplatingSkipped()) {
        outputVars = handleTemplatingResponse(ambiance, responseSupplier.get());
      }
      return submitPostHooks(ambiance, outputVars, ptd.getPostHookLogKeys(), ptd.isTemplatingSkipped());
    }

    // Post-hooks just finished; handle all their responses via the full map, then signal chain end.
    handleHookResponses(ambiance, responseSupplier.get(), "post-template");
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

  private AsyncChainExecutableResponse submitTemplatingOrSkip(
      Ambiance ambiance, TemplatingStepParameters stepParameters, Map<String, String> postHookLogKeys) {
    if (!willRunTemplating(ambiance)) {
      return buildSkippedTemplatingChainResponse(postHookLogKeys);
    }

    String manifestType =
        cdStepsExpressionResolver.renderValue(ambiance, SERVICE_OUTPUT_MANIFESTS_PRIMARY_TYPE_EXP, true);
    String templateYaml = deployTemplateFetchHelper.getTemplatingTemplateYamlContent(manifestType, ambiance);

    String callbackId;
    StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();
    String logKey = RunnerRequestBuilder.generateLogKey(ambiance, COMMAND_UNIT_TEMPLATING);

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
                                            .postHookLogKeys(postHookLogKeys)
                                            .build();

    return AsyncChainExecutableResponse.newBuilder()
        .setChainEnd(false)
        .addCallbackIds(callbackId)
        .setPassThroughData(ByteString.copyFrom(RecastOrchestrationUtils.toBytes(ptd)))
        .build();
  }

  private AsyncChainExecutableResponse buildSkippedTemplatingChainResponse(Map<String, String> postHookLogKeys) {
    TemplatingStepPassThroughData ptd = TemplatingStepPassThroughData.builder()
                                            .completedLink(ChainLink.PRE_HOOKS)
                                            .templatingSkipped(true)
                                            .postHookLogKeys(postHookLogKeys)
                                            .build();
    return AsyncChainExecutableResponse.newBuilder()
        .setChainEnd(false)
        .setPassThroughData(ByteString.copyFrom(RecastOrchestrationUtils.toBytes(ptd)))
        .build();
  }

  private AsyncChainExecutableResponse submitPostHooks(Ambiance ambiance, List<Map<String, String>> outputVarsList,
      Map<String, String> postHookLogKeys, boolean templatingSkipped) {
    Map<String, String> mergedOutputVars = new HashMap<>();
    if (isNotEmpty(outputVarsList)) {
      outputVarsList.forEach(mergedOutputVars::putAll);
    }

    TemplatingStepPassThroughData ptd = TemplatingStepPassThroughData.builder()
                                            .completedLink(ChainLink.TEMPLATING)
                                            .templatingSkipped(templatingSkipped)
                                            .outputVars(mergedOutputVars)
                                            .build();

    OptionalSweepingOutput postHooksOpt =
        serviceStepSweepingOutputHelper.fetchPostTemplateHooksSweepingOutput(ambiance);
    if (!postHooksOpt.isFound()) {
      // No post-hooks: skip straight to the terminal link with no callbacks.
      return AsyncChainExecutableResponse.newBuilder()
          .setChainEnd(false)
          .setPassThroughData(ByteString.copyFrom(RecastOrchestrationUtils.toBytes(ptd)))
          .build();
    }

    ServiceHooksSweepingOutput postHooks = (ServiceHooksSweepingOutput) postHooksOpt.getOutput();
    if (isEmpty(postHooks.getHookMetadataMap())) {
      return AsyncChainExecutableResponse.newBuilder()
          .setChainEnd(false)
          .setPassThroughData(ByteString.copyFrom(RecastOrchestrationUtils.toBytes(ptd)))
          .build();
    }

    List<String> callbackIds = new ArrayList<>();

    for (ServiceHookMetadata hookMetadata : postHooks.getHookMetadataMap().values()) {
      // Use the logKey pre-computed in startChainLinkAfterRbac so K8 task submission and the
      // already-registered UI log tab both point at the same key.
      String logKey = postHookLogKeys != null ? postHookLogKeys.get(hookMetadata.getStepId()) : null;
      String callbackId =
          serviceHookTaskHelper.submitHookTask(ambiance, hookMetadata, postHooks.getEnvVars(), new HashMap<>(), logKey);
      if (isNotEmpty(callbackId)) {
        callbackIds.add(callbackId);
      }
    }

    // chainEnd=false so executeNextLink receives the full response map for all hooks,
    // allowing every hook failure to be inspected (not just iterator().next()).
    return AsyncChainExecutableResponse.newBuilder()
        .setChainEnd(false)
        .addAllCallbackIds(callbackIds)
        .setPassThroughData(ByteString.copyFrom(RecastOrchestrationUtils.toBytes(ptd)))
        .build();
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
}
