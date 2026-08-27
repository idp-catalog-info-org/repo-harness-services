/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.beans.steps.CIStepInfoType.UNIFIED_MANIFESTS_STEP;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.MANIFESTS_NODE_ID;
import static io.harness.ci.execution.common.ManifestTemplateConstants.STORE_TYPE_HARNESS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.sdk.core.plugin.CommonAbstractStepExecutable.generateLogKey;
import static io.harness.utils.TemplateYamlGenerator.REPO_NAME_MANIFEST_INPUT;
import static io.harness.utils.TemplateYamlGenerator.REPO_NAME_PLACEHOLDER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.cd.beans.outcomes.ManifestOutputVarsSweepingOutput;
import io.harness.cd.beans.outcomes.ManifestsSweepingOutput;
import io.harness.cd.beans.outcomes.ServiceStepUnitStatusSweepingOutput;
import io.harness.ci.execution.common.ManifestTemplatesPathsUtils;
import io.harness.ci.execution.states.helpers.ManifestsStepUtils;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.ci.execution.states.helpers.ServiceStepUtility;
import io.harness.ci.states.V1.cd.ManifestsStepPassThroughData.ManifestsStepPassThroughDataBuilder;
import io.harness.data.validator.EntityIdentifierValidator;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.filestore.dto.FileStoreFetchedFileDTO;
import io.harness.plancreator.stages.v1.EmptyStepParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncChainExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.runner.request.helpers.infra.InfraBasedHelper;
import io.harness.runner.request.utils.RunnerSubmitTaskUtils;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.steps.executable.AsyncChainExecutableWithRbac;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.unified.cd.service.manifests.ManifestConfig;
import io.harness.unified.cd.service.manifests.ManifestType;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.TemplateYamlEntityType;
import io.harness.utils.TemplateYamlGenerator;
import io.harness.utils.TemplateYamlResult;
import io.harness.utils.TemplateYamlSourceType;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class ManifestsStep implements AsyncChainExecutableWithRbac<EmptyStepParameters> {
  @Inject private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Inject EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject RunnerSubmitTaskUtils runnerSubmitTaskUtils;
  @Inject private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Inject SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private CommonAbstractStepUtils commonAbstractStepUtils;
  @Inject private ResponseHandlerUtils responseHandlerUtils;
  @Inject private TemplateYamlGenerator templateYamlGenerator;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private HarnessManifestFileStoreFetcher harnessManifestFileStoreFetcher;
  @Inject private InfraBasedHelper infraBasedHelper;

  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(UNIFIED_MANIFESTS_STEP.getDisplayName()).setStepCategory(StepCategory.STEP).build();
  public static final String PRIMARY = "primary";
  public static final String USES = "uses";
  public static final String ID = "id";
  public static final String ARTIFACT = "artifact";
  public static final String MANIFESTS_VALIDATION_UNIT = "manifest-validation";

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, EmptyStepParameters stepParameters) {
    // Todo: Nothing to validate
  }

  @Override
  public AsyncChainExecutableResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      EmptyStepParameters stepParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<Map<String, ResponseData>> responseSupplier) throws Exception {
    Map<String, ResponseData> responseDataMap = responseSupplier.get();
    if (isEmpty(responseDataMap)) {
      return AsyncChainExecutableResponse.newBuilder()
          .setStatus(Status.SKIPPED)
          .setChainEnd(true)
          .setPassThroughData(ByteString.copyFrom(
              RecastOrchestrationUtils.toBytes(ManifestFailedFetchResponse.builder().status(Status.SKIPPED).build())))
          .build();
    }

    // If any of the responses are in serialized format, deserialize them
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      entry.setValue(serializedResponseDataHelper.deserialize(entry.getValue()));
    }

    StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();

    Map<String, String> taskIdToManifestId = extractTaskIdToManifestId(passThroughData);

    // Collect per-manifest unit statuses for the parent's unit-progress timeline. This walks
    // every response (success or failure) and persists the result for UnifiedServiceStep to read.
    // Kept separate from the chain-control failure handlers below so we don't have to alter
    // their early-return semantics or message construction.
    collectAndPersistManifestUnitStatuses(ambiance, responseDataMap, taskIdToManifestId);

    Map<String, VmTaskExecutionResponse> vmTaskResponseMap = new HashMap<>();
    if (stageInfraType == StageInfraDetails.Type.K8) {
      AsyncChainExecutableResponse response = handleK8AsyncFailureResponse(ambiance, responseDataMap);
      if (!Status.SUCCEEDED.equals(response.getStatus())) {
        return response;
      }
      vmTaskResponseMap = handleK8AsyncSuccessResponse(ambiance, responseDataMap);
    } else if (stageInfraType == StageInfraDetails.Type.VM || stageInfraType == StageInfraDetails.Type.DLITE_VM) {
      AsyncChainExecutableResponse response = handleVmAsyncFailureResponses(responseDataMap);
      if (!Status.SUCCEEDED.equals(response.getStatus())) {
        return response;
      }
      vmTaskResponseMap = handleVmAsyncSuccessResponses(responseDataMap);
    }

    saveManifestOutputVars(ambiance, passThroughData, vmTaskResponseMap);

    OptionalSweepingOutput optionalManifestOutput =
        serviceStepSweepingOutputHelper.fetchServiceManifestsSweepingOutput(ambiance);
    if (!optionalManifestOutput.isFound()) {
      return AsyncChainExecutableResponse.newBuilder().setStatus(Status.SUCCEEDED).setChainEnd(true).build();
    }

    return AsyncChainExecutableResponse.newBuilder().setStatus(Status.SUCCEEDED).setChainEnd(true).build();
  }

  private AsyncChainExecutableResponse handleK8AsyncFailureResponse(
      Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    log.info("Received response for step {}", stepIdentifier);

    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      ResponseData responseData = entry.getValue();
      if (responseData instanceof ErrorNotifyResponseData) {
        log.error("Received error response for step {}, error: {}", stepIdentifier,
            ((ErrorNotifyResponseData) responseData).getErrorMessage());

        return AsyncChainExecutableResponse.newBuilder()
            .setStatus(Status.FAILED)
            .setPassThroughData(ByteString.copyFrom(
                RecastOrchestrationUtils.toBytes(ManifestFailedFetchResponse.builder().status(Status.FAILED).build())))
            .setChainEnd(true)
            .build();
      }

      StepStatusTaskResponseData stepStatusTaskResponseData = (StepStatusTaskResponseData) entry.getValue();
      if (stepStatusTaskResponseData == null) {
        log.error("stepStatusTaskResponseData should not be null for step {}", stepIdentifier);
        return AsyncChainExecutableResponse.newBuilder()
            .setStatus(Status.FAILED)
            .setPassThroughData(ByteString.copyFrom(
                RecastOrchestrationUtils.toBytes(ManifestFailedFetchResponse.builder().status(Status.FAILED).build())))
            .setChainEnd(true)
            .build();
      }

      if (stepStatusTaskResponseData.getStepStatus() != null
          && !StepExecutionStatus.SUCCESS.equals(stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus())) {
        // Todo: check if output vars can be accessed here form stepStatusTaskResponseData
        return AsyncChainExecutableResponse.newBuilder()
            .setStatus(Status.FAILED)
            .setPassThroughData(ByteString.copyFrom(
                RecastOrchestrationUtils.toBytes(ManifestFailedFetchResponse.builder().status(Status.FAILED).build())))
            .setChainEnd(true)
            .build();
      }
    }
    return AsyncChainExecutableResponse.newBuilder().setStatus(Status.SUCCEEDED).build();
  }

  private static Map<String, String> extractTaskIdToManifestId(PassThroughData passThroughData) {
    if (passThroughData instanceof ManifestsStepPassThroughData ptd && isNotEmpty(ptd.getTaskIdToManifestId())) {
      return ptd.getTaskIdToManifestId();
    }
    return new HashMap<>();
  }

  /**
   * Walks every entry in {@code responseDataMap} and records SUCCESS or FAILURE per manifest id,
   * then persists the map for {@link io.harness.ci.states.V1.cd.UnifiedServiceStep} to consume
   * when building the unit-progress timeline. Does no logging, no chain-control, no passthrough
   * construction — that all stays in the existing K8/VM failure handlers.
   */
  private void collectAndPersistManifestUnitStatuses(
      Ambiance ambiance, Map<String, ResponseData> responseDataMap, Map<String, String> taskIdToManifestId) {
    Map<String, UnitStatus> statuses = new HashMap<>();
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      // Only record a status when we can resolve the callback id back to a manifest id. If the
      // mapping is missing, skip rather than surface a synthetic row keyed by the raw taskId.
      String manifestId = taskIdToManifestId.get(entry.getKey());
      if (isNotEmpty(manifestId)) {
        statuses.put(manifestId, ResponseHandlerUtils.getUnitStatus(entry.getValue()));
      }
    }
    serviceStepSweepingOutputHelper.saveManifestUnitStatusesSweepingOutput(
        ambiance, ServiceStepUnitStatusSweepingOutput.builder().statuses(statuses).build());
  }

  private Map<String, VmTaskExecutionResponse> handleK8AsyncSuccessResponse(
      Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    log.info("Received response for step {}", stepIdentifier);
    Map<String, VmTaskExecutionResponse> vmTaskResponse = new HashMap<>();

    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      StepStatusTaskResponseData stepStatusTaskResponseData = (StepStatusTaskResponseData) entry.getValue();
      Map<String, String> resolvedOutputVariables = new HashMap<>();

      if (isNotEmpty(stepStatusTaskResponseData.getStepStatus().getOutputV2())) {
        stepStatusTaskResponseData.getStepStatus().getOutputV2().forEach(
            outputVariable -> { resolvedOutputVariables.put(outputVariable.getKey(), outputVariable.getValue()); });
      }

      VmTaskExecutionResponse vmTaskExecutionResponse =
          VmTaskExecutionResponse.builder()
              .outputVars(resolvedOutputVariables)
              .outputs(stepStatusTaskResponseData.getStepStatus().getOutputV2())
              .build();
      vmTaskResponse.put(entry.getKey(), vmTaskExecutionResponse);
    }
    return vmTaskResponse;
  }

  @Override
  public AsyncChainExecutableResponse startChainLinkAfterRbac(
      Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage) {
    return startChainLinkInternal(ambiance);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, EmptyStepParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    StepResponse passThroughResponse = handlePassThroughData(ambiance, passThroughData);
    if (passThroughResponse != null) {
      return passThroughResponse;
    }
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private AsyncChainExecutableResponse startChainLinkInternal(Ambiance ambiance) {
    List<String> callbackIds = new ArrayList<>();
    List<String> logKeys = new ArrayList<>();
    List<String> commandUnits = new ArrayList<>();
    ManifestsStepPassThroughDataBuilder passThroughDataBuilder = ManifestsStepPassThroughData.builder();

    // getting manifest metadata (i.e. yaml and log key) saved in service step
    OptionalSweepingOutput optionalManifestOutput =
        serviceStepSweepingOutputHelper.fetchServiceManifestsSweepingOutput(ambiance);

    if (!optionalManifestOutput.isFound()) {
      return AsyncChainExecutableResponse.newBuilder().setChainEnd(true).setStatus(Status.SKIPPED).build();
    }

    ManifestsSweepingOutput manifestSweepingOutput = (ManifestsSweepingOutput) optionalManifestOutput.getOutput();
    if (isNotEmpty(manifestSweepingOutput.getManifestMetadataMap())) {
      log.debug("Processing manifests");
      Map<String, ManifestConfig> manifestConfigMap =
          ManifestsStepUtils.toManifestConfigMap(manifestSweepingOutput.getManifestMetadataMap());
      return processManifests(ambiance, manifestConfigMap, manifestSweepingOutput.getEnvVars(), passThroughDataBuilder);
    }

    return buildResponseWithPassThroughData(passThroughDataBuilder.build(), logKeys, commandUnits, callbackIds, false);
  }

  private AsyncChainExecutableResponse buildResponseWithPassThroughData(ManifestsStepPassThroughData passThroughData,
      List<String> logKeys, List<String> commandUnits, List<String> callbackIds, boolean isChainEnd) {
    ByteString passThroughDataByteString = ByteString.copyFrom(RecastOrchestrationUtils.toBytes(passThroughData));
    return AsyncChainExecutableResponse.newBuilder()
        .setChainEnd(isChainEnd)
        .addAllLogKeys(logKeys)
        .addAllUnits(commandUnits)
        .setPassThroughData(passThroughDataByteString)
        .addAllCallbackIds(callbackIds)
        .build();
  }

  private String submitTask(Ambiance ambiance, String stepId,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars, String logKey, String templateYaml,
      Map<String, String> files) {
    StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();
    // Runner-relative path -> content map for files the runner must materialize before the step runs
    // (e.g. Harness File Store manifests). Empty for store types fetched on the runner itself (e.g. git).
    Map<String, String> filesToWrite = files != null ? files : new HashMap<>();

    if (stageInfraType == StageInfraDetails.Type.K8 || stageInfraType.name().equals("K8")) {
      // On K8 the step id keys the Initialize port/container-definition maps. Namespace it with the parent
      // node id (manifests_<id>) so a manifest and an artifact sharing an id (e.g. both "primary") don't
      // collide in those maps. Applied only here (and to the build-time container def) - the log stream key
      // is passed separately as logKey (raw id), so command-unit/log rendering is unaffected.
      String manifestId = ServiceStepUtility.getUniqueStepIdentifier(MANIFESTS_NODE_ID, stepId);
      return runnerSubmitTaskUtils.submitK8sTask(ambiance, manifestId, envVars, templateYaml,
          (K8StageInfraDetails) stageInfraDetails, logKey, filesToWrite, new ArrayList<>());
    }

    // Non-K8 (VM) path derives the runner log stream key from this identifier (commandUnit), so it must stay
    // the raw id to match the command-unit/log keys reported to orchestration. VM has no port/container map.
    return runnerSubmitTaskUtils.submitTaskByTemplate(
        ambiance, stepId, envVars, templateYaml, new ArrayList<>(), filesToWrite);
  }

  /**
   * Whether the manifest references the Harness File Store. The unified conversion encodes the store
   * in the template action suffix (e.g. {@code k8s-harness}), see UnifiedConversionRegistry.
   */
  private static boolean isHarnessStore(ManifestConfig manifestConfig) {
    String action = manifestConfig.getAction();
    return isNotEmpty(action) && action.endsWith("-" + STORE_TYPE_HARNESS);
  }

  /**
   * Fetches Harness File Store manifest and values content (files and folders) and maps each fetched
   * file to its runner-relative path. The path computation mirrors what
   * {@code ServiceEntityProcessor#getManifestOutputFromInputs} writes to
   * {@code serviceOutput.manifests.<id>.paths}/{@code .overrides}, so the files written here line up
   * with the paths consumed by the deploy step and {@code RuntimeFunctor#handleManifestPath}.
   */
  private Map<String, String> buildHarnessStoreRunnerFiles(
      Ambiance ambiance, ManifestConfig manifestConfig, Map<String, Object> mergedInputs, String basePath) {
    Map<String, String> runnerFiles = new HashMap<>();
    if (isEmpty(mergedInputs)) {
      return runnerFiles;
    }
    Map<String, Object> resolvedInputs = new HashMap<>(mergedInputs);
    cdStepsExpressionResolver.updateExpressions(ambiance, resolvedInputs, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);

    String manifestId = manifestConfig.getId();

    // Manifest content (files/folders): written under basePath/manifestId/<store path> (matches PATHS output).
    List<String> manifestScopedPaths = ManifestTemplatesPathsUtils.getRawHarnessFilesFromInputs(resolvedInputs);
    for (FileStoreFetchedFileDTO file :
        harnessManifestFileStoreFetcher.fetchManifestFiles(ambiance, manifestScopedPaths)) {
      String runnerPath = ManifestsStepUtils.getRunnerRelativePath(file.getPath(), basePath, manifestId);
      runnerFiles.put(runnerPath, file.getContent() == null ? "" : file.getContent());
    }

    // Values/override content (files/folders): written under basePath/manifestId/<store path> (matches OVERRIDES).
    List<String> overrideScopedPaths = ManifestTemplatesPathsUtils.getRawOverridesFromInputs(resolvedInputs);
    for (FileStoreFetchedFileDTO file :
        harnessManifestFileStoreFetcher.fetchManifestFiles(ambiance, overrideScopedPaths)) {
      for (String runnerPath :
          ManifestsStepUtils.getRunnerRelativePath(basePath, manifestId, Collections.singletonList(file.getPath()))) {
        runnerFiles.put(runnerPath, file.getContent() == null ? "" : file.getContent());
      }
    }
    return runnerFiles;
  }

  private Map<String, Map<String, String>> saveManifestOutputVars(
      Ambiance ambiance, PassThroughData passThroughData, Map<String, VmTaskExecutionResponse> vmTaskResponseMap) {
    Map<String, Map<String, String>> manifestsOutputVars = new HashMap<>();

    if (passThroughData != null) {
      ManifestsStepPassThroughData manifestsStepPassThroughData = (ManifestsStepPassThroughData) passThroughData;
      Map<String, String> deployManifestOutputValue = new HashMap<>();
      if (isNotEmpty(manifestsStepPassThroughData.getSingleDeployManifestTaskId())) {
        VmTaskExecutionResponse deployManifestTaskVmResponse =
            vmTaskResponseMap.get(manifestsStepPassThroughData.getSingleDeployManifestTaskId());
        if (deployManifestTaskVmResponse != null && isNotEmpty(deployManifestTaskVmResponse.getOutputVars())) {
          deployManifestOutputValue = new HashMap<>(deployManifestTaskVmResponse.getOutputVars());
        }
      }

      if (isNotEmpty(manifestsStepPassThroughData.getTaskIdToManifestId())) {
        Map<String, String> taskIdToManifestId = manifestsStepPassThroughData.getTaskIdToManifestId();
        vmTaskResponseMap.forEach((taskId, response) -> {
          if (isNotEmpty(response.getOutputVars()) && taskIdToManifestId.containsKey(taskId)) {
            manifestsOutputVars.put(taskIdToManifestId.get(taskId), response.getOutputVars());
          }
        });
      }

      saveManifestOutputVarsSweepingOutput(ambiance, deployManifestOutputValue, manifestsOutputVars);
    }

    return manifestsOutputVars;
  }

  private void saveManifestOutputVarsSweepingOutput(Ambiance ambiance, Map<String, String> deployManifestOutputValue,
      Map<String, Map<String, String>> manifestsOutputVars) {
    if (isEmpty(manifestsOutputVars)) {
      return;
    }

    ManifestOutputVarsSweepingOutput sweepingOutput = ManifestOutputVarsSweepingOutput.builder()
                                                          .manifestsOutputVars(manifestsOutputVars)
                                                          .singleDeployManifestOutputVars(deployManifestOutputValue)
                                                          .build();
    serviceStepSweepingOutputHelper.saveManifestsOutputVarsSweepingOutput(ambiance, sweepingOutput);
  }

  private Map<String, VmTaskExecutionResponse> handleVmAsyncSuccessResponses(
      Map<String, ResponseData> responseDataMap) {
    Map<String, VmTaskExecutionResponse> vmTaskResponse = new HashMap<>();
    if (isNotEmpty(responseDataMap)) {
      for (Map.Entry<String, ResponseData> responseDataEntry : responseDataMap.entrySet()) {
        ResponseData responseData = responseDataEntry.getValue();
        String taskId = responseDataEntry.getKey();
        if (responseData instanceof VmTaskExecutionResponse vmTaskExecutionResponse) {
          vmTaskResponse.put(taskId, vmTaskExecutionResponse);
        }
      }
    }
    return vmTaskResponse;
  }

  private AsyncChainExecutableResponse handleVmAsyncFailureResponses(Map<String, ResponseData> responseDataMap) {
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      Map<String, String> outputVariables = new HashMap<>();
      if (entry.getValue() instanceof VmTaskExecutionResponse vmTaskExecutionResponse) {
        if (CommandExecutionStatus.FAILURE.equals(vmTaskExecutionResponse.getCommandExecutionStatus())) {
          log.error("Failed to fetch manifest for taskId: " + entry.getKey()
              + " reason: " + vmTaskExecutionResponse.getErrorMessage());

          if (isNotEmpty(vmTaskExecutionResponse.getOutputs())) {
            outputVariables = responseHandlerUtils.getOutputVariables(vmTaskExecutionResponse.getOutputs());
          }
          return AsyncChainExecutableResponse.newBuilder()
              .setStatus(Status.FAILED)
              .setPassThroughData(ByteString.copyFrom(
                  RecastOrchestrationUtils.toBytes(ManifestFailedFetchResponse.builder()
                                                       .status(Status.FAILED)
                                                       .errorEnvVars(outputVariables)
                                                       .responseErrorMessage(vmTaskExecutionResponse.getErrorMessage())
                                                       .build())))
              .setChainEnd(true)
              .build();
        }
      }
    }
    return AsyncChainExecutableResponse.newBuilder().setStatus(Status.SUCCEEDED).build();
  }

  /**
   * Process manifests.
   */
  private AsyncChainExecutableResponse processManifests(Ambiance ambiance,
      Map<String, ManifestConfig> manifestConfigMap, ParameterField<Map<String, ParameterField<JsonNode>>> envVars,
      ManifestsStepPassThroughDataBuilder passThroughDataBuilder) {
    List<String> callbackIds = new ArrayList<>();
    List<String> logKeys = new ArrayList<>();
    List<String> commandUnits = new ArrayList<>();

    // Resolve expressions in inputs values
    resolveInputsExpressions(ambiance, manifestConfigMap);

    // Todo: (Tathagat) Validate manifest config map
    validateManifestConfigMap(manifestConfigMap);

    // Submit tasks for each manifest
    Map<String, String> taskIdToManifestId = new HashMap<>();
    final Pattern identifierPattern = EntityIdentifierValidator.IDENTIFIER_PATTERN;
    List<String> invalidManifestIds = new ArrayList<>();
    // Resolved lazily and reused only when a Harness File Store manifest is encountered.
    String basePath = null;
    for (Map.Entry<String, ManifestConfig> entry : manifestConfigMap.entrySet()) {
      String logKey = entry.getKey();
      ManifestConfig manifestConfig = entry.getValue();
      String manifestId = manifestConfig.getId();

      if (!identifierPattern.matcher(manifestId).matches()) {
        invalidManifestIds.add(manifestId);
      }

      TemplateYamlResult result = templateYamlGenerator.generateYamlWithMergedDefaults(ambiance,
          manifestConfig.getAction(), manifestConfig.getId(), manifestConfig.getInputs(),
          TemplateYamlEntityType.MANIFEST, TemplateYamlSourceType.SERVICE);
      if (result == null) {
        throw new InvalidRequestException(String.format(
            "Could not fetch template to submit manifest task for manifest: [%s]", manifestConfig.getId()));
      }
      String templateYaml = result.getYaml();
      if (isNotEmpty(manifestConfig.getInputs()) && manifestConfig.getInputs().containsKey(REPO_NAME_MANIFEST_INPUT)) {
        templateYaml = templateYaml.replace(
            REPO_NAME_PLACEHOLDER, (String) manifestConfig.getInputs().get(REPO_NAME_MANIFEST_INPUT));
      }

      // For Harness File Store manifests, fetch content here and hand it to the runner as files to
      // write. Other store types (e.g. git) fetch on the runner, so no files are pre-materialized.
      Map<String, String> runnerFiles = new HashMap<>();
      if (isHarnessStore(manifestConfig)) {
        if (basePath == null) {
          basePath = infraBasedHelper.getBasePath(ambiance, infraBasedHelper.getStageInfra(ambiance));
        }
        runnerFiles = buildHarnessStoreRunnerFiles(ambiance, manifestConfig, result.getMergedInputs(), basePath);
      }

      // Submit task
      String callBackId = submitTask(ambiance, manifestId, envVars, logKey, templateYaml, runnerFiles);
      if (isNotEmpty(callBackId)) {
        callbackIds.add(callBackId);
        logKeys.add(logKey);
        commandUnits.add(manifestId);
        taskIdToManifestId.put(callBackId, manifestId);

        // Check if single deploy manifest
        if (manifestConfig.getUses() != null
            && ManifestType.getSingleDeployPathSupportingTypes().contains(manifestConfig.getUses())) {
          passThroughDataBuilder.singleDeployManifestTaskId(callBackId).build();
        }
      }
    }
    if (!invalidManifestIds.isEmpty()) {
      NGLogCallback validationLogCallback =
          new NGLogCallback(logStreamingStepClientFactory, ambiance, MANIFESTS_VALIDATION_UNIT, true);
      logKeys.add(generateLogKey(ambiance, MANIFESTS_VALIDATION_UNIT));
      commandUnits.add(MANIFESTS_VALIDATION_UNIT);
      for (String invalidId : invalidManifestIds) {
        validationLogCallback.saveExecutionLog(
            String.format("Manifest identifier [%s] is not valid as per Harness Identifier Regex %s. Using this "
                    + "identifier in harness expressions might not work",
                invalidId, identifierPattern.pattern()),
            LogLevel.WARN, CommandExecutionStatus.RUNNING);
      }
      validationLogCallback.saveExecutionLog(
          "Manifest identifier validation completed.", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    }

    passThroughDataBuilder.taskIdToManifestId(taskIdToManifestId);

    return buildResponseWithPassThroughData(passThroughDataBuilder.build(), logKeys, commandUnits, callbackIds, false);
  }

  /**
   * Resolve expressions in inputs values.
   */
  private void resolveInputsExpressions(Ambiance ambiance, Map<String, ManifestConfig> manifestConfigMap) {
    for (ManifestConfig manifestConfig : manifestConfigMap.values()) {
      Map<String, Object> inputsMap = manifestConfig.getInputs();
      if (isNotEmpty(inputsMap)) {
        cdStepsExpressionResolver.updateExpressions(ambiance, inputsMap, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
      }
    }
  }

  /**
   * Validate manifest config map structure.
   */
  @VisibleForTesting
  void validateManifestConfigMap(Map<String, ManifestConfig> manifestConfigMap) {
    for (Map.Entry<String, ManifestConfig> entry : manifestConfigMap.entrySet()) {
      ManifestConfig manifestConfig = entry.getValue();

      if (isEmpty(manifestConfig.getId())) {
        throw new io.harness.exception.InvalidRequestException("Manifest id is required");
      }

      if (manifestConfig.getUses() == null) {
        throw new io.harness.exception.InvalidRequestException("Manifest uses is required");
      }
    }
  }

  private StepResponse handlePassThroughData(Ambiance ambiance, PassThroughData passThroughData) {
    if (passThroughData == null) {
      return null;
    }

    ManifestFailedFetchResponse fetchResponse = (ManifestFailedFetchResponse) passThroughData;

    if (Status.SKIPPED.equals(fetchResponse.getStatus())) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    String failureErrorMsg = responseHandlerUtils.getFailureErrorMsg(fetchResponse.getErrorEnvVars(),
        "Failed to execute manifest fetch task: ", fetchResponse.getResponseErrorMessage());

    String errorMessage = "Failed to execute manifest fetch task: "
        + (isNotEmpty(fetchResponse.getResponseErrorMessage()) ? fetchResponse.getResponseErrorMessage() : "");

    return responseHandlerUtils.getGenericFailedStepResponse(ambiance, errorMessage, failureErrorMsg);
  }

  private static boolean hasK8sTaskFailed(String stepId, ResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      log.error("Received error response for step {}, error: {}", stepId,
          ((ErrorNotifyResponseData) responseData).getErrorMessage());

      return true;
    }

    StepStatusTaskResponseData stepStatusTaskResponseData = (StepStatusTaskResponseData) responseData;
    if (stepStatusTaskResponseData == null) {
      log.error("stepStatusTaskResponseData should not be null for step {}", stepId);
      return true;
    }
    return false;
  }
}
