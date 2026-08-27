/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import io.harness.ModuleType;
import io.harness.PipelineUtils;
import io.harness.account.settings.service.impl.PipelineSettingsServiceImpl;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.exceptions.FeatureNotSupportedException;
import io.harness.pms.contracts.steps.SdkStep;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.pipeline.CommonStepInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PipelineEnforcementService;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.plan.creation.info.PlanCreatorServiceInfo;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.sdk.helper.PmsSdkHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PipelineEnforcementServiceImpl implements PipelineEnforcementService {
  private static final String DEPLOYMENT_EXCEEDED_KEY = "DeploymentExceeded";
  private static final String BUILD_EXCEEDED_KEY = "BuildExceeded";

  private static final String EXECUTION_ERROR = "Your current plan does not support the use of following steps: %s.";
  private static final String UPGRADE_YOUR_PLAN_ERROR_MESSAGE = "Please upgrade your plan.";
  private static final Map<String, String> stageTypeToModule = new ConcurrentHashMap<>();
  public static final String PIPELINE_CHAINING_ENFORCEMENT_ERROR_MESSAGE =
      "Pipeline chaining is an Enterprise feature. Please contact sales to upgrade your plan.";
  // Regex patterns for pipeline chaining detection
  private static final String FIELD_PATTERN_TEMPLATE = "%s:\\s*\\[?[^\\]]*type:\\s*[\"']?Pipeline[\"']?";
  private static final String NESTED_FIELD_PATTERN_TEMPLATE = "%s:[^}]*type:\\s*[\"']?Pipeline[\"']?";
  private static final String PIPELINE_TYPE_PATTERN = "type:\\s*[\"']?Pipeline[\"']?";

  @Inject PmsSdkInstanceService pmsSdkInstanceService;
  @Inject EnforcementClientService enforcementClientService;
  @Inject PmsSdkHelper pmsSdkHelper;
  @Inject PipelineSettingsServiceImpl pipelineSettingsService;
  @Inject PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Override
  public boolean isFeatureRestricted(String accountId, String featureRestrictionName) {
    return enforcementClientService.isAvailable(FeatureRestrictionName.valueOf(featureRestrictionName), accountId);
  }

  @Override
  public Map<FeatureRestrictionName, Boolean> getFeatureRestrictionMap(
      String accountId, Set<String> featureRestrictionNameList) {
    Set<FeatureRestrictionName> featureRestrictionNames =
        featureRestrictionNameList.stream().map(FeatureRestrictionName::valueOf).collect(Collectors.toSet());
    return enforcementClientService.getAvailabilityForRemoteFeatures(
        new ArrayList<>(featureRestrictionNames), accountId);
  }

  @Override
  public Set<FeatureRestrictionName> getDisabledFeatureRestrictionNames(
      String accountId, Set<String> featureRestrictionNameList) {
    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap =
        getFeatureRestrictionMap(accountId, featureRestrictionNameList);
    Set<FeatureRestrictionName> disabledFeatures = new HashSet<>();
    for (Map.Entry<FeatureRestrictionName, Boolean> entry : featureRestrictionNameBooleanMap.entrySet()) {
      if (entry.getValue() == Boolean.FALSE) {
        disabledFeatures.add(entry.getKey());
      }
    }
    return disabledFeatures;
  }

  @Override
  public void validateExecutionEnforcementsBasedOnStage(PipelineEntity pipelineEntity) {
    validateExecutionEnforcementsBasedOnStage(pipelineEntity, null);
  }

  @Override
  public void validateExecutionEnforcementsBasedOnStage(PipelineEntity pipelineEntity, String processedYaml) {
    long start = System.currentTimeMillis();
    try {
      Set<String> modules = new HashSet<>(pipelineEntity.getFilters().keySet());
      if (containsCIModule(modules) && !hasActualCIStage(pipelineEntity, processedYaml)) {
        removeCIModule(modules);
      }
      validateExecutionFeatureRestrictions(pipelineEntity.getAccountId(), modules);
    } finally {
      log.info("[PMS_Enforcement] Validating enforcement on stages took time {}ms", System.currentTimeMillis() - start);
    }
  }

  private boolean containsCIModule(Set<String> modules) {
    return modules.stream().anyMatch(m -> m.equalsIgnoreCase(ModuleType.CI.name()));
  }

  private void removeCIModule(Set<String> modules) {
    modules.removeIf(m -> m.equalsIgnoreCase(ModuleType.CI.name()));
  }

  @VisibleForTesting
  boolean hasActualCIStage(PipelineEntity pipelineEntity, String processedYaml) {
    boolean templateHasCI = EmptyPredicate.isNotEmpty(pipelineEntity.getTemplateModules())
        && pipelineEntity.getTemplateModules().stream().anyMatch(m -> m.equalsIgnoreCase(ModuleType.CI.name()));

    String yamlToParse = templateHasCI ? processedYaml : pipelineEntity.getYaml();

    if (EmptyPredicate.isEmpty(yamlToParse)) {
      return templateHasCI;
    }
    return containsCIStageInYaml(yamlToParse, pipelineEntity);
  }

  private boolean containsCIStageInYaml(String yaml, PipelineEntity pipelineEntity) {
    try {
      YamlField pipelineField = YamlUtils.extractPipelineField(yaml);
      Set<YamlField> stageFields = PipelineUtils.getStagesFieldFromPipeline(pipelineField);
      for (YamlField stageField : stageFields) {
        String stageType = stageField.getNode().getType();
        if (ModuleType.CI.name().equals(stageType)) {
          return true;
        }
      }
    } catch (Exception e) {
      log.warn("[PMS_Enforcement] Failed to parse YAML for CI stage verification, "
              + "allowing CI enforcement as fallback. AccountId: {}, PipelineId: {}",
          pipelineEntity.getAccountId(), pipelineEntity.getIdentifier(), e);
      return true;
    }
    return false;
  }

  @Override
  public void validatePipelineChainingEnforcement(String accountIdentifier) {
    if (pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PIPE_DISABLE_PIPELINE_CHAINING_FOR_FREE_TIER)) {
      if (!enforcementClientService.isAvailable(
              FeatureRestrictionName.PIPELINE_CHAINING_AVAILABILITY, accountIdentifier)) {
        throw new FeatureNotSupportedException(PIPELINE_CHAINING_ENFORCEMENT_ERROR_MESSAGE);
      }
    }
  }

  @Override
  public void validatePipelineChainingInYaml(
      String accountId, String yaml, String orgIdentifier, String projectIdentifier) {
    if (yaml == null || yaml.isEmpty()) {
      return;
    }
    try {
      if (containsPipelineStages(yaml)) {
        validatePipelineChainingEnforcement(accountId);
      }

    } catch (Exception e) {
      log.warn("Error while parsing YAML for pipeline chaining detection. Will proceed without validation.", e);
    }
  }

  private boolean containsPipelineStages(String yaml) {
    try {
      if (containsPipelineStagesInYamlField(yaml, "stages")) {
        return true;
      }
      if (containsPipelineStagesInYamlField(yaml, "parallel")) {
        return true;
      }
      return false;
    } catch (Exception e) {
      log.warn("Error checking for pipeline stages in YAML", e);
      return false;
    }
  }

  private boolean containsPipelineStagesInYamlField(String yaml, String fieldName) {
    try {
      // Look for the specific field (stages or parallel) and then check for Pipeline type within that context
      String fieldPattern = String.format(FIELD_PATTERN_TEMPLATE, fieldName);
      if (yaml.matches("(?s).*" + fieldPattern + ".*")) {
        return true;
      }
      // Also check for nested structures where the field might contain arrays or objects with Pipeline stages
      String nestedFieldPattern = String.format(NESTED_FIELD_PATTERN_TEMPLATE, fieldName);
      return yaml.matches("(?s).*" + nestedFieldPattern + ".*");
    } catch (Exception e) {
      log.warn("Error checking for pipeline stages in field: " + fieldName, e);
      // Fallback to basic pipeline type detection
      return yaml.matches("(?s).*" + PIPELINE_TYPE_PATTERN + ".*");
    }
  }

  @Override
  public void validateExecutionEnforcementsBasedOnStage(String accountId, YamlField pipelineField) {
    long start = System.currentTimeMillis();
    try {
      Set<YamlField> stageFields = PipelineUtils.getStagesFieldFromPipeline(pipelineField);
      Set<String> modules = new HashSet<>();
      if (!populateModulesFromCache(stageFields, modules)) {
        populateModuleAndUpdateCache(stageFields, modules);
      }

      validateExecutionFeatureRestrictions(accountId, modules);
    } finally {
      log.info("[PMS_Enforcement] Validating enforcement on stages took time {}ms", System.currentTimeMillis() - start);
    }
  }

  /**
   * Populate module list from previous cached values.
   *
   * @return {@code true} when all fields are found in cache and {@code false} otherwise.
   */
  @VisibleForTesting
  boolean populateModulesFromCache(Set<YamlField> stageFields, Set<String> modules) {
    Set<YamlField> nonCachedStageYamlFields = new HashSet<>();
    for (YamlField stageField : stageFields) {
      if (stageTypeToModule.containsKey(stageField.getNode().getType())) {
        modules.add(stageTypeToModule.get(stageField.getNode().getType()));
      } else {
        nonCachedStageYamlFields.add(stageField);
      }
    }
    return nonCachedStageYamlFields.isEmpty();
  }

  /**
   * Populate modules and update the cache with these modules to speed the access time in next call
   */
  @VisibleForTesting
  void populateModuleAndUpdateCache(Set<YamlField> stageFields, Set<String> modules) {
    Map<String, PlanCreatorServiceInfo> services = pmsSdkHelper.getServices();
    for (Map.Entry<String, PlanCreatorServiceInfo> planCreatorServiceInfoEntry : services.entrySet()) {
      Map<String, Set<String>> supportedTypes = planCreatorServiceInfoEntry.getValue().getSupportedTypes();
      for (YamlField stageField : stageFields) {
        if (stageTypeToModule.containsKey(stageField.getNode().getType())) {
          modules.add(stageTypeToModule.get(stageField.getNode().getType()));
        } else {
          if (PlanCreatorUtils.supportsField(supportedTypes, stageField, HarnessYamlVersion.V0)) {
            modules.add(planCreatorServiceInfoEntry.getKey());
            stageTypeToModule.put(stageField.getNode().getType(), planCreatorServiceInfoEntry.getKey());
          }
        }
      }
    }
  }

  private void validateExecutionFeatureRestrictions(String accountId, Set<String> modules) {
    Multimap<String, String> featureRestrictionToStepNameMap = HashMultimap.create();
    // Add featureRestriction based on executions (Builds or deployments)
    for (String module : modules) {
      // Todo: Take via PmsSdkInstance
      if (module.equalsIgnoreCase(ModuleType.CD.name())) {
        featureRestrictionToStepNameMap.put(
            FeatureRestrictionName.DEPLOYMENTS_PER_MONTH.name(), DEPLOYMENT_EXCEEDED_KEY);
      } else if (module.equalsIgnoreCase(ModuleType.CI.name())) {
        featureRestrictionToStepNameMap.put(FeatureRestrictionName.BUILDS.name(), BUILD_EXCEEDED_KEY);
        featureRestrictionToStepNameMap.put(FeatureRestrictionName.MAX_BUILDS_PER_DAY.name(), BUILD_EXCEEDED_KEY);
      }
    }

    Set<FeatureRestrictionName> disabledFeatures =
        getDisabledFeatureRestrictionNames(accountId, featureRestrictionToStepNameMap.keySet());
    if (disabledFeatures.isEmpty()) {
      return;
    }
    throw new FeatureNotSupportedException(constructErrorMessage(featureRestrictionToStepNameMap, disabledFeatures));
  }

  /**
   * NOTE: Use this function during execution only.
   */
  @Override
  public void validatePipelineExecutionRestriction(String accountId, Set<StepType> stepTypes) {
    // Todo: Create a method in SdkStepsHelper and use it here. Cache this data.
    Map<String, Set<SdkStep>> sdkSteps = pmsSdkInstanceService.getSdkSteps();
    Multimap<String, String> featureRestrictionToStepNamesMap =
        getFeatureRestrictionMapFromUsedSteps(sdkSteps, stepTypes);
    Set<FeatureRestrictionName> disabledFeatures =
        getDisabledFeatureRestrictionNames(accountId, featureRestrictionToStepNamesMap.keySet());
    if (disabledFeatures.isEmpty()) {
      return;
    }
    throw new FeatureNotSupportedException(constructErrorMessage(featureRestrictionToStepNamesMap, disabledFeatures));
  }

  /**
   * returns a map of feature restriction to the stepNames on which the feature is present.
   * @param sdkSteps
   * @param stepTypes
   * @return
   */
  private Multimap<String, String> getFeatureRestrictionMapFromUsedSteps(
      Map<String, Set<SdkStep>> sdkSteps, Set<StepType> stepTypes) {
    Multimap<String, String> featureRestrictionToStepNameMap = HashMultimap.create();
    Set<String> modules = new HashSet<>();

    // Add featureRestriction based on steps from all modules
    for (Map.Entry<String, Set<SdkStep>> entry : sdkSteps.entrySet()) {
      for (SdkStep sdkStep : entry.getValue()) {
        if (stepTypes.contains(sdkStep.getStepType())) {
          if (sdkStep.hasStepInfo() && EmptyPredicate.isNotEmpty(sdkStep.getStepInfo().getFeatureRestrictionName())) {
            featureRestrictionToStepNameMap.put(
                sdkStep.getStepInfo().getFeatureRestrictionName(), sdkStep.getStepInfo().getName());
          }
          if (sdkStep.getStepType().getStepCategory() == StepCategory.STAGE) {
            modules.add(entry.getKey());
          }
        }
      }
    }
    // Add featureRestriction based on common steps
    List<String> stepTypeString = stepTypes.stream().map(StepType::getType).collect(Collectors.toList());
    for (StepInfo stepInfo : CommonStepInfo.getCommonSteps("")) {
      if (stepTypeString.contains(stepInfo.getType())
          && EmptyPredicate.isNotEmpty(stepInfo.getFeatureRestrictionName())) {
        featureRestrictionToStepNameMap.put(stepInfo.getFeatureRestrictionName(), stepInfo.getName());
      }
    }
    return featureRestrictionToStepNameMap;
  }

  private String constructErrorMessage(
      Multimap<String, String> featureRestrictionToStepNamesMap, Set<FeatureRestrictionName> disabledFeatures) {
    Set<String> disabledSteps = new HashSet<>();
    boolean deploymentsExceeded = false;
    boolean buildsExceeded = false;
    for (FeatureRestrictionName featureRestrictionName : disabledFeatures) {
      if (isExecutionFeatureRestriction(featureRestrictionName)) {
        continue;
      }
      // Todo: Take via pmsSdkInstance
      if (FeatureRestrictionName.DEPLOYMENTS_PER_MONTH.equals(featureRestrictionName)) {
        deploymentsExceeded = true;
        continue;
      }
      if (FeatureRestrictionName.BUILDS.equals(featureRestrictionName)) {
        buildsExceeded = true;
        continue;
      }
      if (FeatureRestrictionName.MAX_BUILDS_PER_DAY.equals(featureRestrictionName)) {
        buildsExceeded = true;
        continue;
      }
      disabledSteps.addAll(featureRestrictionToStepNamesMap.get(featureRestrictionName.name()));
    }
    StringBuilder stringBuilder = new StringBuilder(40);
    if (!disabledSteps.isEmpty()) {
      stringBuilder.append(String.format(EXECUTION_ERROR, disabledSteps));
    }
    if (deploymentsExceeded && buildsExceeded) {
      stringBuilder.append("You have exceeded max number of deployments and builds.");
    } else if (deploymentsExceeded) {
      stringBuilder.append("You have exceeded max number of deployments.");
    } else if (buildsExceeded) {
      stringBuilder.append("You have exceeded max number of builds.");
    }
    stringBuilder.append(UPGRADE_YOUR_PLAN_ERROR_MESSAGE);
    return stringBuilder.toString();
  }

  private boolean isExecutionFeatureRestriction(FeatureRestrictionName featureRestrictionName) {
    return ImmutableSet.of(FeatureRestrictionName.INITIAL_DEPLOYMENTS, FeatureRestrictionName.DEPLOYMENTS)
        .contains(featureRestrictionName);
  }
}
