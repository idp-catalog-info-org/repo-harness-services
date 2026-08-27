/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container;

import static io.harness.beans.FeatureName.REGISTRY_VANITY_URL_ENABLED;
import static io.harness.beans.FeatureName.USE_COMPLETE_STEP_GROUP_ID;
import static io.harness.beans.outcomes.VmDetailsOutcome.VM_DETAILS_OUTCOME;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveOSType;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.TASK_SELECTORS;
import static io.harness.beans.sweepingoutputs.ContainerPortDetails.PORT_DETAILS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.ci.commonconstants.CIExecutionConstants.CI_UPLOAD_LOGS_VIA_HARNESS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_SERVICE_LOG_KEY_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LOG_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LOG_SERVICE_TOKEN_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_PREFIX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_REQUEST_MEMORY_MIB;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_REQUEST_MILLI_CPU;
import static io.harness.common.ParameterFieldHelper.getParameterFieldFinalValueString;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.govern.Switch.unhandled;
import static io.harness.pms.utils.NGPipelineSettingsConstant.DEFAULT_IMAGE_PULL_POLICY_ADD_ON_CONTAINER_SET_TO_EMPTY_BY_DEFAULT;
import static io.harness.pms.utils.NGPipelineSettingsConstant.DEFAULT_IMAGE_PULL_POLICY_ADD_ON_CONTANER;
import static io.harness.steps.container.constants.ContainerStepExecutionConstants.CLEANUP_DETAILS;
import static io.harness.steps.container.execution.output.ContainerDetailsSweepingOutput.INIT_POD;
import static io.harness.steps.plugin.infrastructure.ContainerStepInfra.Type.ECS_DIRECT;
import static io.harness.steps.plugin.infrastructure.ContainerStepInfra.Type.KUBERNETES_DIRECT;
import static io.harness.steps.plugin.infrastructure.ContainerStepInfra.Type.VM;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.environment.ConnectorConversionInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome.VmDetailsOutcomeBuilder;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.EcsStageInfraDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.TaskSelectorSweepingOutput;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.EcsDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.buildstate.StepContainerUtils;
import io.harness.ci.execution.integrationstage.utils.HarnessTokenUtils;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtility;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.remote.CiServiceResourceClient;
import io.harness.ci.utils.ContainerSecretEvaluator;
import io.harness.ci.utils.HarnessRegistryConnectorUtils;
import io.harness.ci.utils.PortFinder;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.ecs.CIECSContainerParams;
import io.harness.delegate.beans.ci.ecs.CIECSInitializeTaskParams;
import io.harness.delegate.beans.ci.ecs.CIECSPodParams;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerParams;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.PodTopologySpreadConstraints;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.taskparams.CIVmInitializeTaskParams;
import io.harness.delegate.task.citasks.cik8handler.params.CIConstants;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.ImageDetails;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.plancreator.inject.InjectUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.ExpressionResolverUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.steps.container.beans.ServiceEnvironmentVars;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.output.ContainerDetailsSweepingOutput;
import io.harness.steps.container.execution.plugin.PluginExecutionConfigHelper;
import io.harness.steps.container.utils.ConnectorUtils;
import io.harness.steps.container.utils.ContainerInfraMapper;
import io.harness.steps.container.utils.ContainerParamsProvider;
import io.harness.steps.container.utils.ContainerStepImageUtils;
import io.harness.steps.container.utils.ContainerStepResolverUtils;
import io.harness.steps.container.utils.ContainerStepV2DefinitionCreator;
import io.harness.steps.container.utils.K8sPodInitUtils;
import io.harness.steps.container.utils.PluginUtils;
import io.harness.steps.container.utils.VmInitializeUtils;
import io.harness.steps.container.utils.yaml.OverlayYamlSecurityValidator;
import io.harness.steps.plugin.ContainerStepInfo;
import io.harness.steps.plugin.ContainerStepSpec;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.PluginStep;
import io.harness.steps.plugin.infrastructure.ContainerCleanupDetails;
import io.harness.steps.plugin.infrastructure.ContainerEcsInfra;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.steps.plugin.infrastructure.ContainerVMInfra;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.RetryUtils;
import io.harness.yaml.core.variables.SecretNGVariable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.tuple.Pair;
import retrofit2.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ECS, HarnessModuleComponent.CDS_PIPELINE,
        HarnessModuleComponent.CDS_COMMON_STEPS})
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepInitHelper {
  private static final String FAILED_TO_FETCH_CI_EXECUTION_CONFIG_MSG =
      "Failed to fetch execution configuration for container creation";
  private static final RetryPolicy<Object> RETRY_POLICY_EXECUTION_CONFIGS = RetryUtils.getRetryPolicy(
      "Error calling CI Manager for fetching execution configs..retrying", FAILED_TO_FETCH_CI_EXECUTION_CONFIG_MSG,
      Collections.singletonList(IOException.class), Duration.ofMillis(10), 3, log);
  private static final String IMAGE_PULL_POLICY_SET_TO_EMPTY = "Empty";
  @Inject private ConnectorUtils connectorUtils;
  @Inject private HarnessRegistryConnectorUtils harnessRegistryConnectorUtils;
  @Inject private ContainerStepImageUtils harnessImageUtils;
  @Inject private ContainerParamsProvider containerParamsProvider;
  @Inject K8sPodInitUtils k8sPodInitUtils;

  @Inject VmInitializeUtils vmInitializeUtils;
  @Inject SecretUtils secretUtils;
  @Inject PluginExecutionConfigHelper pluginExecutionConfigHelper;
  @Inject PluginUtils pluginUtils;
  @Inject CiServiceResourceClient ciServiceResourceClient;
  @Inject LogStreamingServiceConfiguration logStreamingServiceConfiguration;

  @Inject(optional = true) @Nullable private NGSettingsClient settingsClient;

  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject protected FeatureFlagService featureFlagService;
  @Inject(optional = true) private CIFeatureFlagService ciFeatureFlagService;
  @Inject private HarnessTokenUtils harnessTokenUtils;
  @Inject private OutcomeService outcomeService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;

  public CIK8InitializeTaskParams getK8InitializeTaskParams(
      ContainerStepSpec containerStepInfo, Ambiance ambiance, String logPrefix, List<TaskSelector> delegateSelectors) {
    return getK8InitializeTaskParams(
        containerStepInfo, ambiance, logPrefix, containerStepInfo.getIdentifier(), delegateSelectors, false);
  }

  private CIK8InitializeTaskParams buildK8DirectTaskParams(ContainerStepSpec containerStepInfo,
      ContainerDetailsSweepingOutput k8PodDetails, ContainerK8sInfra infrastructure, Ambiance ambiance,
      String logPrefix, List<TaskSelector> delegateSelectors, boolean routeToRunner) {
    NGAccess ngAccess = AmbianceUtils.getNgOidcAccess(ambiance,
        pmsFeatureFlagService.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_OIDC_AWS_SESSION_TAGS),
        outcomeService, executionSweepingOutputResolver);
    String connectorRef = infrastructure.getSpec().getConnectorRef().getValue();

    boolean useSocketCapability = pmsFeatureFlagService.isEnabled(
        ngAccess.getAccountIdentifier(), FeatureName.CDS_K8S_SOCKET_CAPABILITY_CHECK_NG);

    ConnectorDetails k8sConnector = connectorUtils.getConnectorDetails(ngAccess, connectorRef);
    return CIK8InitializeTaskParams.builder()
        .k8sConnector(k8sConnector)
        .cik8PodParams(getK8DirectPodParams(
            containerStepInfo, k8PodDetails, infrastructure, ambiance, logPrefix, delegateSelectors, routeToRunner))
        .podMaxWaitUntilReadySecs(k8sPodInitUtils.getPodWaitUntilReadTimeout(infrastructure))
        .useSocketCapability(useSocketCapability)
        .skipImagePullSecret(k8sPodInitUtils.shouldSkipImagePullSecret(ambiance))
        .build();
  }

  private CIK8PodParams<CIK8ContainerParams> getK8DirectPodParams(ContainerStepSpec containerStepInfo,
      ContainerDetailsSweepingOutput k8PodDetails, ContainerK8sInfra k8sDirectInfraYaml, Ambiance ambiance,
      String logPrefix, List<TaskSelector> delegateSelectors, boolean routeToRunner) {
    String podName = getPodName(containerStepInfo.getIdentifier().toLowerCase());
    Map<String, String> buildLabels = k8sPodInitUtils.getLabels(
        ambiance, ContainerUnitStepUtils.getKubernetesStandardPodName(containerStepInfo.getIdentifier()));
    Map<String, String> annotations = ExpressionResolverUtils.resolveMapParameter(
        "annotations", "ContainerStep", "stepSetup", k8sDirectInfraYaml.getSpec().getAnnotations(), false);
    Map<String, String> labels = ExpressionResolverUtils.resolveMapParameter(
        "labels", "ContainerStep", "stepSetup", k8sDirectInfraYaml.getSpec().getLabels(), false);
    Map<String, String> nodeSelector = ExpressionResolverUtils.resolveMapParameter(
        "nodeSelector", "ContainerStep", "stepSetup", k8sDirectInfraYaml.getSpec().getNodeSelector(), false);
    Integer stepAsUser =
        ExpressionResolverUtils.resolveIntegerParameter(k8sDirectInfraYaml.getSpec().getRunAsUser(), null);
    String serviceAccountName = ExpressionResolverUtils.resolveStringParameter("serviceAccountName", "ContainerStep",
        "stageSetup", k8sDirectInfraYaml.getSpec().getServiceAccountName(), false);

    if (isNotEmpty(labels)) {
      buildLabels.putAll(labels);
    }

    List<PodVolume> volumes = k8sPodInitUtils.convertDirectK8Volumes(k8sDirectInfraYaml, ambiance);
    Pair<Integer, Integer> stageRequest = k8sPodInitUtils.getStepLimits(
        containerStepInfo, AmbianceUtils.getAccountId(ambiance), InjectUtils.IsFlexibleTemplatesEnabled(ambiance));
    Pair<Optional<CIK8ContainerParams>, List<CIK8ContainerParams>> podContainers = getStepContainers(
        containerStepInfo, k8PodDetails, k8sDirectInfraYaml, ambiance, volumes, logPrefix, stageRequest);
    saveSweepingOutput(podName, k8sDirectInfraYaml, podContainers, ambiance, delegateSelectors, routeToRunner);

    boolean enableOverlayYaml =
        ciFeatureFlagService.isEnabled(FeatureName.CI_K8S_OVERLAY_YAML, AmbianceUtils.getAccountId(ambiance));
    List<PodTopologySpreadConstraints> topologySpreadConstraints = null;
    String overlayYaml = null;
    if (enableOverlayYaml) {
      overlayYaml = resolveStringParameter(
          "podSpecOverlay", null, "stageSetup", k8sDirectInfraYaml.getSpec().getPodSpecOverlay(), false);
      if (ciFeatureFlagService.isEnabled(
              FeatureName.CI_K8S_OVERLAY_YAML_SECRET_RESTRICTION, AmbianceUtils.getAccountId(ambiance))) {
        OverlayYamlSecurityValidator.validateNoSecretReferences(overlayYaml);
      }
    } else {
      topologySpreadConstraints =
          k8sPodInitUtils.getTopologySpreadConstraintsList(k8sDirectInfraYaml.getSpec().getPodSpecOverlay());
    }

    return CIK8PodParams.<CIK8ContainerParams>builder()
        .name(podName)
        .namespace(getParameterFieldValue(k8sDirectInfraYaml.getSpec().getNamespace()))
        .labels(buildLabels)
        .serviceAccountName(serviceAccountName)
        .annotations(annotations)
        .nodeSelector(nodeSelector)
        .runAsUser(stepAsUser)
        .automountServiceAccountToken(k8sDirectInfraYaml.getSpec().getAutomountServiceAccountToken().getValue())
        .priorityClassName(k8sDirectInfraYaml.getSpec().getPriorityClassName().getValue())
        .containerParamsList(podContainers.getRight())
        .initContainerParamsList(podContainers.getLeft().map(Collections::singletonList).orElse(emptyList()))
        .tolerations(k8sPodInitUtils.getPodTolerations(k8sDirectInfraYaml.getSpec().getTolerations()))
        .overlayYaml(overlayYaml)
        .topologySpreadConstraints(topologySpreadConstraints)
        .activeDeadLineSeconds(getActiveDeadlineSeconds(ambiance, true))
        .volumes(volumes)
        .stageCpuMilli(stageRequest.getLeft())
        .stageMemoryMiB(stageRequest.getRight())
        .build();
  }

  private List<String> getSharedPaths(ContainerStepSpec initializeStepInfo) {
    if (initializeStepInfo instanceof InitContainerV2StepInfo) {
      InitContainerV2StepInfo initConfig = (InitContainerV2StepInfo) initializeStepInfo;
      return (List<String>) initConfig.getSharedPaths().fetchFinalValue();
    }
    return Collections.emptyList();
  }

  private Pair<Optional<CIK8ContainerParams>, List<CIK8ContainerParams>> getStepContainers(
      ContainerStepSpec containerStepInfo, ContainerDetailsSweepingOutput k8PodDetails,
      ContainerK8sInfra infrastructure, Ambiance ambiance, List<PodVolume> volumes, String logPrefix,
      Pair<Integer, Integer> wrapperRequests) {
    List<String> sharedPaths = getSharedPaths(containerStepInfo);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    boolean ephemeralDelegateMode =
        featureFlagService.isEnabled(FeatureName.PIPE_ENABLE_EPHEMERAL_DELEGATE_MODE, accountId);
    Map<String, String> volumeToMountPath =
        k8sPodInitUtils.getVolumeToMountPath(sharedPaths, volumes, !ephemeralDelegateMode);
    OSType os = k8sPodInitUtils.getOS(infrastructure);
    NGAccess ngAccess = AmbianceUtils.getNgOidcAccess(ambiance,
        pmsFeatureFlagService.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_OIDC_AWS_SESSION_TAGS),
        outcomeService, executionSweepingOutputResolver);

    Map<String, String> commonEnvVars =
        k8sPodInitUtils.getCommonStepEnvVariables(containerStepInfo, k8sPodInitUtils.getWorkDir(), logPrefix, ambiance);
    commonEnvVars.putAll(harnessTokenUtils.getHarnessBaseUrlEnvVariable(accountId));

    Map<String, String> principalTokenEnvVars = new HashMap<>();
    Map<String, String> tokenVars =
        harnessTokenUtils.getPrincipalTokenEnvVariables(ambiance, accountId, containerStepInfo.getPermissions());
    if (tokenVars != null) {
      principalTokenEnvVars.putAll(tokenVars);
    }

    ServiceEnvironmentVars serviceEnvironmentVars = k8sPodInitUtils.getServiceEnvironmentVars(k8PodDetails, accountId);

    ConnectorDetails harnessInternalImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForK8(ngAccess, infrastructure);

    ContainerSecretEvaluator liteEngineSecretEvaluator =
        ContainerSecretEvaluator.builder()
            .secretUtils(secretUtils)
            .withSingleQuotes(AmbianceUtils.checkIfFeatureFlagEnabled(
                ambiance, FeatureName.CDS_USE_SINGLE_QUOTES_IN_SECRET_FUNCTOR.name()))
            .build();
    List<SecretVariableDetails> secretVariableDetails =
        liteEngineSecretEvaluator.resolve(containerStepInfo, ngAccess, ambiance.getExpressionFunctorToken());
    k8sPodInitUtils.checkSecretAccess(ambiance, secretVariableDetails, accountId,
        AmbianceUtils.getProjectIdentifier(ambiance), AmbianceUtils.getOrgIdentifier(ambiance));

    Integer stageCpuRequest = wrapperRequests.getLeft();
    Integer stageMemoryRequest = wrapperRequests.getRight();
    List<CIK8ContainerParams> containerParams = new ArrayList<>();

    final CIExecutionImages overridenExecutionConfig =
        fetchCiExecutionImagesWithRetries(accountId, ContainerInfraMapper.toStageInfraType(infrastructure))
            .orElse(null);

    String imagePullPolicy = null;
    if (featureFlagService.isEnabledReloadCache(
            FeatureName.CDS_DEFAULT_IMAGE_PULL_POLICY_ADD_ON_CONTAINER_SET_TO_EMPTY_BY_DEFAULT,
            ngAccess.getAccountIdentifier())) {
      imagePullPolicy = AmbianceUtils.getSettingValue(
          ambiance, DEFAULT_IMAGE_PULL_POLICY_ADD_ON_CONTAINER_SET_TO_EMPTY_BY_DEFAULT.getName());
      if (IMAGE_PULL_POLICY_SET_TO_EMPTY.equals(imagePullPolicy)) {
        imagePullPolicy = null;
      }
    } else {
      imagePullPolicy = AmbianceUtils.getSettingValue(ambiance, DEFAULT_IMAGE_PULL_POLICY_ADD_ON_CONTANER.getName());
    }
    Optional<CIK8ContainerParams> setupAddOnContainerParams = ephemeralDelegateMode
        ? Optional.empty()
        : Optional.of(getSetupAddOnContainerParams(infrastructure, volumeToMountPath, os, ngAccess,
              harnessInternalImageConnector, overridenExecutionConfig, imagePullPolicy));

    Optional<CIK8ContainerParams> liteEngineContainerParams = ephemeralDelegateMode
        ? Optional.empty()
        : Optional.of(getLiteEngineContainerParams(k8PodDetails, infrastructure, ambiance, logPrefix, volumeToMountPath,
              serviceEnvironmentVars, harnessInternalImageConnector, stageCpuRequest, stageMemoryRequest,
              overridenExecutionConfig, imagePullPolicy, containerStepInfo));
    List<ContainerDefinitionInfo> stepCtrDefinitions = getContainerDefinitionInfos(containerStepInfo, infrastructure,
        ambiance, logPrefix, volumeToMountPath, os, ngAccess, commonEnvVars, serviceEnvironmentVars,
        harnessInternalImageConnector, secretVariableDetails, containerParams, principalTokenEnvVars);

    consumePortDetails(ambiance, stepCtrDefinitions);
    consumeContainerDetails(ambiance, stepCtrDefinitions, k8PodDetails);
    liteEngineContainerParams.ifPresent(containerParams::add);
    return Pair.of(setupAddOnContainerParams, containerParams);
  }

  private List<ContainerDefinitionInfo> getContainerDefinitionInfos(ContainerStepSpec containerStepInfo,
      ContainerK8sInfra infrastructure, Ambiance ambiance, String logPrefix, Map<String, String> volumeToMountPath,
      OSType os, NGAccess ngAccess, Map<String, String> commonEnvVars, ServiceEnvironmentVars serviceEnvironmentVars,
      ConnectorDetails harnessInternalImageConnector, List<SecretVariableDetails> secretVariableDetails,
      List<CIK8ContainerParams> containerParams, Map<String, String> principalTokenEnvVars) {
    List<ContainerDefinitionInfo> stepCtrDefinitions =
        getStepContainerDefinitions(containerStepInfo, infrastructure, ambiance);
    Map<String, List<ConnectorConversionInfo>> stepConnectorMap = getStepConnectorRefsV2(containerStepInfo,
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, USE_COMPLETE_STEP_GROUP_ID.name())
            ? AmbianceUtils.getAllStepGroupIdentifiersForCurrentLevel(ambiance)
            : AmbianceUtils.obtainStepGroupIdentifier(ambiance),
        ngAccess.getAccountIdentifier());
    for (ContainerDefinitionInfo containerDefinitionInfo : stepCtrDefinitions) {
      CIK8ContainerParams cik8ContainerParams = createCIK8ContainerParams(ngAccess, containerDefinitionInfo,
          harnessInternalImageConnector, commonEnvVars, serviceEnvironmentVars, stepConnectorMap, volumeToMountPath,
          k8sPodInitUtils.getWorkDir(), k8sPodInitUtils.getCtrSecurityContext(infrastructure), logPrefix,
          secretVariableDetails, os, ambiance, principalTokenEnvVars);
      containerParams.add(cik8ContainerParams);
    }
    return stepCtrDefinitions;
  }

  private Map<String, List<ConnectorConversionInfo>> getStepConnectorRefsV2(
      ContainerStepSpec containerStepInfo, String stepGroupIdentifier, String accountIdentifier) {
    Map<String, List<ConnectorConversionInfo>> stepConnectorMap = new HashMap<>();
    if (containerStepInfo instanceof PluginStep) {
      PluginStep pluginStep = (PluginStep) containerStepInfo;
      String identifier = containerStepInfo.getIdentifier();
      if (isNotEmpty(stepGroupIdentifier)) {
        identifier = stepGroupIdentifier + "_" + identifier;
      }
      stepConnectorMap.put(identifier, new ArrayList<>());
      // This is required for ContainerStep V1.
      String kubernetesStandardPodName =
          ContainerUnitStepUtils.getKubernetesStandardPodName(containerStepInfo.getIdentifier());
      stepConnectorMap.put(kubernetesStandardPodName, new ArrayList<>());
      String connectorRef = PluginUtils.getConnectorRef(pluginStep);
      if (EmptyPredicate.isEmpty(connectorRef)) {
        return stepConnectorMap;
      }
      Map<EnvVariableEnum, String> envToSecretMap = PluginUtils.getConnectorSecretEnvMap(pluginStep.getType());
      stepConnectorMap.get(identifier)
          .add(ConnectorConversionInfo.builder().connectorRef(connectorRef).envToSecretsMap(envToSecretMap).build());
      stepConnectorMap.get(kubernetesStandardPodName)
          .add(ConnectorConversionInfo.builder().connectorRef(connectorRef).envToSecretsMap(envToSecretMap).build());
    } else if (containerStepInfo instanceof InitContainerV2StepInfo) {
      InitContainerV2StepInfo initContainerV2StepInfo = (InitContainerV2StepInfo) containerStepInfo;
      initContainerV2StepInfo.getPluginsData().values().forEach(PluginCreationResponseWrapper -> {
        for (PluginCreationResponseWrapper responseV2 : PluginCreationResponseWrapper.getResponseList()) {
          List<io.harness.pms.contracts.plan.ConnectorDetails> connectorsForStepList =
              responseV2.getResponse().getPluginDetails().getConnectorsForStepList();
          if (isNotEmpty(connectorsForStepList)) {
            List<ConnectorConversionInfo> connectorConversionInfo =
                connectorsForStepList.stream()
                    .map(detail
                        -> ConnectorConversionInfo.builder()
                               .connectorRef(detail.getConnectorRef())
                               .registryRef(detail.getRegistryRef())
                               .isHarnessCodeRepo(detail.getIsHarnessCodeRepo())
                               .harnessCodeToken(detail.getHarnessCodeToken())
                               .envToSecretsMap(new HashMap<>(convertDetailMap(detail.getConnectorSecretEnvMapMap())))
                               .build())
                    .collect(toList());
            String identifier = responseV2.getStepInfo().getIdentifier();
            if (isNotEmpty(stepGroupIdentifier)) {
              identifier = stepGroupIdentifier + "_" + identifier;
            }
            stepConnectorMap.put(identifier, connectorConversionInfo);
          }
        }
      });
    }
    return stepConnectorMap;
  }

  private CIK8ContainerParams getLiteEngineContainerParams(ContainerDetailsSweepingOutput k8PodDetails,
      ContainerK8sInfra infrastructure, Ambiance ambiance, String logPrefix, Map<String, String> volumeToMountPath,
      ServiceEnvironmentVars serviceEnvironmentVars, ConnectorDetails harnessInternalImageConnector,
      Integer stageCpuRequest, Integer stageMemoryRequest, CIExecutionImages ciExecutionImages, String imagePullPolicy,
      ContainerStepSpec containerStepInfo) {
    return containerParamsProvider.getLiteEngineContainerParams(harnessInternalImageConnector, k8PodDetails,
        stageCpuRequest, stageMemoryRequest, serviceEnvironmentVars, volumeToMountPath, k8sPodInitUtils.getWorkDir(),
        k8sPodInitUtils.getCtrSecurityContext(infrastructure), logPrefix, ambiance, ciExecutionImages, imagePullPolicy,
        containerStepInfo);
  }

  private CIK8ContainerParams getSetupAddOnContainerParams(ContainerK8sInfra infrastructure,
      Map<String, String> volumeToMountPath, OSType os, NGAccess ngAccess,
      ConnectorDetails harnessInternalImageConnector, CIExecutionImages overridenExecutionImages,
      String imagePullPolicy) {
    return containerParamsProvider.getSetupAddonContainerParams(harnessInternalImageConnector, volumeToMountPath,
        k8sPodInitUtils.getWorkDir(), k8sPodInitUtils.getCtrSecurityContext(infrastructure), os,
        overridenExecutionImages, imagePullPolicy);
  }

  private enum StepContainerBuildTarget { K8, ECS }

  private CIK8ContainerParams createCIK8ContainerParams(NGAccess ngAccess,
      ContainerDefinitionInfo containerDefinitionInfo, ConnectorDetails harnessInternalImageConnector,
      Map<String, String> commonEnvVars, ServiceEnvironmentVars serviceEnvironmentVars,
      Map<String, List<ConnectorConversionInfo>> connectorRefs, Map<String, String> volumeToMountPath,
      String workDirPath, ContainerSecurityContext ctrSecurityContext, String logPrefix,
      List<SecretVariableDetails> secretVariableDetails, OSType os, Ambiance ambiance,
      Map<String, String> principalTokenEnvVars) {
    return (CIK8ContainerParams) buildStepContainerParams(ngAccess, containerDefinitionInfo,
        harnessInternalImageConnector, commonEnvVars, serviceEnvironmentVars, connectorRefs, volumeToMountPath,
        workDirPath, ctrSecurityContext, logPrefix, secretVariableDetails, os, ambiance, StepContainerBuildTarget.K8,
        principalTokenEnvVars);
  }

  private CIECSContainerParams createCIECSContainerParams(NGAccess ngAccess,
      ContainerDefinitionInfo containerDefinitionInfo, ConnectorDetails harnessInternalImageConnector,
      Map<String, String> commonEnvVars, ServiceEnvironmentVars serviceEnvironmentVars,
      Map<String, List<ConnectorConversionInfo>> connectorRefs, Map<String, String> volumeToMountPath,
      String workDirPath, ContainerSecurityContext ctrSecurityContext, String logPrefix,
      List<SecretVariableDetails> secretVariableDetails, OSType os, Ambiance ambiance,
      Map<String, String> principalTokenEnvVars) {
    return (CIECSContainerParams) buildStepContainerParams(ngAccess, containerDefinitionInfo,
        harnessInternalImageConnector, commonEnvVars, serviceEnvironmentVars, connectorRefs, volumeToMountPath,
        workDirPath, ctrSecurityContext, logPrefix, secretVariableDetails, os, ambiance, StepContainerBuildTarget.ECS,
        principalTokenEnvVars);
  }

  private ContainerParams buildStepContainerParams(NGAccess ngAccess, ContainerDefinitionInfo containerDefinitionInfo,
      ConnectorDetails harnessInternalImageConnector, Map<String, String> commonEnvVars,
      ServiceEnvironmentVars serviceEnvironmentVars, Map<String, List<ConnectorConversionInfo>> connectorRefs,
      Map<String, String> volumeToMountPath, String workDirPath, ContainerSecurityContext ctrSecurityContext,
      String logPrefix, List<SecretVariableDetails> secretVariableDetails, OSType os, Ambiance ambiance,
      StepContainerBuildTarget target, Map<String, String> principalTokenEnvVars) {
    Map<String, String> envVars = new HashMap<>();
    if (isNotEmpty(containerDefinitionInfo.getEnvVars())) {
      envVars.putAll(containerDefinitionInfo.getEnvVars()); // Put customer input env variables
    }
    Map<String, ConnectorDetails> stepConnectorDetails = new HashMap<>();
    if (isNotEmpty(containerDefinitionInfo.getStepIdentifier()) && isNotEmpty(connectorRefs)) {
      List<ConnectorConversionInfo> connectorConversionInfos =
          connectorRefs.get(containerDefinitionInfo.getStepIdentifier());
      if (isNotEmpty(connectorConversionInfos)) {
        for (ConnectorConversionInfo connectorConversionInfo : connectorConversionInfos) {
          ConnectorDetails connectorDetails =
              connectorUtils.getConnectorDetailsWithConversionInfo(ngAccess, connectorConversionInfo);
          IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(connectorConversionInfo.getConnectorRef(),
              ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
          stepConnectorDetails.put(identifierRef.getFullyQualifiedName(), connectorDetails);
        }
      }
    }

    ImageDetails imageDetails = containerDefinitionInfo.getContainerImageDetails().getImageDetails();
    ConnectorDetails connectorDetails = null;
    if (containerDefinitionInfo.getContainerImageDetails().getConnectorIdentifier() != null) {
      connectorDetails = connectorUtils.getConnectorDetails(
          ngAccess, containerDefinitionInfo.getContainerImageDetails().getConnectorIdentifier());
    }

    boolean isHarnessArtifactFFEnabled =
        featureFlagService.isEnabled(FeatureName.HAR_ENABLED, ngAccess.getAccountIdentifier());
    boolean isVanityEnabled = false;
    if (isHarnessArtifactFFEnabled && containerDefinitionInfo.getContainerImageDetails().getRegistryRef() != null) {
      isVanityEnabled = featureFlagService.isEnabled(REGISTRY_VANITY_URL_ENABLED, ngAccess.getAccountIdentifier());
      connectorDetails = harnessRegistryConnectorUtils.getConnectorDetailsForHarnessArtifactRegistry(ngAccess);
    }
    ConnectorDetails imgConnector = connectorDetails;
    if (containerDefinitionInfo.isHarnessManagedImage()) {
      imgConnector = harnessInternalImageConnector;
    }
    String fullyQualifiedImageName = IntegrationStageUtility.getFullyQualifiedImageName(imageDetails.getName(),
        imgConnector, ngAccess, containerDefinitionInfo.getContainerImageDetails().getRegistryRef(),
        isHarnessArtifactFFEnabled, isVanityEnabled);
    imageDetails.setName(fullyQualifiedImageName);
    ImageDetailsWithConnector imageDetailsWithConnector =
        ImageDetailsWithConnector.builder().imageConnectorDetails(imgConnector).imageDetails(imageDetails).build();

    List<SecretVariableDetails> containerSecretVariableDetails =
        k8sPodInitUtils.getSecretVariableDetails(ngAccess, containerDefinitionInfo, secretVariableDetails);

    Map<String, String> envVarsWithSecretRef = k8sPodInitUtils.removeEnvVarsWithSecretRef(envVars);
    envVars.putAll(commonEnvVars); //  commonEnvVars needs to be put in end because they overrides webhook parameters
    if (containerDefinitionInfo.getContainerType() == CIContainerType.SERVICE) {
      envVars.put(HARNESS_SERVICE_LOG_KEY_VARIABLE,
          format("%s/serviceId:%s", logPrefix, containerDefinitionInfo.getStepIdentifier()));
    }

    if (containerDefinitionInfo.getPrivileged() != null) {
      ctrSecurityContext.setPrivileged(containerDefinitionInfo.getPrivileged());
    }
    if (containerDefinitionInfo.getRunAsUser() != null) {
      ctrSecurityContext.setRunAsUser(containerDefinitionInfo.getRunAsUser());
    }
    boolean privileged = containerDefinitionInfo.getPrivileged() != null && containerDefinitionInfo.getPrivileged();
    Map<String, String> envVarsWithPlainTextSecret = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(containerDefinitionInfo.getEnvVarsWithPlainTextSecret())) {
      envVarsWithPlainTextSecret = containerDefinitionInfo.getEnvVarsWithPlainTextSecret();
    }

    ContainerSecrets containerSecrets =
        ContainerSecrets.builder()
            .secretVariableDetails(containerSecretVariableDetails)
            .connectorDetailsMap(stepConnectorDetails)
            .plainTextSecretsByName(containerParamsProvider.getLiteEngineSecretVars(
                envVarsWithPlainTextSecret, emptyMap(), serviceEnvironmentVars.getStoEnvVars(), principalTokenEnvVars))
            .build();

    ContainerParams stepContainerParams;
    if (target == StepContainerBuildTarget.ECS) {
      stepContainerParams = CIECSContainerParams.builder()
                                .name(containerDefinitionInfo.getName())
                                .containerResourceParams(containerDefinitionInfo.getContainerResourceParams())
                                .containerType(containerDefinitionInfo.getContainerType())
                                .envVars(envVars)
                                .envVarsWithSecretRef(envVarsWithSecretRef)
                                .containerSecrets(containerSecrets)
                                .commands(containerDefinitionInfo.getCommands())
                                .ports(containerDefinitionInfo.getPorts())
                                .args(containerDefinitionInfo.getArgs())
                                .imageDetailsWithConnector(imageDetailsWithConnector)
                                .volumeToMountPath(volumeToMountPath)
                                .imagePullPolicy(containerDefinitionInfo.getImagePullPolicy())
                                .securityContext(ctrSecurityContext)
                                .build();
    } else {
      stepContainerParams = CIK8ContainerParams.builder()
                                .name(containerDefinitionInfo.getName())
                                .containerResourceParams(containerDefinitionInfo.getContainerResourceParams())
                                .containerType(containerDefinitionInfo.getContainerType())
                                .envVars(envVars)
                                .envVarsWithSecretRef(envVarsWithSecretRef)
                                .containerSecrets(containerSecrets)
                                .commands(containerDefinitionInfo.getCommands())
                                .ports(containerDefinitionInfo.getPorts())
                                .args(containerDefinitionInfo.getArgs())
                                .imageDetailsWithConnector(imageDetailsWithConnector)
                                .volumeToMountPath(volumeToMountPath)
                                .imagePullPolicy(containerDefinitionInfo.getImagePullPolicy())
                                .securityContext(ctrSecurityContext)
                                .build();
    }

    if (os != OSType.Windows) {
      stepContainerParams.setPrivileged(privileged);
      stepContainerParams.setRunAsUser(containerDefinitionInfo.getRunAsUser());
    }

    if (containerDefinitionInfo.getContainerType() != CIContainerType.SERVICE) {
      stepContainerParams.setWorkingDir(workDirPath);
    }
    return stepContainerParams;
  }

  private List<ContainerDefinitionInfo> getStepContainerDefinitions(
      ContainerStepSpec initializeStepInfo, ContainerK8sInfra infrastructure, Ambiance ambiance) {
    return getStepContainerDefinitionsInternal(initializeStepInfo, ambiance, k8sPodInitUtils.getOS(infrastructure));
  }

  private List<ContainerDefinitionInfo> getStepContainerDefinitionsInternal(
      ContainerStepSpec initializeStepInfo, Ambiance ambiance, OSType os) {
    Set<Integer> usedPorts = new HashSet<>();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(usedPorts).build();

    return createStepContainerDefinitions(
        initializeStepInfo, portFinder, AmbianceUtils.getAccountId(ambiance), os, ambiance);
  }

  private void saveSweepingOutput(String podName, ContainerK8sInfra infrastructure,
      Pair<Optional<CIK8ContainerParams>, List<CIK8ContainerParams>> podContainers, Ambiance ambiance,
      List<TaskSelector> delegateSelectors, boolean routeToRunner) {
    List<String> containerNames = podContainers.getRight().stream().map(CIK8ContainerParams::getName).collect(toList());
    podContainers.getLeft().map(CIK8ContainerParams::getName).ifPresent(containerNames::add);

    k8sPodInitUtils.consumeSweepingOutput(ambiance,
        ContainerCleanupDetails.builder()
            .infrastructure(infrastructure)
            .podName(podName)
            .cleanUpContainerNames(containerNames)
            .delegateSelectors(delegateSelectors)
            .build(),
        CLEANUP_DETAILS);
    k8sPodInitUtils.consumeSweepingOutput(ambiance,
        K8StageInfraDetails.builder()
            .infrastructure(infrastructure.toCIInfra())
            .podName(podName)
            .containerNames(containerNames)
            .delegateSelectors(delegateSelectors)
            .routeToRunner(routeToRunner)
            .build(),
        STAGE_INFRA_DETAILS);

    // CD uses utility steps provided by CI, i.e. GitClone, Background, Run etc.
    // These steps are of type AbstractStepExecutable. CI has their own logic to determine the delegate selectors for
    // these steps (i.e. fetchDelegateSelector() method). They look for value saved as TASK_SELECTORS. If this is not
    // found, then take the delegate selector provided for k8s infra connector at step group.
    // Therefore, we need to save the delegateSelectors as TASK_SELECTORS
    TaskSelectorSweepingOutput taskSelectorSweepingOutput =
        TaskSelectorSweepingOutput.builder().taskSelectors(delegateSelectors).build();
    k8sPodInitUtils.consumeSweepingOutput(ambiance, taskSelectorSweepingOutput, TASK_SELECTORS);
  }

  private void saveSweepingOutputForVM(Ambiance ambiance, String poolId, String workDir,
      String harnessImageConnectorRef, Map<String, String> volToMountPath, CIInitializeTaskParams.Type infraInfo,
      Optional<String> resourceClass, ContainerStepInfra containerStepInfra, String containerStepGroupRuntimeid) {
    k8sPodInitUtils.consumeSweepingOutput(ambiance,
        ContainerCleanupDetails.builder()
            .infrastructure(containerStepInfra)
            .stepGroupRuntimeId(containerStepGroupRuntimeid)
            .build(),
        CLEANUP_DETAILS);
    k8sPodInitUtils.consumeSweepingOutput(ambiance,
        VmStageInfraDetails.builder()
            .poolId(poolId)
            .workDir(workDir)
            .volToMountPathMap(volToMountPath)
            .harnessImageConnectorRef(harnessImageConnectorRef)
            .infraInfo(infraInfo)
            .build(),
        STAGE_INFRA_DETAILS);
  }

  private String getPodName(String stageId) {
    return k8sPodInitUtils.generatePodName(stageId);
  }

  private void consumePortDetails(Ambiance ambiance, List<ContainerDefinitionInfo> containerDefinitionInfos) {
    Map<String, List<Integer>> portDetails = containerDefinitionInfos.stream().collect(
        Collectors.toMap(ContainerDefinitionInfo::getStepIdentifier, ContainerDefinitionInfo::getPorts));
    k8sPodInitUtils.consumeSweepingOutput(
        ambiance, ContainerPortDetails.builder().portDetails(portDetails).build(), PORT_DETAILS);
  }

  private void consumeContainerDetails(Ambiance ambiance, List<ContainerDefinitionInfo> containerDefinitionInfos,
      ContainerDetailsSweepingOutput k8PodDetails) {
    if (EmptyPredicate.isEmpty(containerDefinitionInfos)) {
      k8sPodInitUtils.consumeSweepingOutput(ambiance, k8PodDetails, INIT_POD);
      return;
    }
    Map<String, String> nameDetails =
        containerDefinitionInfos.stream()
            .filter(info -> info != null && info.getStepIdentifier() != null && info.getName() != null)
            .collect(Collectors.toMap(ContainerDefinitionInfo::getStepIdentifier, ContainerDefinitionInfo::getName,
                (existing, replacement) -> existing));
    Map<String, List<Integer>> portDetails =
        containerDefinitionInfos.stream()
            .filter(info -> info != null && info.getStepIdentifier() != null && info.getPorts() != null)
            .collect(Collectors.toMap(ContainerDefinitionInfo::getStepIdentifier, ContainerDefinitionInfo::getPorts,
                (existing, replacement) -> existing));

    // Use toBuilder() to add additional details to existing object
    ContainerDetailsSweepingOutput updatedDetails =
        k8PodDetails.toBuilder().nameDetails(nameDetails).portDetails(portDetails).build();
    k8sPodInitUtils.consumeSweepingOutput(ambiance, updatedDetails, INIT_POD);
  }

  private List<ContainerDefinitionInfo> createStepContainerDefinitions(
      ContainerStepSpec containerStepInfo, PortFinder portFinder, String accountId, OSType os, Ambiance ambiance) {
    boolean flexibleTemplateEnabled = InjectUtils.IsFlexibleTemplatesEnabled(ambiance);
    switch (containerStepInfo.getType()) {
      case RUN_CONTAINER:
        return Collections.singletonList(createStepContainerDefinition(
            (ContainerStepInfo) containerStepInfo, portFinder, accountId, os, flexibleTemplateEnabled));
      case CD_SSCA_ORCHESTRATION:
      case CD_SSCA_ENFORCEMENT:
        return Collections.singletonList(
            createPluginStepContainerDefinition((PluginStep) containerStepInfo, portFinder, accountId, os, ambiance));
      case INIT_CONTAINER_V2:
        String stepGroupIdentifier =
            AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, USE_COMPLETE_STEP_GROUP_ID.name())
            ? AmbianceUtils.getAllStepGroupIdentifiersForCurrentLevel(ambiance)
            : AmbianceUtils.obtainStepGroupIdentifier(ambiance);
        boolean isCustomResourceEnabled =
            featureFlagService.isEnabled(FeatureName.CI_SUPPORT_RESOURCE_REQUESTS, accountId);
        return ContainerStepV2DefinitionCreator.getContainerDefinitionInfo(
            (InitContainerV2StepInfo) containerStepInfo, stepGroupIdentifier, ambiance, isCustomResourceEnabled);
      default:
        throw new ContainerStepExecutionException("Container step initialization not handled");
    }
  }

  private ContainerDefinitionInfo createPluginStepContainerDefinition(
      PluginStep pluginStep, PortFinder portFinder, String accountId, OSType os, Ambiance ambiance) {
    Integer port = portFinder.getNextPort();
    String identifier = ContainerUnitStepUtils.getKubernetesStandardPodName(pluginStep.getIdentifier());
    String containerName = format("%s%s", STEP_PREFIX, identifier).toLowerCase();

    Map<String, String> envMap =
        new HashMap<>(pluginUtils.getPluginCompatibleEnvVariables(pluginStep, identifier, ambiance));
    Map<String, SecretNGVariable> secretNGVariableMap =
        new HashMap<>(pluginUtils.getPluginCompatibleSecretVars(pluginStep, identifier));
    boolean flexibleTemplateEnabled = InjectUtils.IsFlexibleTemplatesEnabled(ambiance);

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand(os))
        .args(StepContainerUtils.getArguments(port))
        .envVars(envMap)
        .secretVariables(new ArrayList<>(secretNGVariableMap.values()))
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(k8sPodInitUtils.getImageInfo(
                                       pluginExecutionConfigHelper.getPluginImage(pluginStep).getImage()))
                                   .build())
        .isHarnessManagedImage(true)
        .containerResourceParams(getStepContainerResource(pluginStep, accountId, flexibleTemplateEnabled))
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.PLUGIN)
        .stepIdentifier(identifier)
        .stepName(pluginStep.getName())
        .imagePullPolicy(null)
        .privileged(null)
        .runAsUser(null)
        .build();
  }

  private ContainerDefinitionInfo createStepContainerDefinition(ContainerStepInfo runStepInfo, PortFinder portFinder,
      String accountId, OSType os, boolean flexibleTemplateEnabled) {
    if (runStepInfo.getImage() == null) {
      throw new CIStageExecutionException(
          format("With a Kubernetes cluster build infrastructure, image is required for stepId: %s and stepName: %s",
              runStepInfo.getIdentifier(), runStepInfo.getName()));
    }

    if (ParameterField.isNull(runStepInfo.getConnectorRef()) && ParameterField.isNull(runStepInfo.getRegistryRef())) {
      throw new ContainerStepExecutionException(format("With a Kubernetes cluster build infrastructure, connector ref "
              + "or registry ref is required for stepId: %s and stepName: %s",
          runStepInfo.getIdentifier(), runStepInfo.getName()));
    }
    String identifier = ContainerUnitStepUtils.getKubernetesStandardPodName(runStepInfo.getIdentifier());
    Integer port = portFinder.getNextPort();
    String containerName = format("%s%s", STEP_PREFIX, identifier).toLowerCase();

    Map<String, String> stepEnvVars = new HashMap<>();
    Map<String, String> envvars = ExpressionResolverUtils.resolveMapParameter(
        "envVariables", "Run", identifier, runStepInfo.getEnvVariables(), false);
    if (!isEmpty(envvars)) {
      stepEnvVars.putAll(envvars);
    }
    Integer runAsUser = ExpressionResolverUtils.resolveIntegerParameter(runStepInfo.getRunAsUser(), null);

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand(os))
        .args(StepContainerUtils.getArguments(port))
        .envVars(stepEnvVars)
        .stepIdentifier(identifier)
        .containerImageDetails(
            ContainerImageDetails.builder()
                .imageDetails(k8sPodInitUtils.getImageInfo(ExpressionResolverUtils.resolveStringParameter(
                    "Image", "Run", identifier, runStepInfo.getImage(), true)))
                .connectorIdentifier(ExpressionResolverUtils.resolveStringParameter(
                    "connectorRef", "Run", identifier, runStepInfo.getConnectorRef(), false))
                .registryRef(ExpressionResolverUtils.resolveStringParameter(
                    "registryRef", "Run", identifier, runStepInfo.getRegistryRef(), false))
                .build())
        .containerResourceParams(getStepContainerResource(runStepInfo, accountId, flexibleTemplateEnabled))
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.RUN)
        .stepName(runStepInfo.getName())
        .privileged(runStepInfo.getPrivileged().getValue())
        .runAsUser(runAsUser)
        .imagePullPolicy(ContainerStepResolverUtils.resolveImagePullPolicy(runStepInfo.getImagePullPolicy()))
        .build();
  }

  // Requests: use values from getStepRequests iff feature flag is on; if a value is null or 0, fall back
  // to STEP_REQUEST_* defaults independently per field (CPU/Memory). Limits come from getStepLimits.
  private ContainerResourceParams getStepContainerResource(
      ContainerStepSpec resource, String accountId, boolean flexibleTemplateEnabled) {
    // Defaults
    int cpuReqDefault = STEP_REQUEST_MILLI_CPU;
    int memReqDefault = STEP_REQUEST_MEMORY_MIB;

    // Limits (unchanged logic)
    Pair<Integer, Integer> stepLimits = k8sPodInitUtils.getStepLimits(resource, accountId, flexibleTemplateEnabled);

    // Requests: start with defaults, optionally override via feature flag
    Pair<Integer, Integer> stepRequests = Pair.of(cpuReqDefault, memReqDefault);
    if (featureFlagService.isEnabled(FeatureName.CI_SUPPORT_RESOURCE_REQUESTS, accountId)) {
      Pair<Integer, Integer> fromSpec = k8sPodInitUtils.getStepRequests(resource, accountId, flexibleTemplateEnabled);
      Integer cpu = (fromSpec != null) ? fromSpec.getLeft() : null;
      Integer mem = (fromSpec != null) ? fromSpec.getRight() : null;

      stepRequests = Pair.of(getEffectiveMemoryRequest(mem, memReqDefault), getEffectiveCpuRequest(cpu, cpuReqDefault));
    }

    return ContainerResourceParams.builder()
        .resourceRequestMilliCpu(stepRequests.getLeft())
        .resourceRequestMemoryMiB(stepRequests.getRight())
        .resourceLimitMilliCpu(stepLimits.getLeft())
        .resourceLimitMemoryMiB(stepLimits.getRight())
        .build();
  }

  private Integer getEffectiveCpuRequest(Integer cpu, Integer cpuReqDefault) {
    return (cpu == null || cpu == 0) ? cpuReqDefault : cpu;
  }

  private Integer getEffectiveMemoryRequest(Integer mem, Integer memReqDefault) {
    return (mem == null || mem == 0) ? memReqDefault : mem;
  }

  public CIInitializeTaskParams getBuildSetupTaskParams(ContainerStepSpec containerStepSpec, Ambiance ambiance,
      String logPrefix, String stepIdentifier, List<TaskSelector> delegateSelectors, boolean routeToRunner) {
    switch (containerStepSpec.getInfrastructure().getType()) {
      case KUBERNETES_DIRECT:
        return getK8InitializeTaskParams(
            containerStepSpec, ambiance, logPrefix, stepIdentifier, delegateSelectors, routeToRunner);
      case ECS_DIRECT:
        return getEcsInitializeTaskParams(containerStepSpec, ambiance, logPrefix, stepIdentifier, delegateSelectors);
      case VM:
        return getDirectVmInitializeTaskParams(
            containerStepSpec, ambiance, logPrefix, stepIdentifier, delegateSelectors);
      default:
        unhandled(containerStepSpec.getInfrastructure().getType());
    }
    return null;
  }

  public CIVmInitializeTaskParams getDirectVmInitializeTaskParams(ContainerStepSpec containerStepSpec,
      Ambiance ambiance, String logPrefix, String stepIdentifier, List<TaskSelector> delegateSelectors) {
    ContainerStepInfra infra = containerStepSpec.getInfrastructure();
    if (infra.getType() != VM) {
      throw new ContainerStepExecutionException(format("Invalid infrastructure type: %s", infra.getType()));
    }
    validateInfrastructure(infra);
    VmInfraSpec containerVMInfraYamlSpec = ((ContainerVMInfra) infra).getSpec();
    String poolId = getPoolName(containerVMInfraYamlSpec);
    Long activeDeadlineSeconds = getActiveDeadlineSeconds(ambiance, false);
    return getVmInitializeParams(containerStepSpec, ambiance, poolId, Collections.emptyList(), Optional.empty(), false,
        activeDeadlineSeconds, logPrefix, stepIdentifier);
  }

  public CIVmInitializeTaskParams getVmInitializeParams(ContainerStepSpec containerStepSpec, Ambiance ambiance,
      String poolId, List<String> fallbackPoolIds, Optional<String> resourceClass, boolean shouldRouteStageToRunner,
      Long activeDeadlineSeconds, String logPrefix, String stepIdentifier) {
    ContainerStepInfra infrastructure = containerStepSpec.getInfrastructure();
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    CIInitializeTaskParams.Type infraInfo = validateInfrastructureAndGetInfraInfo(infrastructure);

    String accountID = AmbianceUtils.getAccountId(ambiance);

    OSType os = getInfraOS(infrastructure);
    InitContainerV2StepInfo initContainerV2StepInfo = (InitContainerV2StepInfo) containerStepSpec;
    Map<String, String> volToMountPath =
        vmInitializeUtils.getVolumeToMountPath(initContainerV2StepInfo.getSharedPaths(), os, accountID, infrastructure);
    String workDir = vmInitializeUtils.getWorkDir(os, accountID, infrastructure);

    String harnessImageConnectorRef = null;
    Optional<ParameterField<String>> optionalHarnessImageConnectorRef = getHarnessImageConnector(infrastructure);
    if (optionalHarnessImageConnectorRef.isPresent()) {
      harnessImageConnectorRef = optionalHarnessImageConnectorRef.get().getValue();
    }

    Optional<Level> levelOptional = AmbianceUtils.getStepGroupLevelFromAmbiance(ambiance);
    String stepGroupRuntimeId = "";
    if (levelOptional.isPresent()) {
      stepGroupRuntimeId = levelOptional.get().getRuntimeId();
    }

    saveSweepingOutputForVM(ambiance, poolId, workDir, harnessImageConnectorRef, volToMountPath, infraInfo,
        resourceClass, infrastructure, stepGroupRuntimeId);

    Map<String, String> envVars = new HashMap<>();

    Map<String, String> commonEnvVars = vmInitializeUtils.getCommonStepEnvVariables(ambiance);
    envVars.putAll(commonEnvVars);

    Map<String, String> principalTokenEnvVars = new HashMap<>();
    Map<String, String> tokenVars =
        harnessTokenUtils.getPrincipalTokenEnvVariables(ambiance, accountID, containerStepSpec.getPermissions());
    if (tokenVars != null) {
      principalTokenEnvVars.putAll(tokenVars);
    }
    envVars.putAll(principalTokenEnvVars);
    envVars.putAll(harnessTokenUtils.getHarnessBaseUrlEnvVariable(accountID));

    Set<String> secrets = new HashSet<>(principalTokenEnvVars.values());
    String ciUploadLogsViaHarness = null;

    if (settingsClient != null) {
      try {
        ciUploadLogsViaHarness = NGRestUtils
                                     .getResponse(settingsClient.getSetting(
                                         SettingIdentifiers.CI_UPLOAD_LOGS_VIA_HARNESS, accountID, null, null))
                                     .getValue();
      } catch (Exception e) {
        log.error("Setting {} is not found", CI_UPLOAD_LOGS_VIA_HARNESS);
      }
    }

    k8sPodInitUtils.getCommonStepEnvVariables(initContainerV2StepInfo, workDir, logPrefix, ambiance);

    boolean logServiceIndirectUpload = featureFlagService.isEnabled(FeatureName.CI_INDIRECT_LOG_UPLOAD, accountID)
        || Boolean.parseBoolean(ciUploadLogsViaHarness);

    boolean logServiceIncreaseLogLimit = featureFlagService.isEnabled(FeatureName.CI_INCREASE_LOG_LIMIT, accountID);

    boolean trimLogNewLineSuffix = featureFlagService.isEnabled(FeatureName.CI_TRIM_NEW_LINE_SUFFIX, accountID);

    Map<String, String> logSvcTokenMap = k8sPodInitUtils.getLogServiceEnvVariables(null, accountID);

    return CIVmInitializeTaskParams.builder()
        .poolID(poolId)
        .fallbackPoolIDs(fallbackPoolIds)
        .workingDir(workDir)
        .environment(envVars)
        .stageRuntimeId(stepGroupRuntimeId)
        .accountID(accountID)
        .orgID(AmbianceUtils.getOrgIdentifier(ambiance))
        .projectID(AmbianceUtils.getProjectIdentifier(ambiance))
        .pipelineID(ambiance.getMetadata().getPipelineIdentifier())
        .stageID(levelOptional.isPresent() ? levelOptional.get().getIdentifier()
                                           : AmbianceUtils.getStageIdentifierFromAmbiance(ambiance))
        .buildID(String.valueOf(ambiance.getMetadata().getRunSequence()))
        .logKey(logPrefix)
        .logStreamUrl(logSvcTokenMap.get(LOG_SERVICE_ENDPOINT_VARIABLE))
        .logSvcToken(logSvcTokenMap.get(LOG_SERVICE_TOKEN_VARIABLE))
        .logSvcIndirectUpload(logServiceIndirectUpload)
        .logSvcIncreaseLogLimit(logServiceIncreaseLogLimit)
        .secrets(new ArrayList<>(secrets))
        .volToMountPath(volToMountPath)
        .tags(vmInitializeUtils.getBuildTags(ambiance, stepIdentifier, AmbianceUtils.obtainCurrentRuntimeId(ambiance)))
        .infraInfo(infraInfo)
        .tty(featureFlagService.isEnabled(FeatureName.CI_ENABLE_TTY_LOGS, accountID))
        .resourceClass(resourceClass.orElse(null))
        .parseSavings(true)
        .trimNewLineSuffix(trimLogNewLineSuffix)
        .build();
  }

  public static String getPoolName(VmInfraSpec containerVMInfraYamlSpec) {
    VmPoolYaml vmPoolYaml = (VmPoolYaml) containerVMInfraYamlSpec;
    String poolName = vmPoolYaml.getSpec().getPoolName().getValue();
    if (isNotEmpty(poolName)) {
      return poolName;
    }

    String poolId = vmPoolYaml.getSpec().getIdentifier();
    if (isEmpty(poolId)) {
      throw new CIStageExecutionException("VM pool name should be set");
    }
    return poolId;
  }

  public Optional<ParameterField<String>> getHarnessImageConnector(ContainerStepInfra infrastructure) {
    ParameterField<String> harnessImageConnector = null;
    switch (infrastructure.getType()) {
      case KUBERNETES_DIRECT:
        harnessImageConnector = ((K8sDirectInfraYaml) infrastructure).getSpec().getHarnessImageConnectorRef();
        break;
      case ECS_DIRECT:
        harnessImageConnector = ((ContainerEcsInfra) infrastructure).getSpec().getHarnessImageConnectorRef();
        break;
      case VM:
        VmInfraSpec vmInfraSpec = ((ContainerVMInfra) infrastructure).getSpec();
        if (vmInfraSpec instanceof VmPoolYaml) {
          harnessImageConnector = ((VmPoolYaml) vmInfraSpec).getSpec().getHarnessImageConnectorRef();
        }
        break;
      default:
        break;
    }
    return ParameterField.isNull(harnessImageConnector) ? Optional.empty() : Optional.of(harnessImageConnector);
  }

  public static void validateInfrastructure(ContainerStepInfra infrastructure) {
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure for vm can not be empty");
    }

    if (((ContainerVMInfra) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("VM input infrastructure can not be empty");
    }

    ContainerVMInfra containerVMInfra = (ContainerVMInfra) infrastructure;
    if (containerVMInfra.getSpec().getType() != VmInfraSpec.Type.POOL) {
      throw new CIStageExecutionException(
          format("Invalid VM infrastructure spec type: %s", containerVMInfra.getSpec().getType()));
    }
  }

  public static CIInitializeTaskParams.Type validateInfrastructureAndGetInfraInfo(ContainerStepInfra infrastructure) {
    ContainerStepInfra.Type type = infrastructure.getType();
    CIInitializeTaskParams.Type infraInfo = null;
    if (type == ContainerStepInfra.Type.VM) {
      validateInfrastructure(infrastructure);
      infraInfo = CIInitializeTaskParams.Type.VM;
    }
    return infraInfo;
  }

  public static OSType getInfraOS(ContainerStepInfra infrastructure) {
    ContainerStepInfra.Type infraType = infrastructure.getType();

    ContainerVMInfra vmInfraYaml = (ContainerVMInfra) infrastructure;
    if (vmInfraYaml.getSpec() == null) {
      throw new CIStageExecutionException("Infrastructure spec should not be empty");
    }

    if (vmInfraYaml.getSpec().getType() != VmInfraSpec.Type.POOL) {
      throw new CIStageExecutionException(format("Invalid VM type: %s", vmInfraYaml.getSpec().getType()));
    }

    VmPoolYaml vmPoolYaml = (VmPoolYaml) vmInfraYaml.getSpec();
    return resolveOSType(vmPoolYaml.getSpec().getOs());
  }

  public CIK8InitializeTaskParams getK8InitializeTaskParams(ContainerStepSpec containerStepSpec, Ambiance ambiance,
      String logPrefix, String stepIdentifier, List<TaskSelector> delegateSelectors, boolean routeToRunner) {
    ContainerStepInfra infra = containerStepSpec.getInfrastructure();
    if (infra.getType() != KUBERNETES_DIRECT) {
      throw new ContainerStepExecutionException(format("Invalid infrastructure type: %s", infra.getType()));
    }
    ContainerK8sInfra infrastructure = (ContainerK8sInfra) infra;

    ContainerDetailsSweepingOutput k8PodDetails = ContainerDetailsSweepingOutput.builder()
                                                      .stepIdentifier(stepIdentifier)
                                                      .accountId(AmbianceUtils.getAccountId(ambiance))
                                                      .build();
    // Skip early save - will be saved later with all fields in consumeContainerDetails
    return buildK8DirectTaskParams(
        containerStepSpec, k8PodDetails, infrastructure, ambiance, logPrefix, delegateSelectors, routeToRunner);
  }

  private Map<EnvVariableEnum, String> convertDetailMap(Map<String, String> connectorSecretEnvMapMap) {
    return emptyIfNull(connectorSecretEnvMapMap)
        .entrySet()
        .stream()
        .collect(Collectors.toMap(e -> EnvVariableEnum.valueOf(e.getKey()), Map.Entry::getValue));
  }

  private Optional<CIExecutionImages> fetchCiExecutionImagesWithRetries(
      String accountIdentifier, StageInfraDetails.Type infraType) {
    try {
      return Failsafe.with(RETRY_POLICY_EXECUTION_CONFIGS)
          .get(() -> fetchCiExecutionImagesInternal(accountIdentifier, infraType));
    } catch (Exception ex) {
      log.error("Failed to fetch CI execution configs, will use default execution config", ex);
      return Optional.empty();
    }
  }

  private Optional<CIExecutionImages> fetchCiExecutionImagesInternal(
      String accountIdentifier, StageInfraDetails.Type infraType) throws IOException {
    final Response<ResponseDTO<CIExecutionImages>> response =
        ciServiceResourceClient.getCustomersExecutionConfig(infraType, false, accountIdentifier).execute();
    if (response.isSuccessful()) {
      if (response.body() != null) {
        CIExecutionImages data = response.body().getData();
        return Optional.ofNullable(data);
      }
      return Optional.empty();
    } else {
      // silently ignore error during reading the error body
      try (ResponseBody errorBody = response.errorBody()) {
        String errorBodyString = errorBody != null ? errorBody.string() : null;
        log.error(FAILED_TO_FETCH_CI_EXECUTION_CONFIG_MSG + ": " + errorBodyString);
      }
      throw new InvalidRequestException(FAILED_TO_FETCH_CI_EXECUTION_CONFIG_MSG);
    }
  }

  public CIECSInitializeTaskParams getEcsInitializeTaskParams(ContainerStepSpec containerStepSpec, Ambiance ambiance,
      String logPrefix, String stepIdentifier, List<TaskSelector> delegateSelectors) {
    ContainerStepInfra infra = containerStepSpec.getInfrastructure();
    if (infra.getType() != ECS_DIRECT) {
      throw new ContainerStepExecutionException(format("Invalid infrastructure type: %s", infra.getType()));
    }
    ContainerEcsInfra infrastructure = (ContainerEcsInfra) infra;
    ContainerDetailsSweepingOutput ecsPodDetails = ContainerDetailsSweepingOutput.builder()
                                                       .stepIdentifier(stepIdentifier)
                                                       .accountId(AmbianceUtils.getAccountId(ambiance))
                                                       .build();
    return buildEcsDirectTaskParams(
        containerStepSpec, ecsPodDetails, infrastructure, ambiance, logPrefix, delegateSelectors);
  }

  private CIECSInitializeTaskParams buildEcsDirectTaskParams(ContainerStepSpec containerStepInfo,
      ContainerDetailsSweepingOutput podDetails, ContainerEcsInfra infrastructure, Ambiance ambiance, String logPrefix,
      List<TaskSelector> delegateSelectors) {
    NGAccess ngAccess = AmbianceUtils.getNgOidcAccess(ambiance,
        pmsFeatureFlagService.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_OIDC_AWS_SESSION_TAGS),
        outcomeService, executionSweepingOutputResolver);
    String connectorRef = infrastructure.getSpec().getConnectorRef().getValue();
    ConnectorDetails awsConnector = connectorUtils.getConnectorDetails(ngAccess, connectorRef);
    EcsDirectInfraYamlSpec spec = infrastructure.getSpec();
    String region = getParameterFieldFinalValueString(spec.getRegion());
    String cluster = getParameterFieldFinalValueString(spec.getCluster());
    List<String> subnets = getParameterFieldValue(spec.getSubnets());
    List<String> securityGroups = getParameterFieldValue(spec.getSecurityGroups());
    String executionRoleResolved = getParameterFieldFinalValueString(spec.getExecutionRoleArn());
    return CIECSInitializeTaskParams.builder()
        .awsConnector(awsConnector)
        .ecsPodParams(getEcsDirectPodParams(
            containerStepInfo, podDetails, infrastructure, ambiance, logPrefix, delegateSelectors))
        .region(region)
        .cluster(cluster)
        .subnets(subnets)
        .securityGroups(securityGroups)
        .taskRoleArn(getParameterFieldFinalValueString(spec.getTaskRoleArn()))
        .executionRoleArn(isEmpty(executionRoleResolved) ? null : executionRoleResolved)
        .maxWaitForTaskReadySecs(k8sPodInitUtils.getPodWaitUntilReadTimeoutForEcs(infrastructure))
        .enableExecuteCommand(resolveEcsEnableExecuteCommand(spec))
        .logGroupName(getParameterFieldFinalValueString(spec.getLogGroupName()))
        .build();
  }

  private static boolean resolveEcsEnableExecuteCommand(EcsDirectInfraYamlSpec spec) {
    if (spec.getEnableExecuteCommand() == null || spec.getEnableExecuteCommand().getValue() == null) {
      return false;
    }
    return Boolean.TRUE.equals(ParameterFieldHelper.getBooleanParameterFieldValue(spec.getEnableExecuteCommand()));
  }

  private CIECSPodParams getEcsDirectPodParams(ContainerStepSpec containerStepInfo,
      ContainerDetailsSweepingOutput podDetails, ContainerEcsInfra ecsInfra, Ambiance ambiance, String logPrefix,
      List<TaskSelector> delegateSelectors) {
    String podName = getPodName(containerStepInfo.getIdentifier().toLowerCase());
    String accountId = AmbianceUtils.getAccountId(ambiance);
    Map<String, String> buildLabels = k8sPodInitUtils.getLabels(
        ambiance, ContainerUnitStepUtils.getKubernetesStandardPodName(containerStepInfo.getIdentifier()));
    List<PodVolume> volumes = k8sPodInitUtils.convertDirectEcsVolumes(ecsInfra);
    Pair<Integer, Integer> stageRequest =
        k8sPodInitUtils.getStepLimits(containerStepInfo, accountId, InjectUtils.IsFlexibleTemplatesEnabled(ambiance));
    int stageCpuMilli = stageRequest.getLeft();
    int stageMemoryMiB = stageRequest.getRight();
    if (featureFlagService.isEnabled(FeatureName.CI_CONSERVATIVE_K8_RESOURCE_LIMITS, accountId)) {
      stageCpuMilli = 0;
      stageMemoryMiB = 0;
    }
    Pair<CIECSContainerParams, List<CIECSContainerParams>> podContainers =
        getStepContainersForEcs(containerStepInfo, podDetails, ecsInfra, ambiance, volumes, logPrefix);
    saveSweepingOutputEcs(podName, ecsInfra, podContainers, ambiance, delegateSelectors);
    return CIECSPodParams.builder()
        .name(podName)
        .labels(buildLabels)
        .annotations(Collections.emptyMap())
        .nodeSelector(Collections.emptyMap())
        .automountServiceAccountToken(true)
        .containerParamsList(podContainers.getRight())
        .initContainerParamsList(podContainers.getLeft() != null ? singletonList(podContainers.getLeft()) : emptyList())
        .tolerations(Collections.emptyList())
        .activeDeadLineSeconds(getActiveDeadlineSeconds(ambiance, false))
        .volumes(volumes)
        .stageCpuMilli(stageCpuMilli)
        .stageMemoryMiB(stageMemoryMiB)
        .build();
  }

  private Pair<CIECSContainerParams, List<CIECSContainerParams>> getStepContainersForEcs(
      ContainerStepSpec containerStepInfo, ContainerDetailsSweepingOutput podDetails, ContainerEcsInfra infrastructure,
      Ambiance ambiance, List<PodVolume> volumes, String logPrefix) {
    List<String> sharedPaths = getSharedPaths(containerStepInfo);

    String accountId = AmbianceUtils.getAccountId(ambiance);
    boolean ephemeralDelegateMode =
        featureFlagService.isEnabled(FeatureName.PIPE_ENABLE_EPHEMERAL_DELEGATE_MODE, accountId);
    Map<String, String> volumeToMountPath =
        k8sPodInitUtils.getVolumeToMountPath(sharedPaths, volumes, !ephemeralDelegateMode);

    OSType os = k8sPodInitUtils.getOS(infrastructure);
    NGAccess ngAccess = AmbianceUtils.getNgOidcAccess(ambiance,
        pmsFeatureFlagService.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_OIDC_AWS_SESSION_TAGS),
        outcomeService, executionSweepingOutputResolver);
    Map<String, String> commonEnvVars =
        k8sPodInitUtils.getCommonStepEnvVariables(containerStepInfo, k8sPodInitUtils.getWorkDir(), logPrefix, ambiance);
    commonEnvVars.putAll(harnessTokenUtils.getHarnessBaseUrlEnvVariable(accountId));
    Map<String, String> principalTokenEnvVars = new HashMap<>();
    Map<String, String> tokenVars =
        harnessTokenUtils.getPrincipalTokenEnvVariables(ambiance, accountId, containerStepInfo.getPermissions());
    if (tokenVars != null) {
      principalTokenEnvVars.putAll(tokenVars);
    }
    ServiceEnvironmentVars serviceEnvironmentVars = k8sPodInitUtils.getServiceEnvironmentVars(podDetails, accountId);
    ConnectorDetails harnessInternalImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForEcs(ngAccess, infrastructure);
    ContainerSecretEvaluator liteEngineSecretEvaluator =
        ContainerSecretEvaluator.builder()
            .secretUtils(secretUtils)
            .withSingleQuotes(AmbianceUtils.checkIfFeatureFlagEnabled(
                ambiance, FeatureName.CDS_USE_SINGLE_QUOTES_IN_SECRET_FUNCTOR.name()))
            .build();
    List<SecretVariableDetails> secretVariableDetails =
        liteEngineSecretEvaluator.resolve(containerStepInfo, ngAccess, ambiance.getExpressionFunctorToken());
    k8sPodInitUtils.checkSecretAccess(ambiance, secretVariableDetails, accountId,
        AmbianceUtils.getProjectIdentifier(ambiance), AmbianceUtils.getOrgIdentifier(ambiance));
    boolean flexibleTemplateEnabled = InjectUtils.IsFlexibleTemplatesEnabled(ambiance);
    Pair<Integer, Integer> wrapperRequests =
        k8sPodInitUtils.getStepLimits(containerStepInfo, accountId, flexibleTemplateEnabled);
    Integer stageCpuRequest = wrapperRequests.getLeft();
    Integer stageMemoryRequest = wrapperRequests.getRight();
    List<CIECSContainerParams> containerParams = new ArrayList<>();
    final CIExecutionImages overridenExecutionConfig =
        fetchCiExecutionImagesWithRetries(accountId, ContainerInfraMapper.toStageInfraType(infrastructure))
            .orElse(null);
    String imagePullPolicy = null;
    if (featureFlagService.isEnabledReloadCache(
            FeatureName.CDS_DEFAULT_IMAGE_PULL_POLICY_ADD_ON_CONTAINER_SET_TO_EMPTY_BY_DEFAULT,
            ngAccess.getAccountIdentifier())) {
      imagePullPolicy = AmbianceUtils.getSettingValue(
          ambiance, DEFAULT_IMAGE_PULL_POLICY_ADD_ON_CONTAINER_SET_TO_EMPTY_BY_DEFAULT.getName());
      if (IMAGE_PULL_POLICY_SET_TO_EMPTY.equals(imagePullPolicy)) {
        imagePullPolicy = null;
      }
    } else {
      imagePullPolicy = AmbianceUtils.getSettingValue(ambiance, DEFAULT_IMAGE_PULL_POLICY_ADD_ON_CONTANER.getName());
    }
    // Match K8s init: fresh infra security context per container. buildStepContainerParams mutates the object with
    // step-level privileged/runAsUser; sharing one instance would leak settings across steps and corrupt addon/engine.
    CIECSContainerParams setupAddOnContainerParams = ephemeralDelegateMode
        ? null
        : containerParamsProvider.getSetupAddonEcsContainerParams(harnessInternalImageConnector, volumeToMountPath,
              k8sPodInitUtils.getWorkDir(), k8sPodInitUtils.getCtrSecurityContext(infrastructure), os,
              overridenExecutionConfig, imagePullPolicy);
    List<ContainerDefinitionInfo> stepCtrDefinitions = getContainerDefinitionInfosForEcs(containerStepInfo,
        infrastructure, ambiance, logPrefix, volumeToMountPath, os, ngAccess, commonEnvVars, serviceEnvironmentVars,
        harnessInternalImageConnector, secretVariableDetails, containerParams, principalTokenEnvVars);
    consumePortDetails(ambiance, stepCtrDefinitions);
    consumeContainerDetails(ambiance, stepCtrDefinitions, podDetails);
    if (!ephemeralDelegateMode) {
      CIECSContainerParams liteEngineContainerParams = containerParamsProvider.getLiteEngineEcsContainerParams(
          harnessInternalImageConnector, podDetails, stageCpuRequest, stageMemoryRequest, serviceEnvironmentVars,
          volumeToMountPath, k8sPodInitUtils.getWorkDir(), k8sPodInitUtils.getCtrSecurityContext(infrastructure),
          logPrefix, ambiance, overridenExecutionConfig, imagePullPolicy, containerStepInfo);
      containerParams.add(liteEngineContainerParams);
    }
    return Pair.of(setupAddOnContainerParams, containerParams);
  }

  private List<ContainerDefinitionInfo> getContainerDefinitionInfosForEcs(ContainerStepSpec containerStepInfo,
      ContainerEcsInfra infrastructure, Ambiance ambiance, String logPrefix, Map<String, String> volumeToMountPath,
      OSType os, NGAccess ngAccess, Map<String, String> commonEnvVars, ServiceEnvironmentVars serviceEnvironmentVars,
      ConnectorDetails harnessInternalImageConnector, List<SecretVariableDetails> secretVariableDetails,
      List<CIECSContainerParams> containerParams, Map<String, String> principalTokenEnvVars) {
    List<ContainerDefinitionInfo> stepCtrDefinitions =
        getStepContainerDefinitionsInternal(containerStepInfo, ambiance, os);
    Map<String, List<ConnectorConversionInfo>> stepConnectorMap = getStepConnectorRefsV2(containerStepInfo,
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, USE_COMPLETE_STEP_GROUP_ID.name())
            ? AmbianceUtils.getAllStepGroupIdentifiersForCurrentLevel(ambiance)
            : AmbianceUtils.obtainStepGroupIdentifier(ambiance),
        ngAccess.getAccountIdentifier());
    for (ContainerDefinitionInfo containerDefinitionInfo : stepCtrDefinitions) {
      CIECSContainerParams ecsContainerParams = createCIECSContainerParams(ngAccess, containerDefinitionInfo,
          harnessInternalImageConnector, commonEnvVars, serviceEnvironmentVars, stepConnectorMap, volumeToMountPath,
          k8sPodInitUtils.getWorkDir(), k8sPodInitUtils.getCtrSecurityContext(infrastructure), logPrefix,
          secretVariableDetails, os, ambiance, principalTokenEnvVars);
      containerParams.add(ecsContainerParams);
    }
    return stepCtrDefinitions;
  }

  private void saveSweepingOutputEcs(String taskName, ContainerEcsInfra infrastructure,
      Pair<CIECSContainerParams, List<CIECSContainerParams>> podContainers, Ambiance ambiance,
      List<TaskSelector> delegateSelectors) {
    List<String> containerNames =
        podContainers.getRight().stream().map(CIECSContainerParams::getName).collect(toList());
    if (podContainers.getLeft() != null) {
      containerNames.add(podContainers.getLeft().getName());
    }
    k8sPodInitUtils.consumeSweepingOutput(ambiance,
        ContainerCleanupDetails.builder()
            .infrastructure(infrastructure)
            .podName(taskName)
            .cleanUpContainerNames(containerNames)
            .delegateSelectors(delegateSelectors)
            .build(),
        CLEANUP_DETAILS);
    String cluster = getParameterFieldFinalValueString(infrastructure.getSpec().getCluster());
    k8sPodInitUtils.consumeSweepingOutput(ambiance,
        EcsStageInfraDetails.builder()
            .infrastructure(infrastructure.toCIInfra())
            .cluster(cluster)
            .taskName(taskName)
            .containerNames(containerNames)
            .delegateSelectors(delegateSelectors)
            .routeToRunner(true)
            .build(),
        STAGE_INFRA_DETAILS);
    TaskSelectorSweepingOutput taskSelectorSweepingOutput =
        TaskSelectorSweepingOutput.builder().taskSelectors(delegateSelectors).build();
    k8sPodInitUtils.consumeSweepingOutput(ambiance, taskSelectorSweepingOutput, TASK_SELECTORS);
  }

  public void checkIfEverythingIsHealthyForVM(VmTaskExecutionResponse vmTaskExecutionResponse) {
    if (!vmTaskExecutionResponse.getCommandExecutionStatus().equals(CommandExecutionStatus.SUCCESS)) {
      throw new ContainerStepExecutionException(
          String.format("Container creation ran into error: %s", vmTaskExecutionResponse.getErrorMessage()));
    }
  }

  private Status getStatus(CommandExecutionStatus commandExecutionStatus) {
    Status status;
    if (commandExecutionStatus == CommandExecutionStatus.SUCCESS) {
      status = Status.SUCCEEDED;
    } else {
      status = Status.FAILED;
    }
    return status;
  }

  public StepResponse handleVMTaskExecutionResponse(VmTaskExecutionResponse vmTaskExecutionResponse) {
    CommandExecutionStatus commandExecutionStatus = vmTaskExecutionResponse.getCommandExecutionStatus();
    Status status = getStatus(commandExecutionStatus);
    checkIfEverythingIsHealthyForVM(vmTaskExecutionResponse);

    return StepResponse.builder()
        .status(status)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(VM_DETAILS_OUTCOME)
                         .outcome(getVmDetailsOutcome(vmTaskExecutionResponse))
                         .group(StepCategory.STEP_GROUP.name())
                         .build())
        .build();
  }

  public VmDetailsOutcome getVmDetailsOutcome(VmTaskExecutionResponse vmTaskExecutionResponse) {
    VmDetailsOutcomeBuilder builder = VmDetailsOutcome.builder()
                                          .ipAddress(vmTaskExecutionResponse.getIpAddress())
                                          .poolDriverUsed(vmTaskExecutionResponse.getPoolDriverUsed());
    if (vmTaskExecutionResponse.getDelegateMetaInfo() == null
        || isEmpty(vmTaskExecutionResponse.getDelegateMetaInfo().getId())) {
      return builder.build();
    }

    return builder.delegateId(vmTaskExecutionResponse.getDelegateMetaInfo().getId()).build();
  }

  // K8s gets a 10-min buffer because activeDeadlineSeconds hard-kills the pod (SIGTERM then SIGKILL);
  // the buffer allows Harness cleanup tasks (log collection, artifact upload) to complete.
  // VM/ECS don't need it: VM uses lite-engine application-level timeout, ECS uses its own stopTimeout mechanism.
  private Long getActiveDeadlineSeconds(Ambiance ambiance, boolean addBuffer) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (!featureFlagService.isEnabled(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT, accountId)) {
      return CIConstants.POD_MAX_TTL_SECS;
    }
    StageDetails stageDetails = getStageDetails(ambiance);
    if (stageDetails == null) {
      return CIConstants.POD_MAX_TTL_SECS;
    }
    if (stageDetails.getTimeout() != null && stageDetails.getTimeout().getValue() != null) {
      long timeoutMillis = stageDetails.getTimeout().getValue().getTimeoutInMillis();
      if (timeoutMillis > CIConstants.TWENTY_FOUR_HOUR_IN_MILLI_SEC) {
        long buffer = addBuffer ? CIConstants.TEN_MINUTES_IN_MILLI_SEC : 0;
        return (timeoutMillis + buffer) / 1000;
      }
    }
    return CIConstants.POD_MAX_TTL_SECS;
  }

  private StageDetails getStageDetails(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      log.warn("StageDetails sweeping output not found, defaulting to POD_MAX_TTL_SECS");
      return null;
    }
    return (StageDetails) optionalSweepingOutput.getOutput();
  }
}
