/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.beans.FeatureName.CI_CUSTOM_ERROR_CATEGORIZATION;
import static io.harness.beans.FeatureName.CI_ENABLE_LONG_TIMEOUTS;
import static io.harness.beans.FeatureName.CI_K8S_OVERLAY_YAML_SECRET_RESTRICTION;
import static io.harness.beans.FeatureName.REGISTRY_VANITY_URL_ENABLED;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveIntegerParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveListParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.sweepingoutputs.ContainerDetails.CONTAINER_DETAILS;
import static io.harness.beans.sweepingoutputs.ContainerPortDetails.PORT_DETAILS;
import static io.harness.beans.sweepingoutputs.PodCleanupDetails.CLEANUP_DETAILS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CODEBASE_DISCLAIM_KEYS;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_PR_MERGE_STRATEGY_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REMOTE_URL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_STEP_NAME;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_STEP_NUMBER;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_WORKSPACE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.HARNESS_CI_EXPLICIT_GIT_CLONE_STEP;
import static io.harness.ci.commonconstants.CIExecutionConstants.CI_IGNORE_CONSERVATIVE_LIMITS;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_ERRORS_YAML_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_SERVICE_LOG_KEY_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.ci.commonconstants.CIExecutionConstants.TI_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.TI_SERVICE_TOKEN_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_WINDOWS_CERT_INJECTION_FF;
import static io.harness.ci.coverage.CoverageExecutionConstants.COVERAGE_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.coverage.CoverageExecutionConstants.COVERAGE_SERVICE_TOKEN_VARIABLE;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.citasks.cik8handler.params.CIConstants.TWENTY_FOUR_HOUR_IN_MILLI_SEC;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.environment.ConnectorConversionInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.StepOperationMetadata;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.ContainerDetails;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.PodCleanupDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.cache.Caching;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.commonconstants.BuildEnvironmentConstants;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.buildstate.providers.InternalContainerParamsProvider;
import io.harness.ci.execution.integrationstage.k8s.K8InitializeStepUtils;
import io.harness.ci.execution.integrationstage.k8s.K8InitializeTaskUtils;
import io.harness.ci.execution.integrationstage.k8s.ModulesImplicitStepsConfigHandler;
import io.harness.ci.execution.integrationstage.secret.SecretEnvVars;
import io.harness.ci.execution.integrationstage.secret.SecretEnvVars.SecretEnvVarsBuilder;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtility;
import io.harness.ci.execution.utils.LiteEngineSecretEvaluator;
import io.harness.ci.execution.utils.ci.CIStepInfoUtils;
import io.harness.ci.execution.utils.ci.HarnessImageUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.utils.BaseConnectorUtils;
import io.harness.ci.utils.PortFinder;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.HostAliasParams;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.PodTopologySpreadConstraints;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.beans.ci.pod.VolumeMountInfo;
import io.harness.delegate.task.citasks.cik8handler.params.CIConstants;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.hsa.client.HSAServiceUtils;
import io.harness.k8s.model.ImageDetails;
import io.harness.ng.core.NGAccess;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.inject.InjectUtils;
import io.harness.plugin.service.PluginServiceImpl;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.ssca.execution.SSCALicenseHelper;
import io.harness.steps.container.utils.yaml.OverlayYamlSecurityValidator;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.InitialiseTaskUtils;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class K8InitializeTaskParamsBuilder {
  @Inject private ConnectorUtils connectorUtils;
  @Inject private K8InitializeTaskUtils k8InitializeTaskUtils;
  @Inject private K8InitializeStepUtils k8InitializeStepUtils;
  @Inject private K8InitializeServiceUtils k8InitializeServiceUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private HarnessImageUtils harnessImageUtils;
  @Inject private InternalContainerParamsProvider internalContainerParamsProvider;
  @Inject private SecretUtils secretUtils;
  @Inject private CILicenseService ciLicenseService;
  @Inject CodebaseUtils codebaseUtils;
  @Inject(optional = true) HSAServiceUtils hsaServiceUtils;
  @Inject SSCAServiceUtils sscaServiceUtils;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Inject SSCALicenseHelper sscaLicenseHelper;
  @Inject @Named("ciBackgroundTaskExecutor") private ExecutorService executorService;
  @Inject(optional = true) private ModulesImplicitStepsConfigHandler modulesImplicitStepsConfigHandler;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;

  private static String RUNTIME_CLASS_NAME = "gvisor";

  public static String privilegeFieldExceptionMessage =
      "Configuration Error: The 'allowPrivilegeEscalation' field cannot be set to 'false' while the 'privileged' field "
      + "is 'true'. Please review the 'securityContext' settings at the infrastructure level.";

  public CIK8InitializeTaskParams getK8InitializeTaskParams(
      InitializeStepInfo initializeStepInfo, Ambiance ambiance, String logPrefix, boolean shouldRouteStageToRunner) {
    Infrastructure infrastructure = initializeStepInfo.getInfrastructure();
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    if (infrastructure.getType() != Infrastructure.Type.KUBERNETES_DIRECT) {
      throw new CIStageExecutionException(format("Invalid infrastructure type: %s", infrastructure.getType()));
    }

    K8PodDetails k8PodDetails = (K8PodDetails) executionSweepingOutputResolver.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails));
    if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
      return buildK8DirectTaskParams(initializeStepInfo, k8PodDetails, (K8sDirectInfraYaml) infrastructure, ambiance,
          logPrefix, shouldRouteStageToRunner);
    }
    return null;
  }

  private CIK8InitializeTaskParams buildK8DirectTaskParams(InitializeStepInfo initializeStepInfo,
      K8PodDetails k8PodDetails, K8sDirectInfraYaml k8sDirectInfraYaml, Ambiance ambiance, String logPrefix,
      boolean shouldRouteStageToRunner) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String connectorRef = k8sDirectInfraYaml.getSpec().getConnectorRef().getValue();
    if (isEmpty(connectorRef)) {
      throw new CIStageExecutionException("Kubernetes connector identifier cannot be empty for the stage.");
    }
    ConnectorDetails k8sConnector = connectorUtils.getConnectorDetails(ngAccess, connectorRef);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    return CIK8InitializeTaskParams.builder()
        .k8sConnector(k8sConnector)
        .cik8PodParams(getK8DirectPodParams(
            initializeStepInfo, k8PodDetails, k8sDirectInfraYaml, ambiance, logPrefix, shouldRouteStageToRunner))
        .podMaxWaitUntilReadySecs(k8InitializeTaskUtils.getPodWaitUntilReadTimeout(k8sDirectInfraYaml))
        .applyCommonEnvOptimization(isCommonEnvPodEnabled(accountId, initializeStepInfo.getVariables()))
        .build();
  }

  private CIK8PodParams<CIK8ContainerParams> getK8DirectPodParams(InitializeStepInfo initializeStepInfo,
      K8PodDetails k8PodDetails, K8sDirectInfraYaml k8sDirectInfraYaml, Ambiance ambiance, String logPrefix,
      boolean shouldRouteStageToRunner) {
    String podName = getPodName(ambiance, initializeStepInfo.getStageIdentifier());
    Map<String, String> buildLabels = k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails);

    Map<String, String> annotations = resolveMapParameter(
        "annotations", "K8InitializeStep", "stageSetup", k8sDirectInfraYaml.getSpec().getAnnotations(), false);
    Map<String, String> labels = resolveMapParameter(
        "labels", "K8InitializeStep", "stageSetup", k8sDirectInfraYaml.getSpec().getLabels(), false);
    Map<String, String> nodeSelector = resolveMapParameter(
        "nodeSelector", "K8InitializeStep", "stageSetup", k8sDirectInfraYaml.getSpec().getNodeSelector(), false);
    Integer stageRunAsUser = resolveIntegerParameter(k8sDirectInfraYaml.getSpec().getRunAsUser(), null);
    String serviceAccountName = resolveStringParameter("serviceAccountName", "K8InitializeStep", "stageSetup",
        k8sDirectInfraYaml.getSpec().getServiceAccountName(), false);

    List<String> hostNames = resolveListParameter(
        "hostNames", "K8InitializeStep", "stageSetup", k8sDirectInfraYaml.getSpec().getHostNames(), false);
    List<HostAliasParams> hostAliasParamsList = new ArrayList<>();
    if (isNotEmpty(hostNames)) {
      hostAliasParamsList.add(HostAliasParams.builder().ipAddress("127.0.0.1").hostnameList(hostNames).build());
    }
    if (isNotEmpty(labels)) {
      buildLabels.putAll(labels);
    }

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorDetails gitConnector = codebaseUtils.getGitConnector(
        ngAccess, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone(), ambiance);
    List<PodVolume> volumes = k8InitializeTaskUtils.convertDirectK8Volumes(k8sDirectInfraYaml);
    // getStageContainers returns: (setupContainer, containerList, commonEnvVars)
    Triple<Optional<CIK8ContainerParams>, List<CIK8ContainerParams>, Map<String, String>> stageContainersResult =
        getStageContainers(
            initializeStepInfo, k8PodDetails, k8sDirectInfraYaml, ambiance, volumes, logPrefix, gitConnector);

    Pair<Optional<CIK8ContainerParams>, List<CIK8ContainerParams>> podContainers =
        Pair.of(stageContainersResult.getLeft(), stageContainersResult.getMiddle());
    Map<String, String> commonEnvVarsForPod = stageContainersResult.getRight();

    saveSweepingOutput(
        initializeStepInfo, podName, k8sDirectInfraYaml, podContainers, ambiance, shouldRouteStageToRunner);
    Long activeDeadlineSeconds = CIConstants.POD_MAX_TTL_SECS;
    if (featureFlagService.isEnabled(CI_ENABLE_LONG_TIMEOUTS, AmbianceUtils.getAccountId(ambiance))) {
      activeDeadlineSeconds = InitialiseTaskUtils.getPodTTLFromStageTime(k8PodDetails);
    }

    boolean enableOverlayYaml = k8InitializeTaskUtils.checkStageVarState(initializeStepInfo.getVariables(),
                                    FeatureName.CI_K8S_OVERLAY_YAML.toString(), "true")
        || featureFlagService.isEnabled(FeatureName.CI_K8S_OVERLAY_YAML, AmbianceUtils.getAccountId(ambiance));
    List<PodTopologySpreadConstraints> topologySpreadConstraints = null;
    String overlayYaml = null;
    if (enableOverlayYaml) {
      overlayYaml = resolveOverlayYaml(k8sDirectInfraYaml, ambiance);
      if (featureFlagService.isEnabled(CI_K8S_OVERLAY_YAML_SECRET_RESTRICTION, AmbianceUtils.getAccountId(ambiance))) {
        OverlayYamlSecurityValidator.validateNoSecretReferences(overlayYaml);
      }
    } else {
      String resolvedOverlay = resolveOverlayYaml(k8sDirectInfraYaml, ambiance);
      ParameterField<String> overlayField =
          isEmpty(resolvedOverlay) ? null : ParameterField.createValueField(resolvedOverlay);
      topologySpreadConstraints = k8InitializeTaskUtils.getTopologySpreadConstraintsList(overlayField);
    }

    log.info("Pod {}: commonEnvVars size={}", podName, commonEnvVarsForPod != null ? commonEnvVarsForPod.size() : 0);

    Pair<Integer, Integer> stageRequest = k8InitializeStepUtils.getStageRequest(
        initializeStepInfo, AmbianceUtils.getAccountId(ambiance), InjectUtils.IsFlexibleTemplatesEnabled(ambiance));

    return CIK8PodParams.<CIK8ContainerParams>builder()
        .name(podName)
        .namespace(getParameterFieldValue(k8sDirectInfraYaml.getSpec().getNamespace()))
        .labels(buildLabels)
        .serviceAccountName(serviceAccountName)
        .annotations(annotations)
        .nodeSelector(nodeSelector)
        .runAsUser(stageRunAsUser)
        .automountServiceAccountToken(k8sDirectInfraYaml.getSpec().getAutomountServiceAccountToken().getValue())
        .priorityClassName(k8sDirectInfraYaml.getSpec().getPriorityClassName().getValue())
        .tolerations(k8InitializeTaskUtils.getPodTolerations(k8sDirectInfraYaml.getSpec().getTolerations()))
        .gitConnector(gitConnector)
        .containerParamsList(podContainers.getRight())
        //.pvcParamList(pvcParamsList)
        .initContainerParamsList(podContainers.getLeft().map(Collections::singletonList).orElse(emptyList()))
        .activeDeadLineSeconds(activeDeadlineSeconds)
        .volumes(volumes)
        .hostAliasParamsList(hostAliasParamsList)
        .overlayYaml(overlayYaml)
        .topologySpreadConstraints(topologySpreadConstraints)
        .commonEnvVars(commonEnvVarsForPod)
        .stageCpuMilli(stageRequest.getLeft())
        .stageMemoryMiB(stageRequest.getRight())
        .build();
  }

  private Triple<Optional<CIK8ContainerParams>, List<CIK8ContainerParams>, Map<String, String>> getStageContainers(
      InitializeStepInfo initializeStepInfo, K8PodDetails k8PodDetails, Infrastructure infrastructure,
      Ambiance ambiance, List<PodVolume> volumes, String logPrefix, ConnectorDetails gitConnector) {
    List<String> sharedPaths = k8InitializeTaskUtils.getSharedPaths(initializeStepInfo);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    boolean ephemeralDelegateMode =
        featureFlagService.isEnabled(FeatureName.PIPE_ENABLE_EPHEMERAL_DELEGATE_MODE, accountId);
    Map<String, String> volumeToMountPath =
        k8InitializeTaskUtils.getVolumeToMountPath(sharedPaths, volumes, !ephemeralDelegateMode);
    OSType os = k8InitializeTaskUtils.getOS(infrastructure);
    NGAccess ngAccess = AmbianceUtils.getNgOidcAccess(ambiance);
    CodeBase ciCodebase = initializeStepInfo.getCiCodebase();
    Map<String, List<VolumeMountInfo>> volumeToMountInfoV2 = new HashMap<>();
    // Log when customer is using Windows infra with SSH git connector
    if (os == OSType.Windows && gitConnector != null && gitConnector.getSshKeyDetails() != null) {
      log.info("Customer is using Windows K8s infrastructure with SSH git connector. AccountId: {}, OrgId: {}, "
              + "ProjectId: {}, ConnectorIdentifier: {}",
          accountId, AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
          gitConnector.getIdentifier());
    }
    volumeToMountInfoV2 = k8InitializeTaskUtils.getVolumeV2ToMountPath(
        sharedPaths, volumes, initializeStepInfo.getCiCodebase(), os, !ephemeralDelegateMode);

    boolean extendTokenTTL = false;
    if (featureFlagService.isEnabled(CI_ENABLE_LONG_TIMEOUTS, accountId)) {
      if (k8PodDetails.getTimeout() != null && k8PodDetails.getTimeout().getValue() != null) {
        long timeoutMillis = k8PodDetails.getTimeout().getValue().getTimeoutInMillis();
        if (timeoutMillis > TWENTY_FOUR_HOUR_IN_MILLI_SEC) {
          extendTokenTTL = true;
        }
      }
    }
    Map<String, String> logEnvVars =
        k8InitializeTaskUtils.getLogServiceEnvVariables(k8PodDetails, accountId, extendTokenTTL);
    Map<String, String> tiEnvVars = k8InitializeTaskUtils.getTIServiceEnvVariables(accountId, extendTokenTTL);
    Map<String, String> principalTokenEnvVars = k8InitializeTaskUtils.getPrincipalTokenEnvVariables(
        ambiance, accountId, initializeStepInfo.getStageElementConfig().getPermissions());
    Map<String, String> gitEnvVars =
        codebaseUtils.getGitEnvVariables(gitConnector, ciCodebase, initializeStepInfo.isSkipGitClone());
    SecretEnvVars secretEnvVars = getSecretEnvVars(ambiance);
    String repoName = ciCodebase != null ? RunTimeInputHandler.resolveString(ciCodebase.getRepoName()) : null;
    Map<String, String> runtimeCodebaseVars = codebaseUtils.getRuntimeCodebaseVars(ambiance, gitConnector, repoName);
    Map<String, String> commonEnvVars = k8InitializeTaskUtils.getCommonStepEnvVariables(k8PodDetails, gitEnvVars,
        runtimeCodebaseVars, k8InitializeTaskUtils.getWorkDir(), logPrefix, ambiance, initializeStepInfo, os);
    commonEnvVars.putAll(k8InitializeTaskUtils.getHarnessBaseUrlEnvVariable(accountId));
    String repoUrl = commonEnvVars.get(BuildEnvironmentConstants.DRONE_REMOTE_URL);
    Map<String, String> gitAdvancedVars = codebaseUtils.getGitAdvancedVariables(
        ciCodebase, initializeStepInfo.isSkipGitClone(), repoUrl, infrastructure, accountId, false);
    commonEnvVars.putAll(gitAdvancedVars);

    // Details on https://github.com/wings-software/drone-git/pull/95
    if (featureFlagService.isEnabled(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH, accountId)) {
      commonEnvVars.put(DRONE_PR_MERGE_STRATEGY_BRANCH, "true");
    }
    if (featureFlagService.isEnabled(FeatureName.CI_WINDOWS_CERT_INJECTION, accountId)) {
      commonEnvVars.put(CI_WINDOWS_CERT_INJECTION_FF, "true");
    }
    Map<String, String> coverageEnvVariables = Collections.emptyMap();
    if (featureFlagService.isEnabled(FeatureName.CODE_COVERAGE_ENABLED, accountId)) {
      coverageEnvVariables = k8InitializeTaskUtils.getCoverageEnvVariables(accountId, ambiance);
    }
    StepServiceEnvBundle stepServiceEnvVars = buildStepServiceEnvVars(tiEnvVars, coverageEnvVariables);
    commonEnvVars.putAll(stepServiceEnvVars.getCommonEndpointEnvVars());
    Map<String, String> stepTiSecretEnvVars = stepServiceEnvVars.getTiSecretEnvVars();
    Map<String, String> stepCoverageSecretEnvVars = stepServiceEnvVars.getCoverageSecretEnvVars();

    Caching caching = initializeStepInfo.getStageElementConfig().getCaching();
    StageDetails stageDetails = getStageDetails(ambiance);
    if ((caching != null && RunTimeInputHandler.resolveBooleanParameter(caching.getEnabled(), false))
        || Boolean.TRUE.equals(stageDetails.getCacheEnabled())) {
      Map<String, String> cacheEnvVars = k8InitializeTaskUtils.getCacheEnvironmentVariable(os);
      commonEnvVars.putAll(cacheEnvVars);
    }

    ConnectorDetails harnessInternalImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForK8(ngAccess, infrastructure);
    boolean isV1Yaml = HarnessYamlVersion.isV1(AmbianceUtils.getPipelineVersion(ambiance));

    // Phase 1: stash the expression-valued (whose value is raw expression) env vars (currently PLUGIN_OVERRIDE_IMAGE,
    // which holds a serverlessImageConfig expression) BEFORE resolveGitAppFunctor where "resolveGitAppFunctor" mutates
    // initializeStepInfo with a gitApp-only containing RETURN_NULL_IF_UNRESOLVED evaluator which clobbers an unresolved
    // JEXL <+serverlessImageConfig.get(...)> to "null". We cannot render yet: serviceOutput (which the functor needs)
    // is only produced later by setModuleImplicitStepsConfigToInitInfo. Capturing the raw expression here lets it
    // survive the clobber; Phase 2 (below) resolves it once serviceOutput exists.
    List<EnvironmentVariablesResolver.EnvironmentVariableRef> envVarsToResolve = isV1Yaml
        ? EnvironmentVariablesResolver.getEnvVarsToResolve(initializeStepInfo.getExecutionElementConfig())
        : Collections.emptyList();

    Map<String, ConnectorDetails> githubApiTokenFunctorConnectors =
        k8InitializeTaskUtils.resolveGitAppFunctor(ngAccess, initializeStepInfo, ambiance);

    LiteEngineSecretEvaluator liteEngineSecretEvaluator =
        LiteEngineSecretEvaluator.builder()
            .secretUtils(secretUtils)
            .withSingleQuotes(AmbianceUtils.checkIfFeatureFlagEnabled(
                ambiance, FeatureName.CDS_USE_SINGLE_QUOTES_IN_SECRET_FUNCTOR.name()))
            .executorService(executorService)
            .build();

    if (isV1Yaml) {
      setModuleImplicitStepsConfigToInitInfo(
          initializeStepInfo, ambiance, accountId, initializeStepInfo.getModulesMetadata());

      // V1: nullify unresolved template inputs before container env is built
      UnresolvedExpressionNullifier.processInitializeStepInfo(
          initializeStepInfo.getExecutionElementConfig(), initializeStepInfo.getModuleImplicitStepsConfig());

      // Phase 2: serviceOutput is now saved, so the stashed env var expressions can finally resolve to a concrete
      // value. This writes the resolved value back onto the same env node, repairing any JEXL value that
      // resolveGitAppFunctor clobbered to "null".
      EnvironmentVariablesResolver.resolveEnvVars(
          envVarsToResolve, raw -> cdStepsExpressionResolver.renderValue(ambiance, raw, true));
    }

    List<SecretVariableDetails> resolveSecretVariableDetails =
        liteEngineSecretEvaluator.resolve(initializeStepInfo, ngAccess, ambiance.getExpressionFunctorToken(), true);

    List<SecretVariableDetails> secretVariableDetails =
        k8InitializeTaskUtils.deDupSecrets(resolveSecretVariableDetails);

    k8InitializeTaskUtils.checkSecretAccess(ambiance, secretVariableDetails, accountId,
        AmbianceUtils.getProjectIdentifier(ambiance), AmbianceUtils.getOrgIdentifier(ambiance));

    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    Map<String, String> k8InfraSettings =
        k8InitializeTaskUtils.fetchK8InfraAdvancedSettings(accountId, orgId, projectId);

    validateContainerSecurityContext(os, k8InitializeTaskUtils.getCtrSecurityContext(infrastructure, k8InfraSettings));

    String imagePullPolicy = harnessImageUtils.getUpdatedImagePullPolicyBasedOnAmbiance(null, infrastructure, ambiance);

    Optional<CIK8ContainerParams> setupAddOnContainerParams = ephemeralDelegateMode
        ? Optional.empty()
        : Optional.of(internalContainerParamsProvider.getSetupAddonContainerParams(harnessInternalImageConnector,
              volumeToMountPath, volumeToMountInfoV2, k8InitializeTaskUtils.getWorkDir(),
              resolveCtrSecurityContext(infrastructure, isV1Yaml, os, k8InfraSettings), ngAccess.getAccountIdentifier(),
              os, imagePullPolicy));

    boolean flexibleTemplateEnabled = InjectUtils.IsFlexibleTemplatesEnabled(ambiance);
    Pair<Integer, Integer> wrapperRequests =
        k8InitializeStepUtils.getStageRequest(initializeStepInfo, accountId, flexibleTemplateEnabled);
    Integer stageCpuRequest = wrapperRequests.getLeft();
    Integer stageMemoryRequest = wrapperRequests.getRight();

    // Check if stage variable IGNORE_CONSERVATIVE_LIMITS is set to true
    boolean ignoreConservativeLimits = false;
    if (initializeStepInfo.getVariables() != null) {
      for (NGVariable variable : initializeStepInfo.getVariables()) {
        if (variable.getName().equals(CI_IGNORE_CONSERVATIVE_LIMITS)
            && (ParameterField.isNotNull(variable.fetchValue()) && variable.fetchValue() != null)) {
          String varValue = "false";
          if (variable.fetchValue().isExpression()) {
            varValue = variable.fetchValue().getExpressionValue();
          } else if (variable.fetchValue().obtainValue() != null) {
            varValue = variable.fetchValue().obtainValue().toString();
          }
          if ("true".equals(varValue)) {
            ignoreConservativeLimits = true;
          }
        }
      }
    }

    // Extract stage variables to pass to LiteEngine
    // Only pass HARNESS_ERRORS_YAML_PATH if CI_CUSTOM_ERROR_CATEGORIZATION feature flag is enabled
    List<String> liteEngineStageVars = new ArrayList<>();
    if (featureFlagService.isEnabled(CI_CUSTOM_ERROR_CATEGORIZATION, accountId)) {
      liteEngineStageVars.add(HARNESS_ERRORS_YAML_PATH);
    }
    // Always forward the support bundle opt-in stage variable so the lite-engine can enable
    // collection via the stage variable even when the account-level FF is off (OR gating).
    liteEngineStageVars.add("HARNESS_CI_SUPPORT_BUNDLE_ENABLED");
    Map<String, String> liteEngineStageEnvVars =
        extractStageVariablesForLiteEngine(initializeStepInfo.getVariables(), liteEngineStageVars);

    Optional<CIK8ContainerParams> liteEngineContainerParams = ephemeralDelegateMode
        ? Optional.empty()
        : Optional.of(internalContainerParamsProvider.getLiteEngineContainerParams(harnessInternalImageConnector,
              new HashMap<>(), k8PodDetails, stageCpuRequest, stageMemoryRequest, logEnvVars, tiEnvVars, emptyMap(),
              coverageEnvVariables, principalTokenEnvVars, volumeToMountPath, volumeToMountInfoV2,
              k8InitializeTaskUtils.getWorkDir(),
              resolveCtrSecurityContext(infrastructure, isV1Yaml, os, k8InfraSettings), logPrefix, ambiance,
              secretEnvVars, imagePullPolicy, os, ignoreConservativeLimits, liteEngineStageEnvVars));

    List<CIK8ContainerParams> containerParams = new ArrayList<>();
    List<ContainerDefinitionInfo> stageCtrDefinitions =
        getStageContainerDefinitions(initializeStepInfo, infrastructure, ambiance);
    // TODO: (vinicius.calasans) Remove call to consumePortDetails here after a couple of releases;
    //  since port details is already being via `consumeContainerDetails`.
    consumePortDetails(ambiance, stageCtrDefinitions);
    consumeContainerDetails(ambiance, stageCtrDefinitions);
    Map<String, List<ConnectorConversionInfo>> stepConnectors =
        k8InitializeStepUtils.getStepConnectorRefs(initializeStepInfo.getExecutionElementConfig(), ambiance);
    for (ContainerDefinitionInfo containerDefinitionInfo : stageCtrDefinitions) {
      CIK8ContainerParams cik8ContainerParams =
          createCIK8ContainerParams(ngAccess, containerDefinitionInfo, harnessInternalImageConnector, commonEnvVars,
              emptyMap(), principalTokenEnvVars, stepTiSecretEnvVars, stepCoverageSecretEnvVars, stepConnectors,
              volumeToMountPath, volumeToMountInfoV2, k8InitializeTaskUtils.getWorkDir(),
              k8InitializeTaskUtils.getCtrSecurityContext(infrastructure, k8InfraSettings), logPrefix,
              secretVariableDetails, githubApiTokenFunctorConnectors, os, secretEnvVars, ambiance,
              initializeStepInfo.getVariables(), isV1Yaml);
      containerParams.add(cik8ContainerParams);
    }
    Map<String, String> extractedCommonEnvVars = new HashMap<>();
    // When the stage variable is explicitly set to "true", the known delegate is new enough (87300+)
    // so we strip common vars from individual containers here on the manager side.  This keeps the
    // task payload small and lets 87300–89299 delegates benefit from the optimization today.
    //
    // When only the feature flag is enabled we must not strip here, because the account may have
    // delegates older than 87300 that would fail to start the pod with missing env vars.  Instead
    // we leave commonEnvVars empty and rely on the applyCommonEnvOptimization flag (set in the pod
    // params by getK8DirectPodParams) to signal 89300+ delegates to compute and strip themselves.
    // Delegates below 89300 do not know this flag and safely fall back to using full container vars,
    // keeping the pod YAML the same size as before the feature existed — no regression.
    if (isCommonEnvPodEnabled(accountId, initializeStepInfo.getVariables())
        && CIStepInfoUtils.isCommonEnvPodStageVarEnabled(initializeStepInfo.getVariables())) {
      extractedCommonEnvVars = extractCommonEnvVars(containerParams);
    }
    liteEngineContainerParams.ifPresent(containerParams::add);

    return Triple.of(setupAddOnContainerParams, containerParams, extractedCommonEnvVars);
  }

  private void setModuleImplicitStepsConfigToInitInfo(InitializeStepInfo initializeStepInfo, Ambiance ambiance,
      String accountId, Map<String, Object> deployModuleMetadata) {
    List<ExecutionWrapperConfig> modulesImplicitSteps =
        modulesImplicitStepsConfigHandler.getModulesImplicitSteps(accountId, ambiance, deployModuleMetadata);
    initializeStepInfo.setModuleImplicitStepsConfig(modulesImplicitSteps);
  }

  private void consumePortDetails(Ambiance ambiance, List<ContainerDefinitionInfo> containerDefinitionInfos) {
    Map<String, List<Integer>> portDetails = containerDefinitionInfos.stream().collect(
        Collectors.toMap(ContainerDefinitionInfo::getStepIdentifier, ContainerDefinitionInfo::getPorts));
    k8InitializeTaskUtils.consumeSweepingOutput(
        ambiance, ContainerPortDetails.builder().portDetails(portDetails).build(), PORT_DETAILS);
  }

  private void consumeContainerDetails(Ambiance ambiance, List<ContainerDefinitionInfo> containerDefinitionInfos) {
    Map<String, String> nameDetails = containerDefinitionInfos.stream().collect(
        Collectors.toMap(ContainerDefinitionInfo::getStepIdentifier, ContainerDefinitionInfo::getName));
    Map<String, List<Integer>> portDetails = containerDefinitionInfos.stream().collect(
        Collectors.toMap(ContainerDefinitionInfo::getStepIdentifier, ContainerDefinitionInfo::getPorts));
    k8InitializeTaskUtils.consumeSweepingOutput(ambiance,
        ContainerDetails.builder().nameDetails(nameDetails).portDetails(portDetails).build(), CONTAINER_DETAILS);
  }

  private SecretEnvVars getSecretEnvVars(Ambiance ambiance) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    SecretEnvVarsBuilder builder = SecretEnvVars.builder();
    boolean hasAny = false;
    if (featureFlagService.isEnabled(FeatureName.SSCA_ENABLED, accountId)
        || sscaLicenseHelper.hasActiveLicense(accountId)) {
      builder.sscaEnvVars(sscaServiceUtils.getSSCAServiceEnvVariables(accountId));
      hasAny = true;
    }
    if (hsaServiceUtils != null && featureFlagService.isEnabled(FeatureName.HSA_ENABLED, accountId)) {
      builder.hsaEnvVars(hsaServiceUtils.getHSAServiceEnvVariables());
      hasAny = true;
    }
    return hasAny ? builder.build() : null;
  }

  private CIK8ContainerParams createCIK8ContainerParams(NGAccess ngAccess,
      ContainerDefinitionInfo containerDefinitionInfo, ConnectorDetails harnessInternalImageConnector,
      Map<String, String> commonEnvVars, Map<String, String> stoEnvVars, Map<String, String> principalTokenEnvVars,
      Map<String, String> stepTiSecretEnvVars, Map<String, String> stepCoverageSecretEnvVars,
      Map<String, List<ConnectorConversionInfo>> connectorRefs, Map<String, String> volumeToMountPath,
      Map<String, List<VolumeMountInfo>> volumeMountInfoV2, String workDirPath,
      ContainerSecurityContext ctrSecurityContext, String logPrefix, List<SecretVariableDetails> secretVariableDetails,
      Map<String, ConnectorDetails> githubApiTokenFunctorConnectors, OSType os, SecretEnvVars secretEnvVars,
      Ambiance ambiance, List<NGVariable> variables, boolean isV1Yaml) {
    Map<String, String> envVars = new HashMap<>();
    if (isNotEmpty(containerDefinitionInfo.getEnvVars())) {
      envVars.putAll(containerDefinitionInfo.getEnvVars()); // Put customer input env variables
    }
    Map<String, ConnectorDetails> stepConnectorDetails = new HashMap<>();
    if (isNotEmpty(containerDefinitionInfo.getStepIdentifier()) && isNotEmpty(connectorRefs)) {
      List<ConnectorConversionInfo> connectorConversionInfos =
          connectorRefs.get(containerDefinitionInfo.getStepIdentifier());
      if (connectorConversionInfos != null && connectorConversionInfos.size() > 0) {
        for (ConnectorConversionInfo connectorConversionInfo : connectorConversionInfos) {
          populateStepConnectorDetails(
              containerDefinitionInfo, ngAccess, connectorConversionInfo, ambiance, stepConnectorDetails);
        }
      }
    }

    ImageDetails imageDetails = containerDefinitionInfo.getContainerImageDetails().getImageDetails();
    ConnectorDetails connectorDetails = null;
    if (containerDefinitionInfo.getContainerImageDetails().getConnectorIdentifier() != null) {
      connectorDetails = connectorUtils.getConnectorDetails(
          ngAccess, containerDefinitionInfo.getContainerImageDetails().getConnectorIdentifier());
      if (BaseConnectorUtils.isAwsCredentialBrokerConnector(connectorDetails)) {
        BaseConnectorUtils.renderBrokerSpecExpressions(connectorDetails, ambiance, cdStepsExpressionResolver);
      }
    }

    boolean isHarnessArtifactFFEnabled =
        featureFlagService.isEnabled(FeatureName.HAR_ENABLED, ngAccess.getAccountIdentifier());
    boolean isVanityEnabled = false;
    if (isHarnessArtifactFFEnabled && containerDefinitionInfo.getContainerImageDetails().getRegistryRef() != null) {
      isVanityEnabled = featureFlagService.isEnabled(REGISTRY_VANITY_URL_ENABLED, ngAccess.getAccountIdentifier());
      connectorDetails = connectorUtils.getConnectorDetailsForHarnessArtifactRegistry(ngAccess);
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
        k8InitializeTaskUtils.getSecretVariableDetails(ngAccess, containerDefinitionInfo, secretVariableDetails);

    Map<String, String> envVarsWithSecretRef = k8InitializeTaskUtils.removeEnvVarsWithSecretRef(envVars);

    // CI-22648: V1 explicit Git Clone steps own the codebase env; skip those keys from commonEnvVars.
    boolean isExplicitGitCloneStep = isV1Yaml && "true".equals(envVars.get(HARNESS_CI_EXPLICIT_GIT_CLONE_STEP));
    if (isExplicitGitCloneStep) {
      commonEnvVars.forEach((k, v) -> {
        if (!CODEBASE_DISCLAIM_KEYS.contains(k)) {
          envVars.put(k, v);
        }
      });
    } else {
      envVars.putAll(commonEnvVars);
    }

    /*
    Reuse V0's GitClone rules to default to /harness/<repoName>. When the step did declare
    a workspace, re-apply it after the commonEnvVars merge so the user's clone_directory wins.
    Gated on the explicit-clone marker so non-clone V1 steps that happen to carry DRONE_REMOTE_URL
    are not affected.
    */
    if (isExplicitGitCloneStep && containerDefinitionInfo.getEnvVars() != null) {
      String droneRemoteUrl = containerDefinitionInfo.getEnvVars().get(DRONE_REMOTE_URL);
      if (isNotEmpty(droneRemoteUrl)) {
        String originalWorkspace = containerDefinitionInfo.getEnvVars().get(DRONE_WORKSPACE);
        if (isEmpty(originalWorkspace)) {
          envVars.putAll(PluginServiceImpl.getCloneDirEnvVars(
              ParameterField.ofNull(), null, droneRemoteUrl, GIT_CLONE_STEP_ID, os));
        } else {
          envVars.put(DRONE_WORKSPACE, originalWorkspace);
        }
      }
    }

    envVars.put(DRONE_STEP_NAME, containerDefinitionInfo.getStepName());
    envVars.put(DRONE_STEP_NUMBER, containerDefinitionInfo.getName().split("-")[1]); // to parse step no

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

    validateContainerSecurityContext(os, ctrSecurityContext);

    CIK8ContainerParams cik8ContainerParams =
        CIK8ContainerParams.builder()
            .name(containerDefinitionInfo.getName())
            .containerResourceParams(containerDefinitionInfo.getContainerResourceParams())
            .containerType(containerDefinitionInfo.getContainerType())
            .envVars(envVars)
            .envVarsWithSecretRef(envVarsWithSecretRef)
            .containerSecrets(ContainerSecrets.builder()
                                  .secretVariableDetails(containerSecretVariableDetails)
                                  .connectorDetailsMap(stepConnectorDetails)
                                  .functorConnectors(githubApiTokenFunctorConnectors)
                                  .plainTextSecretsByName(internalContainerParamsProvider.getLiteEngineSecretVars(
                                      emptyMap(), stepTiSecretEnvVars, stoEnvVars, stepCoverageSecretEnvVars,
                                      principalTokenEnvVars, secretEnvVars))
                                  .build())
            .commands(containerDefinitionInfo.getCommands())
            .ports(containerDefinitionInfo.getPorts())
            .args(containerDefinitionInfo.getArgs())
            .imageDetailsWithConnector(imageDetailsWithConnector)
            .volumeToMountPath(volumeToMountPath)
            .volumeToMountPathV2(volumeMountInfoV2)
            .imagePullPolicy(containerDefinitionInfo.getImagePullPolicy())
            .securityContext(isV1Yaml && os == OSType.Windows ? null : ctrSecurityContext)
            .build();

    if (os != OSType.Windows) {
      cik8ContainerParams.setPrivileged(privileged);
      cik8ContainerParams.setRunAsUser(containerDefinitionInfo.getRunAsUser());
    }

    if (containerDefinitionInfo.getContainerType() != CIContainerType.SERVICE) {
      cik8ContainerParams.setWorkingDir(workDirPath);
    }
    return cik8ContainerParams;
  }

  private String resolveOverlayYaml(K8sDirectInfraYaml k8sDirectInfraYaml, Ambiance ambiance) {
    String overlayYaml = resolveStringParameter(
        "podSpecOverlay", null, "stageSetup", k8sDirectInfraYaml.getSpec().getPodSpecOverlay(), false);
    if (isEmpty(overlayYaml)) {
      Map<String, String> k8InfraSettings =
          k8InitializeTaskUtils.fetchK8InfraAdvancedSettings(AmbianceUtils.getAccountId(ambiance),
              AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
      overlayYaml = StringUtils.trimToNull(k8InfraSettings.get(SettingIdentifiers.CI_K8_POD_SPEC_OVERLAY));
    }
    return overlayYaml;
  }

  /*
  k8 throws error whenever the following combination of SecurityContext fields are set
  https://kubernetes.io/docs/tasks/configure-pod-container/security-context/
   */
  private void validateContainerSecurityContext(OSType osType, ContainerSecurityContext ctrSecurityContext) {
    if (OSType.Linux.equals(osType) && Boolean.TRUE.equals(ctrSecurityContext.getPrivileged())
        && Boolean.FALSE.equals(ctrSecurityContext.getAllowPrivilegeEscalation())) {
      throw new CIStageExecutionException(privilegeFieldExceptionMessage);
    }
  }

  // GKE's Windows admission webhook rejects pods that carry Linux SecurityContext fields. The V1 runner
  // serializes the full ContainerSecurityContext into the proto request, so we omit it entirely for
  // V1 + Windows. V0 builds the pod spec via a different layer that produces a webhook-tolerated minimal
  // shape, so it stays unchanged. Returns a fresh ContainerSecurityContext per call so each consumer
  // gets an independent object.
  private ContainerSecurityContext resolveCtrSecurityContext(
      Infrastructure infrastructure, boolean isV1Yaml, OSType os, Map<String, String> k8InfraSettings) {
    if (isV1Yaml && os == OSType.Windows) {
      return null;
    }
    return k8InitializeTaskUtils.getCtrSecurityContext(infrastructure, k8InfraSettings);
  }

  private void populateStepConnectorDetails(ContainerDefinitionInfo containerDefinitionInfo, NGAccess ngAccess,
      ConnectorConversionInfo connectorConversionInfo, Ambiance ambiance,
      Map<String, ConnectorDetails> stepConnectorDetails) {
    String harnessCodeRepo = null;
    StepOperationMetadata stepOperationMetadata = containerDefinitionInfo.getStepOperationMetadata();

    if (stepOperationMetadata != null && stepOperationMetadata.isHarnessCode()) {
      harnessCodeRepo = stepOperationMetadata.getHarnessCodeRepo();
    }

    ConnectorDetails connectorDetails =
        getConnectorDetailsWithConversionInfo(ngAccess, connectorConversionInfo, ambiance, harnessCodeRepo);
    String identifierForKey;
    if ((connectorConversionInfo.getRegistryRef() != null
            && featureFlagService.isEnabled(FeatureName.HAR_ENABLED, ngAccess.getAccountIdentifier()))
        || StringUtils.isNotBlank(harnessCodeRepo)) {
      identifierForKey = connectorDetails.getIdentifier();
    } else {
      identifierForKey = connectorConversionInfo.getConnectorRef();
    }
    connectorUtils.mergeConnectorDetails(stepConnectorDetails, connectorDetails, ngAccess, identifierForKey);
  }

  public ConnectorDetails getConnectorDetailsWithConversionInfo(
      NGAccess ngAccess, ConnectorConversionInfo connectorConversionInfo, Ambiance ambiance, String harnessCodeRepo) {
    ConnectorDetails connectorDetails;
    if (connectorConversionInfo.getRegistryRef() != null
        && featureFlagService.isEnabled(FeatureName.HAR_ENABLED, ngAccess.getAccountIdentifier())) {
      connectorDetails = connectorUtils.getConnectorDetailsForHarnessArtifactRegistry(ngAccess);
    } else {
      connectorDetails =
          codebaseUtils.getGitConnector(ngAccess, connectorConversionInfo.getConnectorRef(), ambiance, harnessCodeRepo);
    }
    BaseConnectorUtils.mergeOrReplaceEnvToSecretsMap(connectorDetails, connectorConversionInfo);
    if (BaseConnectorUtils.isAwsCredentialBrokerConnector(connectorDetails)) {
      BaseConnectorUtils.renderBrokerSpecExpressions(connectorDetails, ambiance, cdStepsExpressionResolver);
    }
    return connectorDetails;
  }

  private List<ContainerDefinitionInfo> getStageContainerDefinitions(
      InitializeStepInfo initializeStepInfo, Infrastructure infrastructure, Ambiance ambiance) {
    OSType os = k8InitializeTaskUtils.getOS(infrastructure);
    Set<Integer> usedPorts = new HashSet<>();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(usedPorts).build();

    IntegrationStageNode stageNode =
        IntegrationStageNode.builder()
            .type(IntegrationStageNode.StepType.CI)
            .identifier(initializeStepInfo.getStageIdentifier())
            .variables(initializeStepInfo.getVariables())
            .pipelineVariables(initializeStepInfo.getPipelineVariables())
            .integrationStageConfig((IntegrationStageConfigImpl) initializeStepInfo.getStageElementConfig())
            .build();
    StageDetails stageDetails = getStageDetails(ambiance);
    CIExecutionArgs ciExecutionArgs =
        CIExecutionArgs.builder()
            .runSequence(String.valueOf(ambiance.getMetadata().getRunSequence()))
            .executionSource(initializeStepInfo.getExecutionSource() != null ? initializeStepInfo.getExecutionSource()
                                                                             : stageDetails.getExecutionSource())
            .build();
    List<ContainerDefinitionInfo> serviceCtrDefinitionInfos =
        k8InitializeServiceUtils.createServiceContainerDefinitions(stageNode, portFinder, os, ambiance);
    List<ContainerDefinitionInfo> stepCtrDefinitionInfos =
        k8InitializeStepUtils.createStepContainerDefinitions(initializeStepInfo, stageNode, ciExecutionArgs, portFinder,
            AmbianceUtils.getAccountId(ambiance), os, ambiance, 0);

    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();
    containerDefinitionInfos.addAll(serviceCtrDefinitionInfos);
    containerDefinitionInfos.addAll(stepCtrDefinitionInfos);
    return containerDefinitionInfos;
  }

  private void saveSweepingOutput(InitializeStepInfo initializeStepInfo, String podName, Infrastructure infrastructure,
      Pair<Optional<CIK8ContainerParams>, List<CIK8ContainerParams>> podContainers, Ambiance ambiance,
      boolean shouldRouteStageToRunner) {
    List<String> containerNames = podContainers.getRight().stream().map(CIK8ContainerParams::getName).collect(toList());
    podContainers.getLeft().map(CIK8ContainerParams::getName).ifPresent(containerNames::add);

    k8InitializeTaskUtils.consumeSweepingOutput(ambiance,
        PodCleanupDetails.builder()
            .infrastructure(infrastructure)
            .podName(podName)
            .cleanUpContainerNames(containerNames)
            .build(),
        CLEANUP_DETAILS);
    log.info("Successfully saved PodCleanupDetails to sweeping output for planExecutionId: {}, stageExecutionId: {}, "
            + "podName: {}",
        ambiance.getPlanExecutionId(), ambiance.getStageExecutionId(), podName);

    k8InitializeTaskUtils.consumeSweepingOutput(ambiance,
        K8StageInfraDetails.builder()
            .variables(initializeStepInfo.getVariables())
            .infrastructure(infrastructure)
            .podName(podName)
            .containerNames(containerNames)
            .routeToRunner(shouldRouteStageToRunner)
            .build(),
        STAGE_INFRA_DETAILS);
    log.info("Successfully saved K8StageInfraDetails to sweeping output for planExecutionId: {}, stageExecutionId: {}, "
            + "podName: {}",
        ambiance.getPlanExecutionId(), ambiance.getStageExecutionId(), podName);
  }

  private String getPodName(Ambiance ambiance, String stageId) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS));
    if (optionalSweepingOutput.isFound()) {
      StageInfraDetails stageInfraDetails = (StageInfraDetails) optionalSweepingOutput.getOutput();
      StageInfraDetails.Type type = stageInfraDetails.getType();
      if (type == StageInfraDetails.Type.K8) {
        K8StageInfraDetails k8StageInfraDetails = (K8StageInfraDetails) stageInfraDetails;
        return k8StageInfraDetails.getPodName();
      }
    }
    return k8InitializeTaskUtils.generatePodName(stageId);
  }

  private StageDetails getStageDetails(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Unable to fetch stage details. Please retry or verify pipeline yaml");
    }
    return (StageDetails) optionalSweepingOutput.getOutput();
  }

  private Map<String, String> extractCommonEnvVars(List<CIK8ContainerParams> containerParams) {
    Map<String, String> extractedCommonEnvVars = computeCommonEnvVars(containerParams);

    // Remove common env vars from individual containers
    if (!extractedCommonEnvVars.isEmpty()) {
      for (CIK8ContainerParams containerParam : containerParams) {
        Map<String, String> containerEnvVars = containerParam.getEnvVars();
        if (containerEnvVars != null) {
          for (String commonKey : extractedCommonEnvVars.keySet()) {
            containerEnvVars.remove(commonKey);
          }
        }
      }
      log.info("Extracted {} common environment variables from containers to commonEnvVars map",
          extractedCommonEnvVars.size());
    }

    return extractedCommonEnvVars;
  }

  /**
   * Computes the intersection of environment variables that are identical across all containers,
   * WITHOUT removing them from individual containers. Used for the feature-flag-only path where
   * old delegates must still be able to start the pod using the full per-container env vars.
   * New delegates will strip these vars themselves (see CIK8InitializeTaskHandler).
   */
  private Map<String, String> computeCommonEnvVars(List<CIK8ContainerParams> containerParams) {
    Map<String, String> commonEnvVars = new HashMap<>();

    if (containerParams.isEmpty()) {
      return commonEnvVars;
    }

    if (containerParams.get(0).getEnvVars() != null) {
      commonEnvVars.putAll(containerParams.get(0).getEnvVars());
    }

    for (int i = 1; i < containerParams.size(); i++) {
      Map<String, String> containerEnvVars = containerParams.get(i).getEnvVars();
      if (containerEnvVars == null) {
        commonEnvVars.clear();
        break;
      }
      commonEnvVars.entrySet().removeIf(entry
          -> !containerEnvVars.containsKey(entry.getKey())
              || !Objects.equals(entry.getValue(), containerEnvVars.get(entry.getKey())));
    }

    return commonEnvVars;
  }

  boolean isCommonEnvPodEnabled(String accountId, List<NGVariable> variables) {
    // Check if stage variable explicitly disables common env pod
    if (CIStepInfoUtils.isCommonEnvPodStageVarDisabled(variables)) {
      return false;
    }
    // Check if stage variable enables common env pod OR feature flag is enabled
    return CIStepInfoUtils.isCommonEnvPodStageVarEnabled(variables)
        || featureFlagService.isEnabled(FeatureName.CI_COMMON_ENV_POD, accountId);
  }

  private Map<String, String> extractStageVariablesForLiteEngine(
      List<NGVariable> variables, List<String> variableNames) {
    Map<String, String> envVars = new HashMap<>();
    if (isEmpty(variables) || isEmpty(variableNames)) {
      return envVars;
    }

    for (NGVariable variable : variables) {
      if (variable == null) {
        log.warn("Encountered null variable in stage variables list, skipping");
        continue;
      }
      String varName = variable.getName();
      if (varName == null || isEmpty(varName)) {
        log.warn("Encountered variable with null or empty name, skipping");
        continue;
      }
      if (variableNames.contains(varName)) {
        ParameterField<?> parameterField = variable.fetchValue();
        if (ParameterField.isNotNull(parameterField)) {
          try {
            String varValue = null;
            if (parameterField.isExpression()) {
              varValue = parameterField.getExpressionValue();
            } else if (parameterField.obtainValue() != null) {
              varValue = parameterField.obtainValue().toString();
            }
            if (isNotEmpty(varValue)) {
              envVars.put(varName, varValue);
            }
          } catch (Exception e) {
            log.warn("Failed to extract stage variable '{}' for LiteEngine, skipping", varName, e);
          }
        }
      }
    }

    return envVars;
  }

  private StepServiceEnvBundle buildStepServiceEnvVars(
      Map<String, String> tiEnvVars, Map<String, String> coverageEnvVariables) {
    Map<String, String> commonEndpointEnvVars = new HashMap<>();
    Map<String, String> tiSecretEnvVars = new HashMap<>();
    Map<String, String> coverageSecretEnvVars = new HashMap<>();

    if (isNotEmpty(tiEnvVars)) {
      if (tiEnvVars.containsKey(TI_SERVICE_ENDPOINT_VARIABLE)) {
        commonEndpointEnvVars.put(TI_SERVICE_ENDPOINT_VARIABLE, tiEnvVars.get(TI_SERVICE_ENDPOINT_VARIABLE));
      }
      if (tiEnvVars.containsKey(TI_SERVICE_TOKEN_VARIABLE)) {
        tiSecretEnvVars.put(TI_SERVICE_TOKEN_VARIABLE, tiEnvVars.get(TI_SERVICE_TOKEN_VARIABLE));
      }
    }

    if (isNotEmpty(coverageEnvVariables)) {
      if (coverageEnvVariables.containsKey(COVERAGE_SERVICE_ENDPOINT_VARIABLE)) {
        commonEndpointEnvVars.put(
            COVERAGE_SERVICE_ENDPOINT_VARIABLE, coverageEnvVariables.get(COVERAGE_SERVICE_ENDPOINT_VARIABLE));
      }
      if (coverageEnvVariables.containsKey(COVERAGE_SERVICE_TOKEN_VARIABLE)) {
        coverageSecretEnvVars.put(
            COVERAGE_SERVICE_TOKEN_VARIABLE, coverageEnvVariables.get(COVERAGE_SERVICE_TOKEN_VARIABLE));
      }
    }

    return StepServiceEnvBundle.builder()
        .commonEndpointEnvVars(commonEndpointEnvVars)
        .tiSecretEnvVars(tiSecretEnvVars)
        .coverageSecretEnvVars(coverageSecretEnvVars)
        .build();
  }

  @Value
  @Builder
  private static class StepServiceEnvBundle {
    Map<String, String> commonEndpointEnvVars;
    Map<String, String> tiSecretEnvVars;
    Map<String, String> coverageSecretEnvVars;
  }
}
