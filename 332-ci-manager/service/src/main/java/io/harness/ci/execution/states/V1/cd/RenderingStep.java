/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.beans.steps.CIStepInfoType.RENDERING_STEP;
import static io.harness.ci.execution.states.helpers.ManifestsStepUtils.getRunnerRelativePath;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.runner.request.utils.RunnerSubmitTaskUtils.COMMON_TEMPLATES_FOLDER_PATH;

import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.cd.beans.outcomes.ConfigFileOutcome;
import io.harness.cd.beans.outcomes.ConfigFilesOutcome;
import io.harness.cd.beans.outcomes.ServiceConfigOutcome;
import io.harness.cd.beans.outcomes.ServiceHooksOutputVarsSweepingOutput;
import io.harness.ci.execution.common.ManifestTemplateConstants;
import io.harness.ci.execution.common.RuntimeExpressionConversionHelper;
import io.harness.ci.execution.integrationstage.ci.CIStepGroupUtils;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.ci.execution.utils.ci.CIStepInfoUtils;
import io.harness.ci.utils.SecretHelper;
import io.harness.configFiles.ConfigGitFile;
import io.harness.delegate.HarnessSecret;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.SerializedResponseData;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.exception.ManifestCollectionException;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncChainExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.runner.request.builder.RunnerRequestBuilder;
import io.harness.runner.request.helpers.infra.InfraBasedHelper;
import io.harness.runner.request.utils.RunnerSubmitTaskUtils;
import io.harness.steps.executable.AsyncChainExecutableWithRbac;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.DeployTemplateFetchHelper;
import io.harness.utils.FileUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RenderingStep implements AsyncChainExecutableWithRbac<RenderingStepParameters> {
  private static final String PLUGIN_FILES_LIST_PLACEHOLDER = "PLUGIN_FILES_LIST_PLACEHOLDER";
  @Inject private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Inject private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Inject private RunnerSubmitTaskUtils runnerSubmitTaskUtils;
  @Inject private CommonAbstractStepUtils commonAbstractStepUtils;
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private SecretHelper secretHelper;
  @Inject private InfraBasedHelper infraBasedHelper;
  @Inject private DeployTemplateFetchHelper deployTemplateFetchHelper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private RuntimeExpressionConversionHelper expressionConversionHelper;

  public static final String COMMAND_UNIT_FETCH = "fetch-files";
  public static final String COMMAND_UNIT_RENDER = "render-files";
  public static final String RENDERING_TEMPLATE_FILE_NAME = "rendering.yaml";
  public static final String FETCH_TEMPLATE_FILE_NAME = "fetch.yaml";
  public static final String FETCH_PLUGIN_STEP_ID = "harnessFetchFiles";
  private static final String RENDERING_STEP_FETCH_OUTPUT = "renderingStepFetchOutput";

  public static final String ID_PLACEHOLDER = "ID_PLACEHOLDER";

  private final ObjectMapper objectMapper = new ObjectMapper();

  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(RENDERING_STEP.getDisplayName()).setStepCategory(StepCategory.STEP).build();

  @Override
  public Class<RenderingStepParameters> getStepParametersClass() {
    return RenderingStepParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, RenderingStepParameters stepParameters) {}

  @Override
  public AsyncChainExecutableResponse startChainLinkAfterRbac(
      Ambiance ambiance, RenderingStepParameters stepParameters, StepInputPackage inputPackage) {
    String logKeyFetch = RunnerRequestBuilder.generateLogKey(ambiance, COMMAND_UNIT_FETCH);
    String logKeyRender = RunnerRequestBuilder.generateLogKey(ambiance, COMMAND_UNIT_RENDER);

    String manifestType = resolveManifestType(ambiance);

    if (isEmpty(manifestType)) {
      // No manifest to fetch, but config files may still need rendering.
      // Skip the fetch plugin and proceed directly to the next chain link.
      return AsyncChainExecutableResponse.newBuilder()
          .setChainEnd(false)
          .addAllUnits(List.of(COMMAND_UNIT_FETCH, COMMAND_UNIT_RENDER))
          .addAllLogKeys(List.of(logKeyFetch, logKeyRender))
          .build();
    }

    // Plugin flow (k8s/helm for now): the plugin fetches all files directly from the cloned
    // repo and returns them as output variables.
    StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
    String callbackId = submitPluginFetchTask(
        ambiance, manifestType, stageInfraDetails, stepParameters.getId(), logKeyFetch, stepParameters.getEnvVars());

    return AsyncChainExecutableResponse.newBuilder()
        .setChainEnd(false)
        .addCallbackIds(callbackId)
        .addAllUnits(List.of(COMMAND_UNIT_FETCH, COMMAND_UNIT_RENDER))
        .addAllLogKeys(List.of(logKeyFetch, logKeyRender))
        .build();
  }

  @Override
  public AsyncChainExecutableResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      RenderingStepParameters stepParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<Map<String, ResponseData>> responseSupplier) throws Exception {
    Map<String, VmTaskExecutionResponse> taskResponseMap = new HashMap<>();

    if (isNotEmpty(responseSupplier.get())) {
      for (Map.Entry<String, ResponseData> entry : responseSupplier.get().entrySet()) {
        entry.setValue(serializedResponseDataHelper.deserialize(entry.getValue()));
      }
      StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
      StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();

      if (stageInfraType == StageInfraDetails.Type.K8) {
        handleK8AsyncFailureResponse(ambiance, responseSupplier.get());
        taskResponseMap = handleK8AsyncSuccessResponse(ambiance, responseSupplier.get());
      } else if (stageInfraType == StageInfraDetails.Type.VM || stageInfraType == StageInfraDetails.Type.DLITE_VM) {
        taskResponseMap = handleAsyncResponses(responseSupplier.get());
      }
    }

    boolean hasFetchOutput = isNotEmpty(taskResponseMap)
        && taskResponseMap.values().stream().anyMatch(r -> r != null && isNotEmpty(r.getOutputVars()));

    // Publish a sweeping output with the validated file paths for the TemplatingStep
    Map<String, String> pathRemap = new HashMap<>();
    if (hasFetchOutput) {
      pathRemap = saveRenderingStepOutput(ambiance, taskResponseMap);
      saveFetchOutputSweepingOutput(ambiance, taskResponseMap);
    }

    // Config files are always read from their sweeping output — handled separately via git,
    // not affected by the fetch step.
    Map<String, String> configFilesToRender = getFileContentsFromConfigFileStep(ambiance);

    if (!hasFetchOutput && isEmpty(configFilesToRender)) {
      return AsyncChainExecutableResponse.newBuilder().setStatus(Status.SKIPPED).setChainEnd(true).build();
    }

    return executeRendering(ambiance, taskResponseMap, new HashMap<>(), configFilesToRender,
        stepParameters.getAddOnFiles(), stepParameters.getId(), stepParameters.getEnvVars(), pathRemap);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, RenderingStepParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    ResponseData responseData = responseDataSupplier.get();

    if (responseData == null) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    if (responseData != null) {
      SerializedResponseData serializedResponseData = (SerializedResponseData) responseData;
      try {
        responseData = serializedResponseDataHelper.deserialize(responseData);
        StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
        StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();

        if (stageInfraType == StageInfraDetails.Type.K8) {
          if (hasK8sTaskFailed(stepParameters, responseData)) {
            return StepResponse.builder().status(Status.FAILED).build();
          }
        } else if (stageInfraType == StageInfraDetails.Type.VM || stageInfraType == StageInfraDetails.Type.DLITE_VM) {
          VmTaskExecutionResponse vmTaskExecutionResponse =
              objectMapper.readValue(serializedResponseData.getData(), VmTaskExecutionResponse.class);

          if (CommandExecutionStatus.FAILURE.equals(vmTaskExecutionResponse.getCommandExecutionStatus())) {
            return StepResponse.builder()
                .status(Status.FAILED)
                .failureInfo(FailureInfo.newBuilder()
                                 .addFailureData(CIStepInfoUtils.getDefaultCIFailureDataInfo(
                                     (vmTaskExecutionResponse.getErrorMessage()), ambiance))
                                 .setErrorMessage(emptyIfNull(vmTaskExecutionResponse.getErrorMessage()))
                                 .build())
                .build();
          }
        }
      } catch (Exception ex) {
        throw new ManifestCollectionException("Failed to process after files content expression rendering");
      }
    }
    long stepStartTs = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    long stepEndTs = System.currentTimeMillis();
    List<UnitProgress> unitProgresses = new ArrayList<>();
    unitProgresses.add(UnitProgress.newBuilder()
                           .setStatus(UnitStatus.SUCCESS)
                           .setUnitName(COMMAND_UNIT_FETCH)
                           .setStartTime(stepStartTs)
                           .setEndTime(stepEndTs)
                           .build());

    unitProgresses.add(UnitProgress.newBuilder()
                           .setStatus(UnitStatus.SUCCESS)
                           .setUnitName(COMMAND_UNIT_RENDER)
                           .setStartTime(stepStartTs)
                           .setEndTime(stepEndTs)
                           .build());

    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    addStepOutcomes(ambiance, stepOutcomes);

    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcomes(stepOutcomes)
        .unitProgressList(unitProgresses)
        .build();
  }

  private static boolean hasK8sTaskFailed(RenderingStepParameters stepParameters, ResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      log.error("Received error response for step {}, error: {}", stepParameters.getId(),
          ((ErrorNotifyResponseData) responseData).getErrorMessage());

      return true;
    }

    StepStatusTaskResponseData stepStatusTaskResponseData = (StepStatusTaskResponseData) responseData;
    if (stepStatusTaskResponseData == null) {
      log.error("stepStatusTaskResponseData should not be null for step {}", stepParameters.getId());
      return true;
    }
    StepStatus stepStatus = stepStatusTaskResponseData.getStepStatus();
    if (stepStatusTaskResponseData.getStepStatus() != null
        && !StepExecutionStatus.SUCCESS.equals(stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus())) {
      log.error("Received error response for step {}, error: {}", stepParameters.getStepId(), stepStatus.getError());
      return true;
    }
    return false;
  }

  /**
   * Resolves the primary manifest type (e.g. "k8s", "helm-chart") at runtime by evaluating the
   * serviceOutput expression.
   */
  private String resolveManifestType(Ambiance ambiance) {
    try {
      String manifestType = cdStepsExpressionResolver.renderValue(
          ambiance, RenderingStepUtils.SERVICE_OUTPUT_MANIFESTS_PRIMARY_TYPE_EXP, true);
      if (isNotEmpty(manifestType)
          && !RenderingStepUtils.SERVICE_OUTPUT_MANIFESTS_PRIMARY_TYPE_EXP.equals(manifestType)) {
        return manifestType;
      }
    } catch (Exception e) {
      log.warn("Failed to resolve manifest type for rendering plugin selection", e);
    }
    return null;
  }

  private String submitPluginFetchTask(Ambiance ambiance, String manifestType, StageInfraDetails stageInfraDetails,
      String stepId, String logKey, ParameterField<Map<String, ParameterField<JsonNode>>> envVars) {
    String templateYaml = deployTemplateFetchHelper.getPluginFetchTemplateYaml(ambiance, manifestType);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();
    String callbackId;
    if (stageInfraType == StageInfraDetails.Type.K8 || stageInfraType.name().equals("K8")) {
      String completeStepId = CIStepGroupUtils.getUniqueStepIdentifier(ambiance.getLevelsList(), FETCH_PLUGIN_STEP_ID);
      callbackId = runnerSubmitTaskUtils.submitK8sTask(ambiance, completeStepId, envVars, templateYaml,
          (K8StageInfraDetails) stageInfraDetails, logKey, new HashMap<>(), new ArrayList<>());
    } else {
      callbackId = runnerSubmitTaskUtils.submitTaskByTemplate(
          ambiance, COMMAND_UNIT_FETCH, envVars, templateYaml, new ArrayList<>(), new HashMap<>());
    }
    return callbackId;
  }

  private Map<String, String> getFileContentsFromConfigFileStep(Ambiance ambiance) {
    Map<String, String> configFileContentMap = new HashMap<>();
    Infrastructure infrastructure = infraBasedHelper.getStageInfra(ambiance);
    String basePath = infraBasedHelper.getBasePath(ambiance, infrastructure);
    OptionalSweepingOutput sweepingOutput = serviceStepSweepingOutputHelper.fetchConfigFilesSweepingOutput(ambiance);
    if (sweepingOutput.isFound()) {
      ConfigFilesOutcome outcome = (ConfigFilesOutcome) sweepingOutput.getOutput();
      for (Map.Entry<String, ConfigFileOutcome> entry : outcome.entrySet()) {
        if (isNotEmpty(entry.getValue().getGitFiles())) {
          var gitFiles = entry.getValue().getGitFiles();
          gitFiles.forEach(file
              -> configFileContentMap.put(
                  getRunnerRelativePath(file.getFilePath(), basePath, entry.getKey()), file.getFileContent()));
        }
      }
    }
    return configFileContentMap;
  }

  /**
   * After rendering expressions in config file contents, updates both the
   * {@link ConfigFilesOutcome} sweeping output and the {@code ngOutcomes} so that
   * downstream steps and expressions see the rendered (expression-resolved) content
   * rather than the raw content fetched from git.
   */
  private void updateConfigFilesWithRenderedContent(
      Ambiance ambiance, Map<String, String> configFiles, Map<String, String> renderedFilesContentMap) {
    try {
      Infrastructure infrastructure = infraBasedHelper.getStageInfra(ambiance);
      String basePath = infraBasedHelper.getBasePath(ambiance, infrastructure);
      OptionalSweepingOutput sweepingOutput = serviceStepSweepingOutputHelper.fetchConfigFilesSweepingOutput(ambiance);
      if (!sweepingOutput.isFound()) {
        return;
      }

      ConfigFilesOutcome outcome = (ConfigFilesOutcome) sweepingOutput.getOutput();
      Map<String, List<ConfigGitFile>> renderedConfigFilesByConfigId = new HashMap<>();

      for (Map.Entry<String, ConfigFileOutcome> entry : outcome.entrySet()) {
        if (isEmpty(entry.getValue().getGitFiles())) {
          continue;
        }
        String configFileId = entry.getKey();
        List<ConfigGitFile> renderedGitFiles = new ArrayList<>();
        for (ConfigGitFile gitFile : entry.getValue().getGitFiles()) {
          String runnerPath = getRunnerRelativePath(gitFile.getFilePath(), basePath, configFileId);
          String renderedContent = renderedFilesContentMap.get(runnerPath);
          if (renderedContent != null) {
            renderedGitFiles.add(
                ConfigGitFile.builder().filePath(gitFile.getFilePath()).fileContent(renderedContent).build());
          } else {
            renderedGitFiles.add(gitFile);
          }
        }
        if (isNotEmpty(renderedGitFiles)) {
          renderedConfigFilesByConfigId.put(configFileId, renderedGitFiles);
        }
      }

      if (isEmpty(renderedConfigFilesByConfigId)) {
        return;
      }

      // Update the immutable ConfigFileOutcome in-place with rendered gitFiles
      for (Map.Entry<String, List<ConfigGitFile>> entry : renderedConfigFilesByConfigId.entrySet()) {
        ConfigFileOutcome existing = outcome.get(entry.getKey());
        if (existing != null) {
          outcome.put(entry.getKey(),
              ConfigFileOutcome.builder()
                  .id(existing.getId())
                  .store(existing.getStore())
                  .files(existing.getFiles())
                  .gitFiles(entry.getValue())
                  .secretFiles(existing.getSecretFiles())
                  .build());
        }
      }
      serviceStepSweepingOutputHelper.saveConfigFilesSweepingOutput(ambiance, outcome);
    } catch (Exception e) {
      log.warn("Failed to update config files outcomes with rendered content", e);
    }
  }

  /**
   * Collects all file paths returned by the fetch plugin, cross-references them with
   * ServiceConfigOutcome.manifests (overrides, toTemplate, toRender), and updates
   * ServiceConfigOutcome with only the paths confirmed to exist by the fetch plugin.
   *
   * This ensures that ${{serviceOutput.manifests.overrides}} — used as PLUGIN_VALUES_PATH
   * in TemplatingStep's plugin YAMLs — resolves to only valid paths. Auto-derived paths
   * like defaultValuesYaml that don't actually exist in the repo are automatically removed,
   * preventing the TemplatingStep plugin from failing on non-existent files.
   */
  @SuppressWarnings("unchecked")
  private Map<String, String> saveRenderingStepOutput(
      Ambiance ambiance, Map<String, VmTaskExecutionResponse> taskResponseMap) {
    Set<String> fetchedPaths = new LinkedHashSet<>();
    for (VmTaskExecutionResponse response : taskResponseMap.values()) {
      if (response != null && isNotEmpty(response.getOutputVars())) {
        fetchedPaths.addAll(response.getOutputVars().keySet());
      }
    }

    OptionalSweepingOutput serviceConfigOpt =
        serviceStepSweepingOutputHelper.fetchServiceConfigMetadataOutput(ambiance);
    if (!serviceConfigOpt.isFound()) {
      return new HashMap<>();
    }

    ServiceConfigOutcome serviceConfig = (ServiceConfigOutcome) serviceConfigOpt.getOutput();
    Map<String, Object> manifests = serviceConfig.getManifests();
    if (isEmpty(manifests)) {
      return new HashMap<>();
    }

    RenderingStepUtils.sanitizeFilePaths(fetchedPaths);
    List<String> filePaths = new ArrayList<>(fetchedPaths);
    if (isEmpty(filePaths)) {
      return new HashMap<>();
    }

    // Merge the fetched paths into each key if it already exists; otherwise create the key with the fetched paths.
    manifests.put(ManifestTemplateConstants.OVERRIDES,
        RenderingStepUtils.mergePathsWithManifestOutput(manifests, ManifestTemplateConstants.OVERRIDES, fetchedPaths));
    manifests.put(ManifestTemplateConstants.TO_TEMPLATE,
        RenderingStepUtils.mergePathsWithManifestOutput(
            manifests, ManifestTemplateConstants.TO_TEMPLATE, fetchedPaths));
    manifests.put(ManifestTemplateConstants.TO_RENDER,
        RenderingStepUtils.mergePathsWithManifestOutput(manifests, ManifestTemplateConstants.TO_RENDER, fetchedPaths));

    Map<String, String> pathRemap = appendHookOverrideFiles(ambiance, manifests);

    serviceStepSweepingOutputHelper.saveServiceConfigSweepingOutput(ambiance, serviceConfig);
    log.info(
        "Updated ServiceConfigOutcome with validated paths from fetch plugin — fetchedFilePaths={}", filePaths.size());
    return pathRemap;
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> appendHookOverrideFiles(Ambiance ambiance, Map<String, Object> manifests) {
    OptionalSweepingOutput hookOutputOpt =
        serviceStepSweepingOutputHelper.fetchServiceHooksOutputVarsSweepingOutput(ambiance);
    if (!hookOutputOpt.isFound()) {
      return new HashMap<>();
    }
    ServiceHooksOutputVarsSweepingOutput hookOutput = (ServiceHooksOutputVarsSweepingOutput) hookOutputOpt.getOutput();

    Map<String, String> pathRemap = new HashMap<>();
    List<String> materializedOverridePaths = hookOutput.getMaterializedOverridePaths();
    if (isNotEmpty(materializedOverridePaths)) {
      List<String> currentOverrides = (List<String>) manifests.get(ManifestTemplateConstants.OVERRIDES);
      if (isNotEmpty(currentOverrides)) {
        for (int i = 0; i < currentOverrides.size() && i < materializedOverridePaths.size(); i++) {
          pathRemap.put(currentOverrides.get(i), materializedOverridePaths.get(i));
        }
      }

      // We're modifying `manifests.overrides` here instead of while handling async response in ServiceHooksStep to
      // avoid duplicating the logic to update overrides at two places because regardless of whether the hooks run or
      // not, we anyway have to modify manifests during RenderingStep.
      manifests.put(ManifestTemplateConstants.OVERRIDES, new ArrayList<>(materializedOverridePaths));
    }

    if (isEmpty(hookOutput.getOverrideFiles())) {
      return pathRemap;
    }
    Set<String> hookOverrides = Arrays.stream(hookOutput.getOverrideFiles().split(","))
                                    .map(String::trim)
                                    .filter(s -> isNotEmpty(s))
                                    .collect(Collectors.toCollection(LinkedHashSet::new));
    if (isNotEmpty(hookOverrides)) {
      manifests.put(ManifestTemplateConstants.OVERRIDES,
          RenderingStepUtils.mergePathsWithManifestOutput(
              manifests, ManifestTemplateConstants.OVERRIDES, hookOverrides));
      log.info("Appended {} SOPS hook override files to manifests.overrides", hookOverrides.size());
    }
    return pathRemap;
  }

  private void saveFetchOutputSweepingOutput(Ambiance ambiance, Map<String, VmTaskExecutionResponse> taskResponseMap) {
    try {
      VariablesSweepingOutput output = new VariablesSweepingOutput();
      for (VmTaskExecutionResponse response : taskResponseMap.values()) {
        if (response != null && isNotEmpty(response.getOutputVars())) {
          output.putAll(response.getOutputVars());
        }
      }
      if (!output.isEmpty()) {
        sweepingOutputService.consumeUpsert(ambiance, RENDERING_STEP_FETCH_OUTPUT, output, StepCategory.STAGE.name());
      }
    } catch (Exception ex) {
      log.warn("Failed to save fetch output sweeping output for rendering step", ex);
    }
  }

  private void addStepOutcomes(Ambiance ambiance, List<StepResponse.StepOutcome> stepOutcomes) {
    try {
      OptionalSweepingOutput fetchOutput = sweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(RENDERING_STEP_FETCH_OUTPUT));
      if (fetchOutput.isFound()) {
        stepOutcomes.add(StepResponse.StepOutcome.builder()
                             .name("output")
                             .group(StepCategory.STAGE.name())
                             .outcome((VariablesSweepingOutput) fetchOutput.getOutput())
                             .build());
      }
    } catch (Exception ex) {
      log.warn("Failed to add step outcomes for rendering step output tab", ex);
    }
  }

  private AsyncChainExecutableResponse executeRendering(Ambiance ambiance,
      Map<String, VmTaskExecutionResponse> vmTaskResponseMap, Map<String, String> manifestsFilesToRender,
      Map<String, String> configFiles, List<String> addOnFiles, String stepId,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars, Map<String, String> pathRemap) {
    Map<String, String> decodedContentsToRender =
        getDecodedContentFromResponse(vmTaskResponseMap, manifestsFilesToRender, configFiles, addOnFiles);

    decodeAndCollectManifestsFiles(manifestsFilesToRender, decodedContentsToRender);
    collectConfigFiles(configFiles, decodedContentsToRender);

    if (isNotEmpty(pathRemap)) {
      Map<String, String> remapped = new LinkedHashMap<>();
      for (Map.Entry<String, String> entry : decodedContentsToRender.entrySet()) {
        String newKey = pathRemap.getOrDefault(entry.getKey(), entry.getKey());
        remapped.put(newKey, entry.getValue());
      }
      decodedContentsToRender = remapped;
    }

    if (isNotEmpty(decodedContentsToRender)) {
      Map<String, String> unescapedDecodedContentsToRender =
          ManifestRenderUtils.unescapeJavaStringsInMap(decodedContentsToRender);
      Map<String, String> renderedFilesContentMap = renderExpressions(ambiance, unescapedDecodedContentsToRender);
      List<HarnessSecret> filesContentSecrets =
          ManifestRenderUtils.getHarnessSecrets(ambiance, renderedFilesContentMap);
      Map<String, String> replacedSecretsRenderedFilesContentlMap =
          ManifestRenderUtils.replaceSecretsRunnerCompatible(renderedFilesContentMap);
      Map<String, String> unescapeFilesContentMap =
          ManifestRenderUtils.unescapeJavaStringsInMap(replacedSecretsRenderedFilesContentlMap);
      Map<String, String> trimmedDoubleQuotesContentMap =
          ManifestRenderUtils.trimWhitespaceInsideDoubleQuotesInMap(unescapeFilesContentMap);
      Map<String, String> replacedDoubleQuotesContentMap =
          ManifestRenderUtils.replaceDoubleQuotesInMap(trimmedDoubleQuotesContentMap);

      // Exclude config-file paths from the rendering script's PLUGIN_FILES_LIST so they do not
      // surface in the render-files command unit log.
      Map<String, String> renderingScriptVisibleFiles = renderedFilesContentMap;
      if (isNotEmpty(configFiles)) {
        renderingScriptVisibleFiles = new HashMap<>(renderedFilesContentMap);
        renderingScriptVisibleFiles.keySet().removeAll(configFiles.keySet());
      }

      String templateYaml = getRenderingTemplateYaml(ambiance, renderingScriptVisibleFiles);
      String callbackId;
      StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
      StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();
      String commandUnit = COMMAND_UNIT_RENDER;
      String logKey = RunnerRequestBuilder.generateLogKey(ambiance, commandUnit);

      if (isNotEmpty(configFiles)) {
        updateConfigFilesWithRenderedContent(ambiance, configFiles, unescapeFilesContentMap);
      }

      if (stageInfraType == StageInfraDetails.Type.K8 || stageInfraType.name().equals("K8")) {
        List<SecretVariableDetails> stepSecrets = secretHelper.getSecrets(ambiance, renderedFilesContentMap);
        String completeStepId = CIStepGroupUtils.getUniqueStepIdentifier(ambiance.getLevelsList(), stepId);
        callbackId = runnerSubmitTaskUtils.submitK8sTask(ambiance, completeStepId, envVars, templateYaml,
            (K8StageInfraDetails) stageInfraDetails, logKey, replacedDoubleQuotesContentMap, stepSecrets);
      } else {
        callbackId = runnerSubmitTaskUtils.submitTaskByTemplate(
            ambiance, COMMAND_UNIT_RENDER, envVars, templateYaml, filesContentSecrets, replacedDoubleQuotesContentMap);
      }

      return AsyncChainExecutableResponse.newBuilder()
          .setChainEnd(true)
          .addCallbackIds(callbackId)
          .addAllLogKeys(List.of(logKey))
          .addAllUnits(List.of(commandUnit))
          .build();
    }
    return AsyncChainExecutableResponse.newBuilder().setChainEnd(true).build();
  }

  private static void collectConfigFiles(Map<String, String> configFiles, Map<String, String> decodedContentsToRender) {
    if (isNotEmpty(configFiles)) {
      Map<String, String> filteredValuesContent = new HashMap<>(configFiles);
      if (isNotEmpty(decodedContentsToRender)) {
        filteredValuesContent.keySet().removeAll(decodedContentsToRender.keySet());
      }
      if (isNotEmpty(filteredValuesContent)) {
        decodedContentsToRender.putAll(filteredValuesContent);
      }
    }
  }

  private static void decodeAndCollectManifestsFiles(
      Map<String, String> manifestsFilesToRender, Map<String, String> decodedContentsToRender) {
    if (isNotEmpty(manifestsFilesToRender)) {
      Map<String, String> filteredFilesContent = new HashMap<>(manifestsFilesToRender);
      if (isNotEmpty(decodedContentsToRender)) {
        filteredFilesContent.keySet().removeAll(decodedContentsToRender.keySet());
      }
      if (isNotEmpty(filteredFilesContent)) {
        Map<String, String> decodedContent = ManifestRenderUtils.getDecodedFilesContent(filteredFilesContent);
        decodedContentsToRender.putAll(decodedContent);
      }
    }
  }

  private static Map<String, String> getDecodedContentFromResponse(
      Map<String, VmTaskExecutionResponse> vmTaskResponseMap, Map<String, String> manifestsFilesToRender,
      Map<String, String> configFiles, List<String> addOnFiles) {
    // Paths from sweeping output — their content in the response is base64-encoded
    Set<String> filePaths = new HashSet<>();
    if (isNotEmpty(manifestsFilesToRender)) {
      filePaths.addAll(manifestsFilesToRender.keySet());
    }
    if (isNotEmpty(addOnFiles)) {
      filePaths.addAll(addOnFiles);
    }
    if (isNotEmpty(configFiles)) {
      filePaths.addAll(configFiles.keySet());
    }

    Map<String, String> contentsToRender = new HashMap<>();

    // Decode base64-encoded content for sweeping-output paths only.
    if (isNotEmpty(vmTaskResponseMap) && isNotEmpty(filePaths)) {
      contentsToRender = ManifestRenderUtils.getDecodedContents(filePaths, vmTaskResponseMap);
    }

    // The fetch plugin (kubernetes-manifest / helm-manifest) returns file contents as
    // plain text in its output variables — NOT base64-encoded.
    if (isNotEmpty(vmTaskResponseMap)) {
      for (VmTaskExecutionResponse response : vmTaskResponseMap.values()) {
        if (response != null && isNotEmpty(response.getOutputVars())) {
          RenderingStepUtils.sanitizeOutputVars(response.getOutputVars());
          response.getOutputVars().forEach(contentsToRender::putIfAbsent);
        }
      }
    }

    return contentsToRender;
  }

  private String getRenderingTemplateYaml(Ambiance ambiance, Map<String, String> filesContent) {
    String fileContent = FileUtils.getFileContent(COMMON_TEMPLATES_FOLDER_PATH + RENDERING_TEMPLATE_FILE_NAME);
    if (isEmpty(fileContent)) {
      throw new InvalidRequestException("Failed to fetch expression rendering template");
    }
    fileContent = (String) cdStepsExpressionResolver.updateExpressions(
        ambiance, fileContent, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    return fileContent.replace(ID_PLACEHOLDER, COMMAND_UNIT_RENDER)
        .replace(PLUGIN_FILES_LIST_PLACEHOLDER, filesContent.keySet().toString());
  }

  private Map<String, String> renderExpressions(Ambiance ambiance, Map<String, String> filesContentToRender) {
    Map<String, String> renderedFilesContentMap = new HashMap<>();
    if (isNotEmpty(filesContentToRender)) {
      String pipelineYaml = expressionConversionHelper.isExpressionConversionEnabled(ambiance)
          ? expressionConversionHelper.fetchPipelineYaml(ambiance)
          : null;
      filesContentToRender.forEach((path, content) -> {
        String renderedContent = renderFilesContent(ambiance, content, path, pipelineYaml);
        renderedFilesContentMap.put(path, renderedContent);
      });
    }
    return renderedFilesContentMap;
  }

  private String renderFilesContent(Ambiance ambiance, String content, String filePath, String pipelineYaml) {
    try {
      boolean shouldConvertExpressions = isNotEmpty(pipelineYaml);
      String contentToRender =
          shouldConvertExpressions ? expressionConversionHelper.convertExpressions(content, pipelineYaml) : content;
      return cdStepsExpressionResolver.renderValue(ambiance, contentToRender, true);
    } catch (Exception e) {
      throw new InvalidRequestException(
          String.format("Failed to resolve expressions provided in file: [%s], Error: [%s]", filePath, e.getMessage()));
    }
  }

  private Map<String, VmTaskExecutionResponse> handleAsyncResponses(Map<String, ResponseData> responseDataMap) {
    Map<String, VmTaskExecutionResponse> vmTaskResponse = new HashMap<>();
    if (isNotEmpty(responseDataMap)) {
      for (Map.Entry<String, ResponseData> responseDataEntry : responseDataMap.entrySet()) {
        ResponseData responseData = responseDataEntry.getValue();
        String taskId = responseDataEntry.getKey();
        if (responseData instanceof VmTaskExecutionResponse vmTaskExecutionResponse) {
          handleFailureVmResponse(taskId, vmTaskExecutionResponse);
          vmTaskResponse.put(taskId, vmTaskExecutionResponse);
        }
      }
    }
    return vmTaskResponse;
  }

  private void handleFailureVmResponse(String taskId, VmTaskExecutionResponse vmTaskExecutionResponse) {
    if (CommandExecutionStatus.FAILURE.equals(vmTaskExecutionResponse.getCommandExecutionStatus())) {
      log.error(
          "Failed to fetch files for taskId: " + taskId + " reason: " + vmTaskExecutionResponse.getErrorMessage());
      throw new ManifestCollectionException(
          "Failed to fetch files, reason: " + vmTaskExecutionResponse.getErrorMessage());
    }
  }

  private void handleFailureK8Response(String stepIdentifier, ResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData errorNotifyResponseData) {
      log.error(
          "Received error response for step {}, error: {}", stepIdentifier, errorNotifyResponseData.getErrorMessage());
      throw new ManifestCollectionException(
          "Failed to fetch files, reason: " + errorNotifyResponseData.getErrorMessage());
    }
    if (responseData instanceof StepStatusTaskResponseData stepStatusTaskResponseData) {
      StepStatus stepStatus = stepStatusTaskResponseData.getStepStatus();
      if (stepStatusTaskResponseData.getStepStatus() != null
          && !StepExecutionStatus.SUCCESS.equals(stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus())) {
        log.error("Received error response for step {}, error: {}", stepIdentifier, stepStatus.getError());
        throw new ManifestCollectionException("Failed to fetch files, reason: " + stepStatus.getError());
      }
    }
  }

  private void handleK8AsyncFailureResponse(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    log.info("Received response for step {}", stepIdentifier);

    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      ResponseData responseData = entry.getValue();
      if (responseData instanceof ErrorNotifyResponseData) {
        handleFailureK8Response(stepIdentifier, responseData);
      }

      StepStatusTaskResponseData stepStatusTaskResponseData = (StepStatusTaskResponseData) entry.getValue();
      if (stepStatusTaskResponseData == null) {
        log.error("stepStatusTaskResponseData should not be null for step {}", stepIdentifier);
        throw new ManifestCollectionException("Step status response is null for step: " + stepIdentifier);
      }
      handleFailureK8Response(stepIdentifier, responseData);
    }
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
}
