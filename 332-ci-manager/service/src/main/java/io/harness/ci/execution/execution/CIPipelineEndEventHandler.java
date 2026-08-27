/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.beans.FeatureName.CI_USE_UNIQUE_PARENT_ID_FOR_QUERY;
import static io.harness.ci.commonconstants.CIExecutionConstants.OPTIMIZATION_STATE_DISABLED;
import static io.harness.ci.commonconstants.CIExecutionConstants.OPTIMIZATION_STATE_FULL_RUN;
import static io.harness.ci.commonconstants.CIExecutionConstants.OPTIMIZATION_STATE_OPTIMIZED;
import static io.harness.ci.execution.execution.CIPipelineUtils.humanReadableByteCountBin;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.beans.steps.CIPipelineBaseline;
import io.harness.beans.steps.CIStageSavingsInfo;
import io.harness.beans.steps.CIStageTelemetryData;
import io.harness.beans.steps.CITelemetryInfo;
import io.harness.ci.execution.integrationstage.utils.HarnessTokenUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.pipeline.executions.beans.CIImageDetails;
import io.harness.ci.pipeline.executions.beans.CIInfraDetails;
import io.harness.ci.pipeline.executions.beans.CIScmDetails;
import io.harness.ci.pipeline.executions.beans.CIStageOptimizationState;
import io.harness.ci.pipeline.executions.beans.TIBuildDetails;
import io.harness.ci.plan.creator.execution.CIPipelineModuleInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.repositories.CIPipelineBaselineRespository;
import io.harness.repositories.CIStageSavingsInfoRepository;
import io.harness.repositories.CIStageTelemetryRepository;
import io.harness.telemetry.TelemetryReporter;
import io.harness.utils.CIScopeInfoHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CIPipelineEndEventHandler implements OrchestrationEventHandler {
  @Inject CIAccountExecutionMetadataRepository ciAccountExecutionMetadataRepository;
  @Inject TelemetryReporter telemetryReporter;
  @Inject CIPipelineBaselineRespository ciPipelineBaselineRespository;
  @Inject(optional = true) CIStageTelemetryRepository ciStageTelemetryRepository;
  @Inject(optional = true) CIStageSavingsInfoRepository ciStageSavingsInfoRepository;
  @Inject(optional = true) CIScopeInfoHelper scopeInfoHelper;
  @Inject(optional = true) CIFeatureFlagService ciFeatureFlagService;
  @Inject HarnessTokenUtils harnessTokenUtils;

  private static final String CI_EXECUTED = "ci_built";
  private static final String USED_CODEBASE = "used_codebase";
  private static final String URL = "url";
  private static final String BRANCH = "branch";
  private static final String BUILD_TYPE = "build_type";
  private static final String PRIVATE_REPO = "private_repo";
  private static final String REPO_NAME = "repo_name";
  private static final String HARNESS_HOSTED = "Harness Hosted";

  // Extension to language mapping
  private static final Map<String, String> EXTENSION_TO_LANGUAGE_MAP = new HashMap<>();
  static {
    EXTENSION_TO_LANGUAGE_MAP.put(".java", "Java");
    EXTENSION_TO_LANGUAGE_MAP.put(".kt", "Kotlin");
    EXTENSION_TO_LANGUAGE_MAP.put(".scala", "Scala");
    EXTENSION_TO_LANGUAGE_MAP.put(".sc", "Scala");
    EXTENSION_TO_LANGUAGE_MAP.put(".py", "Python");
    EXTENSION_TO_LANGUAGE_MAP.put(".rb", "Ruby");
    EXTENSION_TO_LANGUAGE_MAP.put(".cs", "Csharp");
    EXTENSION_TO_LANGUAGE_MAP.put(".vb", "Csharp");
    EXTENSION_TO_LANGUAGE_MAP.put(".fs", "Csharp");
    EXTENSION_TO_LANGUAGE_MAP.put(".go", "Go");
    EXTENSION_TO_LANGUAGE_MAP.put(".js", "JavaScript");
    EXTENSION_TO_LANGUAGE_MAP.put(".ts", "TypeScript");
    EXTENSION_TO_LANGUAGE_MAP.put(".jsx", "JavaScript");
    EXTENSION_TO_LANGUAGE_MAP.put(".tsx", "TypeScript");
  }

  private static final String SCM_URL_LIST = "scm_url_list";
  private static final String SCM_PROVIDER_LIST = "scm_provider_list";
  private static final String SCM_AUTH_METHOD_LIST = "scm_auth_method_list";
  private static final String SCM_HOST_TYPE_LIST = "scm_host_type_list";

  private static final String INFRA_TYPE_LIST = "infra_type_list";
  private static final String INFRA_OS_LIST = "infra_os_list";
  private static final String INFRA_HOST_LIST = "infra_host_list";
  private static final String INFRA_ARCH_LIST = "infra_arch_list";
  private static final String NESTED_VIRTUALIZATION = "nested_virtualization";
  private static final String RESOURCE_CLASS_LIST = "cloud_resource_class_list";
  private static final String CLOUD_IMAGE_NAME_LIST = "cloud_image_name_list";
  private static final String CUSTOM_IMAGE_NAME_LIST = "cloud_is_custom_image_list";
  private static final String CONNECTOR_IDENTIFIER_LIST = "cloud_image_connector_identifier_list";

  private static final String IMAGES = "images";
  private static final String TI_BUILD_TOOL_LIST = "ti_build_tool_list";
  private static final String TI_LANGUAGE_LIST = "ti_language_list";
  private static final String PIPELINE_ID = "pipeline_id";
  private static final String ORG_ID = "org_id";
  private static final String PROJECT_ID = "project_id";
  private static final String EXECUTION_ID = "execution_id";
  private static final String RUN_SEQUENCE = "run_sequence";

  @Override
  public void handleEvent(OrchestrationEvent event) {
    harnessTokenUtils.cleanupHarnessToken(event.getAmbiance(), AmbianceUtils.getAccountId(event.getAmbiance()));
    PipelineModuleInfo moduleInfo = event.getModuleInfo();
    if (moduleInfo instanceof CIPipelineModuleInfo) {
      CIPipelineModuleInfo ciModuleInfo = (CIPipelineModuleInfo) moduleInfo;
      updateExecutionCount(ciModuleInfo, event);
      sendCITelemetryEvents(ciModuleInfo, event);
      try {
        updatePipelineBaseline(ciModuleInfo, event);
      } catch (Exception ex) {
        log.error("Failed to update pipeline baseline", ex);
      }
    }
  }

  private void updateExecutionCount(CIPipelineModuleInfo moduleInfo, OrchestrationEvent event) {
    if (moduleInfo.getIsPrivateRepo()) {
      ciAccountExecutionMetadataRepository.updateAccountExecutionMetadata(
          AmbianceUtils.getAccountId(event.getAmbiance()), event.getEndTs());
    }
  }

  private void sendCITelemetryEvents(CIPipelineModuleInfo moduleInfo, OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    String identity = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email");
    String accountId = AmbianceUtils.getAccountId(ambiance);
    try {
      sendCIExecutedEvent(ambiance, event, moduleInfo, identity, accountId);
    } catch (Exception e) {
      log.error("Exception while sending CI Executed ci_built event for plan execution id: {}",
          ambiance.getPlanExecutionId(), e);
    }
  }

  private void sendCIExecutedEvent(
      Ambiance ambiance, OrchestrationEvent event, CIPipelineModuleInfo moduleInfo, String identity, String accountId) {
    log.info("Sending CI Executed ci_built event for plan execution id: {}", ambiance.getPlanExecutionId());
    HashMap<String, Object> ciBuiltMap = new HashMap<>();
    boolean isNestedVitualizationEnabled = false;
    boolean isInfraHostTypeHarnessHosted = false;

    // Git details
    ciBuiltMap.put(BRANCH, moduleInfo.getBranch());
    ciBuiltMap.put(BUILD_TYPE, moduleInfo.getBuildType());
    ciBuiltMap.put(PRIVATE_REPO, moduleInfo.getIsPrivateRepo());
    ciBuiltMap.put(REPO_NAME, moduleInfo.getRepoName());

    List<String> imageList = new ArrayList<>();
    for (CIImageDetails imageDetails : moduleInfo.getImageDetailsList()) {
      imageList.add(imageDetails.getImageName() + ":" + imageDetails.getImageTag());
    }

    ciBuiltMap.put(IMAGES, imageList);
    ciBuiltMap.put(ORG_ID, AmbianceUtils.getOrgIdentifier(ambiance));
    ciBuiltMap.put(PROJECT_ID, AmbianceUtils.getProjectIdentifier(ambiance));
    ciBuiltMap.put(PIPELINE_ID, ambiance.getMetadata().getPipelineIdentifier());
    ciBuiltMap.put(EXECUTION_ID, ambiance.getPlanExecutionId());
    ciBuiltMap.put(RUN_SEQUENCE, ambiance.getMetadata().getRunSequence());

    // SCM Vendor details
    if (isNotEmpty(moduleInfo.getScmDetailsList())) {
      List<String> scmUrlList = new ArrayList<>();
      List<String> scmProviderList = new ArrayList<>();
      List<String> scmAuthTypeList = new ArrayList<>();
      List<String> scmHostTypeList = new ArrayList<>();
      for (CIScmDetails scmDetails : moduleInfo.getScmDetailsList()) {
        ciBuiltMap.put(URL, scmDetails.getScmUrl());
        scmUrlList.add(scmDetails.getScmUrl());
        scmProviderList.add(scmDetails.getScmProvider());
        scmAuthTypeList.add(scmDetails.getScmAuthType());
        scmHostTypeList.add(scmDetails.getScmHostType());
      }
      ciBuiltMap.put(SCM_URL_LIST, scmUrlList);
      ciBuiltMap.put(SCM_PROVIDER_LIST, scmProviderList);
      ciBuiltMap.put(SCM_AUTH_METHOD_LIST, scmAuthTypeList);
      ciBuiltMap.put(SCM_HOST_TYPE_LIST, scmHostTypeList);
    }

    ciBuiltMap.put(USED_CODEBASE, false);
    if (ciBuiltMap.get(URL) != null) {
      ciBuiltMap.put(USED_CODEBASE, true);
    }

    // Infrastructure details
    List<String> infraTypeList = new ArrayList<>();
    List<String> infraOsTypeList = new ArrayList<>();
    List<String> infraHostTypeList = new ArrayList<>();
    List<String> infraArchTypeList = new ArrayList<>();
    List<String> resourceClassList = new ArrayList<>();
    List<String> cloudImageNameList = new ArrayList<>();
    List<Boolean> isCustomImageList = new ArrayList<>();
    List<String> connectorIdentifierList = new ArrayList<>();

    for (CIInfraDetails infraDetails : moduleInfo.getInfraDetailsList()) {
      infraTypeList.add(infraDetails.getInfraType());
      infraOsTypeList.add(infraDetails.getInfraOSType());
      infraHostTypeList.add(infraDetails.getInfraHostType());
      infraArchTypeList.add(infraDetails.getInfraArchType());
      resourceClassList.add(infraDetails.getResourceClass());
      cloudImageNameList.add(infraDetails.getImageName());
      isCustomImageList.add(infraDetails.isCustomImage());

      if (infraDetails.getInfraHostType().equals(HARNESS_HOSTED)) {
        isNestedVitualizationEnabled = (isNestedVitualizationEnabled || infraDetails.isNestedVirtualization());
        isInfraHostTypeHarnessHosted = true;
        if (infraDetails.isCustomImage()) {
          connectorIdentifierList.add(infraDetails.getConnectorIdentifier());
        }
      }
    }
    ciBuiltMap.put(INFRA_TYPE_LIST, infraTypeList);
    ciBuiltMap.put(INFRA_OS_LIST, infraOsTypeList);
    ciBuiltMap.put(INFRA_HOST_LIST, infraHostTypeList);
    ciBuiltMap.put(INFRA_ARCH_LIST, infraArchTypeList);

    try {
      List<CIStageTelemetryData> ciStageTelemetryDataList = null;
      List<CIStageSavingsInfo> ciStageSavingsInfoList = null;
      if (ciStageTelemetryRepository != null) {
        ciStageTelemetryDataList = ciStageTelemetryRepository.findByPlanExecutionId(ambiance.getPlanExecutionId());
      }

      if (ciStageSavingsInfoRepository != null) {
        ciStageSavingsInfoList =
            ciStageSavingsInfoRepository.findByAccountIdAndPlanExecutionId(accountId, ambiance.getPlanExecutionId());
      }

      if (ciStageTelemetryDataList != null && ciStageSavingsInfoList != null) {
        ciBuiltMap.putAll(getPipelineTelemetryData(ciStageTelemetryDataList, ciStageSavingsInfoList));
      }
      long duration = System.currentTimeMillis() - ambiance.getStartTs();
      ciBuiltMap.put("pipeline_duration", duration);
    } catch (Exception e) {
      log.error("Exception while getting getPipelineTelemetryData", e);
    }

    // We will be sending this parameter to the event if we have atleast one infra in pipeline is of HARNESS_HOSTED
    // host type.
    if (isInfraHostTypeHarnessHosted) {
      ciBuiltMap.put(RESOURCE_CLASS_LIST, resourceClassList);
      ciBuiltMap.put(CLOUD_IMAGE_NAME_LIST, cloudImageNameList);
      ciBuiltMap.put(NESTED_VIRTUALIZATION, isNestedVitualizationEnabled);
      ciBuiltMap.put(CUSTOM_IMAGE_NAME_LIST, isCustomImageList);
      ciBuiltMap.put(CONNECTOR_IDENTIFIER_LIST, connectorIdentifierList);
    }

    // Test Intelligence details
    if (moduleInfo.getTiBuildDetailsList() != null && moduleInfo.getTiBuildDetailsList().size() != 0) {
      List<String> tiBuildToolList = new ArrayList<>();
      List<String> tiLanguageList = new ArrayList<>();

      for (TIBuildDetails tiBuildDetails : moduleInfo.getTiBuildDetailsList()) {
        tiBuildToolList.add(tiBuildDetails.getBuildTool());
        tiLanguageList.add(tiBuildDetails.getLanguage());
      }
      ciBuiltMap.put(TI_BUILD_TOOL_LIST, tiBuildToolList);
      ciBuiltMap.put(TI_LANGUAGE_LIST, tiLanguageList);
    }

    telemetryReporter.sendTrackEvent(CI_EXECUTED, identity, accountId, ciBuiltMap,
        Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
        io.harness.telemetry.TelemetryOption.builder().sendForCommunity(false).build());

    log.info("CI EXECUTED ci_built event sent for plan execution id: {}", ambiance.getPlanExecutionId());
  }

  private void updatePipelineBaseline(CIPipelineModuleInfo moduleInfo, OrchestrationEvent event) {
    String state = getPipelineOptimizationState(moduleInfo);
    if (OPTIMIZATION_STATE_DISABLED.equals(state)) {
      return;
    }

    Ambiance ambiance = event.getAmbiance();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String pipelineId = AmbianceUtils.getPipelineIdentifier(ambiance);
    String planExecutionId = AmbianceUtils.getPipelineExecutionIdentifier(ambiance);
    String fullRunPlanExecutionId = null;
    String parentUniqueId = AmbianceUtils.getParentUniqueIdentifier(ambiance);
    if (isEmpty(pipelineId)) {
      return;
    }

    long endTs = event.getEndTs();
    long startTs = event.getAmbiance().getStartTs();
    if (startTs == 0L || endTs == 0L) {
      return;
    }
    long timeTakenMs = endTs - startTs;

    Long baselineMs = null;
    if (OPTIMIZATION_STATE_FULL_RUN.equals(state)) {
      baselineMs = timeTakenMs;
      fullRunPlanExecutionId = planExecutionId;
    } else if (OPTIMIZATION_STATE_OPTIMIZED.equals(state)) {
      Long curBaselineMs = getPipelineBaseline(accountId, orgId, projectId, pipelineId, parentUniqueId);
      if (curBaselineMs == null || timeTakenMs > curBaselineMs) {
        // If baseline not found: Update with current time taken
        // If baseline found: Update only if time taken is greater than previous baseline
        baselineMs = timeTakenMs;
      }
    }
    if (baselineMs == null) {
      return;
    }
    if (isEmpty(parentUniqueId) && scopeInfoHelper != null) {
      parentUniqueId = scopeInfoHelper.getParentUniqueId(accountId, orgId, projectId);
    }
    ciPipelineBaselineRespository.upsert(
        accountId, orgId, projectId, pipelineId, parentUniqueId, fullRunPlanExecutionId, baselineMs);
  }

  private String getPipelineOptimizationState(CIPipelineModuleInfo moduleInfo) {
    if (isEmpty(moduleInfo.getCiStageOptimizationStateList())) {
      return OPTIMIZATION_STATE_DISABLED;
    }
    String state = OPTIMIZATION_STATE_DISABLED;
    for (CIStageOptimizationState stageState : moduleInfo.getCiStageOptimizationStateList()) {
      if (OPTIMIZATION_STATE_OPTIMIZED.equals(stageState.getState())) {
        return OPTIMIZATION_STATE_OPTIMIZED;
      } else if (OPTIMIZATION_STATE_FULL_RUN.equals(stageState.getState())) {
        state = OPTIMIZATION_STATE_FULL_RUN;
      }
    }
    return state;
  }

  private Long getPipelineBaseline(
      String accountId, String orgId, String projectId, String pipelineId, String parentUniqueId) {
    CIPipelineBaseline ciPipelineBaseline = null;
    if (!isEmpty(parentUniqueId) && ciFeatureFlagService.isEnabled(CI_USE_UNIQUE_PARENT_ID_FOR_QUERY, accountId)) {
      ciPipelineBaseline = ciPipelineBaselineRespository.findByParentUniqueIdAndPipelineId(parentUniqueId, pipelineId);
    } else {
      ciPipelineBaseline = ciPipelineBaselineRespository.findByAccountIdAndOrgIdAndProjectIdAndPipelineId(
          accountId, orgId, projectId, pipelineId);
    }
    if (ciPipelineBaseline == null) {
      return null;
    }
    return ciPipelineBaseline.getBaselineMs();
  }

  public Map<String, Object> getPipelineTelemetryData(
      List<CIStageTelemetryData> ciStageTelemetryDataList, List<CIStageSavingsInfo> ciStageSavingsInfoList) {
    Map<String, Object> result = new HashMap<>();

    // Aggregated variables
    Set<String> buildTools = new HashSet<>();
    Set<String> languages = new HashSet<>();
    Set<String> biStepTypes = new HashSet<>();
    Set<String> ciStepTypes = new HashSet<>();
    Set<String> errors = new HashSet<>();
    List<String> tiLanguages = new ArrayList<>();
    int totalBuildTasks = 0;
    int totalTasksRestored = 0;
    int totalTests = 0;
    int totalTestClasses = 0;
    int totalSelectedTests = 0;
    int totalSelectedTestClasses = 0;
    int totalLayers = 0;
    int layersRestored = 0;
    long cpuTimeSaved = 0;
    long totalTimeSaved = 0;
    boolean isRunTestV2 = false;
    boolean isCacheIntelEnabled = false;
    boolean isBuildIntelEnabled = false;
    boolean isDlcEnabled = false;
    boolean isRunTestV2Optimized = false;
    boolean isCacheIntelOptimized = false;
    boolean isDlcOptimized = false;
    boolean isBuildIntelOptimized = false;
    boolean isMavenBIUsed = false;
    boolean isGradleBIUsed = false;
    boolean isBazelBIUsed = false;
    boolean isNonDefaultPath = false;
    boolean isCustomKeys = false;

    long totalCacheSizeBytes = 0;

    // Code metrics collection - unique repositories from all stages
    Map<String, CITelemetryInfo.CodeMetricsInfo> uniqueRepositoryMetrics = new HashMap<>();

    // Performance safeguard: limit processing for very large datasets
    final int MAX_REPOSITORIES = 1000;

    for (CIStageTelemetryData telemetryData : ciStageTelemetryDataList) {
      // Fetch buildTool and language directly from CIStageTelemetryData
      if (telemetryData.getBuildTool() != null) {
        buildTools.add(telemetryData.getBuildTool());
      }
      if (telemetryData.getLanguage() != null) {
        languages.add(telemetryData.getLanguage());
      }

      if (telemetryData.getCiTelemetryInfo() == null) {
        continue;
      }

      CITelemetryInfo ciTelemetryInfo = telemetryData.getCiTelemetryInfo();

      // Build Intelligence Info
      if (ciTelemetryInfo.getBuildIntelligenceInfo() != null) {
        CITelemetryInfo.BuildIntelligenceInfo buildInfo = ciTelemetryInfo.getBuildIntelligenceInfo();
        totalBuildTasks += buildInfo.getBuildTasks();
        totalTasksRestored += buildInfo.getTasksRestored();
        if (buildInfo.getStepTypes() != null) {
          biStepTypes.addAll(buildInfo.getStepTypes());
        }
        if (buildInfo.getErrors() != null) {
          errors.addAll(buildInfo.getErrors());
        }
        isBuildIntelEnabled = isBuildIntelEnabled || buildInfo.isBuildIntelEnabled();
        isBuildIntelOptimized = isBuildIntelOptimized || buildInfo.isBuildIntelOptimized();
        isMavenBIUsed = isMavenBIUsed || buildInfo.isMavenBIUsed();
        isGradleBIUsed = isGradleBIUsed || buildInfo.isGradleBIUsed();
        isBazelBIUsed = isBazelBIUsed || buildInfo.isBazelBIUsed();
      }

      // Test Intelligence Info
      if (ciTelemetryInfo.getTestIntelligenceInfo() != null) {
        CITelemetryInfo.TestIntelligenceInfo testInfo = ciTelemetryInfo.getTestIntelligenceInfo();
        totalTests += testInfo.getTotalTests();
        totalTestClasses += testInfo.getTotalTestClasses();
        totalSelectedTests += testInfo.getTotalSelectedTests();
        totalSelectedTestClasses += testInfo.getTotalSelectedTestClass();
        cpuTimeSaved += testInfo.getCpuTimeSaved();
        isRunTestV2 = isRunTestV2 || testInfo.isRunTestV2();
        isRunTestV2Optimized = isRunTestV2Optimized || testInfo.isRunTestV2Optimized();
        if (testInfo.getErrors() != null) {
          errors.addAll(testInfo.getErrors());
        }
        if (testInfo.getLanguage() != null) {
          // Convert extensions to language names
          for (String extension : testInfo.getLanguage()) {
            String languageName = EXTENSION_TO_LANGUAGE_MAP.getOrDefault(extension, extension);
            if (!tiLanguages.contains(languageName)) {
              tiLanguages.add(languageName);
            }
          }
        }
      }

      // Cache Intelligence Info
      if (ciTelemetryInfo.getCacheIntelligenceInfo() != null) {
        CITelemetryInfo.CacheIntelligenceInfo cacheInfo = ciTelemetryInfo.getCacheIntelligenceInfo();
        if (cacheInfo.getCacheSize() != null) {
          try {
            totalCacheSizeBytes += cacheInfo.getCacheSize();
          } catch (NumberFormatException ignored) {
            // Ignore invalid cache size values
          }
        }
        isNonDefaultPath = isNonDefaultPath || cacheInfo.isNonDefaultPath();
        isCustomKeys = isCustomKeys || cacheInfo.isCustomKeys();
        if (cacheInfo.getErrors() != null) {
          errors.addAll(cacheInfo.getErrors());
        }
        isCacheIntelEnabled = isCacheIntelEnabled || cacheInfo.isCacheIntelEnabled();
        isCacheIntelOptimized = isCacheIntelOptimized || cacheInfo.isCacheIntelOptimized();
      }

      // DLC Info
      if (ciTelemetryInfo.getDlcInfo() != null) {
        CITelemetryInfo.DlcInfo dlcInfo = ciTelemetryInfo.getDlcInfo();
        totalLayers += dlcInfo.getTotalLayers();
        layersRestored += dlcInfo.getLayersRestored();
        isDlcEnabled = isDlcEnabled || dlcInfo.isDlcEnabled();
        isDlcOptimized = isDlcOptimized || dlcInfo.isDlcOptimized();
        if (dlcInfo.getErrors() != null) {
          errors.addAll(dlcInfo.getErrors());
        }
      }

      // Code Metrics Info - collect unique repositories from all stages with performance optimization
      if (ciTelemetryInfo.getCodeMetricsByRepository() != null) {
        Map<String, CITelemetryInfo.CodeMetricsInfo> stageCodeMetrics = ciTelemetryInfo.getCodeMetricsByRepository();

        for (Map.Entry<String, CITelemetryInfo.CodeMetricsInfo> entry : stageCodeMetrics.entrySet()) {
          // Early termination for performance: limit number of unique repositories
          if (uniqueRepositoryMetrics.size() >= MAX_REPOSITORIES) {
            log.warn("Reached maximum repository limit ({}) for telemetry processing, skipping remaining repositories",
                MAX_REPOSITORIES);
            break;
          }

          String repository = entry.getKey();
          CITelemetryInfo.CodeMetricsInfo codeMetrics = entry.getValue();

          // Add unique repositories - each repository keeps its own metrics
          if (!uniqueRepositoryMetrics.containsKey(repository)) {
            uniqueRepositoryMetrics.put(repository, codeMetrics);
          }
        }
      }

      if (ciTelemetryInfo.getCiStepTypes() != null) {
        ciStepTypes.addAll(ciTelemetryInfo.getCiStepTypes());
      }
    }

    // Populate the result map
    result.put("build_tools", new ArrayList<>(buildTools)); // Combine tools and languages
    result.put("languages", new ArrayList<>(languages));
    result.put("bi_step_types", new ArrayList<>(biStepTypes));
    result.put("ci_step_types", new ArrayList<>(ciStepTypes));
    result.put("bi_total_build_tasks", totalBuildTasks);
    result.put("bi_total_tasks_restored", totalTasksRestored);
    result.put("bi_is_build_intel_enabled", isBuildIntelEnabled);
    result.put("bi_is_build_intel_optimized", isBuildIntelOptimized);
    result.put("bi_is_maven_used", isMavenBIUsed);
    result.put("bi_is_gradle_used", isGradleBIUsed);
    result.put("bi_is_bazel_used", isBazelBIUsed);
    result.put("ti_total_tests", totalTests);
    result.put("ti_total_test_classes", totalTestClasses);
    result.put("ti_total_selected_tests", totalSelectedTests);
    result.put("ti_total_selected_test_classes", totalSelectedTestClasses);
    result.put("ti_cpu_time_saved", cpuTimeSaved);
    result.put("ti_languages", tiLanguages);
    result.put("is_run_test_v2", isRunTestV2);
    result.put("is_run_test_v2_optimized", isRunTestV2Optimized);
    result.put("dlc_total_layers", totalLayers);
    result.put("dlc_layers_restored", layersRestored);
    result.put("dlc_is_dlc_enabled", isDlcEnabled);
    result.put("dlc_is_dlc_optimized", isDlcOptimized);
    result.put("cache_intel_is_non_default_path", isNonDefaultPath);
    result.put("cache_intel_is_custom_keys", isCustomKeys);
    result.put("cache_intel_is_cache_intel_enabled", isCacheIntelEnabled);
    result.put("cache_intel_is_cache_intel_optimized", isCacheIntelOptimized);
    result.put("cache_intel_total_cache_size",
        humanReadableByteCountBin(totalCacheSizeBytes)); // Convert to human-readable format

    // Code metrics telemetry - flattened for analytics compatibility
    result.put("code_metrics_repositories_count", uniqueRepositoryMetrics.size());
    result.put("code_metrics_enabled", !uniqueRepositoryMetrics.isEmpty());

    addCodeMetricsTelemetryData(result, uniqueRepositoryMetrics);

    // Performance monitoring: log processing statistics
    if (!uniqueRepositoryMetrics.isEmpty()) {
      log.debug("Code metrics telemetry processed {} unique repositories", uniqueRepositoryMetrics.size());
    }

    for (CIStageSavingsInfo ciStageSavingsInfo : ciStageSavingsInfoList) {
      totalTimeSaved += ciStageSavingsInfo.getTimeSaved();
    }
    result.put("total_time_saved", totalTimeSaved);

    return result;
  }

  /**
   * Adds code metrics telemetry data to the result map using flattened arrays for analytics compatibility.
   * Follows the same pattern as scm_url_list, infra_type_list, etc.
   *
   * @param result The telemetry result map to add data to
   * @param uniqueRepositoryMetrics Map of unique repository metrics
   */
  private void addCodeMetricsTelemetryData(
      Map<String, Object> result, Map<String, CITelemetryInfo.CodeMetricsInfo> uniqueRepositoryMetrics) {
    // Create top-level arrays for repository data (analytics-friendly)
    List<String> repositoryUrls = new ArrayList<>();
    List<String> buildEvents = new ArrayList<>();
    List<String> buildEventValues = new ArrayList<>();
    List<String> pluginVersions = new ArrayList<>();
    List<Long> repositoryLines = new ArrayList<>();
    List<Long> repositoryCode = new ArrayList<>();
    List<Long> repositoryComments = new ArrayList<>();
    List<Long> repositoryBlanks = new ArrayList<>();
    List<Long> repositoryFiles = new ArrayList<>();
    List<Long> repositoryComplexity = new ArrayList<>();
    Set<String> allCodeLanguages = new HashSet<>();

    // Language breakdown arrays (detailed per-language metrics)
    List<String> languageNames = new ArrayList<>();
    List<String> languageRepositories = new ArrayList<>();
    List<String> languageRepoKeys = new ArrayList<>(); // Combined "language@repository" for easier correlation
    List<Long> languageLines = new ArrayList<>();
    List<Long> languageCode = new ArrayList<>();
    List<Long> languageComments = new ArrayList<>();
    List<Long> languageBlanks = new ArrayList<>();
    List<Long> languageFiles = new ArrayList<>();
    List<Long> languageComplexity = new ArrayList<>();

    for (CITelemetryInfo.CodeMetricsInfo metrics : uniqueRepositoryMetrics.values()) {
      String repository = metrics.getRepository() != null ? metrics.getRepository() : "";

      repositoryUrls.add(repository);
      buildEvents.add(metrics.getBuildEvent() != null ? metrics.getBuildEvent() : "");
      buildEventValues.add(metrics.getBuildEventValue() != null ? metrics.getBuildEventValue() : "");
      pluginVersions.add(metrics.getPluginVersion() != null ? metrics.getPluginVersion() : "");
      repositoryLines.add(metrics.getTotalLines() != null ? metrics.getTotalLines() : 0L);
      repositoryCode.add(metrics.getTotalCode() != null ? metrics.getTotalCode() : 0L);
      repositoryComments.add(metrics.getTotalComments() != null ? metrics.getTotalComments() : 0L);
      repositoryBlanks.add(metrics.getTotalBlanks() != null ? metrics.getTotalBlanks() : 0L);
      repositoryFiles.add(metrics.getTotalFiles() != null ? metrics.getTotalFiles() : 0L);
      repositoryComplexity.add(metrics.getTotalComplexity() != null ? metrics.getTotalComplexity() : 0L);

      // Collect detailed language breakdown for each repository
      if (metrics.getLanguageMetrics() != null) {
        allCodeLanguages.addAll(metrics.getLanguageMetrics().keySet());

        for (Map.Entry<String, CITelemetryInfo.CodeMetricsInfo.LanguageMetrics> langEntry :
            metrics.getLanguageMetrics().entrySet()) {
          String language = langEntry.getKey();
          CITelemetryInfo.CodeMetricsInfo.LanguageMetrics langMetrics = langEntry.getValue();

          if (langMetrics != null) {
            languageNames.add(language);
            languageRepositories.add(repository);
            languageRepoKeys.add(language + "@" + repository); // Clear language-to-repo identifier
            languageLines.add(langMetrics.getLines() != null ? langMetrics.getLines() : 0L);
            languageCode.add(langMetrics.getCode() != null ? langMetrics.getCode() : 0L);
            languageComments.add(langMetrics.getComments() != null ? langMetrics.getComments() : 0L);
            languageBlanks.add(langMetrics.getBlanks() != null ? langMetrics.getBlanks() : 0L);
            languageFiles.add(langMetrics.getFiles() != null ? langMetrics.getFiles() : 0L);
            languageComplexity.add(langMetrics.getComplexity() != null ? langMetrics.getComplexity() : 0L);
          }
        }
      }
    }

    // Add flattened telemetry data (no nested objects)
    result.put("code_metrics_repository_urls", repositoryUrls);
    result.put("code_metrics_build_events", buildEvents);
    result.put("code_metrics_build_event_values", buildEventValues);
    result.put("code_metrics_plugin_versions", pluginVersions);
    result.put("code_metrics_repository_lines", repositoryLines);
    result.put("code_metrics_repository_code", repositoryCode);
    result.put("code_metrics_repository_comments", repositoryComments);
    result.put("code_metrics_repository_blanks", repositoryBlanks);
    result.put("code_metrics_repository_files", repositoryFiles);
    result.put("code_metrics_repository_complexity", repositoryComplexity);

    // Language summary (unique languages across all repositories)
    result.put("code_metrics_languages", new ArrayList<>(allCodeLanguages));

    // Language breakdown (detailed per-language metrics with repository correlation)
    result.put("code_metrics_language_names", languageNames);
    result.put("code_metrics_language_repositories", languageRepositories);
    result.put("code_metrics_language_repo_keys", languageRepoKeys); // Explicit language@repository correlation
    result.put("code_metrics_language_lines", languageLines);
    result.put("code_metrics_language_code", languageCode);
    result.put("code_metrics_language_comments", languageComments);
    result.put("code_metrics_language_blanks", languageBlanks);
    result.put("code_metrics_language_files", languageFiles);
    result.put("code_metrics_language_complexity", languageComplexity);
  }
}
