/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.beans.steps.CIStepInfoType.UNIFIED_SERVICE_HOOKS_STEP;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.POST_FETCH_FILES_HOOKS_NODE_ID;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.beans.outcomes.ManifestOutputVarsSweepingOutput;
import io.harness.cd.beans.outcomes.ServiceConfigOutcome;
import io.harness.cd.beans.outcomes.ServiceHookMetadata;
import io.harness.cd.beans.outcomes.ServiceHooksOutputVarsSweepingOutput;
import io.harness.cd.beans.outcomes.ServiceHooksSweepingOutput;
import io.harness.cd.beans.outcomes.UnifiedServiceOutcome;
import io.harness.ci.execution.common.ManifestTemplateConstants;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.stages.v1.EmptyStepParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncChainExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.runner.request.helpers.infra.InfraBasedHelper;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.steps.executable.AsyncChainExecutableWithRbac;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
@Singleton
public class ServiceHooksStep implements AsyncChainExecutableWithRbac<EmptyStepParameters> {
  @Inject private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private ResponseHandlerUtils responseHandlerUtils;
  @Inject private InfraBasedHelper infraBasedHelper;
  @Inject private ServiceHookTaskHelper serviceHookTaskHelper;

  private static final String OVERRIDE_FILES_VAR = "OVERRIDE_FILES";

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(UNIFIED_SERVICE_HOOKS_STEP.getDisplayName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, EmptyStepParameters stepParameters) {}

  @Override
  public AsyncChainExecutableResponse startChainLinkAfterRbac(
      Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage) {
    String nodeIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);

    OptionalSweepingOutput optionalOutput = fetchHooksSweepingOutputForPhase(ambiance);
    if (!optionalOutput.isFound()) {
      return AsyncChainExecutableResponse.newBuilder().setChainEnd(true).build();
    }

    ServiceHooksSweepingOutput hooksSweepingOutput = (ServiceHooksSweepingOutput) optionalOutput.getOutput();
    if (isEmpty(hooksSweepingOutput.getHookMetadataMap())) {
      return AsyncChainExecutableResponse.newBuilder().setChainEnd(true).build();
    }

    boolean isPostPhase = POST_FETCH_FILES_HOOKS_NODE_ID.equals(nodeIdentifier);
    Map<String, String> runnerFiles = new HashMap<>();

    if (isPostPhase) {
      String serviceType = resolveServiceType(ambiance);
      if (serviceHookTaskHelper.isNativeHelmWithSopsEnabled(ambiance, serviceType)) {
        List<String> materializedOverridePaths = buildValuesOverrideRunnerFiles(ambiance, runnerFiles);
        if (isNotEmpty(materializedOverridePaths)) {
          serviceStepSweepingOutputHelper.saveServiceHooksOutputVarsSweepingOutput(ambiance,
              ServiceHooksOutputVarsSweepingOutput.builder()
                  .materializedOverridePaths(materializedOverridePaths)
                  .build());
        }
      }
    }

    List<ServiceHookMetadata> orderedHooks = new ArrayList<>(hooksSweepingOutput.getHookMetadataMap().values());
    ParameterField<Map<String, ParameterField<JsonNode>>> envVars = hooksSweepingOutput.getEnvVars();

    // Collect log keys and command units for all hooks upfront so all UI tabs register immediately
    List<String> allLogKeys = new ArrayList<>();
    List<String> allCommandUnits = new ArrayList<>();
    for (ServiceHookMetadata hookMetadata : orderedHooks) {
      allLogKeys.add(hookMetadata.getLogKey());
      allCommandUnits.add(hookMetadata.getStepId());
    }

    ServiceHookMetadata firstHook = orderedHooks.get(0);
    List<ServiceHookMetadata> remaining = new ArrayList<>(orderedHooks.subList(1, orderedHooks.size()));

    String callbackId = serviceHookTaskHelper.submitHookTask(ambiance, firstHook, envVars, runnerFiles);

    ServiceHooksStepPassThroughData ptd = ServiceHooksStepPassThroughData.builder()
                                              .pendingHooks(remaining)
                                              .postFetchFilesPhase(isPostPhase)
                                              .runnerFiles(runnerFiles)
                                              .capturedOverrideFiles(null)
                                              .envVars(envVars)
                                              .build();

    AsyncChainExecutableResponse.Builder responseBuilder =
        AsyncChainExecutableResponse.newBuilder()
            .setChainEnd(false)
            .addAllLogKeys(allLogKeys)
            .addAllUnits(allCommandUnits)
            .setPassThroughData(ByteString.copyFrom(RecastOrchestrationUtils.toBytes(ptd)));
    if (isNotEmpty(callbackId)) {
      responseBuilder.addCallbackIds(callbackId);
    }
    return responseBuilder.build();
  }

  @Override
  public AsyncChainExecutableResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      EmptyStepParameters stepParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<Map<String, ResponseData>> responseSupplier) throws Exception {
    ServiceHooksStepPassThroughData ptd = (ServiceHooksStepPassThroughData) passThroughData;

    Map<String, ResponseData> responseDataMap = responseSupplier.get();
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    String updatedCapturedOverrideFiles = ptd.getCapturedOverrideFiles();

    if (isNotEmpty(responseDataMap)) {
      for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
        ResponseData responseData = serializedResponseDataHelper.deserialize(entry.getValue());

        if (responseData instanceof ErrorNotifyResponseData errorData) {
          log.error("Received error response for hook step {}, error: {}", stepIdentifier, errorData.getErrorMessage());
          throw new InvalidRequestException("Service hook execution failed: " + errorData.getErrorMessage());
        }

        if (responseData instanceof StepStatusTaskResponseData stepStatusData) {
          if (stepStatusData.getStepStatus() != null
              && !StepExecutionStatus.SUCCESS.equals(stepStatusData.getStepStatus().getStepExecutionStatus())) {
            throw new InvalidRequestException("Service hook execution failed for step " + stepIdentifier);
          }

          if (ptd.isPostFetchFilesPhase() && stepStatusData.getStepStatus() != null
              && isNotEmpty(stepStatusData.getStepStatus().getOutputV2())) {
            String overrideValue = stepStatusData.getStepStatus()
                                       .getOutputV2()
                                       .stream()
                                       .filter(o -> OVERRIDE_FILES_VAR.equals(o.getKey()))
                                       .map(o -> o.getValue())
                                       .filter(v -> isNotEmpty(v))
                                       .findFirst()
                                       .orElse(null);
            if (isNotEmpty(overrideValue)) {
              updatedCapturedOverrideFiles = overrideValue;
            }
          }
        } else if (responseData instanceof VmTaskExecutionResponse vmResponse) {
          if (CommandExecutionStatus.FAILURE.equals(vmResponse.getCommandExecutionStatus())) {
            log.error(
                "Service hook execution failed for step {}, error: {}", stepIdentifier, vmResponse.getErrorMessage());
            throw new InvalidRequestException("Service hook execution failed: " + vmResponse.getErrorMessage());
          }
          if (ptd.isPostFetchFilesPhase() && isNotEmpty(vmResponse.getOutputVars())) {
            String overrideValue = vmResponse.getOutputVars().get(OVERRIDE_FILES_VAR);
            if (isNotEmpty(overrideValue)) {
              updatedCapturedOverrideFiles = overrideValue;
            }
          }
        }
      }
    }

    if (isEmpty(ptd.getPendingHooks())) {
      ServiceHooksStepPassThroughData finalPtd = ServiceHooksStepPassThroughData.builder()
                                                     .pendingHooks(new ArrayList<>())
                                                     .postFetchFilesPhase(ptd.isPostFetchFilesPhase())
                                                     .runnerFiles(ptd.getRunnerFiles())
                                                     .capturedOverrideFiles(updatedCapturedOverrideFiles)
                                                     .envVars(ptd.getEnvVars())
                                                     .build();
      return AsyncChainExecutableResponse.newBuilder()
          .setChainEnd(true)
          .setPassThroughData(ByteString.copyFrom(RecastOrchestrationUtils.toBytes(finalPtd)))
          .build();
    }

    ServiceHookMetadata nextHook = ptd.getPendingHooks().get(0);
    List<ServiceHookMetadata> rest = new ArrayList<>(ptd.getPendingHooks().subList(1, ptd.getPendingHooks().size()));

    String callbackId =
        serviceHookTaskHelper.submitHookTask(ambiance, nextHook, ptd.getEnvVars(), ptd.getRunnerFiles());

    ServiceHooksStepPassThroughData nextPtd = ServiceHooksStepPassThroughData.builder()
                                                  .pendingHooks(rest)
                                                  .postFetchFilesPhase(ptd.isPostFetchFilesPhase())
                                                  .runnerFiles(ptd.getRunnerFiles())
                                                  .capturedOverrideFiles(updatedCapturedOverrideFiles)
                                                  .envVars(ptd.getEnvVars())
                                                  .build();

    AsyncChainExecutableResponse.Builder responseBuilder =
        AsyncChainExecutableResponse.newBuilder().setChainEnd(false).setPassThroughData(
            ByteString.copyFrom(RecastOrchestrationUtils.toBytes(nextPtd)));

    if (isNotEmpty(callbackId)) {
      responseBuilder.addCallbackIds(callbackId);
    }
    return responseBuilder.build();
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, EmptyStepParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData == null) {
      return StepResponse.builder().status(Status.SUCCEEDED).build();
    }

    ServiceHooksStepPassThroughData ptd = (ServiceHooksStepPassThroughData) passThroughData;

    if (ptd.isPostFetchFilesPhase() && isNotEmpty(ptd.getCapturedOverrideFiles())) {
      OptionalSweepingOutput existingOutput =
          serviceStepSweepingOutputHelper.fetchServiceHooksOutputVarsSweepingOutput(ambiance);
      List<String> existingMaterializedPaths = null;
      if (existingOutput.isFound()) {
        existingMaterializedPaths =
            ((ServiceHooksOutputVarsSweepingOutput) existingOutput.getOutput()).getMaterializedOverridePaths();
      }
      serviceStepSweepingOutputHelper.saveServiceHooksOutputVarsSweepingOutput(ambiance,
          ServiceHooksOutputVarsSweepingOutput.builder()
              .overrideFiles(ptd.getCapturedOverrideFiles())
              .materializedOverridePaths(existingMaterializedPaths)
              .build());
    }

    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private OptionalSweepingOutput fetchHooksSweepingOutputForPhase(Ambiance ambiance) {
    String nodeIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    if (POST_FETCH_FILES_HOOKS_NODE_ID.equals(nodeIdentifier)) {
      return serviceStepSweepingOutputHelper.fetchPostFetchFilesHooksSweepingOutput(ambiance);
    }
    return serviceStepSweepingOutputHelper.fetchPreFetchFilesHooksSweepingOutput(ambiance);
  }

  private String resolveServiceType(Ambiance ambiance) {
    OptionalSweepingOutput serviceMetadataOpt = serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(ambiance);
    if (!serviceMetadataOpt.isFound()) {
      return null;
    }
    return ((UnifiedServiceOutcome) serviceMetadataOpt.getOutput()).getType();
  }

  @SuppressWarnings("unchecked")
  private List<String> buildValuesOverrideRunnerFiles(Ambiance ambiance, Map<String, String> runnerFiles) {
    OptionalSweepingOutput serviceConfigOpt =
        serviceStepSweepingOutputHelper.fetchServiceConfigMetadataOutput(ambiance);
    if (!serviceConfigOpt.isFound()) {
      return new ArrayList<>();
    }

    ServiceConfigOutcome serviceConfig = (ServiceConfigOutcome) serviceConfigOpt.getOutput();
    if (serviceConfig.getManifests() == null) {
      return new ArrayList<>();
    }

    List<String> overrides = (List<String>) serviceConfig.getManifests().get(ManifestTemplateConstants.OVERRIDES);
    if (isEmpty(overrides)) {
      return new ArrayList<>();
    }

    OptionalSweepingOutput manifestOutputOpt =
        serviceStepSweepingOutputHelper.fetchManifestOutputVarsSweepingOutput(ambiance);
    if (!manifestOutputOpt.isFound()) {
      return new ArrayList<>();
    }

    ManifestOutputVarsSweepingOutput manifestOutput = (ManifestOutputVarsSweepingOutput) manifestOutputOpt.getOutput();

    String basePath = infraBasedHelper.getBasePath(ambiance, infraBasedHelper.getStageInfra(ambiance));
    List<String> materializedPaths = new ArrayList<>();

    for (int i = 0; i < overrides.size(); i++) {
      String overridePath = overrides.get(i);
      String encodedContent = findEncodedContent(manifestOutput, overridePath);
      if (isEmpty(encodedContent)) {
        log.warn("Could not find content for values override path: {}", overridePath);
      }

      String content = isEmpty(encodedContent)
          ? ""
          : new String(Base64.getMimeDecoder().decode(encodedContent), StandardCharsets.UTF_8);
      String targetPath = basePath + "/values-overrides/override-" + i + ".yaml";
      runnerFiles.put(targetPath, content);
      materializedPaths.add(targetPath);
    }

    return materializedPaths;
  }

  private String findEncodedContent(ManifestOutputVarsSweepingOutput manifestOutput, String path) {
    if (isNotEmpty(manifestOutput.getSingleDeployManifestOutputVars())) {
      String content = manifestOutput.getSingleDeployManifestOutputVars().get(path);
      if (isNotEmpty(content)) {
        return content;
      }
    }

    if (isNotEmpty(manifestOutput.getManifestsOutputVars())) {
      for (Map<String, String> manifestVars : manifestOutput.getManifestsOutputVars().values()) {
        if (manifestVars != null) {
          String content = manifestVars.get(path);
          if (isNotEmpty(content)) {
            return content;
          }
        }
      }
    }

    return null;
  }
}
