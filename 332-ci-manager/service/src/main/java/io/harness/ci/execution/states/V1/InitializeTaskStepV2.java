/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.FeatureName.CIE_ENABLED_RBAC;
import static io.harness.beans.FeatureName.CIE_HOSTED_VMS;
import static io.harness.beans.FeatureName.CI_ENABLE_VM_DELEGATE_SELECTOR;
import static io.harness.beans.FeatureName.CODE_ENABLED;
import static io.harness.beans.outcomes.LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME;
import static io.harness.beans.outcomes.VmDetailsOutcome.VM_DETAILS_OUTCOME;
import static io.harness.beans.steps.outcome.CIOutcomeNames.INIT_STEP_OUTCOME;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CI_INITIALIZATION_SUCCEEDED;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.INITIALIZE_EXECUTION;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.INIT_ENV_VARS;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.TASK_SELECTORS;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.UNIQUE_STEP_IDENTIFIERS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.HARNESS_PC_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.MAXIMUM_EXPANSION_LIMIT;
import static io.harness.ci.commonconstants.CIExecutionConstants.MAXIMUM_EXPANSION_LIMIT_FREE_ACCOUNT;
import static io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils.getAllSteps;
import static io.harness.ci.execution.states.InitializeTaskStep.TASK_BUFFER_TIMEOUT_MILLIS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.delegate.beans.ci.CIInitializeTaskParams.Type.DLITE_VM;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_RUNTIME_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_RUNTIME_PERMISSION;
import static io.harness.runner.request.helpers.RunnerRequestBuilderHelper.isCIStage;
import static io.harness.runner.request.helpers.RunnerRequestBuilderHelper.isSecurityStage;
import static io.harness.steps.StepUtils.buildAbstractions;
import static io.harness.steps.StepUtils.generateLogAbstractions;

import static software.wings.beans.TaskType.TASKS_FROM_RUNNER;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

import io.harness.EntityType;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.StepExecutionParameters;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.dependencies.ServiceDependency;
import io.harness.beans.environment.ServiceDefinitionInfo;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.outcomes.DependencyOutcome;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome.VmDetailsOutcomeBuilder;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CILogKeyMetadata;
import io.harness.beans.steps.CILogKeyMetadata.CILogKeyMetadataKeys;
import io.harness.beans.steps.CIStageTelemetryData;
import io.harness.beans.steps.CITelemetryInfo;
import io.harness.beans.steps.outcome.InitStepOutcome;
import io.harness.beans.steps.stepinfo.CIStepInfo;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.StepNodeV1;
import io.harness.beans.sweepingoutputs.CIInitializationSucceededSweepingOutput;
import io.harness.beans.sweepingoutputs.InitializeEnvSweepingOutput;
import io.harness.beans.sweepingoutputs.InitializeExecutionSweepingOutput;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.LocalVmDriverType;
import io.harness.beans.sweepingoutputs.TaskSelectorSweepingOutput;
import io.harness.beans.sweepingoutputs.UniqueStepIdentifiersSweepingOutput;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.buildIntelligence.BuildIntelligence;
import io.harness.beans.yaml.extended.cache.Caching;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.runtime.CloudRuntime.CloudRuntimeImageSpec;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.enforcement.CIBuildEnforcer;
import io.harness.ci.executable.CiAsyncExecutable;
import io.harness.ci.execution.buildstate.BuildSetupUtils;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.execution.BackgroundTaskUtility;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.execution.integrationstage.CILicenseUtils;
import io.harness.ci.execution.integrationstage.DockerInitializeTaskParamsBuilder;
import io.harness.ci.execution.integrationstage.K8InitializeServiceUtils;
import io.harness.ci.execution.integrationstage.VmInitializeTaskParamsBuilder;
import io.harness.ci.execution.integrationstage.utils.InfraInfoUtils;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.ci.execution.integrationstage.vm.intfc.VmInitializeUtils;
import io.harness.ci.execution.utils.CIStagePlanCreationUtils;
import io.harness.ci.execution.utils.HostedVmSecretResolver;
import io.harness.ci.execution.utils.ci.CIStepInfoUtils;
import io.harness.ci.execution.validation.CIAccountValidationService;
import io.harness.ci.execution.validation.CIYAMLSanitizationService;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.license.AITCILicenseBypassEvaluator;
import io.harness.ci.metrics.CIObservabilityConstants;
import io.harness.ci.metrics.ExecutionMetricsService;
import io.harness.ci.metrics.helper.CIMetricsHelper;
import io.harness.ci.stepdetails.GenericStepV1DelegateTaskInfo;
import io.harness.ci.stepdetails.InitStepV2DelegateTaskInfo;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.data.encoding.EncodingUtils;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.LocalExecuteTaskSpec;
import io.harness.delegate.RunnerRequest;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.SerializedResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.CITaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.CIContainerStatus;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CiK8sTaskResponse;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponseFromRunner;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8ServicePodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.VmServiceStatus;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest;
import io.harness.delegate.beans.ci.vm.runner.SetupVmRequest;
import io.harness.delegate.beans.ci.vm.steps.VmServiceDependency;
import io.harness.delegate.beans.ci.vm.taskparams.CIVmInitializeTaskParams;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.KubernetesClusterConfigDTO;
import io.harness.delegate.task.HDelegateTask;
import io.harness.delegate.task.ScheduleTaskRequest;
import io.harness.delegate.task.ScheduleTaskResponse;
import io.harness.delegate.task.taskrunner.TaskRunnerTaskResponse;
import io.harness.encryption.Scope;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.ngexception.CILiteEngineException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.dto.LicensesWithSummaryDTO;
import io.harness.logging.AutoLogContext;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.persistence.HPersistence;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.inject.InjectUtils;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.repositories.CIExecutionRepository;
import io.harness.repositories.CIStageTelemetryRepository;
import io.harness.repositories.StepExecutionParametersRepository;
import io.harness.runner.request.builder.RunnerRequestBuilder;
import io.harness.runner.request.builder.constants.RunnerRequestBuilderConstants;
import io.harness.runner.request.helpers.RunnerRequestBuilderHelper;
import io.harness.runnercommons.logging.TransactionalTaskLogContext;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.steps.StepUtils;
import io.harness.steps.matrix.ExpandedExecutionWrapperInfo;
import io.harness.steps.matrix.StrategyExpansionData;
import io.harness.steps.matrix.StrategyHelper;
import io.harness.tasks.FailureResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.core.variables.NGVariable;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CI)
public class InitializeTaskStepV2 extends CiAsyncExecutable {
  public static final String MATRIX_EXPRESSION = "<+matrix.";
  @Inject private ExceptionManager exceptionManager;
  @Inject private AccountClient accountClient;
  @Inject private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  @Inject CIBuildEnforcer buildEnforcer;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private CIFeatureFlagService ciFeatureFlagService;
  @Inject(optional = true) @Nullable private NGSettingsClient settingsClient;
  @Inject private BuildSetupUtils buildSetupUtils;
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private K8InitializeServiceUtils k8InitializeServiceUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;
  @Inject private DockerInitializeTaskParamsBuilder dockerInitializeTaskParamsBuilder;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private CIYAMLSanitizationService sanitizationService;
  @Inject private CIAccountValidationService validationService;
  @Inject private BackgroundTaskUtility backgroundTaskUtility;
  @Inject private CILicenseService ciLicenseService;
  @Inject private StrategyHelper strategyHelper;
  @Inject private CIAccountExecutionMetadataRepository accountExecutionMetadataRepository;

  @Inject private HsqsClientService hsqsClientService;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private CIStagePlanCreationUtils ciStagePlanCreationUtils;

  @Inject SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @Inject QueueExecutionUtils queueExecutionUtils;
  @Inject private StepExecutionParametersRepository stepExecutionParametersRepository;
  @Inject CIExecutionRepository ciExecutionRepository;

  @Inject private ExecutionMetricsService executionMetricsService;
  @Inject private HPersistence persistence;
  @Inject private CILicenseUtils ciLicenseUtils;
  @Inject private AITCILicenseBypassEvaluator aitBypassEvaluator;
  @Inject private AccessControlClient accessControlClient;
  @Inject private RunnerRequestBuilder runnerRequestBuilder;
  @Inject private VmInitializeUtils vmInitializeUtils;
  @Inject protected CIStageTelemetryRepository ciStageTelemetryRepository;
  @Inject private HostedVmSecretResolver hostedVmSecretResolver;
  private static final String STEP_STATUS = "ci_active_step_execution_count";
  private static final String STEP_TIME_COUNT = "ci_step_execution_time";
  private static final String IMPLICIT_CACHE_STEP = "implicit_restore_cache";

  private static final String DEPENDENCY_OUTCOME = "dependencies";
  private static final String INITIALIZE = "initialize";

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return null;
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    String logKey = getLogKey(ambiance);
    String taskId;
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String runTime = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String stageRuntimeId = AmbianceUtils.getStageRuntimeIdAmbiance(ambiance);
    InitializeStepInfo initializeStepInfo = (InitializeStepInfo) stepParameters.getSpec();

    if (HarnessYamlVersion.isV1(stepParameters.getVersion())) {
      checkCDEntitiesRuntimeAccess(ambiance, initializeStepInfo);
    }

    stepExecutionParametersRepository.save(StepExecutionParameters.builder()
                                               .accountId(accountId)
                                               .runTimeId(runTime)
                                               .stageRunTimeId(stageRuntimeId)
                                               .stepParameters(RecastOrchestrationUtils.toJson(stepParameters))
                                               .build());
    // Check and add log key to execution ID
    UpdateOperations<CILogKeyMetadata> updateOperations =
        persistence.createUpdateOperations(CILogKeyMetadata.class)
            .setOnInsert(CILogKeyMetadataKeys.stageExecutionId, ambiance.getStageExecutionId())
            .push(CILogKeyMetadataKeys.logKeys, List.of(logKey));
    Query<CILogKeyMetadata> upsertQuery =
        persistence.createQuery(CILogKeyMetadata.class, excludeAuthority)
            .filter(CILogKeyMetadataKeys.stageExecutionId, ambiance.getStageExecutionId());
    persistence.upsert(upsertQuery, updateOperations);
    // Measure CI-manager-side dispatch latency for the provision_infra (init) call (phase=submit).
    String initInfraType = getInfraTypeYaml(initializeStepInfo);
    long submitStartMs = System.currentTimeMillis();
    try {
      taskId = executeBuild(ambiance, stepParameters);
      recordProvisionInfra(ambiance, accountId, initInfraType, CIObservabilityConstants.OUTCOME_SUCCESS,
          CIObservabilityConstants.PHASE_SUBMIT, System.currentTimeMillis() - submitStartMs);
    } catch (Exception t) {
      recordProvisionInfra(ambiance, accountId, initInfraType, CIObservabilityConstants.OUTCOME_SYSTEM_FAILURE,
          CIObservabilityConstants.PHASE_SUBMIT, System.currentTimeMillis() - submitStartMs);
      log.error("Exception while executing initialise step", t);
      throw t;
    }
    AsyncExecutableResponse.Builder responseBuilder =
        AsyncExecutableResponse.newBuilder().addCallbackIds(taskId).addAllLogKeys(
            CollectionUtils.emptyIfNull(singletonList(logKey)));

    // TODO - InitStepV2DelegateTaskInfo can be removed post complete migration to GenericStepV1DelegateTaskInfo
    InitStepV2DelegateTaskInfo initStepV2DelegateTaskInfo =
        InitStepV2DelegateTaskInfo.builder().taskID(taskId).taskName("INITIALIZATION_PHASE").build();

    GenericStepV1DelegateTaskInfo genericStepV1DelegateTaskInfo =
        GenericStepV1DelegateTaskInfo.builder().taskID(taskId).taskName("INITIALIZATION_PHASE").build();

    sdkGraphVisualizationDataService.publishStepDetailInformation(
        ambiance, initStepV2DelegateTaskInfo, "initStepV2DelegateTaskInfo");
    sdkGraphVisualizationDataService.publishStepDetailInformation(
        ambiance, genericStepV1DelegateTaskInfo, "genericStepV1DelegateTaskInfo");
    log.info("Submitted initialise step request for taskid {}", taskId);

    return responseBuilder.build();
  }

  private void checkCDEntitiesRuntimeAccess(Ambiance ambiance, InitializeStepInfo initializeStepInfo) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    if (ParameterField.isNotNull(initializeStepInfo.getServiceRef())
        && isNotEmpty(initializeStepInfo.getServiceRef().getValue())
        && !initializeStepInfo.getServiceRef().isExpression()) {
      checkEntityRuntimeAccessOrThrow((String) initializeStepInfo.getServiceRef().fetchFinalValue(), projectIdentifier,
          orgIdentifier, accountId, NGResourceType.SERVICE, SERVICE_RUNTIME_PERMISSION,
          "unable to access service with identifier : ");
    }
    if (ParameterField.isNotNull(initializeStepInfo.getEnvRef())
        && isNotEmpty(initializeStepInfo.getEnvRef().getValue()) && !initializeStepInfo.getEnvRef().isExpression()) {
      checkEntityRuntimeAccessOrThrow((String) initializeStepInfo.getEnvRef().fetchFinalValue(), projectIdentifier,
          orgIdentifier, accountId, NGResourceType.ENVIRONMENT, ENVIRONMENT_RUNTIME_PERMISSION,
          "unable to access environment with identifier : ");
    }
  }

  private void checkEntityRuntimeAccessOrThrow(String entityRef, String projectIdentifier, String orgIdentifier,
      String accountId, String resourceType, String permission, String errorMessage) {
    IdentifierRef entityIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(entityRef, accountId, orgIdentifier, projectIdentifier);
    if (entityIdentifierRef.getIdentifier().contains(MATRIX_EXPRESSION)) {
      throw new InvalidRequestException(
          "multi deployment set up is wrong, could not find service/environment candidates for deployment");
    }
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(entityIdentifierRef.getAccountIdentifier(), entityIdentifierRef.getOrgIdentifier(),
            entityIdentifierRef.getProjectIdentifier()),
        Resource.of(resourceType, entityIdentifierRef.getIdentifier()), permission, errorMessage + entityRef);
  }

  public boolean shouldRouteStageToRunner(
      Ambiance ambiance, InitializeStepInfo initializeStepInfo, boolean isFreePlan) {
    Infrastructure infra = initializeStepInfo.getInfrastructure();
    if (infra == null) {
      return false;
    }
    String accountId = AmbianceUtils.getAccountId(ambiance);

    if (Infrastructure.Type.HOSTED_VM.equals(infra.getType())) {
      return true;
    }

    // Check if routing to runner is disabled via stage variables
    if (isRouteToRunnerStageVarDisabled(initializeStepInfo.getVariables())) {
      return false;
    }

    boolean isCIStage = isCIStage(ambiance);
    boolean isStoStage = isSecurityStage(ambiance);
    boolean routeToRunnerStageVarEnabled = isRouteToRunnerStageVarEnabled(initializeStepInfo.getVariables());
    if (Infrastructure.Type.DOCKER.equals(infra.getType())) {
      // Always route to runner if the feature flag is enabled
      if (routeToRunnerStageVarEnabled) {
        return true;
      }

      // For CI or Security stages, check if runner should be used based on feature flags
      if ((isCIStage || isStoStage)
          && ciFeatureFlagService.isEnabled(
              FeatureName.CI_V0_LOCAL_BUILDS_USE_RUNNER, AmbianceUtils.getAccountId(ambiance))) {
        // this block of code is temporary until local runner has full feature parity
        return !shouldUseDelegate(AmbianceUtils.getAccountId(ambiance), initializeStepInfo);
      }

      // Default to delegate for non-CI/non-Security stages or when feature flag is disabled
      return false;
    }
    if (Infrastructure.Type.KUBERNETES_DIRECT.equals(infra.getType())) {
      return routeToRunnerStageVarEnabled
          || ((isCIStage || isStoStage)
              && ciFeatureFlagService.isEnabled(FeatureName.CI_V0_K8S_BUILDS_USE_RUNNER, accountId));
    }
    if (Infrastructure.Type.VM.equals(infra.getType())) {
      return routeToRunnerStageVarEnabled
          || ((isCIStage || isStoStage)
              && ciFeatureFlagService.isEnabled(FeatureName.CI_V0_VM_BUILDS_USE_RUNNER, accountId));
    }
    return false;
  }

  // checks if the route to runner stage variable is enabled; works for all stages
  @VisibleForTesting
  public boolean isRouteToRunnerStageVarEnabled(List<NGVariable> variables) {
    return CIStepInfoUtils.checkStageVarState(
        variables, RunnerRequestBuilderHelper.HARNESS_CI_INTERNAL_ROUTE_TO_RUNNER, "true");
  }

  @VisibleForTesting
  public boolean isRouteToRunnerStageVarDisabled(List<NGVariable> variables) {
    return CIStepInfoUtils.checkStageVarState(
        variables, RunnerRequestBuilderHelper.HARNESS_CI_INTERNAL_ROUTE_TO_RUNNER, "false");
  }

  @VisibleForTesting
  public boolean isLocalVmsStageVarEnabled(List<NGVariable> variables) {
    return CIStepInfoUtils.isLocalVmsStageVarEnabled(variables);
  }

  @VisibleForTesting
  public boolean isLocalVmsStageVarDisabled(List<NGVariable> variables) {
    return CIStepInfoUtils.isLocalVmsStageVarDisabled(variables);
  }

  @VisibleForTesting
  public LocalVmDriverType resolveLocalVmDriverType(Ambiance ambiance, InitializeStepInfo initializeStepInfo) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

    // FF must be enabled
    if (!ciFeatureFlagService.isEnabled(FeatureName.CI_ENABLE_LOCAL_VMS, accountId)) {
      return LocalVmDriverType.NONE;
    }

    // Must be Docker infra type
    if (!Infrastructure.Type.DOCKER.equals(initializeStepInfo.getInfrastructure().getType())) {
      return LocalVmDriverType.NONE;
    }

    // Stage variable override takes priority over setting
    if (isLocalVmsStageVarEnabled(initializeStepInfo.getVariables())) {
      return LocalVmDriverType.TART_VM;
    }
    if (isLocalVmsStageVarDisabled(initializeStepInfo.getVariables())) {
      return LocalVmDriverType.NONE;
    }

    // Fall back to NG Setting
    if (settingsClient != null) {
      try {
        String settingValue = NGRestUtils
                                  .getResponse(settingsClient.getSetting(
                                      SettingIdentifiers.CI_ENABLE_LOCAL_VMS, accountId, orgId, projectId))
                                  .getValue();
        if ("true".equalsIgnoreCase(settingValue)) {
          return LocalVmDriverType.TART_VM;
        }
      } catch (Exception e) {
        log.error("Setting {} is not found", SettingIdentifiers.CI_ENABLE_LOCAL_VMS, e);
      }
    }
    return LocalVmDriverType.NONE;
  }

  public String executeBuild(Ambiance ambiance, StepBaseParameters stepParameters) {
    log.info("start executeAsyncAfterRbac for initialize step async");
    InitializeStepInfo initializeStepInfo = (InitializeStepInfo) stepParameters.getSpec();

    try {
      initStageTelemetryData(initializeStepInfo, ambiance);
    } catch (Exception ex) {
      log.debug("Error while Initializing stage telemetry data", ex);
    }

    // save unique step identifiers from executionWrapperConfig, this will be used to fetch the correct artifact outcome
    // this should happen after strategy population
    consumeUniqueStepIdentifiers(initializeStepInfo, ambiance);

    String moduleType = AmbianceUtils.getStageModuleType(ambiance);
    String logPrefix = getLogPrefix(ambiance);
    LicensesWithSummaryDTO licenseSummary = ciLicenseService.getLicenseSummary(
        AmbianceUtils.getAccountId(ambiance), moduleType, ambiance.getMetadata().getPrincipalInfo());
    if (licenseSummary == null) {
      throw new CIStageExecutionException("Please enable CI free plan or reach out to support.");
    }
    boolean isCIFreeEdition = licenseSummary.getEdition() == Edition.FREE;
    ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
        isCIFreeEdition, AmbianceUtils.getAccountId(ambiance), initializeStepInfo.getInfrastructure(), moduleType);

    boolean shouldRouteStageToRunner = shouldRouteStageToRunner(ambiance, initializeStepInfo, isCIFreeEdition);
    LocalVmDriverType localVmDriverType = resolveLocalVmDriverType(ambiance, initializeStepInfo);
    CIInitializeTaskParams buildSetupTaskParams = buildSetupUtils.getBuildSetupTaskParams(
        initializeStepInfo, ambiance, logPrefix, shouldRouteStageToRunner, localVmDriverType);

    boolean executeOnHarnessHostedDelegates = false;
    boolean emitEvent = false;
    String stageExecutionId = ambiance.getStageExecutionId();
    List<TaskSelector> taskSelectors = new ArrayList<>();
    String accountId = AmbianceUtils.getAccountId(ambiance);

    if (aitBypassEvaluator != null && aitBypassEvaluator.isAitTriggered(ambiance.getMetadata().getPrincipalInfo())) {
      try {
        executionMetricsService.recordAitBuild(accountId, "ait_ci_builds");
        executionMetricsService.recordAitBypassDecision(
            accountId, "ait_ci_bypass_decision", "allowed", "all_checks_passed");
      } catch (Exception e) {
        log.debug("Failed to record AIT build metric", e);
      }
    } else if (aitBypassEvaluator != null && ciExecutionServiceConfig.isAitCiLicenseBypassEnabled()) {
      try {
        executionMetricsService.recordAitBypassDecision(
            accountId, "ait_ci_bypass_decision", "denied", "not_ait_service_principal");
      } catch (Exception e) {
        log.debug("Failed to record AIT bypass decision metric", e);
      }
    }

    // Secrets are in decrypted format for DLITE_VM type
    if (buildSetupTaskParams.getType() != DLITE_VM) {
      try {
        log.info("Created params for build task: {}", EncodingUtils.convertToBase64String(buildSetupTaskParams));
      } catch (Exception e) {
        log.error("Could not serialize class CIInitializeTaskParams", e);
      }
    }
    if (buildSetupTaskParams.getType() == DLITE_VM) {
      AccountDTO accountDTO =
          CGRestUtils.getResponse(accountClient.getAccountDTO(AmbianceUtils.getAccountId(ambiance)));
      if (accountDTO == null) {
        throw new CIStageExecutionException("Account does not exist, contact Harness support team.");
      }
      String platformSelector = ((DliteVmInitializeTaskParams) buildSetupTaskParams).getSetupVmRequest().getPoolID();
      TaskSelector taskSelector = TaskSelector.newBuilder().setSelector(platformSelector).build();
      taskSelectors.add(taskSelector);
      executeOnHarnessHostedDelegates = true;

      emitEvent = true;
    } else if (initializeStepInfo.getInfrastructure().getType() == Infrastructure.Type.DOCKER) {
      if (initializeStepInfo.getDelegateSelectors().getValue() != null) {
        addExternalDelegateSelector(taskSelectors, initializeStepInfo, ambiance);
      } else if (!HarnessYamlVersion.isV1(stepParameters.getVersion())) {
        DockerInfraYaml dockerInfraYaml = (DockerInfraYaml) initializeStepInfo.getInfrastructure();
        String platformSelector =
            dockerInitializeTaskParamsBuilder.getHostedPoolId(dockerInfraYaml.getSpec().getPlatform());
        TaskSelector taskSelector = TaskSelector.newBuilder().setSelector(platformSelector).build();
        taskSelectors.add(taskSelector);
      }
      // TODO: start emitting & processing event for Docker as well
      // emitEvent = true;
    } else if (initializeStepInfo.getInfrastructure().getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
      ConnectorConfigDTO connectorConfig =
          ((CIK8InitializeTaskParams) buildSetupTaskParams).getK8sConnector().getConnectorConfig();
      Set<String> delegateSelectors = ((KubernetesClusterConfigDTO) connectorConfig).getDelegateSelectors();

      // Delegate Selector Precedence: 1)Stage ->  2)Pipeline ->  3)Connector .If not specified use any delegate
      if (initializeStepInfo.getDelegateSelectors().getValue() != null) {
        addExternalDelegateSelector(taskSelectors, initializeStepInfo, ambiance);
      } else if (isNotEmpty(delegateSelectors)) {
        List<TaskSelector> selectorList = delegateSelectors.stream()
                                              .map(ds -> TaskSelector.newBuilder().setSelector(ds).build())
                                              .collect(Collectors.toList());
        taskSelectors.addAll(selectorList);
      } else {
        List<TaskSelector> selectorList = delegateSelectors.stream()
                                              .map(ds -> TaskSelector.newBuilder().setSelector(ds).build())
                                              .collect(Collectors.toList());
        taskSelectors.addAll(selectorList);
      }
    } else if ((initializeStepInfo.getInfrastructure().getType() == Infrastructure.Type.VM)
        && (ciFeatureFlagService.isEnabled(CI_ENABLE_VM_DELEGATE_SELECTOR, AmbianceUtils.getAccountId(ambiance)))) {
      if (initializeStepInfo.getDelegateSelectors().getValue() != null) {
        addExternalDelegateSelector(taskSelectors, initializeStepInfo, ambiance);
      }
    }

    long timeout = Timeout.fromString((String) stepParameters.getTimeout().fetchFinalValue()).getTimeoutInMillis()
        + TASK_BUFFER_TIMEOUT_MILLIS;
    TaskData taskData = getTaskData(buildSetupTaskParams, timeout);

    Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);
    // Add OIDC context for Vault granular claims
    if (AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PL_ENABLE_GRANULAR_CLAIMS_FOR_VAULT.name())) {
      abstractions.putAll(AmbianceUtils.extractOidcContextFields(ambiance));
    }

    HDelegateTask task = (HDelegateTask) StepUtils.prepareDelegateTaskInput(
        accountId, taskData, abstractions, generateLogAbstractions(ambiance));

    String taskId;
    if (HarnessYamlVersion.isV1(stepParameters.getVersion()) || shouldRouteStageToRunner) {
      DelegateTaskRequest delegateTaskRequest = ciDelegateTaskExecutor.buildInitDelegateTaskRequest(abstractions, task,
          taskSelectors.stream().map(TaskSelector::getSelector).collect(Collectors.toList()), new ArrayList<>(),
          executeOnHarnessHostedDelegates, emitEvent, stageExecutionId, generateLogAbstractions(ambiance),
          ambiance.getExpressionFunctorToken(), true, taskSelectors);
      taskId = submitTaskViaRunner(ambiance, stepParameters, initializeStepInfo, buildSetupTaskParams, taskSelectors,
          getSecretValues(buildSetupTaskParams), timeout, delegateTaskRequest, localVmDriverType);
      if (isLocalInfra(stepParameters.getSpec())) {
        try (AutoLogContext ignore = new TransactionalTaskLogContext(true, taskId, INITIALIZE, stageExecutionId,
                 initializeStepInfo.getInfrastructure().getType().toString(), ambiance.getPlanExecutionId(), accountId,
                 null, null)) {
          log.info("Submitted local initialize task to Unified Task API");
        }
      }
    } else {
      taskId = ciDelegateTaskExecutor.queueTask(abstractions, task,
          taskSelectors.stream().map(TaskSelector::getSelector).collect(Collectors.toList()), new ArrayList<>(),
          executeOnHarnessHostedDelegates, emitEvent, stageExecutionId, generateLogAbstractions(ambiance),
          ambiance.getExpressionFunctorToken(), true, taskSelectors);
    }
    return taskId;
  }

  // default to delegate in the following case: no explicit delegate task selectors are
  // provided at pipeline level & Unified task API is enabled
  private boolean shouldUseDelegate(String accountId, InitializeStepInfo initializeStepInfo) {
    return isEmpty(TaskSelectorYaml.toTaskSelector(initializeStepInfo.getDelegateSelectors()))
        && ciFeatureFlagService.isEnabled(FeatureName.CI_USE_UNIFIED_TASKS_SELECTIVELY_FOR_LOCAL_INFRA, accountId);
  }

  // gets secret values to be masked
  private Set<String> getSecretValues(CIInitializeTaskParams setupTaskParams) {
    Set<String> secrets = new HashSet<>();
    if (setupTaskParams instanceof DliteVmInitializeTaskParams dliteVmInitializeTaskParams) {
      if (isNotEmpty(dliteVmInitializeTaskParams.getSetupVmRequest().getConfig().getSecrets())) {
        secrets.addAll(dliteVmInitializeTaskParams.getSetupVmRequest().getConfig().getSecrets());
      }
      for (ExecuteStepRequest executeStepRequest : dliteVmInitializeTaskParams.getServices()) {
        if (isNotEmpty(executeStepRequest.getConfig().getSecrets())) {
          secrets.addAll(executeStepRequest.getConfig().getSecrets());
        }
      }
    }
    return secrets;
  }

  private String submitTaskViaRunner(Ambiance ambiance, StepBaseParameters stepBaseParameters,
      InitializeStepInfo initializeStepInfo, CIInitializeTaskParams buildSetupTaskParams,
      List<TaskSelector> taskSelectors, Set<String> secretValuesToMask, long timeout,
      DelegateTaskRequest delegateTaskRequest, LocalVmDriverType localVmDriverType) {
    Infrastructure infrastructure = initializeStepInfo.getInfrastructure();
    Map<String, String> initEnvVars = getInitEnvVars(buildSetupTaskParams);
    executionSweepingOutputService.consumeOptional(ambiance, INIT_ENV_VARS,
        InitializeEnvSweepingOutput.builder().envVars(initEnvVars).build(), StepCategory.STAGE.name());

    ParameterField<List<String>> sharedPaths = initializeStepInfo.getStageElementConfig() != null
        ? initializeStepInfo.getStageElementConfig().getSharedPaths()
        : null;
    Map<String, String> volumeToMountPaths;
    boolean enableDockerSetup = true;
    if (Infrastructure.Type.DOCKER.equals(infrastructure.getType())) {
      // send only customer shared paths
      volumeToMountPaths = VmInitializeUtils.getSharedVolumeToMountPaths(sharedPaths);
      enableDockerSetup = ((CIVmInitializeTaskParams) buildSetupTaskParams).isEnableDockerSetup();
    } else {
      // reuse the logic to get volumes for hosted v0 & hosted v1
      volumeToMountPaths = vmInitializeUtils.getVolumeToMountPath(sharedPaths,
          InfraInfoUtils.getInfraOS(initializeStepInfo.getInfrastructure()), AmbianceUtils.getAccountId(ambiance),
          initializeStepInfo.getInfrastructure());
    }

    IntegrationStageConfig stageElementConfig = initializeStepInfo.getStageElementConfig();
    List<VmServiceDependency> serviceDependencies = null;
    if (stageElementConfig != null && stageElementConfig.getServiceDependencies() != null
        && isNotEmpty(stageElementConfig.getServiceDependencies().getValue())) {
      serviceDependencies = vmInitializeTaskParamsBuilder.getServiceDependencies(ambiance, stageElementConfig);
    }
    RunnerRequest runnerRequest;
    if (infrastructure.getType() == Infrastructure.Type.HOSTED_VM) {
      List<LocalExecuteTaskSpec> serviceDependencyTasks = new ArrayList<>();
      // if hosted and FF is disabled, we do the secret resolution here in ci manager
      if (!ciFeatureFlagService.isEnabled(
              FeatureName.CI_RUNNER_FRAMEWORK_SECRET_EVAL, AmbianceUtils.getAccountId(ambiance))) {
        if (isNotEmpty(serviceDependencies)) {
          hostedVmSecretResolver.resolve(ambiance, serviceDependencies,
              !ciFeatureFlagService.isEnabled(
                  FeatureName.CI_INVALID_SECRET_ERROR, AmbianceUtils.getAccountId(ambiance)),
              true);
        }
        hostedVmSecretResolver.resolve(ambiance, initEnvVars,
            !ciFeatureFlagService.isEnabled(FeatureName.CI_INVALID_SECRET_ERROR, AmbianceUtils.getAccountId(ambiance)),
            true);
      }
      if (isNotEmpty(serviceDependencies)) {
        serviceDependencyTasks =
            runnerRequestBuilder.buildExecuteTasks(ambiance, infrastructure, serviceDependencies, volumeToMountPaths);
      }
      DliteVmInitializeTaskParams dliteInitializeTaskParams = (DliteVmInitializeTaskParams) buildSetupTaskParams;
      // Keep INIT_ENV_VARS sanitized while restoring only the platform-owned PC contract in the
      // one setup request that Runner sends to Lite Engine.
      Map<String, String> runnerSetupEnvVars = new HashMap<>(initEnvVars);
      Map<String, String> platformSetupEnvVars = dliteInitializeTaskParams.getSetupVmRequest().getConfig().getEnvs();
      if (platformSetupEnvVars != null) {
        platformSetupEnvVars.forEach((key, value) -> {
          if (key != null && key.startsWith(HARNESS_PC_PREFIX)) {
            runnerSetupEnvVars.put(key, value);
          }
        });
      }
      runnerRequest = runnerRequestBuilder.buildInitRequestWithPoolSpec(ambiance, stepBaseParameters.getVersion(),
          taskSelectors, runnerSetupEnvVars, dliteInitializeTaskParams.getSetupVmRequest(), volumeToMountPaths,
          serviceDependencyTasks, secretValuesToMask, timeout);
    } else if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
      CIK8InitializeTaskParams cik8InitializeTaskParams = (CIK8InitializeTaskParams) buildSetupTaskParams;
      if (ciFeatureFlagService.isEnabled(
              FeatureName.PL_RUNNER_K8S_INFRA_USE_SCHEDULED_TASK_API, AmbianceUtils.getAccountId(ambiance))) {
        try {
          ScheduleTaskRequest scheduleTaskRequest = runnerRequestBuilder.buildInitRequestK8V1(
              ambiance, taskSelectors, cik8InitializeTaskParams, timeout, delegateTaskRequest);
          ScheduleTaskResponse scheduleTaskResponse = ciDelegateTaskExecutor.submitTask(scheduleTaskRequest);
          updateTransactionId(ambiance, scheduleTaskResponse);
          return scheduleTaskResponse.getTaskId();
        } catch (JsonProcessingException ex) {
          throw new RuntimeException("Failed to build init request for k8s infrastructure", ex);
        }
      } else {
        runnerRequest = runnerRequestBuilder.buildInitRequestK8(
            ambiance, taskSelectors, cik8InitializeTaskParams, timeout, delegateTaskRequest);
      }
    } else if (infrastructure.getType() == Infrastructure.Type.VM) {
      CIVmInitializeTaskParams ciVmInitializeTaskParams = (CIVmInitializeTaskParams) buildSetupTaskParams;
      try {
        ScheduleTaskRequest scheduleTaskRequest = runnerRequestBuilder.buildInitRequestVmV1(ambiance, taskSelectors,
            ciVmInitializeTaskParams, initEnvVars, volumeToMountPaths, timeout, delegateTaskRequest);
        ScheduleTaskResponse scheduleTaskResponse = ciDelegateTaskExecutor.submitTask(scheduleTaskRequest);
        updateTransactionId(ambiance, scheduleTaskResponse);
        return scheduleTaskResponse.getTaskId();
      } catch (JsonProcessingException ex) {
        throw new RuntimeException("Failed to build init request for VM infrastructure", ex);
      }
    } else {
      Optional<CloudRuntimeImageSpec> imageSpec = VmInitializeUtils.getImageSpec(infrastructure);
      SetupVmRequest.VmImageConfig vmImageConfig =
          vmInitializeTaskParamsBuilder.getVMConfig(infrastructure, ambiance, imageSpec);

      // For local Tart VM, fetch default macOS ARM image from settings if no image specified in YAML
      if (localVmDriverType == LocalVmDriverType.TART_VM && vmImageConfig == null && settingsClient != null) {
        try {
          String defaultImage =
              NGRestUtils
                  .getResponse(settingsClient.getSetting(SettingIdentifiers.CI_DEFAULT_MACOS_ARM_VM_IMAGE,
                      AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
                      AmbianceUtils.getProjectIdentifier(ambiance)))
                  .getValue();
          if (isNotEmpty(defaultImage)) {
            log.info("Using macOS ARM default VM image from settings: {}", defaultImage);
            vmImageConfig = SetupVmRequest.VmImageConfig.builder().imageName(defaultImage).build();
          }
        } catch (Exception e) {
          log.error("Setting {} is not found", SettingIdentifiers.CI_DEFAULT_MACOS_ARM_VM_IMAGE, e);
        }
      }

      if (localVmDriverType == LocalVmDriverType.TART_VM && vmImageConfig == null) {
        throw new CIStageExecutionException("VM image is required for local Tart VM builds. "
            + "Set the 'Default macOS ARM VM Image' under Default Settings > CI at the project, org, or account level, "
            + "or specify it in the stage YAML under runtime.spec.imageSpec.imageName.");
      }

      runnerRequest = runnerRequestBuilder.buildInitRequest(ambiance, taskSelectors, initEnvVars, volumeToMountPaths,
          timeout, enableDockerSetup, delegateTaskRequest, vmImageConfig, localVmDriverType);
    }
    return ciDelegateTaskExecutor.submitTask(runnerRequest);
  }

  private boolean isResponseFromRunner(ResponseData response) {
    return response instanceof SerializedResponseData
        && TASKS_FROM_RUNNER.equals(((SerializedResponseData) response).getTaskType());
  }

  // This method updates routeToRunner for local infra: if response is from delegate and routeToRunner is true, set
  // routeToRunner to false so that execute task goes to delegate; if response is from runner, ignore
  @VisibleForTesting
  public void updateRouteToRunner(Ambiance ambiance, SpecParameters stepParameters, ResponseData response) {
    if (!isLocalInfra(stepParameters)
        || !ciFeatureFlagService.isEnabled(
            FeatureName.CI_USE_UNIFIED_TASKS_SELECTIVELY_FOR_LOCAL_INFRA, AmbianceUtils.getAccountId(ambiance))) {
      return;
    }
    if (isResponseFromRunner(response)) {
      return;
    }
    OptionalSweepingOutput stageExecutionDetails = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_INFRA_DETAILS));

    if (stageExecutionDetails.isFound()
        && stageExecutionDetails.getOutput() instanceof VmStageInfraDetails vmStageInfraDetails) {
      if (vmStageInfraDetails.isRouteToRunner()) {
        executionSweepingOutputResolver.consumeUpsert(ambiance, STAGE_INFRA_DETAILS,
            vmStageInfraDetails.toBuilder().routeToRunner(false).build(), StepOutcomeGroup.STAGE.name());
      }
    }
  }

  void updateTransactionId(Ambiance ambiance, ScheduleTaskResponse scheduleTaskResponse) {
    OptionalSweepingOutput stageExecutionDetails = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_INFRA_DETAILS));
    if (stageExecutionDetails.isFound()) {
      if (stageExecutionDetails.getOutput() instanceof K8StageInfraDetails k8StageInfraDetails) {
        executionSweepingOutputResolver.consumeUpsert(ambiance, STAGE_INFRA_DETAILS,
            k8StageInfraDetails.toBuilder().transactionId(scheduleTaskResponse.getTransactionId()).build(),
            StepOutcomeGroup.STAGE.name());
      } else if (stageExecutionDetails.getOutput() instanceof VmStageInfraDetails vmStageInfraDetails) {
        executionSweepingOutputResolver.consumeUpsert(ambiance, STAGE_INFRA_DETAILS,
            vmStageInfraDetails.toBuilder().transactionId(scheduleTaskResponse.getTransactionId()).build(),
            StepOutcomeGroup.STAGE.name());
      }
    }
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // Measure end-to-end provision_infra (init) roundtrip latency and outcome (phase=roundtrip).
    // Label and timestamp resolution must not throw: instrumentation may never break init response handling.
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String initInfraType = safeInfraTypeYaml(stepParameters);
    long levelStartMs = safeCurrentLevelStartTs(ambiance);
    StepResponse response;
    try {
      response = doHandleAsyncResponseInternal(ambiance, stepParameters, responseDataMap);
    } catch (Exception ex) {
      recordProvisionInfra(ambiance, accountId, initInfraType, CIObservabilityConstants.OUTCOME_SYSTEM_FAILURE,
          CIObservabilityConstants.PHASE_ROUNDTRIP, elapsedMsSince(levelStartMs));
      throw ex;
    }
    recordProvisionInfra(ambiance, accountId, initInfraType, CIMetricsHelper.classifyInfraOutcome(response.getStatus()),
        CIObservabilityConstants.PHASE_ROUNDTRIP, elapsedMsSince(levelStartMs));
    return response;
  }

  @Override
  public void handleAbort(Ambiance ambiance, StepBaseParameters stepParameters,
      AsyncExecutableResponse executableResponse, boolean userMarked) {
    recordTerminalProvisionInfra(ambiance, stepParameters, CIObservabilityConstants.OUTCOME_ABORTED);
  }

  @Override
  public void handleExpire(
      Ambiance ambiance, StepBaseParameters stepParameters, AsyncExecutableResponse executableResponse) {
    // Init timeout means provisioning did not finish in time — that is a platform fault.
    recordTerminalProvisionInfra(ambiance, stepParameters, CIObservabilityConstants.OUTCOME_SYSTEM_FAILURE);
  }

  private void recordTerminalProvisionInfra(Ambiance ambiance, StepBaseParameters stepParameters, String outcome) {
    try {
      recordProvisionInfra(ambiance, AmbianceUtils.getAccountId(ambiance), safeInfraTypeYaml(stepParameters), outcome,
          CIObservabilityConstants.PHASE_ROUNDTRIP, elapsedMsSince(safeCurrentLevelStartTs(ambiance)));
    } catch (Exception e) {
      log.warn("Failed to record provision_infra metric for outcome {}", outcome, e);
    }
  }

  private StepResponse doHandleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // If any of the responses are in serialized format, deserialize them
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    log.info("Received response for step {}", stepIdentifier);

    String taskId = responseDataMap.entrySet().iterator().next().getKey();
    ResponseData responseData = responseDataMap.entrySet().iterator().next().getValue();
    updateRouteToRunner(ambiance, stepParameters.getSpec(), responseData);

    responseData = serializedResponseDataHelper.deserialize(responseData);
    if (responseData instanceof ErrorNotifyResponseData || responseData instanceof FailureResponseData) {
      String message;
      if (responseData instanceof ErrorNotifyResponseData) {
        if (((InitializeStepInfo) stepParameters.getSpec()).getInfrastructure().getType()
            == Infrastructure.Type.HOSTED_VM) {
          String moduleType = AmbianceUtils.getStageModuleType(ambiance);
          LicensesWithSummaryDTO licensesWithSummaryDTO = ciLicenseService.getLicenseSummary(
              AmbianceUtils.getAccountId(ambiance), moduleType, ambiance.getMetadata().getPrincipalInfo());
          if (licensesWithSummaryDTO == null) {
            throw new CIStageExecutionException("Please enable CI free plan or reach out to support.");
          }

          log.error("Initialize step should not fail for hosted vm, account type {}, retryIndex [{}]",
              licensesWithSummaryDTO.getEdition().name(),
              AmbianceUtils.obtainCurrentLevel(ambiance) != null
                  ? AmbianceUtils.obtainCurrentLevel(ambiance).getRetryIndex()
                  : null);
        }

        if (((InitializeStepInfo) stepParameters.getSpec()).getInfrastructure().getType()
            == Infrastructure.Type.KUBERNETES_DIRECT) {
          message = emptyIfNull(ExceptionUtils.getMessage(exceptionManager.processException(
              new CILiteEngineException(((ErrorNotifyResponseData) responseData).getErrorMessage()))));
        } else {
          message = emptyIfNull(((ErrorNotifyResponseData) responseData).getErrorMessage());
        }
      } else if (responseData instanceof FailureResponseData) {
        message = emptyIfNull(ExceptionUtils.getMessage(exceptionManager.processException(
            new CIStageExecutionException(((FailureResponseData) responseData).getErrorMessage()))));
      } else {
        throw new CIStageExecutionException("Unexpected response received while process CI execution");
      }

      if (isResponseFromRunner(responseData) && isLocalInfra(stepParameters.getSpec())) {
        // Log task response details if response came from a Local Unified Runner
        try (AutoLogContext ignore = new TransactionalTaskLogContext(true, taskId, INITIALIZE,
                 ambiance.getStageExecutionId(),
                 ((InitializeStepInfo) stepParameters).getInfrastructure().getType().toString(),
                 ambiance.getPlanExecutionId(), AmbianceUtils.getAccountId(ambiance), Status.FAILED.toString(), null)) {
          log.warn("Received failure response for Initialize Task on Runner. Error: {}", message);
        }
      }
      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder()
                           .addFailureData(CIStepInfoUtils.getDefaultCIFailureDataInfo(message, ambiance))
                           .build())
          .build();
    }

    final CITaskExecutionResponse ciTaskExecutionResponse;
    if (responseData instanceof TaskRunnerTaskResponse) {
      // task-runner returns a single, infra-agnostic TaskRunnerTaskResponse. Normalize it into a
      // K8s-shaped response so it flows through the existing infra-type switch below.
      // TODO: Generalize for VM / Docker once those land on task-runner (route to VmTaskExecutionResponse).
      ciTaskExecutionResponse = toK8sTaskExecutionResponseFromRunner((TaskRunnerTaskResponse) responseData);
    } else {
      ciTaskExecutionResponse = (CITaskExecutionResponse) responseData;
    }

    CommandExecutionStatus status = getTaskCommandExecutionStatus(ciTaskExecutionResponse);
    if (status == CommandExecutionStatus.SUCCESS) {
      backgroundTaskUtility.queueJob(() -> saveInitialiseExecutionSweepingOutput(ambiance));
    }

    CITaskExecutionResponse.Type type = ciTaskExecutionResponse.getType();
    if (type == CITaskExecutionResponse.Type.K8) {
      return handleK8TaskResponse(ambiance, taskId, stepParameters, ciTaskExecutionResponse);
    } else if (type == CITaskExecutionResponse.Type.VM || type == CITaskExecutionResponse.Type.DOCKER) {
      return handleVmTaskResponse(ambiance, taskId, ciTaskExecutionResponse, stepParameters);
    } else {
      throw new CIStageExecutionException(format("Invalid infra type for task response: %s", type));
    }
  }

  private boolean isLocalInfra(SpecParameters spec) {
    return ((InitializeStepInfo) spec).getInfrastructure().getType() == Infrastructure.Type.DOCKER;
  }

  private CommandExecutionStatus getTaskCommandExecutionStatus(CITaskExecutionResponse ciTaskExecutionResponse) {
    CITaskExecutionResponse.Type type = ciTaskExecutionResponse.getType();
    CommandExecutionStatus status = null;
    if (type == CITaskExecutionResponse.Type.K8) {
      if (ciTaskExecutionResponse instanceof K8sTaskExecutionResponse) {
        status = ((K8sTaskExecutionResponse) ciTaskExecutionResponse).getCommandExecutionStatus();
      } else if (ciTaskExecutionResponse instanceof K8sTaskExecutionResponseFromRunner) {
        status = ((K8sTaskExecutionResponseFromRunner) ciTaskExecutionResponse).getCommandExecutionStatus();
      }
    } else if (type == CITaskExecutionResponse.Type.VM || type == CITaskExecutionResponse.Type.DOCKER) {
      status = ((VmTaskExecutionResponse) ciTaskExecutionResponse).getCommandExecutionStatus();
    }
    return status;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters StepBaseParameters) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String moduleType = AmbianceUtils.getStageModuleType(ambiance);

    InitializeStepInfo initializeStepInfo = (InitializeStepInfo) StepBaseParameters.getSpec();
    validateFeatureFlags(initializeStepInfo, accountIdentifier);

    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    String principal = executionPrincipalInfo.getPrincipal();

    populateStrategyExpansion(initializeStepInfo, ambiance);
    if (EmptyPredicate.isEmpty(principal)) {
      log.info("principal info is null");
      return;
    }

    boolean flexibleTemplatesEnabled = InjectUtils.IsFlexibleTemplatesEnabled(ambiance);
    if (initializeStepInfo.getCiCodebase() != null) {
      initializeStepInfo.getCiCodebase().validateCodeBase();
    }
    List<EntityDetail> connectorsEntityDetails = getConnectorIdentifiers(
        initializeStepInfo, accountIdentifier, projectIdentifier, orgIdentifier, flexibleTemplatesEnabled);

    if (isNotEmpty(connectorsEntityDetails) && ciFeatureFlagService.isEnabled(CIE_ENABLED_RBAC, accountIdentifier)) {
      log.info("validating rbac for account id: {}", accountIdentifier);
      pipelineRbacHelper.checkRuntimePermissions(ambiance, connectorsEntityDetails, true);
    }

    validateConnectors(
        initializeStepInfo, connectorsEntityDetails, accountIdentifier, orgIdentifier, projectIdentifier);
    sanitizeExecution(initializeStepInfo, accountIdentifier, moduleType, executionPrincipalInfo);
  }

  private void sanitizeExecution(InitializeStepInfo initializeStepInfo, String accountIdentifier, String moduleType,
      ExecutionPrincipalInfo principalInfo) {
    LicensesWithSummaryDTO licenseSummary =
        ciLicenseService.getLicenseSummary(accountIdentifier, moduleType, principalInfo);
    if (licenseSummary == null) {
      throw new CIStageExecutionException("Please enable CI free or other plan or reach out to support.");
    }
    Edition edition = licenseSummary.getEdition();
    boolean isEditionEnterpriseOrEssentials = Edition.ENTERPRISE.equals(edition) || Edition.ESSENTIALS.equals(edition);
    List<ExecutionWrapperConfig> steps = initializeStepInfo.getExecutionElementConfig().getSteps();
    if (initializeStepInfo.getInfrastructure().getType() == Infrastructure.Type.HOSTED_VM
        && !isEditionEnterpriseOrEssentials) {
      sanitizationService.validate(steps);
    }
  }

  private void validateConnectors(InitializeStepInfo initializeStepInfo, List<EntityDetail> connectorEntitiesList,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (initializeStepInfo.getInfrastructure().getType() != Infrastructure.Type.HOSTED_VM) {
      return;
    }

    // For hosted VMs, we need to validate whether all the connectors connect via platform or not
    List<ConnectorDetails> connectorDetailsList =
        getConnectorDetails(connectorEntitiesList, accountIdentifier, projectIdentifier, orgIdentifier);
    Set<String> invalidIdentifiers = new HashSet<>();
    for (ConnectorDetails connectorDetails : connectorDetailsList) {
      if (connectorDetails.getExecuteOnDelegate() != null) {
        if (connectorDetails.getExecuteOnDelegate()) {
          invalidIdentifiers.add(connectorDetails.getIdentifier());
        }
      } else {
        log.warn("Connector type: {} has executeOnDelegate set as null", connectorDetails.getConnectorType());
        invalidIdentifiers.add(connectorDetails.getIdentifier());
      }
    }
    if (!isEmpty(invalidIdentifiers)) {
      throw new CIStageExecutionException(
          format("While using hosted infrastructure, all connectors should be configured to go via the Harness "
                  + "platform instead of via the delegate. "
                  + "Please update the connectors: %s to connect via the Harness platform instead. This can be done by "
                  + "editing the connector and updating the connectivity to go via the Harness platform.",
              invalidIdentifiers));
    }
  }

  @VisibleForTesting
  public Map<String, String> getInitEnvVars(CIInitializeTaskParams buildSetupTaskParams) {
    Map<String, String> envVars = new HashMap<>();
    switch (buildSetupTaskParams.getType()) {
      case DLITE_VM:
        DliteVmInitializeTaskParams dliteVmInitializeTaskParams = (DliteVmInitializeTaskParams) buildSetupTaskParams;
        Map<String, String> setupEnvVars = dliteVmInitializeTaskParams.getSetupVmRequest().getConfig().getEnvs();
        envVars = setupEnvVars == null ? new HashMap<>() : new HashMap<>(setupEnvVars);
        for (ExecuteStepRequest executeStepRequest : dliteVmInitializeTaskParams.getServices()) {
          if (isNotEmpty(executeStepRequest.getConfig().getEnvs())) {
            envVars.putAll(executeStepRequest.getConfig().getEnvs());
          }
        }
        // HARNESS_PC_* is setup-only. Never persist it in INIT_ENV_VARS or expose it to later
        // customer steps; the original SetupVmRequest still carries the platform-owned contract
        // to Lite Engine.
        envVars.keySet().removeIf(key -> key != null && key.startsWith(HARNESS_PC_PREFIX));
        return envVars;
      case VM:
        CIVmInitializeTaskParams ciVmInitializeTaskParams = (CIVmInitializeTaskParams) buildSetupTaskParams;
        return ciVmInitializeTaskParams.getEnvironment();
      case GCP_K8:
        CIK8InitializeTaskParams cik8InitializeTaskParams = (CIK8InitializeTaskParams) buildSetupTaskParams;

        if (isNotEmpty(cik8InitializeTaskParams.getServicePodParams())) {
          for (CIK8ServicePodParams cik8ServicePodParams : cik8InitializeTaskParams.getServicePodParams()) {
            for (CIK8ContainerParams containerParams :
                cik8ServicePodParams.getCik8PodParams().getContainerParamsList()) {
              envVars.putAll(containerParams.getEnvVars());
            }
            Map<String, String> servicePodCommonEnvVars = cik8ServicePodParams.getCik8PodParams().getCommonEnvVars();
            if (isNotEmpty(servicePodCommonEnvVars)) {
              envVars.putAll(servicePodCommonEnvVars);
            }
          }
        }
        for (CIK8ContainerParams containerParams :
            cik8InitializeTaskParams.getCik8PodParams().getContainerParamsList()) {
          envVars.putAll(containerParams.getEnvVars());
        }
        Map<String, String> podCommonEnvVars = cik8InitializeTaskParams.getCik8PodParams().getCommonEnvVars();
        if (isNotEmpty(podCommonEnvVars)) {
          envVars.putAll(podCommonEnvVars);
        }
        return envVars;
      default:
        return new HashMap<>();
    }
  }

  private List<ConnectorDetails> getConnectorDetails(
      List<EntityDetail> entityDetails, String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    List<ConnectorDetails> connectorDetailsList = new ArrayList<>();
    BaseNGAccess ngAccess = IntegrationStageUtils.getBaseNGAccess(accountIdentifier, orgIdentifier, projectIdentifier);
    for (EntityDetail entityDetail : entityDetails) {
      if (!EntityType.CONNECTORS.equals(entityDetail.getType())) {
        continue;
      }
      ConnectorDetails connectorDetails =
          connectorUtils.getConnectorDetailsWithIdentifier(ngAccess, (IdentifierRef) entityDetail.getEntityRef());
      connectorDetailsList.add(connectorDetails);
    }
    return connectorDetailsList;
  }

  private void validateFeatureFlags(InitializeStepInfo initializeStepInfo, String accountIdentifier) {
    if (initializeStepInfo.getInfrastructure().getType() != Infrastructure.Type.HOSTED_VM) {
      return;
    }

    // For hosted VMs, we need to check whether the feature flag is enabled or not
    Boolean isEnabled = ciFeatureFlagService.isEnabled(CIE_HOSTED_VMS, accountIdentifier);
    if (!isEnabled) {
      throw new CIStageExecutionException(
          "Hosted builds are not enabled for this account. Please contact Harness support.");
    }
  }

  private String getLogKey(Ambiance ambiance) {
    return LogStreamingStepClientFactory.getLogBaseKey(ambiance);
  }

  public TaskData getTaskData(CIInitializeTaskParams buildSetupTaskParams, long timeout) {
    SerializationFormat serializationFormat = SerializationFormat.KRYO;
    String taskType = TaskType.INITIALIZATION_PHASE.getDisplayName();
    if (buildSetupTaskParams.getType() == DLITE_VM) {
      serializationFormat = SerializationFormat.JSON;
      taskType = TaskType.DLITE_CI_VM_INITIALIZE_TASK.getDisplayName();
    }

    return TaskData.builder()
        .async(true)
        .timeout(timeout)
        .taskType(taskType)
        .serializationFormat(serializationFormat)
        .parameters(new Object[] {buildSetupTaskParams})
        .build();
  }

  private void saveInitialiseExecutionSweepingOutput(Ambiance ambiance) {
    long startTime = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    long currentTime = System.currentTimeMillis();

    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION));
    if (!optionalSweepingOutput.isFound()) {
      try {
        InitializeExecutionSweepingOutput initializeExecutionSweepingOutput =
            InitializeExecutionSweepingOutput.builder().initialiseExecutionTime(currentTime - startTime).build();
        executionSweepingOutputResolver.consume(
            ambiance, INITIALIZE_EXECUTION, initializeExecutionSweepingOutput, StepOutcomeGroup.STAGE.name());
      } catch (Exception e) {
        log.error("Error while consuming initialize execution sweeping output", e);
      }
    }
  }

  /**
   * V1/Unified-only marker — published synchronously, only on the SUCCESS path of the forward Init.
   * Consumed by RollbackOptionalChildChainStep (under a V1-only gate) to skip stage-rollback /
   * pipeline-rollback / post-execution-rollback when the forward Init never succeeded.
   *
   * <p>Init succeeds at most once per stage execution under normal conditions, so the publish runs
   * once.
   *
   * <p>No-op for non-V1 pipelines, so V0 / non-CI plans never produce this output and the
   * consumer's V1 gate stays inert there.
   */
  private void saveInitializationSucceededSweepingOutput(Ambiance ambiance) {
    if (!HarnessYamlVersion.isV1(AmbianceUtils.getPipelineVersion(ambiance))) {
      return;
    }
    try {
      executionSweepingOutputResolver.consume(ambiance, CI_INITIALIZATION_SUCCEEDED,
          CIInitializationSucceededSweepingOutput.builder().build(), StepOutcomeGroup.STAGE.name());
    } catch (Exception e) {
      // Includes the duplicate-key path on retried Init: marker already present, nothing to do.
      log.warn("Failed to publish {} sweeping output for V1 stage", CI_INITIALIZATION_SUCCEEDED, e);
    }
  }

  private StepResponse handleK8TaskResponse(Ambiance ambiance, String taskId, StepBaseParameters StepBaseParameters,
      CITaskExecutionResponse ciTaskExecutionResponse) {
    boolean isFromRunner = false;
    if (ciTaskExecutionResponse instanceof K8sTaskExecutionResponseFromRunner) {
      // convert K8sTaskExecutionResponseFromRunner to K8sTaskExecutionResponse
      ciTaskExecutionResponse =
          ((K8sTaskExecutionResponseFromRunner) ciTaskExecutionResponse).toK8sTaskExecutionResponse();
      isFromRunner = true;
      DelegateMetaInfo delegateMetaInfo = ciTaskExecutionResponse.getDelegateMetaInfo();
      CommandExecutionStatus status = ((K8sTaskExecutionResponse) ciTaskExecutionResponse).getCommandExecutionStatus();
      String statusString = status != null ? status.toString() : null;
      String errorMessage = ((K8sTaskExecutionResponse) ciTaskExecutionResponse).getErrorMessage();
      // Log task response details if response came from a Unified Runner
      try (AutoLogContext ignore = new TransactionalTaskLogContext(true, taskId, INITIALIZE,
               ambiance.getStageExecutionId(), CITaskExecutionResponse.Type.K8.name(), ambiance.getPlanExecutionId(),
               AmbianceUtils.getAccountId(ambiance), statusString,
               delegateMetaInfo != null ? delegateMetaInfo.getId() : null)) {
        log.info("Received response for Initialize Task on Runner. Status: {}, Error: {}", statusString, errorMessage);
      }
    }
    K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) ciTaskExecutionResponse;
    InitializeStepInfo initializeStepInfo = (InitializeStepInfo) StepBaseParameters.getSpec();

    long startTime = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    long currentTime = System.currentTimeMillis();
    DependencyOutcome dependencyOutcome =
        getK8DependencyOutcome(ambiance, initializeStepInfo, k8sTaskExecutionResponse.getK8sTaskResponse());
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome =
        getPodDetailsOutcome(k8sTaskExecutionResponse.getK8sTaskResponse(), isFromRunner);
    StepResponse.StepOutcome stepOutcome =
        StepResponse.StepOutcome.builder().name(DEPENDENCY_OUTCOME).outcome(dependencyOutcome).build();

    try {
      executionMetricsService.recordStepExecutionCount(k8sTaskExecutionResponse.getCommandExecutionStatus().name(),
          STEP_STATUS, AmbianceUtils.getAccountId(ambiance), InitializeStepInfo.STEP_TYPE.getType());
      executionMetricsService.recordStepStatusExecutionTime(k8sTaskExecutionResponse.getCommandExecutionStatus().name(),
          (currentTime - startTime) / 1000, STEP_TIME_COUNT, AmbianceUtils.getAccountId(ambiance),
          InitializeStepInfo.STEP_TYPE.getType());
    } catch (Exception ex) {
      log.error(ex.getMessage());
    }
    if (k8sTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      log.info(
          "LiteEngineTaskStep pod creation task executed successfully with response [{}]", k8sTaskExecutionResponse);
      if (liteEnginePodDetailsOutcome == null) {
        throw new CIStageExecutionException("Failed to get pod local ipAddress details");
      }
      if (k8sTaskExecutionResponse.getK8sTaskResponse() != null) {
        log.info("ip address for pod {} is {}", k8sTaskExecutionResponse.getK8sTaskResponse().getPodName(),
            liteEnginePodDetailsOutcome.getIpAddress());
      }
      // V1-only marker so rollback chain can skip when forward Init never succeeded.
      saveInitializationSucceededSweepingOutput(ambiance);
      return StepResponse.builder()
          .status(Status.SUCCEEDED)
          .stepOutcome(stepOutcome)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(POD_DETAILS_OUTCOME)
                           .group(StepOutcomeGroup.STAGE.name())
                           .outcome(liteEnginePodDetailsOutcome)
                           .build())
          .build();

    } else {
      log.error("LiteEngineTaskStep execution finished with status [{}] and response [{}]",
          k8sTaskExecutionResponse.getCommandExecutionStatus(), k8sTaskExecutionResponse);

      StepResponseBuilder stepResponseBuilder = StepResponse.builder().status(Status.FAILED).stepOutcome(stepOutcome);
      String errorMessage = CIStepInfoUtils.enrichImagePullErrorMessage(k8sTaskExecutionResponse.getErrorMessage());
      stepResponseBuilder.failureInfo(
          FailureInfo.newBuilder()
              .addFailureData(CIStepInfoUtils.getDefaultCIFailureDataInfo(errorMessage, ambiance))
              .setErrorMessage(emptyIfNull(errorMessage))
              .build());
      return stepResponseBuilder.build();
    }
  }

  private void populateStrategyExpansion(InitializeStepInfo initializeStepInfo, Ambiance ambiance) {
    ExecutionElementConfig executionElement = initializeStepInfo.getExecutionElementConfig();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    List<ExecutionWrapperConfig> expandedExecutionElement = new ArrayList<>();
    List<ExecutionWrapperConfig> rollbackExecutionElement = new ArrayList<>();

    Map<String, StrategyExpansionData> strategyExpansionMap = new HashMap<>();
    String moduleType = AmbianceUtils.getStageModuleType(ambiance);

    LicensesWithSummaryDTO licensesWithSummaryDTO =
        ciLicenseService.getLicenseSummary(accountId, moduleType, ambiance.getMetadata().getPrincipalInfo());
    if (licensesWithSummaryDTO == null) {
      throw new CIStageExecutionException("Please enable CI free plan or reach out to support.");
    }
    Optional<Integer> maxExpansionLimit = Optional.of(Integer.valueOf(MAXIMUM_EXPANSION_LIMIT));
    if (licensesWithSummaryDTO.getEdition() == Edition.FREE
        && ciStagePlanCreationUtils.isHostedInfra(initializeStepInfo.getInfrastructure())) {
      maxExpansionLimit = Optional.of(Integer.valueOf(MAXIMUM_EXPANSION_LIMIT_FREE_ACCOUNT));
    }

    updateStrategyExpansionAndExpandedExecutionMap(
        executionElement.getSteps(), ambiance, maxExpansionLimit, expandedExecutionElement, strategyExpansionMap);
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      updateStrategyExpansionAndExpandedExecutionMap(executionElement.getRollbackSteps(), ambiance, maxExpansionLimit,
          rollbackExecutionElement, strategyExpansionMap);
    }

    initializeStepInfo.setExecutionElementConfig(ExecutionElementConfig.builder()
                                                     .version(executionElement.getVersion())
                                                     .steps(expandedExecutionElement)
                                                     .rollbackSteps(rollbackExecutionElement)
                                                     .build());
    IntegrationStageConfigImpl integrationStageConfigImpl =
        (IntegrationStageConfigImpl) initializeStepInfo.getStageElementConfig();
    integrationStageConfigImpl.setExecution(ExecutionElementConfig.builder()
                                                .uuid(integrationStageConfigImpl.getExecution().getUuid())
                                                .steps(expandedExecutionElement)
                                                .build());
    initializeStepInfo.setStageElementConfig(integrationStageConfigImpl);
    initializeStepInfo.setStrategyExpansionMap(strategyExpansionMap);
  }

  private void updateStrategyExpansionAndExpandedExecutionMap(List<ExecutionWrapperConfig> executionElement,
      Ambiance ambiance, Optional<Integer> maxExpansionLimit, List<ExecutionWrapperConfig> expandedExecutionElement,
      Map<String, StrategyExpansionData> strategyExpansionMap) {
    if (isEmpty(executionElement)) {
      return;
    }
    for (ExecutionWrapperConfig config : executionElement) {
      ExpandedExecutionWrapperInfo expandedExecutionWrapperInfo;
      if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
        expandedExecutionWrapperInfo =
            strategyHelper.expandExecutionWrapperConfigFromClass(config, maxExpansionLimit, StepNodeV1.class, ambiance);
      } else {
        expandedExecutionWrapperInfo = strategyHelper.expandExecutionWrapperConfigFromClass(
            config, maxExpansionLimit, CIAbstractStepNode.class, ambiance);
      }

      expandedExecutionElement.addAll(expandedExecutionWrapperInfo.getExpandedExecutionConfigs());
      strategyExpansionMap.putAll(expandedExecutionWrapperInfo.getUuidToStrategyExpansionData());
    }
  }

  private static K8sTaskExecutionResponseFromRunner toK8sTaskExecutionResponseFromRunner(
      TaskRunnerTaskResponse taskResponse) {
    return K8sTaskExecutionResponseFromRunner.builder()
        .delegateMetaInfo(taskResponse.getDelegateMetaInfo())
        .commandExecutionStatus(taskResponse.getCommandExecutionStatus())
        .errorMessage(taskResponse.getErrorMessage())
        .build();
  }

  private StepResponse handleVmTaskResponse(Ambiance ambiance, String taskId,
      CITaskExecutionResponse ciTaskExecutionResponse, StepBaseParameters stepParameters) {
    VmTaskExecutionResponse vmTaskExecutionResponse = (VmTaskExecutionResponse) ciTaskExecutionResponse;
    Infrastructure.Type infraType = ((InitializeStepInfo) stepParameters.getSpec()).getInfrastructure().getType();
    CommandExecutionStatus status = vmTaskExecutionResponse.getCommandExecutionStatus();
    String statusString = status != null ? status.toString() : null;
    DelegateMetaInfo delegateMetaInfo = vmTaskExecutionResponse.getDelegateMetaInfo();
    // Log task response details if response came from a Unified Runner
    if (vmTaskExecutionResponse.isFromUnifiedRunner() && isLocalInfra(stepParameters.getSpec())) {
      try (AutoLogContext ignore = new TransactionalTaskLogContext(true, taskId, INITIALIZE,
               ambiance.getStageExecutionId(), infraType != null ? infraType.toString() : null,
               ambiance.getPlanExecutionId(), AmbianceUtils.getAccountId(ambiance), statusString,
               delegateMetaInfo != null ? delegateMetaInfo.getId() : null)) {
        log.info("Received response for Initialize Task on Runner. Status: {}, Error: {}", statusString,
            vmTaskExecutionResponse.getErrorMessage());
      }
    }
    if (infraType == Infrastructure.Type.HOSTED_VM
        && (status == CommandExecutionStatus.FAILURE || status == CommandExecutionStatus.TIMEOUT
            || status == CommandExecutionStatus.SKIPPED)) {
      String moduleType = AmbianceUtils.getStageModuleType(ambiance);
      LicensesWithSummaryDTO licensesWithSummaryDTO = ciLicenseService.getLicenseSummary(
          AmbianceUtils.getAccountId(ambiance), moduleType, ambiance.getMetadata().getPrincipalInfo());
      if (licensesWithSummaryDTO == null) {
        throw new CIStageExecutionException("Please enable CI free plan or reach out to support.");
      }
      log.error(
          "Initialize step should not fail for hosted vm, [{}] and response [{}], account type {}, retryIndex [{}]",
          vmTaskExecutionResponse.getCommandExecutionStatus(), vmTaskExecutionResponse,
          licensesWithSummaryDTO.getEdition().name(),
          AmbianceUtils.obtainCurrentLevel(ambiance) != null
              ? AmbianceUtils.obtainCurrentLevel(ambiance).getRetryIndex()
              : null);
    }
    DependencyOutcome dependencyOutcome = getVmDependencyOutcome(vmTaskExecutionResponse);
    StepResponse.StepOutcome dependencyStepOutcome =
        StepResponse.StepOutcome.builder().name(DEPENDENCY_OUTCOME).outcome(dependencyOutcome).build();
    StepResponse.StepOutcome unifiedOutcome =
        StepResponse.StepOutcome.builder()
            .name(INIT_STEP_OUTCOME)
            .group(StepCategory.STAGE.name())
            .outcome(InitStepOutcome.builder()
                         .executionId(ambiance.getStageExecutionId())
                         .workspace(format(
                             RunnerRequestBuilderConstants.HARNESS_WORKSPACE_VALUE, ambiance.getStageExecutionId()))
                         .build())
            .build();

    if (status == CommandExecutionStatus.SUCCESS) {
      // V1-only marker so rollback chain can skip when forward Init never succeeded.
      saveInitializationSucceededSweepingOutput(ambiance);
      return StepResponse.builder()
          .status(Status.SUCCEEDED)
          .stepOutcome(dependencyStepOutcome)
          .stepOutcome(unifiedOutcome)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(VM_DETAILS_OUTCOME)
                           .group(StepCategory.STAGE.name())
                           .outcome(getVmDetailsOutcome(vmTaskExecutionResponse))
                           .build())
          .build();
    } else {
      log.error(
          "VM initialize step execution finished with status [{}] and response [{}]", status, vmTaskExecutionResponse);
      StepResponseBuilder stepResponseBuilder =
          StepResponse.builder().status(Status.FAILED).stepOutcome(dependencyStepOutcome);
      String errorMessage = CIStepInfoUtils.enrichImagePullErrorMessage(vmTaskExecutionResponse.getErrorMessage());
      stepResponseBuilder.failureInfo(
          FailureInfo.newBuilder()
              .addFailureData(CIStepInfoUtils.getDefaultCIFailureDataInfo(errorMessage, ambiance))
              .setErrorMessage(emptyIfNull(errorMessage))
              .build());
      return stepResponseBuilder.build();
    }
  }

  private VmDetailsOutcome getVmDetailsOutcome(VmTaskExecutionResponse vmTaskExecutionResponse) {
    VmDetailsOutcomeBuilder builder = VmDetailsOutcome.builder()
                                          .ipAddress(vmTaskExecutionResponse.getIpAddress())
                                          .poolDriverUsed(vmTaskExecutionResponse.getPoolDriverUsed());
    if (vmTaskExecutionResponse.getDelegateMetaInfo() == null
        || isEmpty(vmTaskExecutionResponse.getDelegateMetaInfo().getId())) {
      return builder.build();
    }

    return builder.delegateId(vmTaskExecutionResponse.getDelegateMetaInfo().getId()).build();
  }

  private DependencyOutcome getVmDependencyOutcome(VmTaskExecutionResponse vmTaskExecutionResponse) {
    List<ServiceDependency> serviceDependencyList = new ArrayList<>();

    List<VmServiceStatus> serviceStatuses = vmTaskExecutionResponse.getServiceStatuses();
    if (isEmpty(serviceStatuses)) {
      return DependencyOutcome.builder().serviceDependencyList(serviceDependencyList).build();
    }

    for (VmServiceStatus serviceStatus : serviceStatuses) {
      ServiceDependency.Status status = ServiceDependency.Status.SUCCESS;
      if (serviceStatus.getStatus() == VmServiceStatus.Status.ERROR) {
        status = ServiceDependency.Status.ERROR;
      }
      serviceDependencyList.add(ServiceDependency.builder()
                                    .identifier(serviceStatus.getIdentifier())
                                    .name(serviceStatus.getName())
                                    .image(serviceStatus.getImage())
                                    .errorMessage(serviceStatus.getErrorMessage())
                                    .status(status.getDisplayName())
                                    .logKeys(Collections.singletonList(serviceStatus.getLogKey()))
                                    .build());
    }
    return DependencyOutcome.builder().serviceDependencyList(serviceDependencyList).build();
  }

  private DependencyOutcome getK8DependencyOutcome(
      Ambiance ambiance, InitializeStepInfo stepParameters, CiK8sTaskResponse ciK8sTaskResponse) {
    List<ServiceDefinitionInfo> serviceDefinitionInfos =
        k8InitializeServiceUtils.getServiceInfos(stepParameters.getStageElementConfig());
    List<ServiceDependency> serviceDependencyList = new ArrayList<>();
    if (serviceDefinitionInfos == null) {
      return DependencyOutcome.builder().serviceDependencyList(serviceDependencyList).build();
    }

    Map<String, CIContainerStatus> containerStatusMap = new HashMap<>();
    if (ciK8sTaskResponse != null && ciK8sTaskResponse.getPodStatus() != null
        && ciK8sTaskResponse.getPodStatus().getCiContainerStatusList() != null) {
      for (CIContainerStatus containerStatus : ciK8sTaskResponse.getPodStatus().getCiContainerStatusList()) {
        containerStatusMap.put(containerStatus.getName(), containerStatus);
      }
    }

    String logPrefix = getLogPrefix(ambiance);
    for (ServiceDefinitionInfo serviceDefinitionInfo : serviceDefinitionInfos) {
      String logKey = format("%s/serviceId:%s", logPrefix, serviceDefinitionInfo.getIdentifier());
      String containerName = serviceDefinitionInfo.getContainerName();
      if (containerStatusMap.containsKey(containerName)) {
        CIContainerStatus containerStatus = containerStatusMap.get(containerName);

        ServiceDependency.Status status = ServiceDependency.Status.SUCCESS;
        if (containerStatus.getStatus() == CIContainerStatus.Status.ERROR) {
          status = ServiceDependency.Status.ERROR;
        }
        serviceDependencyList.add(ServiceDependency.builder()
                                      .identifier(serviceDefinitionInfo.getIdentifier())
                                      .name(serviceDefinitionInfo.getName())
                                      .image(containerStatus.getImage())
                                      .startTime(containerStatus.getStartTime())
                                      .endTime(containerStatus.getEndTime())
                                      .errorMessage(containerStatus.getErrorMsg())
                                      .status(status.getDisplayName())
                                      .logKeys(Collections.singletonList(logKey))
                                      .build());
      } else {
        serviceDependencyList.add(ServiceDependency.builder()
                                      .identifier(serviceDefinitionInfo.getIdentifier())
                                      .name(serviceDefinitionInfo.getName())
                                      .image(serviceDefinitionInfo.getImage())
                                      .errorMessage("Unknown")
                                      .status(ServiceDependency.Status.ERROR.getDisplayName())
                                      .logKeys(Collections.singletonList(logKey))
                                      .build());
      }
    }
    return DependencyOutcome.builder().serviceDependencyList(serviceDependencyList).build();
  }

  private String getLogPrefix(Ambiance ambiance) {
    return LogStreamingStepClientFactory.getLogBaseKey(ambiance, StepCategory.STAGE.name());
  }

  private LiteEnginePodDetailsOutcome getPodDetailsOutcome(CiK8sTaskResponse ciK8sTaskResponse, boolean isFromRunner) {
    if (ciK8sTaskResponse != null && ciK8sTaskResponse.getPodStatus() != null) {
      String ip = ciK8sTaskResponse.getPodStatus().getIp();
      String namespace = ciK8sTaskResponse.getPodNamespace();
      return LiteEnginePodDetailsOutcome.builder().ipAddress(ip).namespace(namespace).build();
    }
    return isFromRunner ? LiteEnginePodDetailsOutcome.builder().build() : null;
  }

  private List<EntityDetail> getConnectorIdentifiers(InitializeStepInfo initializeStepInfo, String accountIdentifier,
      String projectIdentifier, String orgIdentifier, boolean flexibleTemplatesEnabled) {
    Infrastructure infrastructure = initializeStepInfo.getInfrastructure();
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }
    List<EntityDetail> entityDetails = new ArrayList<>();
    // Add git clone connector
    if (!initializeStepInfo.isSkipGitClone()) {
      if (initializeStepInfo.getCiCodebase() == null) {
        throw new CIStageExecutionException("Codebase is mandatory with enabled cloneCodebase flag");
      }

      if (!isHarnessSCM(initializeStepInfo, accountIdentifier)) {
        if (isEmpty(initializeStepInfo.getCiCodebase().getConnectorRef().getValue())) {
          throw new CIStageExecutionException("Git connector is mandatory with enabled cloneCodebase flag");
        }
        entityDetails.add(createEntityDetails(initializeStepInfo.getCiCodebase().getConnectorRef().getValue(),
            accountIdentifier, projectIdentifier, orgIdentifier));
      }
    }
    List<String> connectorRefs = IntegrationStageUtils.getStageConnectorRefs(
        initializeStepInfo.getStageElementConfig(), flexibleTemplatesEnabled);
    if (infrastructure.getType() == Infrastructure.Type.VM || infrastructure.getType() == Infrastructure.Type.DOCKER
        || infrastructure.getType() == Infrastructure.Type.HOSTED_VM) {
      if (!isEmpty(connectorRefs)) {
        entityDetails.addAll(
            connectorRefs.stream()
                .map(connectorIdentifier
                    -> createEntityDetails(connectorIdentifier, accountIdentifier, projectIdentifier, orgIdentifier))
                .collect(Collectors.toList()));
      }
      return entityDetails;
    }
    if (((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }
    K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) infrastructure;
    String infraConnectorRef = k8sDirectInfraYaml.getSpec().getConnectorRef().getValue();
    if (isEmpty(infraConnectorRef)) {
      throw new CIStageExecutionException("Kubernetes connector identifier cannot be empty for the stage.");
    }
    entityDetails.add(createEntityDetails(infraConnectorRef, accountIdentifier, projectIdentifier, orgIdentifier));

    entityDetails.addAll(connectorRefs.stream()
                             .map(connectorIdentifier -> {
                               return createEntityDetails(
                                   connectorIdentifier, accountIdentifier, projectIdentifier, orgIdentifier);
                             })
                             .collect(Collectors.toList()));

    return entityDetails;
  }

  private EntityDetail createEntityDetails(
      String connectorIdentifier, String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
    return EntityDetail.builder().entityRef(connectorRef).type(EntityType.CONNECTORS).build();
  }

  private boolean isHarnessSCM(InitializeStepInfo initializeStepInfo, String accountIdentifier) {
    return isEmpty(initializeStepInfo.getCiCodebase().getConnectorRef().getValue())
        && ciFeatureFlagService.isEnabled(CODE_ENABLED, accountIdentifier);
  }

  private String getInfraTypeYaml(InitializeStepInfo initializeStepInfo) {
    return CIMetricsHelper.infraTypeFrom(initializeStepInfo != null ? initializeStepInfo.getInfrastructure() : null);
  }

  // Absorbs the InitializeStepInfo cast so metric labelling can never fail the surrounding step logic.
  private String safeInfraTypeYaml(StepBaseParameters stepParameters) {
    try {
      return getInfraTypeYaml((InitializeStepInfo) stepParameters.getSpec());
    } catch (Exception ignored) {
      return CIObservabilityConstants.INFRA_TYPE_UNKNOWN;
    }
  }

  // AmbianceUtils.getCurrentLevelStartTs throws when Ambiance.levels is empty.
  private long safeCurrentLevelStartTs(Ambiance ambiance) {
    try {
      return AmbianceUtils.getCurrentLevelStartTs(ambiance);
    } catch (Exception ignored) {
      return 0L;
    }
  }

  private static long elapsedMsSince(long startMs) {
    return CIMetricsHelper.elapsedMsSince(startMs);
  }

  private void recordProvisionInfra(
      Ambiance ambiance, String accountId, String infraType, String outcome, String phase, double latencyMs) {
    CIMetricsHelper.recordSystemApi(executionMetricsService, AmbianceUtils.getStageModuleType(ambiance), accountId,
        infraType, CIObservabilityConstants.OP_PROVISION_INFRA, outcome, phase, null, latencyMs);
  }

  private void addExternalDelegateSelector(
      List<TaskSelector> taskSelectors, InitializeStepInfo initializeStepInfo, Ambiance ambiance) {
    List<TaskSelector> selectorList = TaskSelectorYaml.toTaskSelector(initializeStepInfo.getDelegateSelectors());
    if (isNotEmpty(selectorList)) {
      // Add to selectorList also add to sweeping output so that it can be used during cleanup task
      taskSelectors.addAll(selectorList);
      OptionalSweepingOutput optionalSweepingOutput =
          executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(TASK_SELECTORS));
      if (!optionalSweepingOutput.isFound()) {
        try {
          TaskSelectorSweepingOutput taskSelectorSweepingOutput =
              TaskSelectorSweepingOutput.builder().taskSelectors(selectorList).build();
          executionSweepingOutputResolver.consume(
              ambiance, TASK_SELECTORS, taskSelectorSweepingOutput, StepOutcomeGroup.STAGE.name());
        } catch (Exception e) {
          log.error("Error while consuming taskSelector sweeping output", e);
        }
      }
    }
  }

  private void consumeUniqueStepIdentifiers(InitializeStepInfo initializeStepInfo, Ambiance ambiance) {
    boolean flexibleTemplatesEnabled = InjectUtils.IsFlexibleTemplatesEnabled(ambiance);
    List<String> uniqueStepIdentifiers = IntegrationStageUtils.getStepIdentifiers(
        initializeStepInfo.getExecutionElementConfig().getSteps(), flexibleTemplatesEnabled);
    if (isNotEmpty(uniqueStepIdentifiers)) {
      OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
          ambiance, RefObjectUtils.getOutcomeRefObject(UNIQUE_STEP_IDENTIFIERS));
      if (!optionalSweepingOutput.isFound()) {
        try {
          UniqueStepIdentifiersSweepingOutput uniqueStepIdentifiersSweepingOutput =
              UniqueStepIdentifiersSweepingOutput.builder().uniqueStepIdentifiers(uniqueStepIdentifiers).build();
          executionSweepingOutputResolver.consume(
              ambiance, UNIQUE_STEP_IDENTIFIERS, uniqueStepIdentifiersSweepingOutput, StepOutcomeGroup.STAGE.name());
        } catch (Exception e) {
          log.error("Error while consuming uniqueIdentifiers sweeping output", e);
        }
      }
    }
  }

  private void initStageTelemetryData(InitializeStepInfo initializeStepInfo, Ambiance ambiance) {
    String stageId = ambiance.getStageExecutionId();
    String planExecutionId = ambiance.getPlanExecutionId();
    CIStageTelemetryData ciStageTelemetryData;

    Optional<CIStageTelemetryData> ciStageTelemetryDataResponse =
        ciStageTelemetryRepository.findFirstByStageExecutionId(stageId);

    ciStageTelemetryData =
        ciStageTelemetryDataResponse.orElseGet(()
                                                   -> CIStageTelemetryData.builder()
                                                          .stageExecutionId(stageId)
                                                          .planExecutionId(planExecutionId)
                                                          .accountId(AmbianceUtils.getAccountId(ambiance))
                                                          .ciTelemetryInfo(CITelemetryInfo.builder().build())
                                                          .build());

    CITelemetryInfo ciTelemetryInfo = ciStageTelemetryData.getCiTelemetryInfo();
    Caching caching = initializeStepInfo.getStageElementConfig().getCaching();

    if (caching != null && RunTimeInputHandler.resolveBooleanParameter(caching.getEnabled(), false)) {
      // Set cache info if it is enabled
      if (caching.getEnabled().getValue()) {
        ciTelemetryInfo.setCacheIntelligenceInfo(
            CITelemetryInfo.CacheIntelligenceInfo.builder().isCacheIntelEnabled(true).build());

        List<String> cacheDir = new ArrayList<>();
        if (caching.getPaths() != null) {
          cacheDir = RunTimeInputHandler.resolveListParameter(
              "paths", IMPLICIT_CACHE_STEP, IMPLICIT_CACHE_STEP, caching.getPaths(), false);
        }
        if (cacheDir != null && cacheDir.size() > 0) {
          ciTelemetryInfo.getCacheIntelligenceInfo().setNonDefaultPath(true);
        }

        if (caching.getKey() != null) {
          ciTelemetryInfo.getCacheIntelligenceInfo().setCustomKeys(true);
        }
      }
    }

    BuildIntelligence buildIntelligence = initializeStepInfo.getStageElementConfig().getBuildIntelligence();
    if (buildIntelligence != null
        && RunTimeInputHandler.resolveBooleanParameter(buildIntelligence.getEnabled(), false)) {
      boolean isContainerStepWithImage = hasRunStepsWithImages(initializeStepInfo);
      ciTelemetryInfo.setBuildIntelligenceInfo(CITelemetryInfo.BuildIntelligenceInfo.builder()
                                                   .isBuildIntelEnabled(true)
                                                   .stepTypes(new ArrayList<>())
                                                   .isContainerStepWithImage(isContainerStepWithImage)
                                                   .build());
    }
    Set<String> stepTypesList = getOOTBStepTypes(initializeStepInfo);
    ciTelemetryInfo.setCiStepTypes(stepTypesList);
    ciStageTelemetryData.setCiTelemetryInfo(ciTelemetryInfo);
    ciStageTelemetryRepository.save(ciStageTelemetryData);
  }

  private Set<String> getOOTBStepTypes(InitializeStepInfo initializeStepInfo) {
    Set<String> stepTypesSet = new HashSet<>();
    List<CIAbstractStepNode> allSteps = getAllSteps(initializeStepInfo.getExecutionElementConfig().getSteps());
    allSteps.forEach(step -> stepTypesSet.add(step.getType()));
    return stepTypesSet;
  }

  private boolean hasRunStepsWithImages(InitializeStepInfo initializeStepInfo) {
    List<CIAbstractStepNode> allSteps = getAllSteps(initializeStepInfo.getExecutionElementConfig().getSteps());

    for (CIAbstractStepNode stepNode : allSteps) {
      if (!(stepNode.getStepSpecType() instanceof CIStepInfo)) {
        continue;
      }

      CIStepInfo ciStepInfo = (CIStepInfo) stepNode.getStepSpecType();

      // Check ONLY if Run step has image
      if (ciStepInfo.getStepType() == RunStepInfo.STEP_TYPE) {
        RunStepInfo runStep = (RunStepInfo) ciStepInfo;

        // Extract image value from ParameterField
        ParameterField<String> imageField = runStep.getImage();
        if (imageField != null && isNotEmpty(imageField.getValue())) {
          // Found a Run step with image: "maven:3.8-openjdk-8"
          return true;
        }
      }
    }

    return false;
  }
}
