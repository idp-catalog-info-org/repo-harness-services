/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.beans.FeatureName.CI_ENABLE_MULTILINE_OUTPUTS_SECRETS;
import static io.harness.beans.FeatureName.CI_ENABLE_PLUGIN_OUTPUT_SECRETS;
import static io.harness.beans.FeatureName.CI_MOUNT_PATH_ENABLED_MAC;
import static io.harness.ci.commonconstants.CIExecutionConstants.ACCOUNT_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.ADDON_VOLUME;
import static io.harness.ci.commonconstants.CIExecutionConstants.ADDON_VOL_MOUNT_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.BUILD_NUMBER_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.CI_UPLOAD_LOGS_VIA_HARNESS;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_ACCOUNT_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_BUILD_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_EXECUTION_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_ORG_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_PIPELINE_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_PROJECT_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_TMP_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_USER_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.ORG_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.OSX_ADDON_MOUNT_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.OSX_STEP_MOUNT_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.PIPELINE_EXECUTION_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.PIPELINE_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.PROJECT_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.SHARED_VOLUME_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.STAGE_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.STAGE_RUNTIME_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_MOUNT_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_VOLUME;
import static io.harness.ci.commonconstants.CIExecutionConstants.TMP_HARNESS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_CUSTOM_ERROR_CATEGORIZATION;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_ENABLE_EXTRA_CHARACTERS_SECRETS_MASKING;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_ENABLE_HARNESS_ANNOTATIONS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_NEW_VERSION_GODOTENV;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_INDIRECT_LOG_UPLOAD_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.OSX_STEP_ALTERNATE_MOUNT_PATH;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PLUGIN_PIPELINE;
import static io.harness.ci.execution.utils.UsageUtils.getExecutionUser;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.exception.InvalidRequestException;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class VmInitializeUtils {
  @Inject private PmsFeatureFlagHelper featureFlagHelper;
  @Inject(optional = true) @Nullable private NGSettingsClient settingsClient;

  public Map<String, String> getCommonStepEnvVariables(Ambiance ambiance) {
    Map<String, String> envVars = new HashMap<>();
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String userID = getExecutionUser(ambiance.getMetadata().getPrincipalInfo());
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final int buildNumber = ambiance.getMetadata().getRunSequence();
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
      log.info("Indirect log upload FF is enabled or Setting to Upload logs via log service is being set to true for "
              + "accountID: {}",
          accountID);
      envVars.put(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF, "true");
    }

    envVars.put(HARNESS_ACCOUNT_ID_VARIABLE, accountID);
    envVars.put(HARNESS_USER_ID_VARIABLE, userID);
    envVars.put(HARNESS_PROJECT_ID_VARIABLE, projectID);
    envVars.put(HARNESS_ORG_ID_VARIABLE, orgID);
    envVars.put(HARNESS_PIPELINE_ID_VARIABLE, pipelineID);
    envVars.put(PLUGIN_PIPELINE, pipelineID);
    envVars.put(HARNESS_BUILD_ID_VARIABLE, String.valueOf(buildNumber));
    envVars.put(HARNESS_EXECUTION_ID_VARIABLE, executionID);
    envVars.put(HARNESS_TMP_PATH, TMP_HARNESS);
    if (featureFlagHelper.isEnabled(accountID, CI_ENABLE_MULTILINE_OUTPUTS_SECRETS)) {
      envVars.put(CI_NEW_VERSION_GODOTENV, "true");
    }
    if (featureFlagHelper.isEnabled(accountID, CI_ENABLE_PLUGIN_OUTPUT_SECRETS)) {
      envVars.put(CI_ENABLE_PLUGIN_OUTPUT_SECRETS.toString(), "true");
    }
    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_ENABLE_EXTRA_CHARACTERS_SECRETS_MASKING)) {
      envVars.put(CI_ENABLE_EXTRA_CHARACTERS_SECRETS_MASKING, "true");
    }
    envVars.put(CI_ENABLE_HARNESS_ANNOTATIONS, "true");

    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_CUSTOM_ERROR_CATEGORIZATION)) {
      envVars.put(CI_CUSTOM_ERROR_CATEGORIZATION, "true");
    }
    return envVars;
  }

  public Map<String, String> getVolumeToMountPath(ParameterField<List<String>> parameterSharedPaths, OSType os,
      String accountID, ContainerStepInfra infrastructure) {
    Map<String, String> volumeToMountPath = new HashMap<>();
    String stepMountPath = getStepMountPath(os, accountID, infrastructure);
    String addonMountPath = getAddonMountPath(os);
    volumeToMountPath.put(STEP_VOLUME, stepMountPath);
    volumeToMountPath.put(ADDON_VOLUME, addonMountPath);

    if (parameterSharedPaths == null) {
      return volumeToMountPath;
    }

    List<String> sharedPaths = (List<String>) parameterSharedPaths.fetchFinalValue();
    if (isEmpty(sharedPaths)) {
      return volumeToMountPath;
    }

    int index = 0;
    for (String path : sharedPaths) {
      if (isEmpty(path)) {
        continue;
      }

      String volumeName = format("%s%d", SHARED_VOLUME_PREFIX, index);
      if (path.equals(STEP_MOUNT_PATH)) {
        throw new InvalidRequestException(format("Shared path: %s is a reserved keyword ", path));
      }
      volumeToMountPath.put(volumeName, path);
      index++;
    }
    return volumeToMountPath;
  }

  private String getStepMountPath(OSType os, String accountID, ContainerStepInfra infrastructure) {
    if (os.equals(OSType.MacOS)) {
      if (infrastructure.getType().equals(Infrastructure.Type.DOCKER)
          && featureFlagHelper.isEnabled(accountID, CI_MOUNT_PATH_ENABLED_MAC)) {
        return OSX_STEP_ALTERNATE_MOUNT_PATH;
      } else {
        return OSX_STEP_MOUNT_PATH;
      }
    }
    return STEP_MOUNT_PATH;
  }

  private String getAddonMountPath(OSType os) {
    if (os.equals(OSType.MacOS)) {
      return OSX_ADDON_MOUNT_PATH;
    }
    return ADDON_VOL_MOUNT_PATH;
  }

  public String getWorkDir(OSType os, String accountID, ContainerStepInfra infrastructure) {
    return getStepMountPath(os, accountID, infrastructure);
  }

  public Map<String, String> getBuildTags(Ambiance ambiance, String stageId, String stageRuntimeId) {
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final String pipelineExecutionID = ambiance.getPlanExecutionId();
    final int buildNumber = ambiance.getMetadata().getRunSequence();

    Map<String, String> tags = new HashMap<>();
    tags.put(ACCOUNT_ID_ATTR, accountID);
    tags.put(ORG_ID_ATTR, orgID);
    tags.put(PROJECT_ID_ATTR, projectID);
    tags.put(PIPELINE_ID_ATTR, pipelineID);
    tags.put(PIPELINE_EXECUTION_ID_ATTR, pipelineExecutionID);
    tags.put(STAGE_ID_ATTR, stageId);
    tags.put(STAGE_RUNTIME_ID_ATTR, stageRuntimeId);
    tags.put(BUILD_NUMBER_ATTR, String.valueOf(buildNumber));
    return tags;
  }
}