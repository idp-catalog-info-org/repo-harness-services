/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.buildstate.providers;

import static io.harness.beans.FeatureName.CI_EXTRA_ADDON_RESOURCE;
import static io.harness.ci.commonconstants.CIExecutionConstants.CI_UPLOAD_LOGS_VIA_HARNESS;
import static io.harness.ci.commonconstants.CIExecutionConstants.DELEGATE_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.DELEGATE_SERVICE_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.DELEGATE_SERVICE_ID_VARIABLE_VALUE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_ACCOUNT_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_BUILD_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_CI_ENABLE_OUTPUTS_STEP_FAILURE_FF;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_CI_INCREASE_LOG_LIMIT;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_CI_INDIRECT_LOG_UPLOAD_FF;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_EXECUTION_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_LE_STATUS_REST_ENABLED;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_LOG_PREFIX_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_ORG_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_PIPELINE_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_PROJECT_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_STAGE_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_USER_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_WORKSPACE;
import static io.harness.ci.commonconstants.CIExecutionConstants.LITE_ENGINE_CONTAINER_CPU;
import static io.harness.ci.commonconstants.CIExecutionConstants.LITE_ENGINE_CONTAINER_MEM;
import static io.harness.ci.commonconstants.CIExecutionConstants.PWSH_COMMAND;
import static io.harness.ci.commonconstants.CIExecutionConstants.SETUP_ADDON_CONTAINER_NAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.SH_COMMAND;
import static io.harness.ci.commonconstants.CIExecutionConstants.UNIX_SETUP_ADDON_ARGS;
import static io.harness.ci.commonconstants.CIExecutionConstants.WIN_SETUP_ADDON_ARGS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.ADDON_CONTAINER_CPU;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.ADDON_CONTAINER_MEMORY;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_APPEND_CERTS_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_CUSTOM_ERROR_CATEGORIZATION;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_ENABLE_EXTRA_CHARACTERS_SECRETS_MASKING;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_ENABLE_HARNESS_ANNOTATIONS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_NEW_VERSION_GODOTENV;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_SUPPORT_BUNDLE_COLLECTION;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_TI_RERUN_FAILED_TEST_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_TI_V2_ENHANCED_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_ADDON_RETRY_MARKER_FILE_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_ENGINE_LOG_UPLOAD_CONCURRENCY_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_OVERRIDE_SERVICE_URLS_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_TEST_SUMMARY_OUTPUT_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_TRIM_NEW_LINE_SUFFIX_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.NETRC_SHARED_PATH;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.NETRC_VOLUME;
import static io.harness.ci.execution.utils.UsageUtils.getExecutionUser;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.ci.pod.CICommonConstants.LITE_ENGINE_CONTAINER_NAME;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.TEXT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.commonconstants.AwsBrokerConstants;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.execution.intfc.CIExecutionConfigService;
import io.harness.ci.execution.integrationstage.k8s.K8InitializeTaskUtils;
import io.harness.ci.execution.integrationstage.secret.SecretEnvVars;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtility;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.utils.CIEnvironmentVariablesUtils;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.ci.pod.VolumeMountInfo;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.utils.CIScopeInfoHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides container parameters for internally used containers
 */
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CI)
public class InternalContainerParamsProvider {
  // Env var read by the in-pod engine broker to locate the HarnessID OIDC token-generate endpoint.
  private static final String HARNESS_WI_TOKEN_GENERATE_URL = "HARNESS_WI_TOKEN_GENERATE_URL";

  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject CIExecutionConfigService ciExecutionConfigService;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject(optional = true) @Nullable private NGSettingsClient settingsClient;
  @Inject(optional = true) @Nullable private CIScopeInfoHelper ciScopeInfoHelper;

  public CIK8ContainerParams getSetupAddonContainerParams(ConnectorDetails harnessInternalImageConnector,
      Map<String, String> volumeToMountPath, Map<String, List<VolumeMountInfo>> volumeToMountInfoV2, String workDir,
      ContainerSecurityContext ctrSecurityContext, String accountIdentifier, OSType os, String imagePullPolicy) {
    Map<String, String> envVars = new HashMap<>();
    envVars.put(HARNESS_WORKSPACE, workDir);

    boolean brokerFfEnabled = featureFlagService.isEnabled(FeatureName.CI_AWS_CREDENTIAL_BROKER, accountIdentifier);
    if (brokerFfEnabled) {
      envVars.put(AwsBrokerConstants.HARNESS_CI_AWS_BROKER_ENABLED_ENV, "true");
      log.info("CI addon setup-addon staging enabled for AWS credential broker: account={}, envVar={}=true, os={}",
          accountIdentifier, AwsBrokerConstants.HARNESS_CI_AWS_BROKER_ENABLED_ENV, os);
    } else {
      log.debug(
          "CI_AWS_CREDENTIAL_BROKER FF disabled for account {}; skipping broker staging env var", accountIdentifier);
    }

    String imageName = ciExecutionConfigService.getAddonImage(accountIdentifier);

    if (os.equals(OSType.Windows)
        && featureFlagService.isEnabled(FeatureName.CI_ADDON_LE_WINDOWS_ROOTLESS, accountIdentifier)) {
      imageName = ciExecutionConfigService.getAddonImageRootless(accountIdentifier);
    }
    String fullyQualifiedImage =
        IntegrationStageUtility.getFullyQualifiedImageName(imageName, harnessInternalImageConnector);
    List<String> commands = SH_COMMAND;
    List<String> args = Arrays.asList(UNIX_SETUP_ADDON_ARGS);
    if (os == OSType.Windows) {
      commands = PWSH_COMMAND;
      args = Arrays.asList(WIN_SETUP_ADDON_ARGS);
    }
    Map<String, List<VolumeMountInfo>> newVolumeToMountInfoForV2 =
        new HashMap<>(volumeToMountInfoV2 != null ? volumeToMountInfoV2 : Collections.emptyMap());
    if (newVolumeToMountInfoForV2.containsKey(NETRC_VOLUME) && os == OSType.Linux) {
      newVolumeToMountInfoForV2.put(
          NETRC_VOLUME, Collections.singletonList(VolumeMountInfo.builder().mountPath(NETRC_SHARED_PATH).build()));
    }

    return CIK8ContainerParams.builder()
        .name(SETUP_ADDON_CONTAINER_NAME)
        .envVars(envVars)
        .containerType(CIContainerType.ADD_ON)
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder()
                                       .imageDetails(IntegrationStageUtils.getImageInfo(fullyQualifiedImage))
                                       .imageConnectorDetails(harnessInternalImageConnector)
                                       .build())
        .containerSecrets(ContainerSecrets.builder().build())
        .volumeToMountPath(volumeToMountPath)
        .volumeToMountPathV2(newVolumeToMountInfoForV2)
        .commands(commands)
        .args(args)
        .imagePullPolicy(imagePullPolicy)
        .securityContext(ctrSecurityContext)
        .containerResourceParams(getAddonResourceParams(accountIdentifier))
        .build();
  }

  public CIK8ContainerParams getLiteEngineContainerParams(ConnectorDetails harnessInternalImageConnector,
      Map<String, ConnectorDetails> publishArtifactConnectors, K8PodDetails k8PodDetails, Integer stageCpuRequest,
      Integer stageMemoryRequest, Map<String, String> logEnvVars, Map<String, String> tiEnvVars,
      Map<String, String> stoEnvVars, Map<String, String> coverageEnvVars, Map<String, String> principalTokenEnvVars,
      Map<String, String> volumeToMountPath, Map<String, List<VolumeMountInfo>> volumeToMountInfoV2, String workDirPath,
      ContainerSecurityContext ctrSecurityContext, String logPrefix, Ambiance ambiance, SecretEnvVars secretEnvVars,
      String imagePullPolicy, OSType os, boolean ignoreConservativeLimits, Map<String, String> liteEngineStageEnvVars) {
    String imageName = ciExecutionConfigService.getLiteEngineImage(AmbianceUtils.getAccountId(ambiance));
    if (os.equals(OSType.Windows)
        && featureFlagService.isEnabled(
            FeatureName.CI_ADDON_LE_WINDOWS_ROOTLESS, AmbianceUtils.getAccountId(ambiance))) {
      imageName = ciExecutionConfigService.getLiteEngineImageRootless(AmbianceUtils.getAccountId(ambiance));
    }
    String fullyQualifiedImage =
        IntegrationStageUtility.getFullyQualifiedImageName(imageName, harnessInternalImageConnector);

    return CIK8ContainerParams.builder()
        .name(LITE_ENGINE_CONTAINER_NAME)
        .containerResourceParams(getLiteEngineResourceParams(
            stageCpuRequest, stageMemoryRequest, AmbianceUtils.getAccountId(ambiance), ignoreConservativeLimits))
        .envVars(getLiteEngineEnvVars(k8PodDetails, workDirPath, logPrefix, ambiance, liteEngineStageEnvVars))
        .containerType(CIContainerType.LITE_ENGINE)
        .containerSecrets(ContainerSecrets.builder()
                              .connectorDetailsMap(publishArtifactConnectors)
                              .plainTextSecretsByName(getLiteEngineSecretVars(logEnvVars, tiEnvVars, stoEnvVars,
                                  coverageEnvVars, principalTokenEnvVars, secretEnvVars))
                              .build())
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder()
                                       .imageDetails(IntegrationStageUtils.getImageInfo(fullyQualifiedImage))
                                       .imageConnectorDetails(harnessInternalImageConnector)
                                       .build())
        .volumeToMountPath(volumeToMountPath)
        .volumeToMountPathV2(volumeToMountInfoV2)
        .imagePullPolicy(imagePullPolicy)
        .securityContext(ctrSecurityContext)
        .workingDir(workDirPath)
        .build();
  }

  private Map<String, String> getLiteEngineEnvVars(K8PodDetails k8PodDetails, String workDirPath, String logPrefix,
      Ambiance ambiance, Map<String, String> liteEngineStageEnvVars) {
    Map<String, String> envVars = new HashMap<>();
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String userID = getExecutionUser(ambiance.getMetadata().getPrincipalInfo());
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final int buildNumber = ambiance.getMetadata().getRunSequence();
    final String stageID = k8PodDetails.getStageID();
    final String executionID = ambiance.getPlanExecutionId();

    String ciUploadLogsViaHarness = null;
    // Applying try-catch because there is a possibility that setting won't exist in case of ci-manager running with old
    // ng manager.
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

    // Check whether FF to enable blob upload to log service (as opposed to directly blob storage) is enabled OR Setting
    // to Upload logs via log service is being set to true.
    if (featureFlagService.isEnabled(FeatureName.CI_INDIRECT_LOG_UPLOAD, accountID)
        || Boolean.parseBoolean(ciUploadLogsViaHarness)) {
      envVars.put(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF, "true");
    }

    if (featureFlagService.isEnabled(FeatureName.CI_ENABLE_TEST_SUMMARY_AS_OUTPUTS, accountID)) {
      envVars.put(HARNESS_CI_TEST_SUMMARY_OUTPUT_FF, "true");
    }

    if (featureFlagService.isEnabled(FeatureName.CI_TI_RERUN_FAILED_TEST, accountID)) {
      envVars.put(CI_TI_RERUN_FAILED_TEST_FF, "true");
    }

    if (featureFlagService.isEnabled(FeatureName.CI_TI_V2_ENHANCED, accountID)) {
      envVars.put(CI_TI_V2_ENHANCED_FF, "true");
    }

    // Retrieve and set the 'CI_NEW_VERSION_GODOTENV' setting
    if (featureFlagService != null
        && featureFlagService.isEnabled(FeatureName.CI_ENABLE_MULTILINE_OUTPUTS_SECRETS, accountID)) {
      envVars.put(CI_NEW_VERSION_GODOTENV, "true");
    }

    if (featureFlagService.isEnabled(FeatureName.CI_ENABLE_EXTRA_CHARACTERS_SECRETS_MASKING, accountID)) {
      envVars.put(CI_ENABLE_EXTRA_CHARACTERS_SECRETS_MASKING, "true");
    }

    envVars.put(CI_ENABLE_HARNESS_ANNOTATIONS, "true");

    K8InitializeTaskUtils.configureErrorMessageSettings(featureFlagService, settingsClient, accountID, envVars);

    if (featureFlagService.isEnabled(FeatureName.CI_CUSTOM_ERROR_CATEGORIZATION, accountID)) {
      envVars.put(CI_CUSTOM_ERROR_CATEGORIZATION, "true");
    }

    if (featureFlagService.isEnabled(FeatureName.CI_SUPPORT_BUNDLE_COLLECTION, accountID)) {
      envVars.put(CI_SUPPORT_BUNDLE_COLLECTION, "true");
    }

    if (featureFlagService.isEnabled(FeatureName.CI_ENABLE_OUTPUTS_STEP_FAILURE, accountID)) {
      envVars.put(HARNESS_CI_ENABLE_OUTPUTS_STEP_FAILURE_FF, "true");
    }

    if (featureFlagService.isEnabled(FeatureName.CI_INCREASE_LOG_LIMIT, accountID)) {
      envVars.put(HARNESS_CI_INCREASE_LOG_LIMIT, "true");
    }

    if (featureFlagService.isEnabled(FeatureName.CI_ENGINE_LOG_UPLOAD_CONCURRENCY, accountID)) {
      envVars.put(HARNESS_CI_ENGINE_LOG_UPLOAD_CONCURRENCY_FF, "true");
    }

    if (featureFlagService.isEnabled(FeatureName.CI_TRIM_NEW_LINE_SUFFIX, accountID)) {
      envVars.put(HARNESS_CI_TRIM_NEW_LINE_SUFFIX_FF, "true");
    }

    if (featureFlagService.isEnabled(FeatureName.CI_OVERRIDE_SERVICE_URLS, accountID)) {
      envVars.put(HARNESS_CI_OVERRIDE_SERVICE_URLS_FF, "true");
    }

    envVars.put(HARNESS_LE_STATUS_REST_ENABLED, "true");

    if (featureFlagService.isEnabled(FeatureName.CI_ADDON_RETRY_MARKER_FILE, accountID)) {
      envVars.put(HARNESS_CI_ADDON_RETRY_MARKER_FILE_FF, "true");
    }

    if (featureFlagService.isEnabled(FeatureName.CI_APPEND_CERTS, accountID)) {
      envVars.put(CI_APPEND_CERTS_FF, "true");
    }

    // Add environment variables that need to be used inside the lite engine container
    envVars.put(HARNESS_WORKSPACE, workDirPath);
    envVars.put(DELEGATE_SERVICE_ENDPOINT_VARIABLE, ciExecutionServiceConfig.getDelegateServiceEndpointVariableValue());
    envVars.put(DELEGATE_SERVICE_ID_VARIABLE, DELEGATE_SERVICE_ID_VARIABLE_VALUE);
    envVars.put(HARNESS_ACCOUNT_ID_VARIABLE, accountID);
    envVars.put(HARNESS_USER_ID_VARIABLE, userID);
    envVars.put(HARNESS_PROJECT_ID_VARIABLE, projectID);
    envVars.put(HARNESS_ORG_ID_VARIABLE, orgID);
    envVars.put(HARNESS_PIPELINE_ID_VARIABLE, pipelineID);
    envVars.put(HARNESS_BUILD_ID_VARIABLE, String.valueOf(buildNumber));
    envVars.put(HARNESS_STAGE_ID_VARIABLE, stageID);
    envVars.put(HARNESS_EXECUTION_ID_VARIABLE, executionID);
    envVars.put(HARNESS_LOG_PREFIX_VARIABLE, logPrefix);

    // Workload Identity (OIDC-without-connector): tell the in-pod engine broker where to mint OIDC tokens.
    String harnessIdTokenGenerateUrl = ciExecutionServiceConfig.getHarnessIdTokenGenerateUrl();
    if (isNotEmpty(harnessIdTokenGenerateUrl)) {
      envVars.put(HARNESS_WI_TOKEN_GENERATE_URL, harnessIdTokenGenerateUrl);
    }

    // Add parentUniqueId if feature flag is enabled
    if (featureFlagService.isEnabled(FeatureName.CI_USE_UNIQUE_PARENT_ID_FOR_QUERY, accountID)) {
      CIEnvironmentVariablesUtils.addParentUniqueIdToEnv(
          envVars, ambiance, accountID, orgID, projectID, ciScopeInfoHelper);
    }

    // Add stage variables to LiteEngine environment variables
    if (isNotEmpty(liteEngineStageEnvVars)) {
      envVars.putAll(liteEngineStageEnvVars);
    }

    return envVars;
  }

  public Map<String, SecretParams> getLiteEngineSecretVars(Map<String, String> logEnvVars,
      Map<String, String> tiEnvVars, Map<String, String> stoEnvVars, Map<String, String> coverageEnvVars,
      Map<String, String> principalTokenEnvVars, SecretEnvVars secretEnvVars) {
    Map<String, String> vars = new HashMap<>();
    vars.putAll(logEnvVars);
    vars.putAll(tiEnvVars);
    vars.putAll(stoEnvVars);
    vars.putAll(coverageEnvVars);
    vars.putAll(principalTokenEnvVars);
    if (secretEnvVars != null) {
      if (isNotEmpty(secretEnvVars.getSscaEnvVars())) {
        vars.putAll(secretEnvVars.getSscaEnvVars());
      }
      if (isNotEmpty(secretEnvVars.getHsaEnvVars())) {
        vars.putAll(secretEnvVars.getHsaEnvVars());
      }
    }

    Map<String, SecretParams> secretVars = new HashMap<>();
    for (Map.Entry<String, String> entry : vars.entrySet()) {
      secretVars.put(entry.getKey(),
          SecretParams.builder().secretKey(entry.getKey()).value(encodeBase64(entry.getValue())).type(TEXT).build());
    }
    return secretVars;
  }

  private ContainerResourceParams getLiteEngineResourceParams(
      Integer stageCpuRequest, Integer stageMemoryRequest, String accountId, boolean ignoreConservativeLimits) {
    Integer cpu = stageCpuRequest + LITE_ENGINE_CONTAINER_CPU;
    Integer memory = stageMemoryRequest + LITE_ENGINE_CONTAINER_MEM;
    // If FF "CI_CONSERVATIVE_K8_RESOURCE_LIMITS" is enabled, reset the lite engine resources to default values for cpu
    // and memory.
    if (!ignoreConservativeLimits
        && featureFlagService.isEnabled(FeatureName.CI_CONSERVATIVE_K8_RESOURCE_LIMITS, accountId)) {
      cpu = LITE_ENGINE_CONTAINER_CPU;
      memory = LITE_ENGINE_CONTAINER_MEM;
    }
    log.info("resource allocated to lite-engine:->  cpu: {}, memory: {} for accountId: {}", cpu, memory, accountId);
    return ContainerResourceParams.builder()
        .resourceRequestMilliCpu(cpu)
        .resourceRequestMemoryMiB(memory)
        .resourceLimitMilliCpu(cpu)
        .resourceLimitMemoryMiB(memory)
        .build();
  }

  private ContainerResourceParams getAddonResourceParams(String accountIdentifier) {
    Integer cpu = LITE_ENGINE_CONTAINER_CPU;
    Integer memory = LITE_ENGINE_CONTAINER_MEM;
    if (featureFlagService.isEnabled(CI_EXTRA_ADDON_RESOURCE, accountIdentifier)) {
      cpu = ADDON_CONTAINER_CPU;
      memory = ADDON_CONTAINER_MEMORY;
    }
    return ContainerResourceParams.builder()
        .resourceRequestMilliCpu(cpu)
        .resourceRequestMemoryMiB(memory)
        .resourceLimitMilliCpu(cpu)
        .resourceLimitMemoryMiB(memory)
        .build();
  }
}
