/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.beans.steps.CIStepInfoType.UNIFIED_SERVICE_STEP;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.ARTIFACTS_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.CONFIG_FILES_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.MANIFESTS_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.MANIFEST_SECTION_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.POST_FETCH_FILES_HOOKS_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.POST_TEMPLATE_HOOKS_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.PRE_FETCH_FILES_HOOKS_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.PRE_TEMPLATE_HOOKS_NODE_ID;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.FREEZE_EXCEPTION;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.unified.service.NGOutcomes.NG_OUTCOMES;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cd.beans.ArtifactMetadata;
import io.harness.cd.beans.ArtifactsSweepingOutput;
import io.harness.cd.beans.ArtifactsSweepingOutput.ArtifactsSweepingOutputBuilder;
import io.harness.cd.beans.outcomes.ArtifactsOutcome;
import io.harness.cd.beans.outcomes.ArtifactsOutcomeSweepingOutput;
import io.harness.cd.beans.outcomes.ConfigFileMetadata;
import io.harness.cd.beans.outcomes.ConfigFilesOutcome;
import io.harness.cd.beans.outcomes.ConfigFilesSweepingOutput;
import io.harness.cd.beans.outcomes.ConfigFilesSweepingOutput.ConfigFilesSweepingOutputBuilder;
import io.harness.cd.beans.outcomes.ManifestMetadata;
import io.harness.cd.beans.outcomes.ManifestsSweepingOutput;
import io.harness.cd.beans.outcomes.ManifestsSweepingOutput.ManifestsSweepingOutputBuilder;
import io.harness.cd.beans.outcomes.ServiceConfigOutcome;
import io.harness.cd.beans.outcomes.ServiceHookMetadata;
import io.harness.cd.beans.outcomes.ServiceHooksOutcome;
import io.harness.cd.beans.outcomes.ServiceHooksSweepingOutput;
import io.harness.cd.beans.outcomes.ServiceHooksSweepingOutput.ServiceHooksSweepingOutputBuilder;
import io.harness.cd.beans.outcomes.ServiceStepUnitStatusSweepingOutput;
import io.harness.cd.beans.outcomes.UnifiedServiceOutcome;
import io.harness.cd.multi.deploy.UnifiedServiceTypeValidatorUtils;
import io.harness.cdng.freeze.FreezeOutcome;
import io.harness.ci.execution.common.ProcessedServiceResult;
import io.harness.ci.execution.common.ServiceEntityMetadata;
import io.harness.ci.execution.common.ServiceEntityProcessor;
import io.harness.ci.execution.common.ServiceStepOutcomeHelper;
import io.harness.ci.execution.common.SpotStartupScriptHelper;
import io.harness.ci.execution.states.helpers.ArtifactStepUtils;
import io.harness.ci.execution.states.helpers.ManifestsStepUtils;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.ci.execution.states.helpers.ServiceStepUtility;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.states.V1.cd.helpers.UnifiedServiceStepOpaHelper;
import io.harness.data.validator.EntityIdentifierValidator;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.eraro.Level;
import io.harness.expression.EngineExpressionService;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.beans.request.FreezeEntitiesRequestDTO;
import io.harness.freeze.beans.request.FrozenExecutionCreateRequestDTO;
import io.harness.freeze.beans.response.ActiveFreezeEntitiesResponseDTO;
import io.harness.freeze.entity.FrozenExecutionDTO;
import io.harness.freeze.helpers.FreezeRBACHelper;
import io.harness.freeze.mappers.FrozenExecutionMapper;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.failure.FailureTypeInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.utilities.PrincipalUtility;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.steps.executable.ChildrenExecutableWithRbac;
import io.harness.tasks.ResponseData;
import io.harness.transientData.service.TransientExecutionDataService;
import io.harness.unified.cd.service.artifacts.ArtifactConfig;
import io.harness.unified.cd.service.artifacts.ArtifactWrapper;
import io.harness.unified.cd.service.configfiles.ConfigFile;
import io.harness.unified.cd.service.hooks.ServiceHookAction;
import io.harness.unified.cd.service.hooks.ServiceHookConfig;
import io.harness.unified.cd.service.hooks.ServiceHookType;
import io.harness.unified.cd.service.manifests.ManifestConfig;
import io.harness.unified.cd.service.spec.ServiceConfig;
import io.harness.unified.cd.service.spec.ServiceSpec;
import io.harness.unified.cd.service.spec.ServiceType;
import io.harness.unified.cd.service.spec.SpotServiceSpec;
import io.harness.unified.depoloymentfreeze.NgDeploymentFreezeResourceClient;
import io.harness.utils.ServiceHookImplicitStepHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class UnifiedServiceStep implements ChildrenExecutableWithRbac<UnifiedServiceStepParameters> {
  @Inject private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Inject private ServiceStepOutcomeHelper serviceStepOutcomeHelper;
  @Inject private NgDeploymentFreezeResourceClient ngDeploymentFreezeResourceClient;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject private TransientExecutionDataService transientExecutionDataService;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private ServiceEntityProcessor serviceEntityProcessor;
  @Inject private UnifiedServiceStepOpaHelper unifiedServiceStepOpaHelper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private ServiceHookImplicitStepHelper serviceHookImplicitStepHelper;
  @Inject private CIFeatureFlagService ciFeatureFlagService;
  @Inject private ServiceHookTaskHelper serviceHookTaskHelper;

  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(UNIFIED_SERVICE_STEP.getDisplayName()).setStepCategory(StepCategory.STEP).build();
  public static final String FREEZE_SWEEPING_OUTPUT = "freezeSweepingOutput";

  @Override
  public Class<UnifiedServiceStepParameters> getStepParametersClass() {
    return UnifiedServiceStepParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, UnifiedServiceStepParameters stepParameters) {}

  @Override
  public ChildrenExecutableResponse obtainChildrenAfterRbac(
      Ambiance ambiance, UnifiedServiceStepParameters stepParameters, StepInputPackage inputPackage) {
    final String serviceRef = ServiceStepUtility.validateAndGetServiceRef(stepParameters.getServiceRef());
    final String environmentRef = ServiceStepUtility.validateAndGetEnvironmentRef(stepParameters.getEnvironmentRef());
    final String envBranchRef = ServiceStepUtility.validateAndGetEnvBranchRef(stepParameters.getEnvBranchRef());
    final String infraId = ServiceStepUtility.validateAndGetInfraId(stepParameters.getInfraId());
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    final String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    final String logBaseKey = LogStreamingStepClientFactory.getLogBaseKey(ambiance);
    final String pipelineIdentifier = AmbianceUtils.getPipelineIdentifier(ambiance);
    final Map<String, Object> serviceInputs = ParameterField.isNull(stepParameters.getServiceInputs())
        ? new HashMap<>()
        : stepParameters.getServiceInputs().obtainValue();
    final Map<String, Object> envOverridesInputs = ParameterField.isNull(stepParameters.getEnvOverridesInputs())
        ? new HashMap<>()
        : stepParameters.getEnvOverridesInputs().obtainValue();
    final Map<String, Object> svcOverridesInputs = ParameterField.isNull(stepParameters.getSvcOverridesInputs())
        ? new HashMap<>()
        : stepParameters.getSvcOverridesInputs().obtainValue();
    final String branch =
        ParameterField.isNull(stepParameters.getBranch()) ? null : stepParameters.getBranch().obtainValue();

    String envGroupRef = ParameterField.isNotNull(stepParameters.getEnvGroupRef())
        ? stepParameters.getEnvGroupRef().obtainValue()
        : null;
    ProcessedServiceResult result = serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(ambiance, serviceRef,
        environmentRef, envBranchRef, infraId, accountId, orgIdentifier, projectIdentifier, serviceInputs,
        stepParameters.getInfraInputs(), envOverridesInputs, svcOverridesInputs, branch, envGroupRef);

    // When a group-level 'type' is declared, validate the actual resolved service type against it. For a single service
    // this also covers a non-runtime-input expression ref, which only resolves here. No-op when no type was declared.
    ServiceType resolvedServiceType = result.getServiceConfig().getServiceInfoConfig().getUses();
    String declaredServiceType = stepParameters.getServiceType();
    UnifiedServiceTypeValidatorUtils.validateResolvedServiceType(declaredServiceType, resolvedServiceType, serviceRef);

    saveServiceMetadataOutput(ambiance, result.getServiceEntityMetadata(),
        result.getServiceConfig().getServiceInfoConfig().getUses().getDisplayName());

    List<String> stepLogKeys = new ArrayList<>();
    List<String> stepCommandUnits = new ArrayList<>();

    Map<String, List<String>> fetchHookKeys = Collections.emptyMap();
    boolean serviceHookEnabled = serviceHookTaskHelper.isServiceHooksEnabled(ambiance);
    if (serviceHookEnabled) {
      fetchHookKeys = handleServiceHooksPart(
          ambiance, result.getServiceConfig(), logBaseKey, stepParameters.getEnvVars(), ServiceHookAction.FETCH_FILES);
      stepLogKeys.addAll(fetchHookKeys.getOrDefault("preLogKeys", Collections.emptyList()));
      stepCommandUnits.addAll(fetchHookKeys.getOrDefault("preCommandUnits", Collections.emptyList()));

      handleServiceHooksPart(ambiance, result.getServiceConfig(), logBaseKey, stepParameters.getEnvVars(),
          ServiceHookAction.TEMPLATE_MANIFEST);
    }

    handleManifestsPart(ambiance, result.getServiceConfig(), stepLogKeys, stepCommandUnits, logBaseKey,
        stepParameters.getEnvVars(), result.getManifestMap());

    stepLogKeys.addAll(fetchHookKeys.getOrDefault("postLogKeys", Collections.emptyList()));
    stepCommandUnits.addAll(fetchHookKeys.getOrDefault("postCommandUnits", Collections.emptyList()));
    handleConfigFilesPart(ambiance, result.getServiceConfig(), stepLogKeys, stepCommandUnits, logBaseKey,
        stepParameters.getEnvVars(), result.getConfigFileMap());
    handleArtifactsPart(ambiance, logBaseKey, result, stepLogKeys, stepCommandUnits, stepParameters.getEnvVars());

    // Deployment freeze
    Map<FreezeEntityType, List<String>> freezeEntityTypeListMap =
        getFreezeEntitiesMap(serviceRef, environmentRef, orgIdentifier, projectIdentifier, pipelineIdentifier, result);

    ChildrenExecutableResponse childrenExecutableResponse =
        executeFreezePart(ambiance, freezeEntityTypeListMap, stepCommandUnits, stepLogKeys);
    if (childrenExecutableResponse != null) {
      return childrenExecutableResponse;
    }

    return ChildrenExecutableResponse.newBuilder()
        .addAllLogKeys(stepLogKeys)
        .addAllUnits(stepCommandUnits)
        .addAllChildren(stepParameters.getChildrenNodeIds()
                            .stream()
                            .map(id -> ChildrenExecutableResponse.Child.newBuilder().setChildNodeId(id).build())
                            .collect(Collectors.toList()))
        .build();
  }

  private static Map<FreezeEntityType, List<String>> getFreezeEntitiesMap(String serviceRef, String environmentRef,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, ProcessedServiceResult result) {
    Map<FreezeEntityType, List<String>> freezeEntityTypeListMap = new HashMap<>();
    freezeEntityTypeListMap.put(FreezeEntityType.ORG, List.of(orgIdentifier));
    freezeEntityTypeListMap.put(FreezeEntityType.PROJECT, List.of(projectIdentifier));
    freezeEntityTypeListMap.put(FreezeEntityType.PIPELINE, List.of(pipelineIdentifier));
    freezeEntityTypeListMap.put(FreezeEntityType.SERVICE, List.of(serviceRef));
    if (isNotEmpty(environmentRef)) {
      freezeEntityTypeListMap.put(FreezeEntityType.ENVIRONMENT, List.of(environmentRef));
    }
    if (result.getEnvironmentOutcome() != null) {
      freezeEntityTypeListMap.put(FreezeEntityType.ENV_TYPE, List.of(result.getEnvironmentOutcome().getType().name()));
    }
    return freezeEntityTypeListMap;
  }

  @VisibleForTesting
  public ChildrenExecutableResponse executeFreezePart(Ambiance ambiance, Map<FreezeEntityType, List<String>> entityMap,
      List<String> logCommandUnits, List<String> stepLogKeys) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    if (FreezeRBACHelper.checkIfUserHasFreezeOverrideAccess(accountIdentifier, orgIdentifier, projectIdentifier,
            accessControlClient, PrincipalUtility.constructPrincipalFromAmbiance(ambiance))) {
      return null;
    }
    ActiveFreezeEntitiesResponseDTO freezeEntitiesResponseDTO =
        getResponse(ngDeploymentFreezeResourceClient.getActiveFreezeEntities(accountIdentifier, orgIdentifier,
            projectIdentifier, FreezeEntitiesRequestDTO.builder().entityMap(entityMap).build()));
    if (freezeEntitiesResponseDTO.isDeploymentFreezeActive()) {
      log.info("Deployment Freeze is Active for the given service.");
      transientExecutionDataService.consume(ambiance, FREEZE_SWEEPING_OUTPUT,
          FreezeOutcome.builder()
              .frozen(true)
              .manualFreezeConfigs(freezeEntitiesResponseDTO.getManualFreezeEntities())
              .globalFreezeConfigs(freezeEntitiesResponseDTO.getGlobalFreezeEntities())
              .build(),
          "");
      log.info("Adding Children as empty.");
      return ChildrenExecutableResponse.newBuilder()
          .addAllLogKeys(stepLogKeys)
          .addAllChildren(Collections.emptyList())
          .addAllUnits(logCommandUnits)
          .build();
    }
    return null;
  }

  public StepResponse handleFreezeResponse(Ambiance ambiance) {
    final OptionalSweepingOutput freezeOutcomeOptional = transientExecutionDataService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(FREEZE_SWEEPING_OUTPUT));
    if (freezeOutcomeOptional.isFound()) {
      FreezeOutcome freezeOutcome = (FreezeOutcome) freezeOutcomeOptional.getOutput();
      if (freezeOutcome.isFrozen()) {
        FrozenExecutionDTO frozenExecutionDTO =
            FrozenExecutionMapper.toFreezeWithExecutionDTO(ambiance, freezeOutcome.getManualFreezeConfigs(),
                freezeOutcome.getGlobalFreezeConfigs(), AmbianceUtils.getParentUniqueIdentifier(ambiance));
        String executionUrl = engineExpressionService.renderExpression(ambiance, "${{pipeline.execution.url}}",
            io.harness.pms.contracts.plan.ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        getResponse(ngDeploymentFreezeResourceClient.createFrozenExecution(AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
            FrozenExecutionCreateRequestDTO.builder()
                .frozenExecutionDTO(frozenExecutionDTO)
                .executionUrl(executionUrl)
                .planExecutionId(ambiance.getPlanExecutionId())
                .userId(ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getIdentifier())
                .module("ci")
                .build()));
        return StepResponse.builder()
            .failureInfo(FailureInfo.newBuilder()
                             .addFailureData(FailureData.newBuilder()
                                                 .addFailureTypeInfos(FailureTypeInfo.newBuilder().setFailureType(
                                                     FailureType.FREEZE_ACTIVE_FAILURE))
                                                 .setLevel(Level.ERROR.name())
                                                 .setCode(FREEZE_EXCEPTION.name())
                                                 .setMessage("Pipeline Aborted due to freeze")
                                                 .build())
                             .build())
            .status(Status.FREEZE_FAILED)
            .build();
      }
    }
    return null;
  }

  @Override
  public StepResponse handleChildrenResponse(
      Ambiance ambiance, UnifiedServiceStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // check if freeze is active return freeze response
    StepResponse stepFreezeResponse = handleFreezeResponse(ambiance);
    if (stepFreezeResponse != null) {
      return stepFreezeResponse;
    }
    StepResponse stepResponse = SdkCoreStepUtils.createStepResponseFromChildResponse(responseDataMap);
    List<UnitProgress> unitProgresses = new ArrayList<>();
    final List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();

    // Fetch ngOutcomes once and reuse for all outcome methods
    io.harness.beans.common.VariablesSweepingOutput ngOutcomes = null;
    OptionalSweepingOutput ngOutcomesSweepingOutput =
        sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES));
    if (ngOutcomesSweepingOutput.isFound()) {
      ngOutcomes = (io.harness.beans.common.VariablesSweepingOutput) ngOutcomesSweepingOutput.getOutput();
    }

    // This is v1 outcome, But we are saving v0 outcome coming from NG/ngOutcomes
    // remove this code, once product decided on future direction of v1 service
    OptionalSweepingOutput optionalServiceSweepingOutput =
        serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(ambiance);
    if (optionalServiceSweepingOutput.isFound()) {
      UnifiedServiceOutcome serviceOutcome = (UnifiedServiceOutcome) optionalServiceSweepingOutput.getOutput();
      serviceStepOutcomeHelper.addServiceOutcome(stepOutcomes, serviceOutcome, ngOutcomes);
    }

    long serviceStepStartTs = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    long stepEndTs = System.currentTimeMillis();

    Map<String, UnitStatus> manifestStatuses = fetchManifestUnitStatuses(ambiance);
    Map<String, UnitStatus> artifactStatuses = fetchArtifactUnitStatuses(ambiance);

    OptionalSweepingOutput optionalManifestOutput =
        serviceStepSweepingOutputHelper.fetchServiceManifestsSweepingOutput(ambiance);

    if (optionalManifestOutput.isFound()) {
      ManifestsSweepingOutput manifestsSweepingOutput = (ManifestsSweepingOutput) optionalManifestOutput.getOutput();
      if (isNotEmpty(manifestsSweepingOutput.getManifestMetadataMap())) {
        Map<String, ManifestConfig> manifestLogKeyMap =
            ManifestsStepUtils.toManifestConfigMap(manifestsSweepingOutput.getManifestMetadataMap());

        boolean hasInvalidIds =
            manifestLogKeyMap.values()
                .stream()
                .map(ManifestConfig::getId)
                .anyMatch(id -> !EntityIdentifierValidator.IDENTIFIER_PATTERN.matcher(id).matches());
        if (hasInvalidIds) {
          addUnitProgress(unitProgresses, ManifestsStep.MANIFESTS_VALIDATION_UNIT,
              Map.of(ManifestsStep.MANIFESTS_VALIDATION_UNIT, UnitStatus.SUCCESS), serviceStepStartTs, stepEndTs);
        }
        for (Map.Entry<String, ManifestConfig> entry : manifestLogKeyMap.entrySet()) {
          ManifestConfig manifestConfig = entry.getValue();
          addUnitProgress(unitProgresses, manifestConfig.getId(), manifestStatuses, serviceStepStartTs, stepEndTs);
        }
        serviceStepOutcomeHelper.addManifestsStepOutcome(ambiance, stepOutcomes, ngOutcomes);
      }
    }

    OptionalSweepingOutput optionalArtifactOutput =
        serviceStepSweepingOutputHelper.fetchServiceArtifactsSweepingOutput(ambiance);
    if (optionalArtifactOutput.isFound()) {
      ArtifactsSweepingOutput artifactsSweepingOutput = (ArtifactsSweepingOutput) optionalArtifactOutput.getOutput();
      if (isNotEmpty(artifactsSweepingOutput.getArtifactsMetadataMap())) {
        Map<String, ArtifactConfig> artifactConfigMap =
            ArtifactStepUtils.toArtifactConfigMap(artifactsSweepingOutput.getArtifactsMetadataMap());
        for (ArtifactConfig artifactConfig : artifactConfigMap.values()) {
          addUnitProgress(unitProgresses, artifactConfig.getId(), artifactStatuses, serviceStepStartTs, stepEndTs);
        }
      }

      Optional<ArtifactsOutcome> artifactStepOutcomeOp = getArtifactStepOutcome(ambiance);
      serviceStepOutcomeHelper.addArtifactsStepOutcome(stepOutcomes, artifactStepOutcomeOp.orElse(null), ngOutcomes);
    }

    OptionalSweepingOutput configFilesOutput = serviceStepSweepingOutputHelper.fetchConfigFilesSweepingOutput(ambiance);
    ConfigFilesOutcome configFilesOutcome =
        configFilesOutput.isFound() ? (ConfigFilesOutcome) configFilesOutput.getOutput() : null;

    // No per-config-file UnitProgress is emitted: config-file fetch has no log-streaming wiring,
    // so per-file rows would surface in the UI as empty/UNKNOWN entries. Aggregate visibility is
    // provided through the parent service step. Outcomes are still published below for downstream
    // expressions/consumers.
    if (configFilesOutcome != null) {
      serviceStepOutcomeHelper.addConfigFilesStepOutcome(stepOutcomes, configFilesOutcome, ngOutcomes);
    }

    OptionalSweepingOutput opServiceConfigOutcome =
        serviceStepSweepingOutputHelper.fetchServiceConfigMetadataOutput(ambiance);
    if (opServiceConfigOutcome.isFound()) {
      ServiceConfigOutcome serviceConfigOutcome = (ServiceConfigOutcome) opServiceConfigOutcome.getOutput();
      if (isNotEmpty(serviceConfigOutcome.getHooks())) {
        serviceStepOutcomeHelper.addServiceHooksStepOutcome(
            stepOutcomes, new ServiceHooksOutcome(serviceConfigOutcome.getHooks()));
      }
    }

    stepResponse = stepResponse.withStepOutcomes(stepOutcomes);

    callOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    return stepResponse.toBuilder().unitProgressList(unitProgresses).build();
  }

  private Map<String, List<String>> handleServiceHooksPart(Ambiance ambiance, ServiceConfig serviceConfig,
      String logBaseKey, ParameterField<Map<String, ParameterField<JsonNode>>> envVars,
      ServiceHookAction targetAction) {
    List<ServiceHookConfig> hooks = serviceConfig.getServiceInfoConfig().getWith().getHooks();
    if (isEmpty(hooks)) {
      return Collections.emptyMap();
    }

    if (targetAction == ServiceHookAction.FETCH_FILES) {
      return handleFetchFilesHooks(ambiance, serviceConfig, hooks, logBaseKey, envVars);
    } else if (targetAction == ServiceHookAction.TEMPLATE_MANIFEST) {
      return handleTemplateManifestHooks(ambiance, hooks, logBaseKey, envVars);
    }
    return Collections.emptyMap();
  }

  private Map<String, List<String>> handleFetchFilesHooks(Ambiance ambiance, ServiceConfig serviceConfig,
      List<ServiceHookConfig> hooks, String logBaseKey, ParameterField<Map<String, ParameterField<JsonNode>>> envVars) {
    boolean captureOverrideFiles = isNativeHelmWithSopsEnabled(ambiance, serviceConfig);

    hooks = hooks.stream().sorted(Comparator.comparingInt(ServiceHookConfig::getOrder)).collect(Collectors.toList());

    LinkedHashMap<String, ServiceHookMetadata> preHookMetadataMap = new LinkedHashMap<>();
    LinkedHashMap<String, ServiceHookMetadata> postHookMetadataMap = new LinkedHashMap<>();
    List<String> preLogKeys = new ArrayList<>();
    List<String> preCommandUnits = new ArrayList<>();
    List<String> postLogKeys = new ArrayList<>();
    List<String> postCommandUnits = new ArrayList<>();

    String manifestSectionLogBaseKey = appendChildStepLevel(logBaseKey, MANIFEST_SECTION_NODE_ID, ambiance);
    String preHooksLogBaseKey =
        appendChildStepLevel(manifestSectionLogBaseKey, PRE_FETCH_FILES_HOOKS_NODE_ID, ambiance);
    String postHooksLogBaseKey =
        appendChildStepLevel(manifestSectionLogBaseKey, POST_FETCH_FILES_HOOKS_NODE_ID, ambiance);

    for (ServiceHookConfig hook : hooks) {
      if (hook.getActions() == null || !hook.getActions().contains(ServiceHookAction.FETCH_FILES)) {
        continue;
      }

      String stepId = String.format("%s-%s", hook.getIdentifier(), ServiceHookAction.FETCH_FILES.getDisplayName());
      String script = hook.getStore() != null ? hook.getStore().getContent() : "";
      boolean shouldCapture = captureOverrideFiles && hook.getType() == ServiceHookType.POST_HOOK;
      String hookYaml = serviceHookImplicitStepHelper.buildRunStepYaml(
          stepId, script, ServiceHookAction.FETCH_FILES, hook.getType(), shouldCapture);

      String logKey = hook.getType() == ServiceHookType.PRE_HOOK
          ? ServiceStepUtility.generateLogKey(preHooksLogBaseKey, stepId)
          : ServiceStepUtility.generateLogKey(postHooksLogBaseKey, stepId);

      if (hook.getType() == ServiceHookType.PRE_HOOK) {
        preHookMetadataMap.put(
            stepId, ServiceHookMetadata.builder().hookYaml(hookYaml).logKey(logKey).stepId(stepId).build());
        preLogKeys.add(logKey);
        preCommandUnits.add(stepId);
      } else {
        postHookMetadataMap.put(
            stepId, ServiceHookMetadata.builder().hookYaml(hookYaml).logKey(logKey).stepId(stepId).build());
        postLogKeys.add(logKey);
        postCommandUnits.add(stepId);
      }
    }

    saveSweepingOutputs(ambiance, preHookMetadataMap, postHookMetadataMap, envVars, ServiceHookAction.FETCH_FILES);

    return Map.of("preLogKeys", preLogKeys, "preCommandUnits", preCommandUnits, "postLogKeys", postLogKeys,
        "postCommandUnits", postCommandUnits);
  }

  private Map<String, List<String>> handleTemplateManifestHooks(Ambiance ambiance, List<ServiceHookConfig> hooks,
      String logBaseKey, ParameterField<Map<String, ParameterField<JsonNode>>> envVars) {
    LinkedHashMap<String, ServiceHookMetadata> preHookMetadataMap = new LinkedHashMap<>();
    LinkedHashMap<String, ServiceHookMetadata> postHookMetadataMap = new LinkedHashMap<>();

    hooks = hooks.stream().sorted(Comparator.comparingInt(ServiceHookConfig::getOrder)).collect(Collectors.toList());

    // Template manifest hooks are stage-level siblings of service (e.g. .../steps/preTemplateHooks),
    // not children (.../steps/service/preTemplateHooks). Strip the service level to fix UI grouping.
    int lastSlash = logBaseKey.lastIndexOf('/');
    String hooksLogBaseKey = lastSlash >= 0 ? logBaseKey.substring(0, lastSlash) : logBaseKey;

    String preHooksLogBaseKey = appendChildStepLevel(hooksLogBaseKey, PRE_TEMPLATE_HOOKS_NODE_ID, ambiance);
    String postHooksLogBaseKey = appendChildStepLevel(hooksLogBaseKey, POST_TEMPLATE_HOOKS_NODE_ID, ambiance);

    for (ServiceHookConfig hook : hooks) {
      if (hook.getActions() == null || !hook.getActions().contains(ServiceHookAction.TEMPLATE_MANIFEST)) {
        continue;
      }

      String stepId =
          String.format("%s-%s", hook.getIdentifier(), ServiceHookAction.TEMPLATE_MANIFEST.getDisplayName());
      String script = hook.getStore() != null ? hook.getStore().getContent() : "";
      String hookYaml = serviceHookImplicitStepHelper.buildRunStepYaml(
          stepId, script, ServiceHookAction.TEMPLATE_MANIFEST, hook.getType(), false);

      String logKey = hook.getType() == ServiceHookType.PRE_HOOK
          ? ServiceStepUtility.generateLogKey(preHooksLogBaseKey, stepId)
          : ServiceStepUtility.generateLogKey(postHooksLogBaseKey, stepId);

      if (hook.getType() == ServiceHookType.PRE_HOOK) {
        preHookMetadataMap.put(
            stepId, ServiceHookMetadata.builder().hookYaml(hookYaml).logKey(logKey).stepId(stepId).build());
      } else {
        postHookMetadataMap.put(
            stepId, ServiceHookMetadata.builder().hookYaml(hookYaml).logKey(logKey).stepId(stepId).build());
      }
    }

    saveSweepingOutputs(
        ambiance, preHookMetadataMap, postHookMetadataMap, envVars, ServiceHookAction.TEMPLATE_MANIFEST);

    // Template manifest hooks run as internal chain links inside TemplatingStep (ASYNC_CHAIN), not as
    // separate plan nodes, so no log keys need to be registered.
    return Collections.emptyMap();
  }

  private void saveSweepingOutputs(Ambiance ambiance, LinkedHashMap<String, ServiceHookMetadata> preHookMetadataMap,
      LinkedHashMap<String, ServiceHookMetadata> postHookMetadataMap,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars, ServiceHookAction action) {
    if (isNotEmpty(preHookMetadataMap)) {
      ServiceHooksSweepingOutputBuilder builder =
          ServiceHooksSweepingOutput.builder().hookMetadataMap(preHookMetadataMap);
      if (ParameterField.isNotNull(envVars) && isNotEmpty(envVars.obtainValue())) {
        builder.envVars(envVars);
      }
      savePreHooksSweepingOutput(ambiance, builder.build(), action);
    }

    if (isNotEmpty(postHookMetadataMap)) {
      ServiceHooksSweepingOutputBuilder builder =
          ServiceHooksSweepingOutput.builder().hookMetadataMap(postHookMetadataMap);
      if (ParameterField.isNotNull(envVars) && isNotEmpty(envVars.obtainValue())) {
        builder.envVars(envVars);
      }
      savePostHooksSweepingOutput(ambiance, builder.build(), action);
    }
  }

  private void savePreHooksSweepingOutput(
      Ambiance ambiance, ServiceHooksSweepingOutput output, ServiceHookAction action) {
    if (action == ServiceHookAction.FETCH_FILES) {
      serviceStepSweepingOutputHelper.savePreFetchFilesHooksSweepingOutput(ambiance, output);
    } else if (action == ServiceHookAction.TEMPLATE_MANIFEST) {
      serviceStepSweepingOutputHelper.savePreTemplateHooksSweepingOutput(ambiance, output);
    }
  }

  private void savePostHooksSweepingOutput(
      Ambiance ambiance, ServiceHooksSweepingOutput output, ServiceHookAction action) {
    if (action == ServiceHookAction.FETCH_FILES) {
      serviceStepSweepingOutputHelper.savePostFetchFilesHooksSweepingOutput(ambiance, output);
    } else if (action == ServiceHookAction.TEMPLATE_MANIFEST) {
      serviceStepSweepingOutputHelper.savePostTemplateHooksSweepingOutput(ambiance, output);
    }
  }

  private boolean isNativeHelmWithSopsEnabled(Ambiance ambiance, ServiceConfig serviceConfig) {
    return ServiceType.HELM == serviceConfig.getServiceInfoConfig().getUses()
        && ciFeatureFlagService.isEnabled(
            FeatureName.CDS_HELM_IMPROVED_SOPS_SUPPORT_FOR_SERVICE_HOOKS, AmbianceUtils.getAccountId(ambiance));
  }

  private void handleManifestsPart(Ambiance ambiance, ServiceConfig serviceConfig, List<String> stepLogKeys,
      List<String> stepCommandUnits, String logBaseKey, ParameterField<Map<String, ParameterField<JsonNode>>> envVars,
      Map<String, Object> serviceManifestMap) {
    if (ServiceStepUtility.isManifestPresent(serviceConfig)) {
      List<ManifestConfig> manifestConfigs = serviceConfig.getServiceInfoConfig().getWith().getManifests().getSources();
      List<String> manifestIds = manifestConfigs.stream().map(ManifestConfig::getId).toList();
      String manifestStepLogBaseKey = serviceHookTaskHelper.isServiceHooksEnabled(ambiance)
          ? appendChildStepLevel(
                appendChildStepLevel(logBaseKey, MANIFEST_SECTION_NODE_ID, ambiance), MANIFESTS_NODE_ID, ambiance)
          : appendChildStepLevel(logBaseKey, MANIFESTS_NODE_ID, ambiance);

      Map<String, String> manifestCommanUnitLogKeyMap =
          ServiceStepUtility.getCommanUnitLogKeyMap(manifestStepLogBaseKey, manifestIds);

      saveManifestSweepingOutput(ambiance, manifestConfigs, manifestCommanUnitLogKeyMap, envVars, serviceManifestMap);

      boolean hasInvalidIds =
          manifestIds.stream().anyMatch(id -> !EntityIdentifierValidator.IDENTIFIER_PATTERN.matcher(id).matches());
      if (hasInvalidIds) {
        stepLogKeys.add(
            ServiceStepUtility.generateLogKey(manifestStepLogBaseKey, ManifestsStep.MANIFESTS_VALIDATION_UNIT));
        stepCommandUnits.add(ManifestsStep.MANIFESTS_VALIDATION_UNIT);
      }

      List<String> manifestsLogKeys = new ArrayList<>(manifestCommanUnitLogKeyMap.values());
      stepLogKeys.addAll(manifestsLogKeys);

      List<String> manifestsStepCommandUnits = new ArrayList<>(manifestCommanUnitLogKeyMap.keySet());
      stepCommandUnits.addAll(manifestsStepCommandUnits);
    }
  }

  private void handleConfigFilesPart(Ambiance ambiance, ServiceConfig serviceConfig, List<String> stepLogKeys,
      List<String> stepCommandUnits, String logBaseKey, ParameterField<Map<String, ParameterField<JsonNode>>> envVars,
      Map<String, Object> serviceConfigFileMap) {
    boolean hasConfigFiles = ServiceStepUtility.isConfigFilesPresent(serviceConfig);
    boolean hasStartupScript = ServiceStepUtility.isStartupScriptCodeFetchRequired(serviceConfig);
    if (!hasConfigFiles && !hasStartupScript) {
      return;
    }
    ServiceSpec with = serviceConfig.getServiceInfoConfig().getWith();
    List<ConfigFile> configFiles = new ArrayList<>();
    if (hasConfigFiles) {
      configFiles.addAll(with.getConfigFiles());
    }
    if (hasStartupScript) {
      configFiles.add(SpotStartupScriptHelper.buildConfigFile(((SpotServiceSpec) with).getStartupScript()));
    }
    List<String> configFileIds = configFiles.stream().map(ConfigFile::getId).toList();
    String configFilesStepLogBaseKey = appendChildStepLevel(logBaseKey, CONFIG_FILES_NODE_ID, ambiance);

    // logKey is stored on ConfigFileMetadata for internal routing; not forwarded to the parent task.
    Map<String, String> configFileCommandUnitLogKeyMap =
        ServiceStepUtility.getCommanUnitLogKeyMap(configFilesStepLogBaseKey, configFileIds);
    saveConfigFilesSweepingOutput(ambiance, configFiles, configFileCommandUnitLogKeyMap, envVars, serviceConfigFileMap);
  }

  private String appendChildStepLevel(String baseLogKey, String childStepName, Ambiance ambiance) {
    int lastLevelIndex = baseLogKey.lastIndexOf("/level");
    int currentLevel = -1;
    if (lastLevelIndex != -1) {
      String levelPart = baseLogKey.substring(lastLevelIndex + 6, baseLogKey.indexOf(':', lastLevelIndex));
      currentLevel = Integer.parseInt(levelPart);
    }
    String childStepLevel;
    if (AmbianceUtils.shouldSimplifyLogBaseKey(ambiance)) {
      childStepLevel = "/" + childStepName;
    } else {
      childStepLevel = "/level" + (currentLevel + 1) + ":" + childStepName;
    }
    return baseLogKey + childStepLevel;
  }

  private void handleArtifactsPart(Ambiance ambiance, String logBaseKey, ProcessedServiceResult result,
      List<String> stepLogKeys, List<String> stepCommandUnits,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars) {
    ServiceConfig serviceConfig = result.getServiceConfig();
    if (ServiceStepUtility.isArtifactSourcePresent(serviceConfig)) {
      ArtifactWrapper artifactWrapper = serviceConfig.getServiceInfoConfig().getWith().getArtifacts();
      Map<String, ArtifactConfig> sidecarsArtifactsMap = ArtifactStepUtils.getSidecarsArtifactsMap(artifactWrapper);
      Optional<ArtifactConfig> primaryArtifact = ArtifactStepUtils.getPrimaryArtifact(artifactWrapper);

      ArrayList<String> artifactIds = new ArrayList<>(sidecarsArtifactsMap.keySet());
      primaryArtifact.ifPresent(artifactConfig -> artifactIds.add(artifactConfig.getId()));

      String artifactsStepLogBaseKey = appendChildStepLevel(logBaseKey, ARTIFACTS_NODE_ID, ambiance);
      Map<String, String> artifactCommandUnitLogKeyMap =
          ServiceStepUtility.getCommanUnitLogKeyMap(artifactsStepLogBaseKey, artifactIds);

      Map<String, Object> artifactMap = result.getArtifactMap();
      saveArtifactSweepingOutput(ambiance, new ArrayList<>(sidecarsArtifactsMap.values()), artifactCommandUnitLogKeyMap,
          primaryArtifact.orElse(null), envVars, artifactMap);

      List<String> artifactLogKeys = new ArrayList<>(artifactCommandUnitLogKeyMap.values());
      List<String> artifactStepsCommandUnits = new ArrayList<>(artifactCommandUnitLogKeyMap.keySet());
      stepLogKeys.addAll(artifactLogKeys);
      stepCommandUnits.addAll(artifactStepsCommandUnits);
    }
  }

  private void saveArtifactSweepingOutput(Ambiance ambiance, List<ArtifactConfig> artifactsMap,
      Map<String, String> artifactCommandUnitLogKeyMap, ArtifactConfig primaryArtifact,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars, Map<String, Object> serviceArtifactMap) {
    Map<String, ArtifactMetadata> artifactMetadatMap = new HashMap<>();
    if (primaryArtifact != null) {
      Map<String, Object> artifactMap = getArtifactMapForSweepingOutput(primaryArtifact.getId(), serviceArtifactMap);
      String yaml = getArtifactYamlForSweepingOutput(primaryArtifact, serviceArtifactMap);
      artifactMetadatMap.put(primaryArtifact.getId(),
          ArtifactMetadata.builder()
              .yaml(yaml)
              .templatized(isArtifactTemplatized(artifactMap))
              .logKey(artifactCommandUnitLogKeyMap.get(primaryArtifact.getId()))
              .build());
    }
    for (ArtifactConfig artifactConfig : artifactsMap) {
      if (artifactConfig.isSidecar()) {
        Map<String, Object> artifactMap = getArtifactMapForSweepingOutput(artifactConfig.getId(), serviceArtifactMap);
        String yaml = getArtifactYamlForSweepingOutput(artifactConfig, serviceArtifactMap);
        ArtifactMetadata artifactMetadata = ArtifactMetadata.builder()
                                                .yaml(yaml)
                                                .templatized(isArtifactTemplatized(artifactMap))
                                                .logKey(artifactCommandUnitLogKeyMap.get(artifactConfig.getId()))
                                                .build();
        artifactMetadatMap.put(artifactConfig.getId(), artifactMetadata);
      }
    }

    ArtifactsSweepingOutputBuilder artifactsSweepingOutputBuilder =
        ArtifactsSweepingOutput.builder()
            .artifactsMetadataMap(artifactMetadatMap)
            .primaryArtifactId(primaryArtifact == null ? StringUtils.EMPTY : primaryArtifact.getId());
    if (ParameterField.isNotNull(envVars) && isNotEmpty(envVars.obtainValue())) {
      artifactsSweepingOutputBuilder.envVars(envVars);
    }

    serviceStepSweepingOutputHelper.saveServiceArtifactsSweepingOutput(
        ambiance, artifactsSweepingOutputBuilder.build());
  }

  /**
   * Returns the artifact map for the given artifact id from service artifact map, or null if not present.
   */
  private Map<String, Object> getArtifactMapForSweepingOutput(
      String artifactId, Map<String, Object> serviceArtifactMap) {
    if (serviceArtifactMap == null) {
      return null;
    }
    Object entry = serviceArtifactMap.get(artifactId);
    if (entry instanceof Map) {
      @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) entry;
      return map;
    }
    return null;
  }

  /**
   * Returns the YAML string for sweeping output from artifact config and service artifact map.
   * Uses {@link #getArtifactMapForSweepingOutput} to resolve the artifact map, then builds the YAML string.
   */
  private String getArtifactYamlForSweepingOutput(
      ArtifactConfig artifactConfig, Map<String, Object> serviceArtifactMap) {
    Map<String, Object> artifactMap = getArtifactMapForSweepingOutput(artifactConfig.getId(), serviceArtifactMap);
    if (artifactMap == null) {
      return YamlUtils.writeYamlString(artifactConfig);
    }
    ArtifactConfig configForYaml = artifactConfig;
    if (artifactMap.containsKey("inputs")) {
      Object inputsObject = artifactMap.get("inputs");
      if (inputsObject instanceof Map) {
        @SuppressWarnings("unchecked") Map<String, Object> inputsMap = (Map<String, Object>) inputsObject;
        configForYaml = artifactConfig.toBuilder().inputs(inputsMap).build();
      }
    }
    return YamlUtils.writeYamlString(configForYaml);
  }

  private static boolean isArtifactTemplatized(Map<String, Object> artifactMap) {
    return artifactMap != null && artifactMap.containsKey("action");
  }

  /**
   * Returns the manifest map for the given manifest id from service manifest map, or null if not present.
   */
  private Map<String, Object> getManifestMapForSweepingOutput(
      String manifestId, Map<String, Object> serviceManifestMap) {
    if (serviceManifestMap == null) {
      return null;
    }
    Object entry = serviceManifestMap.get(manifestId);
    if (entry instanceof Map) {
      @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) entry;
      return map;
    }
    return null;
  }

  /**
   * Returns the YAML string for sweeping output from manifest config and service manifest map.
   * Uses {@link #getManifestMapForSweepingOutput} to resolve the manifest map, then builds the YAML string.
   */
  private String getManifestYamlForSweepingOutput(
      ManifestConfig manifestConfig, Map<String, Object> serviceManifestMap) {
    Map<String, Object> manifestMap = getManifestMapForSweepingOutput(manifestConfig.getId(), serviceManifestMap);
    if (manifestMap == null) {
      return YamlUtils.writeYamlString(manifestConfig);
    }
    if (manifestMap.containsKey("inputs")) {
      Object inputsObject = manifestMap.get("inputs");
      if (inputsObject instanceof Map) {
        @SuppressWarnings("unchecked") Map<String, Object> inputsMap = (Map<String, Object>) inputsObject;
        manifestConfig.setInputs(inputsMap);
      }
    }

    return YamlUtils.writeYamlString(manifestConfig);
  }

  private static boolean isManifestTemplatized(Map<String, Object> manifestMap) {
    return manifestMap != null && manifestMap.containsKey("action");
  }

  private Map<String, Object> getConfigFileMapForSweepingOutput(
      String configFileId, Map<String, Object> serviceConfigFileMap) {
    if (serviceConfigFileMap == null) {
      return null;
    }
    Object entry = serviceConfigFileMap.get(configFileId);
    if (entry instanceof Map) {
      @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) entry;
      return map;
    }
    return null;
  }

  private String getConfigFileYamlForSweepingOutput(ConfigFile configFile, Map<String, Object> serviceConfigFileMap) {
    Map<String, Object> configFileMap = getConfigFileMapForSweepingOutput(configFile.getId(), serviceConfigFileMap);
    if (configFileMap == null) {
      return YamlUtils.writeYamlString(configFile);
    }
    ConfigFile configForYaml = configFile;
    if (configFileMap.containsKey("inputs")) {
      Object inputsObject = configFileMap.get("inputs");
      if (inputsObject instanceof Map) {
        @SuppressWarnings("unchecked") Map<String, Object> inputsMap = (Map<String, Object>) inputsObject;
        configForYaml = configFile.toBuilder().inputs(inputsMap).build();
      }
    }
    return YamlUtils.writeYamlString(configForYaml);
  }

  private static boolean isConfigFileTemplatized(ConfigFile configFile) {
    return isNotEmpty(configFile.getAction());
  }

  private Optional<ArtifactsOutcome> getArtifactStepOutcome(Ambiance ambiance) {
    OptionalSweepingOutput artifactsOutcomeOutput =
        serviceStepSweepingOutputHelper.fetchArtifactsOutcomeSweepingOutput(ambiance);
    if (artifactsOutcomeOutput.isFound()) {
      ArtifactsOutcomeSweepingOutput artifactsOutcome =
          (ArtifactsOutcomeSweepingOutput) artifactsOutcomeOutput.getOutput();
      return Optional.of(artifactsOutcome.getArtifactsOutcome());
    }
    return Optional.empty();
  }

  private static void addUnitProgress(List<UnitProgress> unitProgresses, String commandUnit,
      Map<String, UnitStatus> statuses, long serviceStepStartTs, long stepEndTs) {
    // No per-id status recorded (e.g. step skipped, sweeping output missing, or id not produced
    // by the child step) -> mark SKIPPED instead of falsely reporting SUCCESS.
    UnitStatus status = isEmpty(statuses) ? null : statuses.get(commandUnit);
    if (status == null) {
      status = UnitStatus.SKIPPED;
    }
    unitProgresses.add(UnitProgress.newBuilder()
                           .setStatus(status)
                           .setUnitName(commandUnit)
                           .setStartTime(serviceStepStartTs)
                           .setEndTime(stepEndTs)
                           .build());
  }

  private Map<String, UnitStatus> fetchManifestUnitStatuses(Ambiance ambiance) {
    return readUnitStatuses(serviceStepSweepingOutputHelper.fetchManifestUnitStatusesSweepingOutput(ambiance));
  }

  private Map<String, UnitStatus> fetchArtifactUnitStatuses(Ambiance ambiance) {
    return readUnitStatuses(serviceStepSweepingOutputHelper.fetchArtifactUnitStatusesSweepingOutput(ambiance));
  }

  // Reserved for future use: reads per-config-file fetch statuses written by ConfigFilesStep.
  // Not called until per-config-file rows are wired to the UI timeline.
  private Map<String, UnitStatus> fetchConfigFileUnitStatuses(Ambiance ambiance) {
    return readUnitStatuses(serviceStepSweepingOutputHelper.fetchConfigFileUnitStatusesSweepingOutput(ambiance));
  }

  private static Map<String, UnitStatus> readUnitStatuses(OptionalSweepingOutput out) {
    if (out.isFound() && out.getOutput() instanceof ServiceStepUnitStatusSweepingOutput o
        && isNotEmpty(o.getStatuses())) {
      return o.getStatuses();
    }
    return Collections.emptyMap();
  }

  private void saveManifestSweepingOutput(Ambiance ambiance, List<ManifestConfig> manifestConfigs,
      Map<String, String> manifestCommanUnitLogKeyMap, ParameterField<Map<String, ParameterField<JsonNode>>> envVars,
      Map<String, Object> serviceManifestMap) {
    LinkedHashMap<String, ManifestMetadata> manifestMetadataMap = new LinkedHashMap<>();
    for (ManifestConfig manifestConfig : manifestConfigs) {
      Map<String, Object> manifestMap = getManifestMapForSweepingOutput(manifestConfig.getId(), serviceManifestMap);
      String manifestYaml = getManifestYamlForSweepingOutput(manifestConfig, serviceManifestMap);
      ManifestMetadata manifestMetadata = ManifestMetadata.builder()
                                              .manifestYaml(manifestYaml)
                                              .logKey(manifestCommanUnitLogKeyMap.get(manifestConfig.getId()))
                                              .templatized(isManifestTemplatized(manifestMap))
                                              .build();
      manifestMetadataMap.put(manifestConfig.getId(), manifestMetadata);
    }
    ManifestsSweepingOutputBuilder manifestsSweepingOutputBuilder =
        ManifestsSweepingOutput.builder().manifestMetadataMap(manifestMetadataMap);
    if (ParameterField.isNotNull(envVars) && isNotEmpty(envVars.obtainValue())) {
      manifestsSweepingOutputBuilder.envVars(envVars);
    }
    serviceStepSweepingOutputHelper.saveServiceManifestsSweepingOutput(
        ambiance, manifestsSweepingOutputBuilder.build());
  }

  private void saveConfigFilesSweepingOutput(Ambiance ambiance, List<ConfigFile> configFiles,
      Map<String, String> configFileCommandUnitLogKeyMap, ParameterField<Map<String, ParameterField<JsonNode>>> envVars,
      Map<String, Object> serviceConfigFileMap) {
    LinkedHashMap<String, ConfigFileMetadata> configFilesMetadataMap = new LinkedHashMap<>();
    for (ConfigFile configFile : configFiles) {
      String configFileYaml = getConfigFileYamlForSweepingOutput(configFile, serviceConfigFileMap);
      ConfigFileMetadata configFileMetadata = ConfigFileMetadata.builder()
                                                  .configFileYaml(configFileYaml)
                                                  .logKey(configFileCommandUnitLogKeyMap.get(configFile.getId()))
                                                  .templatized(isConfigFileTemplatized(configFile))
                                                  .build();
      configFilesMetadataMap.put(configFile.getId(), configFileMetadata);
    }
    ConfigFilesSweepingOutputBuilder configFilesSweepingOutputBuilder =
        ConfigFilesSweepingOutput.builder().configFilesMetadataMap(configFilesMetadataMap);
    if (ParameterField.isNotNull(envVars) && isNotEmpty(envVars.obtainValue())) {
      configFilesSweepingOutputBuilder.envVars(envVars);
    }
    serviceStepSweepingOutputHelper.saveServiceConfigFilesSweepingOutput(
        ambiance, configFilesSweepingOutputBuilder.build());
  }

  private void saveServiceMetadataOutput(
      Ambiance ambiance, ServiceEntityMetadata serviceEntityMetadata, String serviceType) {
    UnifiedServiceOutcome outcome = UnifiedServiceOutcome.builder()
                                        .identifier(serviceEntityMetadata.getIdentifier())
                                        .name(serviceEntityMetadata.getName())
                                        .tags(serviceEntityMetadata.getTags())
                                        .description(serviceEntityMetadata.getDescription())
                                        .type(serviceType)
                                        .build();

    serviceStepSweepingOutputHelper.saveServiceMetadataOutput(ambiance, outcome);
  }

  @VisibleForTesting
  public void callOpaForServiceRuntimeContext(
      Ambiance ambiance, List<StepResponse.StepOutcome> stepOutcomes, StepResponse stepResponse) {
    try {
      unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);
    } catch (PolicyEvaluationFailureException ex) {
      log.error("OPA policy evaluation failed for service step", ex);
      throw ex;
    }
  }
}