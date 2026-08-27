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
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.stages.v1.EmptyStepParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.runner.request.helpers.infra.InfraBasedHelper;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
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
public class ServiceHooksStep implements AsyncExecutableWithRbac<EmptyStepParameters> {
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
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage) {
    List<String> callbackIds = new ArrayList<>();
    List<String> logKeys = new ArrayList<>();
    List<String> commandUnits = new ArrayList<>();

    OptionalSweepingOutput optionalOutput = fetchHooksSweepingOutputForPhase(ambiance);
    if (!optionalOutput.isFound()) {
      return AsyncExecutableResponse.newBuilder()
          .addAllLogKeys(logKeys)
          .addAllUnits(commandUnits)
          .addAllCallbackIds(callbackIds)
          .build();
    }

    ServiceHooksSweepingOutput hooksSweepingOutput = (ServiceHooksSweepingOutput) optionalOutput.getOutput();
    if (isEmpty(hooksSweepingOutput.getHookMetadataMap())) {
      return AsyncExecutableResponse.newBuilder()
          .addAllLogKeys(logKeys)
          .addAllUnits(commandUnits)
          .addAllCallbackIds(callbackIds)
          .build();
    }

    ParameterField<Map<String, ParameterField<JsonNode>>> envVars = hooksSweepingOutput.getEnvVars();

    String nodeIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    Map<String, String> runnerFiles = new HashMap<>();
    List<String> materializedOverridePaths = new ArrayList<>();
    if (POST_FETCH_FILES_HOOKS_NODE_ID.equals(nodeIdentifier)) {
      String serviceType = resolveServiceType(ambiance);
      if (serviceHookTaskHelper.isNativeHelmWithSopsEnabled(ambiance, serviceType)) {
        materializedOverridePaths = buildValuesOverrideRunnerFiles(ambiance, runnerFiles);
        if (isNotEmpty(materializedOverridePaths)) {
          serviceStepSweepingOutputHelper.saveServiceHooksOutputVarsSweepingOutput(ambiance,
              ServiceHooksOutputVarsSweepingOutput.builder()
                  .materializedOverridePaths(materializedOverridePaths)
                  .build());
        }
      }
    }

    for (Map.Entry<String, ServiceHookMetadata> entry : hooksSweepingOutput.getHookMetadataMap().entrySet()) {
      ServiceHookMetadata hookMetadata = entry.getValue();
      String callbackId = serviceHookTaskHelper.submitHookTask(ambiance, hookMetadata, envVars, runnerFiles);
      if (isNotEmpty(callbackId)) {
        callbackIds.add(callbackId);
        logKeys.add(hookMetadata.getLogKey());
        commandUnits.add(hookMetadata.getStepId());
      }
    }

    return AsyncExecutableResponse.newBuilder()
        .addAllLogKeys(logKeys)
        .addAllUnits(commandUnits)
        .addAllCallbackIds(callbackIds)
        .build();
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

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, EmptyStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    if (isEmpty(responseDataMap)) {
      return StepResponse.builder().status(Status.SUCCEEDED).build();
    }

    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      entry.setValue(serializedResponseDataHelper.deserialize(entry.getValue()));
    }

    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    String capturedOverrideFiles = null;

    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      ResponseData responseData = entry.getValue();
      if (responseData instanceof ErrorNotifyResponseData) {
        log.error("Received error response for hook step {}, error: {}", stepIdentifier,
            ((ErrorNotifyResponseData) responseData).getErrorMessage());
        return responseHandlerUtils.getGenericFailedStepResponse(
            ambiance, "Service hook execution failed", "Service hook execution failed");
      }

      if (responseData instanceof StepStatusTaskResponseData stepStatusTaskResponseData) {
        if (stepStatusTaskResponseData.getStepStatus() != null
            && !StepExecutionStatus.SUCCESS.equals(
                stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus())) {
          return responseHandlerUtils.getGenericFailedStepResponse(
              ambiance, "Service hook execution failed", "Service hook execution failed");
        }

        if (stepStatusTaskResponseData.getStepStatus() != null
            && isNotEmpty(stepStatusTaskResponseData.getStepStatus().getOutputV2())) {
          String overrideValue = stepStatusTaskResponseData.getStepStatus()
                                     .getOutputV2()
                                     .stream()
                                     .filter(o -> OVERRIDE_FILES_VAR.equals(o.getKey()))
                                     .map(o -> o.getValue())
                                     .filter(v -> isNotEmpty(v))
                                     .findFirst()
                                     .orElse(null);
          if (isNotEmpty(overrideValue)) {
            capturedOverrideFiles = overrideValue;
          }
        }
      } else if (responseData instanceof VmTaskExecutionResponse vmResponse) {
        if (CommandExecutionStatus.FAILURE.equals(vmResponse.getCommandExecutionStatus())) {
          log.error(
              "Service hook execution failed for step {}, error: {}", stepIdentifier, vmResponse.getErrorMessage());
          return responseHandlerUtils.getGenericFailedStepResponse(
              ambiance, "Service hook execution failed", "Service hook execution failed");
        }
        if (isNotEmpty(vmResponse.getOutputVars())) {
          String overrideValue = vmResponse.getOutputVars().get(OVERRIDE_FILES_VAR);
          if (isNotEmpty(overrideValue)) {
            capturedOverrideFiles = overrideValue;
          }
        }
      }
    }

    if (isNotEmpty(capturedOverrideFiles)) {
      OptionalSweepingOutput existingOutput =
          serviceStepSweepingOutputHelper.fetchServiceHooksOutputVarsSweepingOutput(ambiance);
      List<String> existingMaterializedPaths = null;
      if (existingOutput.isFound()) {
        existingMaterializedPaths =
            ((ServiceHooksOutputVarsSweepingOutput) existingOutput.getOutput()).getMaterializedOverridePaths();
      }
      serviceStepSweepingOutputHelper.saveServiceHooksOutputVarsSweepingOutput(ambiance,
          ServiceHooksOutputVarsSweepingOutput.builder()
              .overrideFiles(capturedOverrideFiles)
              .materializedOverridePaths(existingMaterializedPaths)
              .build());
    }

    return StepResponse.builder().status(Status.SUCCEEDED).build();
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
