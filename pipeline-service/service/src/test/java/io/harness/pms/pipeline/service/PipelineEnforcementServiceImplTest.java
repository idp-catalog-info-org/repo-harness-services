/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.PipelineServiceTestBase;
import io.harness.PipelineUtils;
import io.harness.account.settings.service.impl.PipelineSettingsServiceImpl;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.exceptions.FeatureNotSupportedException;
import io.harness.pms.contracts.steps.SdkStep;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.plan.creation.info.PlanCreatorServiceInfo;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.sdk.helper.PmsSdkHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.groovy.util.Maps;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineEnforcementServiceImplTest extends PipelineServiceTestBase {
  private static final String accountId = "ACCOUNT_ID";
  @Mock PmsSdkInstanceService pmsSdkInstanceService;
  @Mock EnforcementClientService enforcementClientService;
  @Mock PmsSdkHelper pmsSdkHelper;
  @Mock PipelineSettingsServiceImpl pipelineSettingsService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @InjectMocks PipelineEnforcementServiceImpl pipelineEnforcementService;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetFeatureRestrictionMap() {
    Set<FeatureRestrictionName> featureRestrictionNames = Sets.newHashSet();
    featureRestrictionNames.add(FeatureRestrictionName.TEST1);
    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap = Maps.of(FeatureRestrictionName.TEST1, true);
    when(enforcementClientService.getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId))
        .thenReturn(featureRestrictionNameBooleanMap);

    assertThat(pipelineEnforcementService.getFeatureRestrictionMap(accountId,
                   featureRestrictionNames.stream().map(FeatureRestrictionName::toString).collect(Collectors.toSet())))
        .isEqualTo(featureRestrictionNameBooleanMap);

    verify(enforcementClientService)
        .getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidatingPipelineChainingEnforcement() {
    String account = "acc";

    // Test case 1: Feature flag enabled + feature NOT available (returns false for FREE tier)
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.PIPE_DISABLE_PIPELINE_CHAINING_FOR_FREE_TIER))
        .thenReturn(true);
    when(enforcementClientService.isAvailable(FeatureRestrictionName.PIPELINE_CHAINING_AVAILABILITY, account))
        .thenReturn(false);
    assertThatThrownBy(() -> pipelineEnforcementService.validatePipelineChainingEnforcement(account))
        .isInstanceOf(FeatureNotSupportedException.class);

    // Test case 2: Feature flag enabled + feature available (returns true for ENTERPRISE tier)
    when(enforcementClientService.isAvailable(FeatureRestrictionName.PIPELINE_CHAINING_AVAILABILITY, account))
        .thenReturn(true);
    assertThatCode(() -> pipelineEnforcementService.validatePipelineChainingEnforcement(account))
        .doesNotThrowAnyException();

    // Test case 3: Feature flag not enabled + feature available
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.PIPE_DISABLE_PIPELINE_CHAINING_FOR_FREE_TIER))
        .thenReturn(false);
    when(enforcementClientService.isAvailable(FeatureRestrictionName.PIPELINE_CHAINING_AVAILABILITY, account))
        .thenReturn(true);
    assertThatCode(() -> pipelineEnforcementService.validatePipelineChainingEnforcement(account))
        .doesNotThrowAnyException();

    // Test case 4: Feature flag enabled + feature available (returns true for DOE tier)
    when(enforcementClientService.isAvailable(FeatureRestrictionName.PIPELINE_CHAINING_AVAILABILITY, account))
        .thenReturn(true);
    assertThatCode(() -> pipelineEnforcementService.validatePipelineChainingEnforcement(account))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidatePipelineExecutionRestriction() {
    Map<String, Set<SdkStep>> sdkSteps = new HashMap<>();
    Set<SdkStep> sdkStepSet = new HashSet<>();
    StepType stepType = StepType.newBuilder().setType("test").setStepCategory(StepCategory.STAGE).build();
    sdkStepSet.add(
        SdkStep.newBuilder()
            .setStepType(stepType)
            .setStepInfo(StepInfo.newBuilder().setFeatureRestrictionName(FeatureRestrictionName.TEST5.name()).build())
            .build());
    sdkSteps.put(ModuleType.CD.name(), sdkStepSet);

    Set<FeatureRestrictionName> featureRestrictionNames = Sets.newHashSet();
    featureRestrictionNames.add(FeatureRestrictionName.TEST5);

    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap = Maps.of(FeatureRestrictionName.TEST1, true);
    when(pmsSdkInstanceService.getSdkSteps()).thenReturn(sdkSteps);
    when(enforcementClientService.getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId))
        .thenReturn(featureRestrictionNameBooleanMap);

    pipelineEnforcementService.validatePipelineExecutionRestriction(accountId, Sets.newHashSet(stepType));

    verify(enforcementClientService)
        .getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidatePipelineExecutionRestrictionThrowsException() {
    Map<String, Set<SdkStep>> sdkSteps = new HashMap<>();
    Set<SdkStep> sdkStepSet = new HashSet<>();
    StepType stepType = StepType.newBuilder().setType("test").setStepCategory(StepCategory.STAGE).build();
    sdkStepSet.add(SdkStep.newBuilder()
                       .setStepType(stepType)
                       .setStepInfo(StepInfo.newBuilder()
                                        .setName("test 5")
                                        .setFeatureRestrictionName(FeatureRestrictionName.TEST5.name())
                                        .build())
                       .build());
    sdkSteps.put(ModuleType.CD.name(), sdkStepSet);

    Set<FeatureRestrictionName> featureRestrictionNames = Sets.newHashSet();
    featureRestrictionNames.add(FeatureRestrictionName.TEST5);

    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap =
        Maps.of(FeatureRestrictionName.TEST5, false);
    when(pmsSdkInstanceService.getSdkSteps()).thenReturn(sdkSteps);
    when(enforcementClientService.getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId))
        .thenReturn(featureRestrictionNameBooleanMap);

    assertThatThrownBy(
        () -> pipelineEnforcementService.validatePipelineExecutionRestriction(accountId, Sets.newHashSet(stepType)))
        .isInstanceOf(FeatureNotSupportedException.class)
        .hasMessage(
            "Your current plan does not support the use of following steps: [test 5].Please upgrade your plan.");

    verify(enforcementClientService)
        .getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidatePipelineExecutionRestrictionThrowsExceptionOnlyDeployment() {
    Map<String, Set<SdkStep>> sdkSteps = new HashMap<>();
    Set<SdkStep> sdkStepSet = new HashSet<>();
    StepType stepType = StepType.newBuilder().setType("test").setStepCategory(StepCategory.STAGE).build();
    sdkStepSet.add(SdkStep.newBuilder()
                       .setStepType(stepType)
                       .setStepInfo(StepInfo.newBuilder()
                                        .setName("test 5")
                                        .setFeatureRestrictionName(FeatureRestrictionName.TEST5.name())
                                        .build())
                       .build());
    sdkSteps.put(ModuleType.CD.name(), sdkStepSet);

    Set<FeatureRestrictionName> featureRestrictionNames = Sets.newHashSet();
    featureRestrictionNames.add(FeatureRestrictionName.TEST5);

    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap = Maps.of(FeatureRestrictionName.TEST5, true);
    when(pmsSdkInstanceService.getSdkSteps()).thenReturn(sdkSteps);
    when(enforcementClientService.getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId))
        .thenReturn(featureRestrictionNameBooleanMap);

    pipelineEnforcementService.validatePipelineExecutionRestriction(accountId, Sets.newHashSet(stepType));

    verify(enforcementClientService)
        .getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void verifyPopulateModulesFromCache() throws IOException {
    Set<YamlField> stageFields = getStageFields("pipeline-enforcement-modules.yaml");
    Set<String> modules = new HashSet<>();

    getStageTypeCache().clear();
    assertThat(pipelineEnforcementService.populateModulesFromCache(stageFields, modules)).isFalse();
    assertThat(modules).isEmpty();

    getStageTypeCache().put("Custom", "Custom");
    getStageTypeCache().put("Approval", "Approval");
    assertThat(pipelineEnforcementService.populateModulesFromCache(stageFields, modules)).isTrue();
    assertThat(modules).hasSize(2);
    assertThat(modules).containsExactlyInAnyOrder("Custom", "Approval");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void verifyPopulateModuleAndUpdateCacheWhenUnsupportedFields() throws IOException {
    Set<YamlField> stageFields = getStageFields("pipeline-enforcement-modules.yaml");
    Set<String> modules = new HashSet<>();

    Map<String, PlanCreatorServiceInfo> services =
        Collections.singletonMap("theKey", new PlanCreatorServiceInfo(null, null, 0));
    when(pmsSdkHelper.getServices()).thenReturn(services);

    getStageTypeCache().clear();
    pipelineEnforcementService.populateModuleAndUpdateCache(stageFields, modules);
    assertThat(modules).isEmpty();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void verifyPopulateModuleAndUpdateCacheWhenSupportedFields() throws IOException {
    Set<YamlField> stageFields = getStageFields("pipeline-enforcement-modules.yaml");
    Set<String> modules = new HashSet<>();

    Map<String, Set<String>> supportedTypes = new HashMap<>();
    supportedTypes.put("stage", ImmutableSet.of("Approval", "Custom"));

    Map<String, PlanCreatorServiceInfo> services =
        Collections.singletonMap("theKey", new PlanCreatorServiceInfo(supportedTypes, null, 0));
    when(pmsSdkHelper.getServices()).thenReturn(services);

    getStageTypeCache().clear();
    pipelineEnforcementService.populateModuleAndUpdateCache(stageFields, modules);
    assertThat(modules).hasSize(1);
    assertThat(modules).containsExactlyInAnyOrder("theKey");

    assertThat(getStageTypeCache()).hasSize(2);
    assertThat(getStageTypeCache()).containsKeys("Custom", "Approval");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void verifyPopulateModuleAndUpdateCacheWhenDoesNotMatterSupportedFields() throws IOException {
    Set<YamlField> stageFields = getStageFields("pipeline-enforcement-modules.yaml");
    Set<String> modules = new HashSet<>();

    Map<String, PlanCreatorServiceInfo> services =
        Collections.singletonMap("theKey", new PlanCreatorServiceInfo(null, null, 0));
    when(pmsSdkHelper.getServices()).thenReturn(services);

    getStageTypeCache().clear();
    getStageTypeCache().put("Custom", "Custom");
    getStageTypeCache().put("Approval", "Approval");

    pipelineEnforcementService.populateModuleAndUpdateCache(stageFields, modules);
    assertThat(modules).hasSize(2);
    assertThat(modules).containsExactlyInAnyOrder("Custom", "Approval");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotEnforceUnknowModules() {
    Map<String, Document> filters = new HashMap<>();
    filters.put("ABC", new Document());
    filters.put("DEF", new Document());

    PipelineEntity pipelineEntity = PipelineEntity.builder().accountId("ACCOUNT_ID").filters(filters).build();
    pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity);

    ArgumentCaptor<List<FeatureRestrictionName>> argCaptor = ArgumentCaptor.forClass(List.class);
    verify(enforcementClientService).getAvailabilityForRemoteFeatures(argCaptor.capture(), eq("ACCOUNT_ID"));
    assertThat(argCaptor.getValue()).isEmpty();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateExecutionForKnowModules() throws IOException {
    String yaml = readFile("pipeline-enforcement-ci-stage.yaml");
    Map<String, Document> filters = new HashMap<>();
    filters.put("CD", new Document());
    filters.put("CI", new Document());

    PipelineEntity pipelineEntity =
        PipelineEntity.builder().accountId("ACCOUNT_ID").yaml(yaml).filters(filters).build();
    pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity);

    ArgumentCaptor<List<FeatureRestrictionName>> argCaptor = ArgumentCaptor.forClass(List.class);
    verify(enforcementClientService).getAvailabilityForRemoteFeatures(argCaptor.capture(), eq("ACCOUNT_ID"));
    assertThat(argCaptor.getValue()).hasSize(3);
    assertThat(argCaptor.getValue())
        .containsExactlyInAnyOrder(FeatureRestrictionName.DEPLOYMENTS_PER_MONTH, FeatureRestrictionName.BUILDS,
            FeatureRestrictionName.MAX_BUILDS_PER_DAY);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void shouldSkipCIEnforcementForCustomStageWithRunStep() throws IOException {
    String yaml = readFile("pipeline-enforcement-custom-with-run.yaml");
    Map<String, Document> filters = new HashMap<>();
    filters.put("ci", new Document());

    PipelineEntity pipelineEntity =
        PipelineEntity.builder().accountId("ACCOUNT_ID").identifier("customOnly").yaml(yaml).filters(filters).build();
    pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity);

    ArgumentCaptor<List<FeatureRestrictionName>> argCaptor = ArgumentCaptor.forClass(List.class);
    verify(enforcementClientService).getAvailabilityForRemoteFeatures(argCaptor.capture(), eq("ACCOUNT_ID"));
    assertThat(argCaptor.getValue()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void shouldApplyCIEnforcementForActualCIStage() throws IOException {
    String yaml = readFile("pipeline-enforcement-ci-stage.yaml");
    Map<String, Document> filters = new HashMap<>();
    filters.put("ci", new Document());

    Map<FeatureRestrictionName, Boolean> restrictionMap = new HashMap<>();
    restrictionMap.put(FeatureRestrictionName.BUILDS, false);
    restrictionMap.put(FeatureRestrictionName.MAX_BUILDS_PER_DAY, false);
    when(enforcementClientService.getAvailabilityForRemoteFeatures(
             org.mockito.ArgumentMatchers.anyList(), eq("ACCOUNT_ID")))
        .thenReturn(restrictionMap);

    PipelineEntity pipelineEntity =
        PipelineEntity.builder().accountId("ACCOUNT_ID").identifier("ciPipeline").yaml(yaml).filters(filters).build();
    assertThatThrownBy(() -> pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity))
        .isInstanceOf(FeatureNotSupportedException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void shouldApplyCIEnforcementForMixedCIAndCustomStages() throws IOException {
    String yaml = readFile("pipeline-enforcement-mixed-ci-custom.yaml");
    Map<String, Document> filters = new HashMap<>();
    filters.put("ci", new Document());

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId("ACCOUNT_ID")
                                        .identifier("mixedPipeline")
                                        .yaml(yaml)
                                        .filters(filters)
                                        .build();
    pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity);

    ArgumentCaptor<List<FeatureRestrictionName>> argCaptor = ArgumentCaptor.forClass(List.class);
    verify(enforcementClientService).getAvailabilityForRemoteFeatures(argCaptor.capture(), eq("ACCOUNT_ID"));
    assertThat(argCaptor.getValue()).hasSize(2);
    assertThat(argCaptor.getValue())
        .containsExactlyInAnyOrder(FeatureRestrictionName.BUILDS, FeatureRestrictionName.MAX_BUILDS_PER_DAY);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void shouldHasActualCIStageReturnTrueForMalformedYaml() {
    PipelineEntity pipelineEntity =
        PipelineEntity.builder().accountId("ACCOUNT_ID").identifier("broken").yaml("invalid: {yaml: [").build();
    assertThat(pipelineEnforcementService.hasActualCIStage(pipelineEntity, null)).isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void shouldHasActualCIStageReturnFalseForEmptyYaml() {
    PipelineEntity pipelineEntity =
        PipelineEntity.builder().accountId("ACCOUNT_ID").identifier("empty").yaml(null).build();
    assertThat(pipelineEnforcementService.hasActualCIStage(pipelineEntity, null)).isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void shouldHasActualCIStageReturnTrueForTemplateModulesWithCIAndNullProcessedYaml() {
    Set<String> templateModules = new HashSet<>();
    templateModules.add("ci");
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId("ACCOUNT_ID")
                                        .identifier("templateCI")
                                        .templateModules(templateModules)
                                        .yaml(null)
                                        .build();
    assertThat(pipelineEnforcementService.hasActualCIStage(pipelineEntity, null)).isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void shouldApplyCIEnforcementForCIStageTemplate() throws IOException {
    String unresolvedYaml = readFile("pipeline-enforcement-stage-template-ref.yaml");
    String processedYaml = readFile("pipeline-enforcement-resolved-ci-template.yaml");
    Set<String> templateModules = new HashSet<>();
    templateModules.add("ci");
    Map<String, Document> filters = new HashMap<>();
    filters.put("ci", new Document());

    Map<FeatureRestrictionName, Boolean> restrictionMap = new HashMap<>();
    restrictionMap.put(FeatureRestrictionName.BUILDS, false);
    restrictionMap.put(FeatureRestrictionName.MAX_BUILDS_PER_DAY, false);
    when(enforcementClientService.getAvailabilityForRemoteFeatures(
             org.mockito.ArgumentMatchers.anyList(), eq("ACCOUNT_ID")))
        .thenReturn(restrictionMap);

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId("ACCOUNT_ID")
                                        .identifier("ciTemplate")
                                        .yaml(unresolvedYaml)
                                        .filters(filters)
                                        .templateModules(templateModules)
                                        .build();
    assertThatThrownBy(
        () -> pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity, processedYaml))
        .isInstanceOf(FeatureNotSupportedException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void shouldSkipCIEnforcementForCustomStageTemplateWithRunStep() throws IOException {
    String unresolvedYaml = readFile("pipeline-enforcement-stage-template-ref.yaml");
    String processedYaml = readFile("pipeline-enforcement-resolved-custom-template.yaml");
    Set<String> templateModules = new HashSet<>();
    templateModules.add("ci");
    Map<String, Document> filters = new HashMap<>();
    filters.put("ci", new Document());

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId("ACCOUNT_ID")
                                        .identifier("customTemplate")
                                        .yaml(unresolvedYaml)
                                        .filters(filters)
                                        .templateModules(templateModules)
                                        .build();
    pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity, processedYaml);

    ArgumentCaptor<List<FeatureRestrictionName>> argCaptor = ArgumentCaptor.forClass(List.class);
    verify(enforcementClientService).getAvailabilityForRemoteFeatures(argCaptor.capture(), eq("ACCOUNT_ID"));
    assertThat(argCaptor.getValue()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void shouldFailSafeOnMalformedProcessedYaml() {
    Set<String> templateModules = new HashSet<>();
    templateModules.add("ci");
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId("ACCOUNT_ID")
                                        .identifier("broken")
                                        .yaml(null)
                                        .templateModules(templateModules)
                                        .build();
    assertThat(pipelineEnforcementService.hasActualCIStage(pipelineEntity, "invalid: {yaml: [")).isTrue();
  }

  private Map<String, String> getStageTypeCache() {
    return (Map<String, String>) ReflectionUtils.getFieldValue(pipelineEnforcementService, "stageTypeToModule");
  }

  private Set<YamlField> getStageFields(String yamlFile) throws IOException {
    String yamlContent = readFile(yamlFile);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    YamlField pipelineField = yamlField.getNode().getField("pipeline");

    return PipelineUtils.getStagesFieldFromPipeline(pipelineField);
  }

  private String readFile(String filePath) throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    return Resources.toString(Objects.requireNonNull(classLoader.getResource(filePath)), StandardCharsets.UTF_8);
  }
}
