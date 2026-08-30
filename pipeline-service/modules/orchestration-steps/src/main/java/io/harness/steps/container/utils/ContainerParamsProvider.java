/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.ci.commonconstants.CIExecutionConstants.CI_UPLOAD_LOGS_VIA_HARNESS;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_ERRORS_YAML_PATH;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_APPEND_CERTS_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_CUSTOM_ERROR_CATEGORIZATION;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_ENABLE_EXTRA_CHARACTERS_SECRETS_MASKING;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_ENABLE_HARNESS_ANNOTATIONS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_NEW_VERSION_GODOTENV;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.DELEGATE_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.DELEGATE_SERVICE_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.DELEGATE_SERVICE_ID_VARIABLE_VALUE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_ACCOUNT_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_BUILD_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_ENGINE_LOG_UPLOAD_CONCURRENCY_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_INCREASE_LOG_LIMIT;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_INDIRECT_LOG_UPLOAD_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_OVERRIDE_SERVICE_URLS_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_TEST_SUMMARY_OUTPUT_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_EXECUTION_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_LE_STATUS_REST_ENABLED;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_LOG_PREFIX_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_ORG_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_PIPELINE_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_PROJECT_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_STAGE_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_WORKSPACE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_CONTAINER_CPU;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_CONTAINER_MEM;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PWSH_COMMAND;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.SETUP_ADDON_CONTAINER_NAME;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.SH_COMMAND;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.UNIX_SETUP_ADDON_ARGS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.WIN_SETUP_ADDON_ARGS;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.ci.pod.CICommonConstants.LITE_ENGINE_CONTAINER_NAME;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.TEXT;

import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.utils.CIEnvironmentVariablesUtils;
import io.harness.connector.CiIntegrationStageUtils;
import io.harness.delegate.beans.ci.ecs.CIECSContainerParams;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.exception.WingsException;
import io.harness.k8s.model.ImageDetails;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.steps.container.beans.ServiceEnvironmentVars;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.container.execution.output.ContainerDetailsSweepingOutput;
import io.harness.steps.plugin.ContainerStepSpec;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.utils.CIScopeInfoHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.yaml.core.variables.NGVariable;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
public class ContainerParamsProvider {
  @Inject ContainerExecutionConfig containerExecutionConfig;
  @Inject PmsFeatureFlagHelper featureFlagHelper;
  @Inject(optional = true) @Nullable private NGSettingsClient settingsClient;
  @Inject(optional = true) @Nullable private CIScopeInfoHelper ciScopeInfoHelper;

  public CIK8ContainerParams getLiteEngineContainerParams(ConnectorDetails harnessInternalImageConnector,
      ContainerDetailsSweepingOutput k8PodDetails, Integer stageCpuRequest, Integer stageMemoryRequest,
      ServiceEnvironmentVars serviceEnvironmentVars, Map<String, String> volumeToMountPath, String workDirPath,
      ContainerSecurityContext ctrSecurityContext, String logPrefix, Ambiance ambiance,
      CIExecutionImages overridenExecutionImages, String imagePullPolicy, ContainerStepSpec containerStepInfo) {
    String imageName = overridenExecutionImages == null || isEmpty(overridenExecutionImages.getLiteEngineTag())
        ? containerExecutionConfig.getLiteEngineImage()
        : overridenExecutionImages.getLiteEngineTag();

    return CIK8ContainerParams.builder()
        .name(LITE_ENGINE_CONTAINER_NAME)
        .containerResourceParams(getLiteEngineResourceParams(stageCpuRequest, stageMemoryRequest, ambiance))
        .envVars(getLiteEngineEnvVars(k8PodDetails, workDirPath, logPrefix, ambiance, containerStepInfo))
        .containerType(CIContainerType.LITE_ENGINE)
        .containerSecrets(ContainerSecrets.builder()
                              .plainTextSecretsByName(getLiteEngineSecretVars(emptyMap(),
                                  serviceEnvironmentVars.getLogEnvVars(), serviceEnvironmentVars.getStoEnvVars()))
                              .build())
        .imageDetailsWithConnector(
            ImageDetailsWithConnector.builder()
                .imageDetails(ImageDetails.builder()
                                  .name(getFullyQualifiedImageName(imageName, harnessInternalImageConnector))
                                  .build())
                .imageConnectorDetails(harnessInternalImageConnector)
                .build())
        .volumeToMountPath(volumeToMountPath)
        .securityContext(ctrSecurityContext)
        .workingDir(workDirPath)
        .imagePullPolicy(imagePullPolicy)
        .build();
  }

  /**
   * Same as {@link #getLiteEngineContainerParams} but builds {@link CIECSContainerParams} for ECS CI init (no K8 DTO).
   */
  public CIECSContainerParams getLiteEngineEcsContainerParams(ConnectorDetails harnessInternalImageConnector,
      ContainerDetailsSweepingOutput k8PodDetails, Integer stageCpuRequest, Integer stageMemoryRequest,
      ServiceEnvironmentVars serviceEnvironmentVars, Map<String, String> volumeToMountPath, String workDirPath,
      ContainerSecurityContext ctrSecurityContext, String logPrefix, Ambiance ambiance,
      CIExecutionImages overridenExecutionImages, String imagePullPolicy, ContainerStepSpec containerStepInfo) {
    String imageName = overridenExecutionImages == null || isEmpty(overridenExecutionImages.getLiteEngineTag())
        ? containerExecutionConfig.getLiteEngineImage()
        : overridenExecutionImages.getLiteEngineTag();

    return CIECSContainerParams.builder()
        .name(LITE_ENGINE_CONTAINER_NAME)
        .containerResourceParams(getLiteEngineResourceParams(stageCpuRequest, stageMemoryRequest, ambiance))
        .envVars(getLiteEngineEnvVars(k8PodDetails, workDirPath, logPrefix, ambiance, containerStepInfo))
        .containerType(CIContainerType.LITE_ENGINE)
        .containerSecrets(ContainerSecrets.builder()
                              .plainTextSecretsByName(getLiteEngineSecretVars(emptyMap(),
                                  serviceEnvironmentVars.getLogEnvVars(), serviceEnvironmentVars.getStoEnvVars()))
                              .build())
        .imageDetailsWithConnector(
            ImageDetailsWithConnector.builder()
                .imageDetails(ImageDetails.builder()
                                  .name(getFullyQualifiedImageName(imageName, harnessInternalImageConnector))
                                  .build())
                .imageConnectorDetails(harnessInternalImageConnector)
                .build())
        .volumeToMountPath(volumeToMountPath)
        .securityContext(ctrSecurityContext)
        .workingDir(workDirPath)
        .imagePullPolicy(imagePullPolicy)
        .build();
  }

  private ContainerResourceParams getLiteEngineResourceParams(
      Integer stepCpuRequest, Integer stepMemoryRequest, Ambiance ambiance) {
    // Lite-engine container size uses CDS_CONSERVATIVE_K8_RESOURCE_LIMITS. The same flag also
    // zeros stage overlay in ContainerStepInitHelper. CI_CONSERVATIVE_K8_RESOURCE_LIMITS still
    // zeros K8s runner stage_resource only, in RunnerRequestBuilder.
    boolean conservative = featureFlagHelper.isEnabled(
        AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_CONSERVATIVE_K8_RESOURCE_LIMITS);
    Integer cpu = conservative ? LITE_ENGINE_CONTAINER_CPU : stepCpuRequest + LITE_ENGINE_CONTAINER_CPU;
    Integer memory = conservative ? LITE_ENGINE_CONTAINER_MEM : stepMemoryRequest + LITE_ENGINE_CONTAINER_MEM;
    return ContainerResourceParams.builder()
        .resourceRequestMilliCpu(cpu)
        .resourceRequestMemoryMiB(memory)
        .resourceLimitMilliCpu(cpu)
        .resourceLimitMemoryMiB(memory)
        .build();
  }

  private ContainerResourceParams getAddonResourceParams() {
    Integer cpu = LITE_ENGINE_CONTAINER_CPU;
    Integer memory = LITE_ENGINE_CONTAINER_MEM;
    return ContainerResourceParams.builder()
        .resourceRequestMilliCpu(cpu)
        .resourceRequestMemoryMiB(memory)
        .resourceLimitMilliCpu(cpu)
        .resourceLimitMemoryMiB(memory)
        .build();
  }

  private Map<String, String> getLiteEngineEnvVars(ContainerDetailsSweepingOutput k8PodDetails, String workDirPath,
      String logPrefix, Ambiance ambiance, ContainerStepSpec containerStepInfo) {
    Map<String, String> envVars = new HashMap<>();
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final int buildNumber = ambiance.getMetadata().getRunSequence();
    final String stageID = k8PodDetails.getStepIdentifier();
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
    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_INDIRECT_LOG_UPLOAD)
        || Boolean.parseBoolean(ciUploadLogsViaHarness)) {
      envVars.put(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF, "true");
    }

    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_ENABLE_TEST_SUMMARY_AS_OUTPUTS)) {
      envVars.put(HARNESS_CI_TEST_SUMMARY_OUTPUT_FF, "true");
    }

    if (containerStepInfo instanceof InitContainerV2StepInfo
        && featureFlagHelper.isEnabled(accountID, FeatureName.CI_ENABLE_MULTILINE_OUTPUTS_SECRETS)) {
      envVars.put(CI_NEW_VERSION_GODOTENV, "true");
    }

    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_ENABLE_EXTRA_CHARACTERS_SECRETS_MASKING)) {
      envVars.put(CI_ENABLE_EXTRA_CHARACTERS_SECRETS_MASKING, "true");
    }

    envVars.put(CI_ENABLE_HARNESS_ANNOTATIONS, "true");

    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_CUSTOM_ERROR_CATEGORIZATION)) {
      envVars.put(CI_CUSTOM_ERROR_CATEGORIZATION, "true");
      extractStepGroupVariablesForLiteEngine(containerStepInfo, Arrays.asList(HARNESS_ERRORS_YAML_PATH), envVars);
    }

    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_INCREASE_LOG_LIMIT)) {
      envVars.put(HARNESS_CI_INCREASE_LOG_LIMIT, "true");
    }

    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_ENGINE_LOG_UPLOAD_CONCURRENCY)) {
      envVars.put(HARNESS_CI_ENGINE_LOG_UPLOAD_CONCURRENCY_FF, "true");
    }

    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_OVERRIDE_SERVICE_URLS)) {
      envVars.put(HARNESS_CI_OVERRIDE_SERVICE_URLS_FF, "true");
    }
    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_APPEND_CERTS)) {
      envVars.put(CI_APPEND_CERTS_FF, "true");
    }

    envVars.put(HARNESS_LE_STATUS_REST_ENABLED, "true");

    // Add environment variables that need to be used inside the lite engine container
    envVars.put(HARNESS_WORKSPACE, workDirPath);
    envVars.put(DELEGATE_SERVICE_ENDPOINT_VARIABLE, containerExecutionConfig.getDelegateServiceEndpointVariableValue());
    envVars.put(DELEGATE_SERVICE_ID_VARIABLE, DELEGATE_SERVICE_ID_VARIABLE_VALUE);
    envVars.put(HARNESS_ACCOUNT_ID_VARIABLE, accountID);
    envVars.put(HARNESS_PROJECT_ID_VARIABLE, projectID);
    envVars.put(HARNESS_ORG_ID_VARIABLE, orgID);
    envVars.put(HARNESS_PIPELINE_ID_VARIABLE, pipelineID);
    envVars.put(HARNESS_BUILD_ID_VARIABLE, String.valueOf(buildNumber));
    envVars.put(HARNESS_STAGE_ID_VARIABLE, stageID);
    envVars.put(HARNESS_EXECUTION_ID_VARIABLE, executionID);
    envVars.put(HARNESS_LOG_PREFIX_VARIABLE, logPrefix);

    // Add parentUniqueId if feature flag is enabled
    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_USE_UNIQUE_PARENT_ID_FOR_QUERY)) {
      CIEnvironmentVariablesUtils.addParentUniqueIdToEnv(
          envVars, ambiance, accountID, orgID, projectID, ciScopeInfoHelper);
    }

    return envVars;
  }

  public Map<String, SecretParams> getLiteEngineSecretVars(
      Map<String, String> envVarsWithPlainTextSecret, Map<String, String> logEnvVars, Map<String, String> stoEnvVars) {
    return getLiteEngineSecretVars(envVarsWithPlainTextSecret, logEnvVars, stoEnvVars, emptyMap());
  }

  public Map<String, SecretParams> getLiteEngineSecretVars(Map<String, String> envVarsWithPlainTextSecret,
      Map<String, String> logEnvVars, Map<String, String> stoEnvVars, Map<String, String> principalTokenEnvVars) {
    Map<String, String> vars = new HashMap<>();
    vars.putAll(envVarsWithPlainTextSecret);
    vars.putAll(logEnvVars);
    vars.putAll(stoEnvVars);
    if (isNotEmpty(principalTokenEnvVars)) {
      vars.putAll(principalTokenEnvVars);
    }

    Map<String, SecretParams> secretVars = new HashMap<>();
    for (Map.Entry<String, String> entry : vars.entrySet()) {
      secretVars.put(entry.getKey(),
          SecretParams.builder().secretKey(entry.getKey()).value(encodeBase64(entry.getValue())).type(TEXT).build());
    }
    return secretVars;
  }

  public CIK8ContainerParams getSetupAddonContainerParams(ConnectorDetails harnessInternalImageConnector,
      Map<String, String> volumeToMountPath, String workDir, ContainerSecurityContext ctrSecurityContext, OSType os,
      CIExecutionImages overridenExecutionImages, String imagePullPolicy) {
    Map<String, String> envVars = new HashMap<>();
    envVars.put(HARNESS_WORKSPACE, workDir);

    final String imageName = overridenExecutionImages == null || isEmpty(overridenExecutionImages.getAddonTag())
        ? containerExecutionConfig.getAddonImage()
        : overridenExecutionImages.getAddonTag();
    List<String> commands = SH_COMMAND;
    List<String> args = Arrays.asList(UNIX_SETUP_ADDON_ARGS);
    if (os == OSType.Windows) {
      commands = PWSH_COMMAND;
      args = Arrays.asList(WIN_SETUP_ADDON_ARGS);
    }
    return CIK8ContainerParams.builder()
        .name(SETUP_ADDON_CONTAINER_NAME)
        .envVars(envVars)
        .containerType(CIContainerType.ADD_ON)
        .imageDetailsWithConnector(
            ImageDetailsWithConnector.builder()
                .imageDetails(ImageDetails.builder()
                                  .name(getFullyQualifiedImageName(imageName, harnessInternalImageConnector))
                                  .build())
                .imageConnectorDetails(harnessInternalImageConnector)
                .build())
        .containerSecrets(ContainerSecrets.builder().build())
        .volumeToMountPath(volumeToMountPath)
        .commands(commands)
        .args(args)
        .securityContext(ctrSecurityContext)
        .containerResourceParams(getAddonResourceParams())
        .imagePullPolicy(imagePullPolicy)
        .build();
  }

  /**
   * Same as {@link #getSetupAddonContainerParams} but builds {@link CIECSContainerParams} for ECS CI init (no K8 DTO).
   */
  public CIECSContainerParams getSetupAddonEcsContainerParams(ConnectorDetails harnessInternalImageConnector,
      Map<String, String> volumeToMountPath, String workDir, ContainerSecurityContext ctrSecurityContext, OSType os,
      CIExecutionImages overridenExecutionImages, String imagePullPolicy) {
    Map<String, String> envVars = new HashMap<>();
    envVars.put(HARNESS_WORKSPACE, workDir);

    final String imageName = overridenExecutionImages == null || isEmpty(overridenExecutionImages.getAddonTag())
        ? containerExecutionConfig.getAddonImage()
        : overridenExecutionImages.getAddonTag();
    List<String> commands = SH_COMMAND;
    List<String> args = Arrays.asList(UNIX_SETUP_ADDON_ARGS);
    if (os == OSType.Windows) {
      commands = PWSH_COMMAND;
      args = Arrays.asList(WIN_SETUP_ADDON_ARGS);
    }
    return CIECSContainerParams.builder()
        .name(SETUP_ADDON_CONTAINER_NAME)
        .envVars(envVars)
        .containerType(CIContainerType.ADD_ON)
        .imageDetailsWithConnector(
            ImageDetailsWithConnector.builder()
                .imageDetails(ImageDetails.builder()
                                  .name(getFullyQualifiedImageName(imageName, harnessInternalImageConnector))
                                  .build())
                .imageConnectorDetails(harnessInternalImageConnector)
                .build())
        .containerSecrets(ContainerSecrets.builder().build())
        .volumeToMountPath(volumeToMountPath)
        .commands(commands)
        .args(args)
        .securityContext(ctrSecurityContext)
        .containerResourceParams(getAddonResourceParams())
        .imagePullPolicy(imagePullPolicy)
        .build();
  }

  public String getFullyQualifiedImageName(String imageName, ConnectorDetails connectorDetails) {
    try {
      return CiIntegrationStageUtils.getFullyQualifiedImageName(imageName, connectorDetails);
    } catch (WingsException ex) {
      log.error("Error while getting Fully qualified image", ex);
      throw new ContainerStepExecutionException(ex.getMessage());
    }
  }

  private void extractStepGroupVariablesForLiteEngine(
      ContainerStepSpec containerStepInfo, List<String> variableNames, Map<String, String> envVars) {
    if (!(containerStepInfo instanceof InitContainerV2StepInfo)) {
      return;
    }
    List<NGVariable> variables = ((InitContainerV2StepInfo) containerStepInfo).getVariables();
    if (isEmpty(variables) || isEmpty(variableNames)) {
      return;
    }

    for (NGVariable variable : variables) {
      if (variable == null) {
        log.warn("Encountered null variable in stepGroup variables list, skipping");
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
            log.warn("Failed to extract stepGroup variable '{}' for LiteEngine, skipping", varName, e);
          }
        }
      }
    }
  }
}
