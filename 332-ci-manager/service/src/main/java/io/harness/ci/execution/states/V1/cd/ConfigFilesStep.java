/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.beans.steps.CIStepInfoType.UNIFIED_CONFIG_FILES_STEP;
import static io.harness.ci.execution.utils.ScmGitFileOperationsHelper.GitConnectorInfo;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.TaskType.SCM_BATCH_GET_FILE_TASK;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.GetBatchFileRequestIdentifier;
import io.harness.beans.response.GitFileBatchResponse;
import io.harness.beans.response.GitFileResponse;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.cd.beans.outcomes.ConfigFileMetadata;
import io.harness.cd.beans.outcomes.ConfigFileOutcome;
import io.harness.cd.beans.outcomes.ConfigFilesInfo;
import io.harness.cd.beans.outcomes.ConfigFilesOutcome;
import io.harness.cd.beans.outcomes.ConfigFilesSweepingOutput;
import io.harness.cd.beans.outcomes.ServiceStepUnitStatusSweepingOutput;
import io.harness.ci.execution.common.MapBasedReferenceExtractor;
import io.harness.ci.execution.common.MapBasedValidator;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.ci.execution.utils.ScmGitFileOperationsHelper;
import io.harness.ci.execution.utils.configfile.FileStoreSpec;
import io.harness.ci.execution.utils.configfile.GitFileStoreSpec;
import io.harness.ci.execution.utils.configfile.HarnessFileStoreSpec;
import io.harness.ci.execution.utils.configfile.ResolvedConfigFileStoreSpecFactory;
import io.harness.configFiles.ConfigGitFile;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.task.scm.ScmBatchGetFileTaskParams;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.expression.common.ExpressionMode;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.plancreator.stages.v1.EmptyStepParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.runnercommons.cgi.task.git.RunnerGithubFetchFileTaskBuilder;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;
import io.harness.unified.cd.service.configfiles.ConfigFile;
import io.harness.unified.cd.service.manifests.StoreType;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.validation.JavaxValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.Valid;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class ConfigFilesStep implements AsyncExecutableWithRbac<EmptyStepParameters> {
  @Inject private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Inject private MapBasedReferenceExtractor mapBasedReferenceExtractor;
  @Inject EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Inject private ResponseHandlerUtils responseHandlerUtils;
  @Inject private ScmGitFileOperationsHelper scmGitFileOperationsHelper;
  @Inject private RunnerGithubFetchFileTaskBuilder runnerGithubFetchFileTaskBuilder;
  @Inject private CommonAbstractStepUtils commonAbstractStepUtils;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private MapBasedValidator mapBasedValidator;
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private ConfigFilesStepHelper configFilesStepHelper;
  @Inject private HarnessConfigFileStoreFetcher harnessConfigFileStoreFetcher;

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(UNIFIED_CONFIG_FILES_STEP.getDisplayName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, EmptyStepParameters stepParameters) {
    // Todo: Nothing to validate
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage) {
    List<String> callbackIds = new ArrayList<>();
    List<String> logKeys = new ArrayList<>();
    List<String> commandUnits = new ArrayList<>();

    OptionalSweepingOutput optionalSweepingOutput =
        serviceStepSweepingOutputHelper.fetchServiceConfigFilesSweepingOutput(ambiance);
    LinkedHashMap<String, ConfigFileMetadata> configFilesMetadata = getConfigFiles(optionalSweepingOutput);
    ParameterField<Map<String, ParameterField<JsonNode>>> envVars = getEnvVars(optionalSweepingOutput);
    if (isEmpty(configFilesMetadata)) {
      return AsyncExecutableResponse.newBuilder().build();
    }

    log.debug("Processing config files");
    Map<String, ConfigFile> configFileLogKeyMap = toConfigFileMap(configFilesMetadata);
    return processConfigFiles(ambiance, configFileLogKeyMap);
  }

  private ConfigFilesOutcome prepareNonGitConfigFilesOutcome(List<ConfigFile> configFiles) {
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    configFiles.forEach(configFile -> {
      Optional<HarnessFileStoreSpec> harnessOpt = tryParseHarnessSpec(configFile.getInputs());
      if (harnessOpt.isEmpty()) {
        return;
      }
      List<String> filePaths = harnessOpt.get().getFilePaths();
      List<String> secretPaths = harnessOpt.get().getSecretFilePaths();
      if (isEmpty(filePaths) && isEmpty(secretPaths)) {
        throw new InvalidYamlException("Neither files (Harness File Store) nor secret files have been resolved in "
            + "Harness store config file with id: " + configFile.getId());
      }
      ConfigFileOutcome configFileOutcome = ConfigFileOutcome.builder()
                                                .id(configFile.getId())
                                                .store(StoreType.HARNESS)
                                                .files(isEmpty(filePaths) ? null : filePaths)
                                                .secretFiles(isEmpty(secretPaths) ? null : secretPaths)
                                                .build();
      configFilesOutcome.put(configFileOutcome.getId(), configFileOutcome);
    });

    return configFilesOutcome;
  }

  /**
   * Fetches Harness File Store content ({@code files}) eagerly at step time and materializes it as
   * {@link ConfigGitFile} entries on the corresponding {@link ConfigFileOutcome}, parallel to how git
   * config files are handled. This keeps the rendered/deployed content available via the configFiles
   * sweeping output (consumed by {@code RenderingStep}) and patches {@code ngOutcomes} via
   * {@link ConfigFilesStepHelper#updateNgConfigFilesOutcomeWithGitFiles}.
   */
  private void materializeHarnessFileStoreFiles(Ambiance ambiance, List<ConfigFile> configFiles,
      ConfigFilesOutcome configFilesOutcome, Map<String, List<ConfigGitFile>> gitFilesByConfigFileId) {
    for (ConfigFile configFile : configFiles) {
      Optional<HarnessFileStoreSpec> harnessOpt = tryParseHarnessSpec(configFile.getInputs());
      if (harnessOpt.isEmpty()) {
        continue;
      }
      List<String> filePaths = harnessOpt.get().getFilePaths();
      if (isEmpty(filePaths)) {
        continue;
      }
      Map<String, String> fileContentsByPath =
          harnessConfigFileStoreFetcher.fetchFileStoreContents(ambiance, filePaths);
      List<ConfigGitFile> gitFiles = new ArrayList<>();
      for (String filePath : filePaths) {
        gitFiles.add(ConfigGitFile.builder().filePath(filePath).fileContent(fileContentsByPath.get(filePath)).build());
      }

      ConfigFileOutcome existing = configFilesOutcome.get(configFile.getId());
      ConfigFileOutcome materialized = ConfigFileOutcome.builder()
                                           .id(configFile.getId())
                                           .store(StoreType.HARNESS)
                                           .files(filePaths)
                                           .secretFiles(existing != null ? existing.getSecretFiles() : null)
                                           .gitFiles(gitFiles)
                                           .build();
      configFilesOutcome.put(configFile.getId(), materialized);
      gitFilesByConfigFileId.put(configFile.getId(), gitFiles);
    }
  }

  private ConfigFileOutcome prepareConfigFilesOutComeForGit(
      String configFileId, GitFileStoreSpec gitSpec, Map<String, String> fileContentDataMap) {
    List<String> filePaths = gitSpec.getPaths();
    StoreType storeType = gitSpec.getStoreType();
    List<ConfigGitFile> gitFiles = new ArrayList<>();
    for (String path : filePaths) {
      String fileContent = fileContentDataMap.get(path);
      ConfigGitFile configGitFile = ConfigGitFile.builder().filePath(path).fileContent(fileContent).build();
      gitFiles.add(configGitFile);
    }
    return ConfigFileOutcome.builder().id(configFileId).store(storeType).gitFiles(gitFiles).build();
  }

  private LinkedHashMap<String, ConfigFileMetadata> getConfigFiles(OptionalSweepingOutput optionalSweepingOutput) {
    if (!optionalSweepingOutput.isFound()) {
      return new LinkedHashMap<>();
    }
    ConfigFilesSweepingOutput configFilesSweepingOutput =
        (ConfigFilesSweepingOutput) optionalSweepingOutput.getOutput();
    return isNotEmpty(configFilesSweepingOutput.getConfigFilesMetadataMap())
        ? configFilesSweepingOutput.getConfigFilesMetadataMap()
        : new LinkedHashMap<>();
  }

  private ParameterField<Map<String, ParameterField<JsonNode>>> getEnvVars(
      OptionalSweepingOutput optionalSweepingOutput) {
    if (!optionalSweepingOutput.isFound()) {
      return ParameterField.ofNull();
    }
    ConfigFilesSweepingOutput configFilesSweepingOutput =
        (ConfigFilesSweepingOutput) optionalSweepingOutput.getOutput();
    return ParameterField.isNotNull(configFilesSweepingOutput.getEnvVars())
            && isNotEmpty(configFilesSweepingOutput.getEnvVars().obtainValue())
        ? configFilesSweepingOutput.getEnvVars()
        : ParameterField.ofNull();
  }

  private Map<String, ConfigFile> toConfigFileMap(Map<String, ConfigFileMetadata> configFilesMetadataMap) {
    Map<String, ConfigFile> configFileMap = new HashMap<>();
    for (Map.Entry<String, ConfigFileMetadata> entry : configFilesMetadataMap.entrySet()) {
      ConfigFileMetadata metadata = entry.getValue();
      ConfigFile configFile = getConfigFile(metadata);
      configFileMap.put(metadata.getLogKey(), configFile);
    }
    return configFileMap;
  }

  private static ConfigFile getConfigFile(ConfigFileMetadata metadata) {
    ConfigFile configFile;
    try {
      configFile = YamlUtils.read(metadata.getConfigFileYaml(), ConfigFile.class);
    } catch (IOException e) {
      throw new InvalidYamlException("Failed to read config file yaml");
    }
    return configFile;
  }

  private void checkRuntimeAccessOrThrow(Ambiance ambiance, Collection<ConfigFile> configFiles) {
    Set<EntityDetailProtoDTO> allEntityDetailsProto = new HashSet<>();
    for (ConfigFile configFile : configFiles) {
      Map<String, Object> m = new HashMap<>();
      m.put("id", configFile.getId());
      if (configFile.getInputs() != null) {
        m.put("inputs", configFile.getInputs());
      }
      allEntityDetailsProto.addAll(mapBasedReferenceExtractor.extractReferencesFromConfigFileMap(m, ambiance));
    }
    List<EntityDetail> entityDetails =
        entityDetailProtoToRestMapper.createEntityDetailsDTO(new ArrayList<>(emptyIfNull(allEntityDetailsProto)));
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, true);
  }

  /**
   * Convert ConfigFile POJO to map structure (map-based validation / reference extraction).
   */
  private Map<String, Object> extractConfigFileMap(ConfigFile configFile) {
    Map<String, Object> map = new HashMap<>();
    map.put("id", configFile.getId());
    if (configFile.getInputs() != null) {
      map.put("inputs", configFile.getInputs());
    }
    return map;
  }

  /**
   * Process config files: inputs are pre-merged in {@code ServiceEntityProcessor}; SCM fetch
   * via {@link #handleFileFetchViaDelegate} / {@link #handleFetchFileViaHarness} using {@link GitFileStoreSpec}.
   */
  private AsyncExecutableResponse processConfigFiles(Ambiance ambiance, Map<String, ConfigFile> configFileLogKeyMap) {
    List<String> callbackIds = new ArrayList<>();

    resolveInputsOnConfigFiles(ambiance, configFileLogKeyMap);
    cdStepsExpressionResolver.updateExpressions(
        ambiance, configFileLogKeyMap, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);

    for (ConfigFile configFile : configFileLogKeyMap.values()) {
      Map<String, Object> configFileMap = extractConfigFileMap(configFile);
      if (isEmpty((String) configFileMap.get("id"))) {
        throw new InvalidRequestException("Config file id is required");
      }
      if (isNotEmpty(configFile.getInputs())) {
        mapBasedValidator.validateConfigFileMap(configFileMap);
      }
    }

    checkRuntimeAccessOrThrow(ambiance, configFileLogKeyMap.values());

    List<ConfigFile> configFiles = new ArrayList<>(configFileLogKeyMap.values());
    JavaxValidator.validateBeanOrThrow(new ConfigFileValidatorDTO(configFiles));

    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    configFilesOutcome.putAll(prepareNonGitConfigFilesOutcome(configFiles));

    Map<String, List<ConfigGitFile>> gitFilesByConfigFileId = new HashMap<>();
    // Harness File Store files are fetched eagerly (no delegate task) and materialized as gitFiles.
    materializeHarnessFileStoreFiles(ambiance, configFiles, configFilesOutcome, gitFilesByConfigFileId);

    ConfigFilesInfo configFilesInfo = new ConfigFilesInfo();

    // Remove inputs with "null" string value before processing
    removeNullStringValuesFromInputs(configFileLogKeyMap);

    Map<String, UnitStatus> inlineStatuses = new HashMap<>();

    for (Map.Entry<String, ConfigFile> configFileEntry : configFileLogKeyMap.entrySet()) {
      ConfigFile configFile = configFileEntry.getValue();
      String callBackId = null;
      Optional<GitFileStoreSpec> gitSpecOpt = tryParseGitSpec(configFile.getInputs());
      if (gitSpecOpt.isEmpty()) {
        continue;
      }
      GitFileStoreSpec gitSpec = gitSpecOpt.get();
      String configFileId = configFile.getId();
      GitConnectorInfo gitConnectorInfo = scmGitFileOperationsHelper.getGitConnectorInfo(ambiance, gitSpec);
      boolean executeOnDelegate = gitConnectorInfo.getConnectorDetails().getExecuteOnDelegate();
      GitFileBatchResponse response = null;
      if (executeOnDelegate) {
        callBackId = handleFileFetchViaDelegate(gitSpec, gitConnectorInfo);
        configFilesInfo.put(callBackId, configFile);
      } else {
        // Inline (Harness store) path: handleFetchFileViaHarness records per-file SUCCESS/FAILURE
        // into inlineStatuses, persists on FAILURE, and re-throws to keep the fail-loud semantic.
        response = handleFetchFileViaHarness(ambiance, gitSpec, gitConnectorInfo, configFileId, inlineStatuses);
        Map<String, String> fileContentsDataMap = scmGitFileOperationsHelper.toFileContentsDataMap(response);
        ConfigFileOutcome outcome = prepareConfigFilesOutComeForGit(configFileId, gitSpec, fileContentsDataMap);
        if (outcome != null && isNotEmpty(outcome.getId())) {
          configFilesOutcome.put(outcome.getId(), outcome);
          if (isNotEmpty(outcome.getGitFiles())) {
            gitFilesByConfigFileId.put(outcome.getId(), outcome.getGitFiles());
          }
        }
      }
      if (isNotEmpty(callBackId)) {
        callbackIds.add(callBackId);
      }
    }

    saveConfigFileSweepingOutput(ambiance, configFilesOutcome);
    saveConfigFileInfoSweepingOutput(ambiance, configFilesInfo);
    if (isNotEmpty(inlineStatuses)) {
      persistConfigFileUnitStatuses(ambiance, inlineStatuses);
    }
    configFilesStepHelper.updateNgConfigFilesOutcomeWithGitFiles(ambiance, gitFilesByConfigFileId);

    // No per-config-file log keys / command units are exposed: SCM CGI fetch has no log streaming
    // wiring, so per-file UI rows would surface as empty/UNKNOWN. Aggregate visibility comes from
    // the parent service step.
    return AsyncExecutableResponse.newBuilder().addAllCallbackIds(callbackIds).build();
  }

  private void resolveInputsOnConfigFiles(Ambiance ambiance, Map<String, ConfigFile> configFileLogKeyMap) {
    for (ConfigFile configFile : configFileLogKeyMap.values()) {
      if (isNotEmpty(configFile.getInputs())) {
        cdStepsExpressionResolver.updateExpressions(
            ambiance, configFile.getInputs(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
      }
    }
  }

  /**
   * Remove inputs with "null" string value from all config files.
   * Cleans up inputs map by removing entries where the value is the literal string "null".
   */
  private void removeNullStringValuesFromInputs(Map<String, ConfigFile> configFileLogKeyMap) {
    for (Map.Entry<String, ConfigFile> entry : configFileLogKeyMap.entrySet()) {
      ConfigFile configFile = entry.getValue();
      if (configFile == null || isEmpty(configFile.getInputs())) {
        continue;
      }

      Map<String, Object> inputs = configFile.getInputs();
      // Remove entries where value is the string "null"
      inputs.entrySet().removeIf(inputEntry -> "null".equals(inputEntry.getValue()));

      // Update the config file with cleaned inputs
      ConfigFile updatedConfigFile = configFile.toBuilder().inputs(inputs).build();
      entry.setValue(updatedConfigFile);
    }
  }

  private static Optional<GitFileStoreSpec> tryParseGitSpec(Map<String, Object> inputs) {
    if (isEmpty(inputs)) {
      return Optional.empty();
    }
    try {
      FileStoreSpec spec = ResolvedConfigFileStoreSpecFactory.fromInputs(inputs);
      if (spec instanceof GitFileStoreSpec git) {
        return Optional.of(git);
      }
    } catch (InvalidRequestException e) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private static Optional<HarnessFileStoreSpec> tryParseHarnessSpec(Map<String, Object> inputs) {
    if (isEmpty(inputs)) {
      return Optional.empty();
    }
    try {
      FileStoreSpec spec = ResolvedConfigFileStoreSpecFactory.fromInputs(inputs);
      if (spec instanceof HarnessFileStoreSpec harness) {
        return Optional.of(harness);
      }
    } catch (InvalidRequestException e) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private String handleFileFetchViaDelegate(GitFileStoreSpec gitSpec, GitConnectorInfo gitConnectorInfo) {
    return submitConfigFileFetchTask(gitSpec, gitConnectorInfo);
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, EmptyStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    if (isEmpty(responseDataMap)) {
      OptionalSweepingOutput existingOutcome = serviceStepSweepingOutputHelper.fetchConfigFilesSweepingOutput(ambiance);
      if (existingOutcome.isFound()) {
        return StepResponse.builder().status(Status.SUCCEEDED).build();
      }
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    Map<String, ResponseData> responses = new HashMap<>(responseDataMap);
    for (Map.Entry<String, ResponseData> entry : responses.entrySet()) {
      ResponseData deserialized = serializedResponseDataHelper.deserialize(entry.getValue());
      if (deserialized != null) {
        entry.setValue(deserialized);
      } else {
        log.warn("Could not deserialize response for callbackId={}; passing raw SerializedResponseData downstream",
            entry.getKey());
      }
      // If deserialize returns null (e.g. runner CGI JSON responses whose task type is not
      // registered in TaskTypeToRequestResponseMapper), keep the original SerializedResponseData
      // so that handleVmAsyncFailureResponses / processConfigFilesOutcome can handle it directly.
    }

    // Persists per-config-file fetch statuses for future UI timeline use.
    // Not read by UnifiedServiceStep today; retained so the data is available when
    // per-config-file rows are wired to the timeline.
    collectAndPersistConfigFileUnitStatuses(ambiance, responses);

    StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();
    if (stageInfraType == StageInfraDetails.Type.K8) {
      StepResponse response = handleK8AsyncFailureResponse(ambiance, responses);
      if (!Status.SUCCEEDED.equals(response.getStatus())) {
        return response;
      }
    }
    if (stageInfraType == StageInfraDetails.Type.VM || stageInfraType == StageInfraDetails.Type.DLITE_VM) {
      StepResponse response = handleVmAsyncFailureResponses(ambiance, responses);
      if (!Status.SUCCEEDED.equals(response.getStatus())) {
        return response;
      }
    }
    processConfigFilesOutcome(ambiance, responses);
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private StepResponse handleVmAsyncFailureResponses(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      ResponseData data = runnerGithubFetchFileTaskBuilder.handleFailureResponse(entry.getValue());
      if (data instanceof ErrorNotifyResponseData) {
        log.error("Received error response for step {}, error: {}", stepIdentifier,
            ((ErrorNotifyResponseData) data).getErrorMessage());
        return responseHandlerUtils.getGenericFailedStepResponse(
            ambiance, "Failed to fetch config files", ((ErrorNotifyResponseData) data).getErrorMessage());
      }
    }
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private void collectAndPersistConfigFileUnitStatuses(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    Map<String, String> callbackToConfigFileId = resolveCallbackToConfigFileId(ambiance);
    Map<String, UnitStatus> statuses = loadExistingConfigFileStatuses(ambiance);
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      String configFileId = callbackToConfigFileId.get(entry.getKey());
      if (isNotEmpty(configFileId)) {
        ResponseData rd = runnerGithubFetchFileTaskBuilder.handleFailureResponse(entry.getValue());
        statuses.put(configFileId, ResponseHandlerUtils.getUnitStatus(rd));
      }
    }
    persistConfigFileUnitStatuses(ambiance, statuses);
  }

  private StepResponse handleK8AsyncFailureResponse(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    log.info("Received response for step {}", stepIdentifier);
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      ResponseData responseData = runnerGithubFetchFileTaskBuilder.handleFailureResponse(entry.getValue());
      if (responseData instanceof ErrorNotifyResponseData) {
        log.error("Received error response for step {}, error: {}", stepIdentifier,
            ((ErrorNotifyResponseData) responseData).getErrorMessage());
        return responseHandlerUtils.getGenericFailedStepResponse(
            ambiance, "Failed to get config files", ((ErrorNotifyResponseData) responseData).getErrorMessage());
      }
      if (responseData instanceof StepStatusTaskResponseData stepStatusTaskResponseData) {
        stepStatusTaskResponseData = (StepStatusTaskResponseData) entry.getValue();
        if (stepStatusTaskResponseData == null) {
          log.error("stepStatusTaskResponseData should not be null for step {}", stepIdentifier);
          return responseHandlerUtils.getGenericFailedStepResponse(
              ambiance, "Failed to get config files", "Failed to get config files");
        }

        if (stepStatusTaskResponseData.getStepStatus() != null
            && !StepExecutionStatus.SUCCESS.equals(
                stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus())) {
          return responseHandlerUtils.getGenericFailedStepResponse(
              ambiance, "Failed to get config files", "Failed to get config files");
        }
      }
    }
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private Map<String, String> resolveCallbackToConfigFileId(Ambiance ambiance) {
    Map<String, String> result = new HashMap<>();
    OptionalSweepingOutput out = serviceStepSweepingOutputHelper.fetchConfigFilesInfoSweepingOutput(ambiance);
    if (out.isFound() && out.getOutput() instanceof ConfigFilesInfo info) {
      for (Map.Entry<String, ConfigFile> e : info.entrySet()) {
        if (e.getValue() != null && isNotEmpty(e.getValue().getId())) {
          result.put(e.getKey(), e.getValue().getId());
        }
      }
    }
    return result;
  }

  private Map<String, UnitStatus> loadExistingConfigFileStatuses(Ambiance ambiance) {
    OptionalSweepingOutput out = serviceStepSweepingOutputHelper.fetchConfigFileUnitStatusesSweepingOutput(ambiance);
    if (out.isFound() && out.getOutput() instanceof ServiceStepUnitStatusSweepingOutput existing
        && isNotEmpty(existing.getStatuses())) {
      return new HashMap<>(existing.getStatuses());
    }
    return new HashMap<>();
  }

  private void persistConfigFileUnitStatuses(Ambiance ambiance, Map<String, UnitStatus> statuses) {
    serviceStepSweepingOutputHelper.saveConfigFileUnitStatusesSweepingOutput(
        ambiance, ServiceStepUnitStatusSweepingOutput.builder().statuses(statuses).build());
  }

  @Data
  @Builder
  private static class ConfigFileValidatorDTO {
    @Valid List<ConfigFile> configFiles;
  }

  private GitFileBatchResponse handleFetchFileViaHarness(Ambiance ambiance, GitFileStoreSpec gitSpec,
      GitConnectorInfo gitConnectorInfo, String configFileId, Map<String, UnitStatus> inlineStatuses) {
    GitFileBatchResponse response = scmGitFileOperationsHelper.getBatchFile(gitSpec, gitConnectorInfo);
    try {
      handleFailureResponse(response);
      inlineStatuses.put(configFileId, UnitStatus.SUCCESS);
      return response;
    } catch (Exception ex) {
      inlineStatuses.put(configFileId, UnitStatus.FAILURE);
      persistConfigFileUnitStatuses(ambiance, inlineStatuses);
      throw ex;
    }
  }

  private void handleFailureResponse(GitFileBatchResponse response) {
    Map<GetBatchFileRequestIdentifier, GitFileResponse> batchFileRequestIdentifierGitFileResponseMap =
        response.getGetBatchFileRequestIdentifierGitFileResponseMap();
    for (Map.Entry<GetBatchFileRequestIdentifier, GitFileResponse> entry :
        batchFileRequestIdentifierGitFileResponseMap.entrySet()) {
      GitFileResponse gitFileResponse = entry.getValue();
      if (gitFileResponse.getStatusCode() != 200) {
        String errMsg = runnerGithubFetchFileTaskBuilder.prepareErrorMessage(
            gitFileResponse.getFilepath(), gitFileResponse.getError(), gitFileResponse.getStatusCode());
        // Throwing General exception to maintain consistency in error handling for connector going via delegate and
        // connector going via harness.
        throw new GeneralException(errMsg);
      }
    }
  }

  private String submitConfigFileFetchTask(GitFileStoreSpec gitSpec, GitConnectorInfo gitConnectorInfo) {
    String accountId = gitConnectorInfo.getAccountId();
    final ScmBatchGetFileTaskParams params =
        scmGitFileOperationsHelper.getScmGetBatchFileTaskParams(gitSpec, gitConnectorInfo);
    DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                  .accountId(accountId)
                                                  .executionTimeout(Duration.ofSeconds(120))
                                                  .taskType(SCM_BATCH_GET_FILE_TASK.name())
                                                  .taskParameters(params)
                                                  .build();
    GitApiTaskParams gitApiTaskParams =
        scmGitFileOperationsHelper.getScmCgiFetchFilesTaskParams(gitSpec, gitConnectorInfo);
    try {
      return runnerGithubFetchFileTaskBuilder.sendFetchFilesTask(gitApiTaskParams, accountId, delegateTaskRequest);
    } catch (Exception e) {
      throw new RuntimeException("Failed to fetch config files", e);
    }
  }

  private void saveConfigFileSweepingOutput(Ambiance ambiance, ConfigFilesOutcome configFilesOutcome) {
    serviceStepSweepingOutputHelper.saveConfigFilesSweepingOutput(ambiance, configFilesOutcome);
  }

  private void saveConfigFileInfoSweepingOutput(Ambiance ambiance, ConfigFilesInfo info) {
    serviceStepSweepingOutputHelper.saveConfigFilesInfoSweepingOutput(ambiance, info);
  }

  private void processConfigFilesOutcome(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    OptionalSweepingOutput configFileInfoSweepingOutput =
        serviceStepSweepingOutputHelper.fetchConfigFilesInfoSweepingOutput(ambiance);
    OptionalSweepingOutput configFileOutcomeSweepingOutput =
        serviceStepSweepingOutputHelper.fetchConfigFilesSweepingOutput(ambiance);
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    Map<String, List<ConfigGitFile>> gitFilesByConfigFileId = new HashMap<>();
    if (configFileInfoSweepingOutput.isFound() && configFileOutcomeSweepingOutput.isFound()) {
      ConfigFilesInfo configFilesInfo = (ConfigFilesInfo) configFileInfoSweepingOutput.getOutput();
      configFilesOutcome = (ConfigFilesOutcome) configFileOutcomeSweepingOutput.getOutput();
      for (Map.Entry<String, ConfigFile> entry : configFilesInfo.entrySet()) {
        ConfigFile configFile = entry.getValue();
        String callBackId = entry.getKey();
        ResponseData data = responseDataMap.get(callBackId);
        if (data != null && configFile != null && configFile.getInputs() != null) {
          Map<String, String> getFileContentsDataMap =
              runnerGithubFetchFileTaskBuilder.deserializeScmCgiResponseAndGetFileContent(data);
          Optional<GitFileStoreSpec> gitSpecOpt = tryParseGitSpec(configFile.getInputs());
          if (gitSpecOpt.isEmpty()) {
            continue;
          }
          ConfigFileOutcome configFileOutcome =
              prepareConfigFilesOutComeForGit(configFile.getId(), gitSpecOpt.get(), getFileContentsDataMap);
          if (configFileOutcome != null && isNotEmpty(configFileOutcome.getId())) {
            configFilesOutcome.put(configFileOutcome.getId(), configFileOutcome);
            if (isNotEmpty(configFileOutcome.getGitFiles())) {
              gitFilesByConfigFileId.put(configFileOutcome.getId(), configFileOutcome.getGitFiles());
            }
          }
        }
      }
    }
    saveConfigFileSweepingOutput(ambiance, configFilesOutcome);
    configFilesStepHelper.updateNgConfigFilesOutcomeWithGitFiles(ambiance, gitFilesByConfigFileId);
  }
}