/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plugin;

import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.execution.StepsExecutionConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.PluginInfoProviderServiceGrpc;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.container.utils.K8sPodInitUtils;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.StepInfo;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ContainerStepV2PluginProviderImplTest {
  @Mock
  private Map<ModuleType, PluginInfoProviderServiceGrpc.PluginInfoProviderServiceBlockingStub>
      pluginInfoProviderServiceBlockingStubMap;
  @Mock private K8sPodInitUtils k8sPodInitUtils;
  @Mock private ContainerExecutionConfig containerExecutionConfig;
  @Mock private InitContainerV2StepInfo initContainerV2StepInfo;
  @Mock private ContainerK8sInfra containerK8sInfra;
  @Mock private Ambiance ambiance;
  @Mock private PluginInfoProviderServiceGrpc.PluginInfoProviderServiceBlockingStub blockingStub;
  @Mock private StepsExecutionConfig stepsExecutionConfig;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @InjectMocks private ContainerStepV2PluginProviderImpl containerStepV2PluginProvider;

  private MockedStatic<AmbianceUtils> mockedAmbianceUtils;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);

    // Mock the module to supported steps mapping
    Map<String, List<String>> moduleToSupportedSteps = new HashMap<>();
    moduleToSupportedSteps.put("CI", List.of("Run"));
    when(containerExecutionConfig.getModuleToSupportedSteps()).thenReturn(moduleToSupportedSteps);

    // Mock AmbianceUtils.getAccountId to return a test account ID
    mockedAmbianceUtils = mockStatic(AmbianceUtils.class);
    mockedAmbianceUtils.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn("testAccountId");

    // Mock feature flag to be disabled by default (old implementation)
    when(pmsFeatureFlagHelper.isEnabled("testAccountId", io.harness.beans.FeatureName.OPA_RUN_ON_CUSTOMER_INFRA))
        .thenReturn(false);
  }

  @After
  public void tearDown() {
    if (mockedAmbianceUtils != null) {
      mockedAmbianceUtils.close();
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetStepInfosWithInsertBlock_ModifiesStepIdentifier() {
    // Given
    boolean flexibleTemplatesEnabled = true;

    // Create INSERT block with steps
    List<ExecutionWrapperConfig> steps = new ArrayList<>();

    // Create an INSERT step containing a Run step with correct structure
    String insertJson =
        "{ \"identifier\": \"Insert_Here\", \"name\": \"Insert_Here\", \"steps\": [{ \"step\": { \"identifier\": "
        + "\"Run_1\", \"type\": \"Run\", \"name\": \"Run_1\", \"spec\": { \"image\": \"ubuntu:latest\" } } }] }";
    JsonNode insertNode = createJsonNode(insertJson);
    ExecutionWrapperConfig insertWrapper = ExecutionWrapperConfig.builder().insert(insertNode).build();
    steps.add(insertWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    // When
    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    // Then
    assertThat(stepInfos).hasSize(1);
    StepInfo stepInfo = stepInfos.iterator().next();

    // Verify that the step identifier includes the INSERT block prefix
    assertThat(stepInfo.getStepIdentifier()).isEqualTo("Insert_Here_Run_1");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetStepInfosWithNestedInsertBlock_ModifiesStepIdentifier() {
    // Given
    boolean flexibleTemplatesEnabled = true;

    // Test with multiple INSERT blocks to validate prefixing works correctly
    List<ExecutionWrapperConfig> steps = new ArrayList<>();

    // Create first INSERT step
    String insertJson1 =
        "{ \"identifier\": \"Insert_Here\", \"name\": \"Insert_Here\", \"steps\": [{ \"step\": { \"identifier\": "
        + "\"Run_1\", \"type\": \"Run\", \"name\": \"Run_1\", \"spec\": { \"image\": \"ubuntu:latest\" } } }] }";
    JsonNode insertNode1 = createJsonNode(insertJson1);
    ExecutionWrapperConfig insertWrapper1 = ExecutionWrapperConfig.builder().insert(insertNode1).build();
    steps.add(insertWrapper1);

    // Create second INSERT step
    String insertJson2 =
        "{ \"identifier\": \"Insert_There\", \"name\": \"Insert_There\", \"steps\": [{ \"step\": { \"identifier\": "
        + "\"Run_2\", \"type\": \"Run\", \"name\": \"Run_2\", \"spec\": { \"image\": \"alpine:latest\" } } }] }";
    JsonNode insertNode2 = createJsonNode(insertJson2);
    ExecutionWrapperConfig insertWrapper2 = ExecutionWrapperConfig.builder().insert(insertNode2).build();
    steps.add(insertWrapper2);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    // When
    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    // Then
    assertThat(stepInfos).hasSize(2);

    // Verify that each step identifier includes the correct INSERT block prefix
    List<String> identifiers = stepInfos.stream().map(StepInfo::getStepIdentifier).sorted().collect(toList());
    assertThat(identifiers).containsExactly("Insert_Here_Run_1", "Insert_There_Run_2");
  }

  private JsonNode createJsonNode(String json) {
    try {
      return YamlUtils.read(json, JsonNode.class);
    } catch (Exception ex) {
      throw new RuntimeException("Failed to create JsonNode", ex);
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testModifyStepIdentifierInPlace_ModifiesIdentifier() {
    // Given
    String stepJson = "{ \"identifier\": \"Run_1\", \"type\": \"Run\", \"name\": \"Run_1\", \"spec\": { \"image\": "
        + "\"ubuntu:latest\" } }";
    JsonNode stepNode = createJsonNode(stepJson);
    String prefix = "Insert_Here";

    // When
    containerStepV2PluginProvider.modifyStepIdentifierInPlace(stepNode, prefix);

    // Then
    assertThat(stepNode.has("identifier")).isTrue();
    assertThat(stepNode.get("identifier").asText()).isEqualTo("Insert_Here_Run_1");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testModifyStepIdentifierInPlace_WithNullIdentifier() {
    // Given
    String stepJson = "{ \"type\": \"Run\", \"name\": \"Run_1\", \"spec\": { \"image\": \"ubuntu:latest\" } }";
    JsonNode stepNode = createJsonNode(stepJson);
    String prefix = "Insert_Here";

    // When
    containerStepV2PluginProvider.modifyStepIdentifierInPlace(stepNode, prefix);

    // Then
    assertThat(stepNode.has("identifier")).isFalse();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testModifyStepIdentifierInPlace_WithNonTextualIdentifier() {
    // Given
    String stepJson =
        "{ \"identifier\": 123, \"type\": \"Run\", \"name\": \"Run_1\", \"spec\": { \"image\": \"ubuntu:latest\" } }";
    JsonNode stepNode = createJsonNode(stepJson);
    String prefix = "Insert_Here";

    // When
    containerStepV2PluginProvider.modifyStepIdentifierInPlace(stepNode, prefix);

    // Then
    assertThat(stepNode.has("identifier")).isTrue();
    assertThat(stepNode.get("identifier")).isNotNull();
    // Identifier should remain unchanged since it's not textual
    assertThat(stepNode.get("identifier").asInt()).isEqualTo(123);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testModifyStepIdentifierInPlace_WithNonObjectNode() {
    // Given
    JsonNode nonObjectNode = createJsonNode("\"simple string\"");
    String prefix = "Insert_Here";

    // When - should not throw exception
    containerStepV2PluginProvider.modifyStepIdentifierInPlace(nonObjectNode, prefix);

    // Then - no modification should occur
    assertThat(nonObjectNode.asText()).isEqualTo("simple string");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetStepType_WithRegularStep() {
    // Given
    boolean flexibleTemplatesEnabled = false;
    List<ExecutionWrapperConfig> steps = new ArrayList<>();

    // Create a regular Run step
    String stepJson = "{ \"identifier\": \"Run_1\", \"type\": \"Run\", \"name\": \"Run_1\", \"spec\": { \"image\": "
        + "\"ubuntu:latest\" } }";
    JsonNode stepNode = createJsonNode(stepJson);
    ExecutionWrapperConfig stepWrapper = ExecutionWrapperConfig.builder().step(stepNode).build();
    steps.add(stepWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    // When
    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    // Then
    assertThat(stepInfos).hasSize(1);
    StepInfo stepInfo = stepInfos.iterator().next();
    assertThat(stepInfo.getStepType()).isEqualTo("Run");
    assertThat(stepInfo.getModuleType()).isEqualTo("CI");
    assertThat(stepInfo.getStepIdentifier()).isEqualTo("Run_1");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetStepType_WithParallelSteps() {
    // Given
    boolean flexibleTemplatesEnabled = false;
    List<ExecutionWrapperConfig> steps = new ArrayList<>();

    // Create parallel steps
    String parallelJson =
        "{ \"sections\": [{ \"step\": { \"identifier\": \"Run_1\", \"type\": \"Run\", \"name\": \"Run_1\", \"spec\": { "
        + "\"image\": \"ubuntu:latest\" } } }, { \"step\": { \"identifier\": \"Run_2\", \"type\": \"Run\", \"name\": "
        + "\"Run_2\", \"spec\": { \"image\": \"alpine:latest\" } } }] }";
    JsonNode parallelNode = createJsonNode(parallelJson);
    ExecutionWrapperConfig parallelWrapper = ExecutionWrapperConfig.builder().parallel(parallelNode).build();
    steps.add(parallelWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    // When
    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    // Then
    assertThat(stepInfos).hasSize(2);
    List<String> identifiers = stepInfos.stream().map(StepInfo::getStepIdentifier).sorted().collect(toList());
    assertThat(identifiers).containsExactly("Run_1", "Run_2");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetStepType_WithEmptyStepType_ThrowsException() {
    // Given
    boolean flexibleTemplatesEnabled = false;
    List<ExecutionWrapperConfig> steps = new ArrayList<>();

    // Create a step with empty type
    String stepJson = "{ \"identifier\": \"Empty_1\", \"type\": \"\", \"name\": \"Empty_1\", \"spec\": { \"image\": "
        + "\"ubuntu:latest\" } }";
    JsonNode stepNode = createJsonNode(stepJson);
    ExecutionWrapperConfig stepWrapper = ExecutionWrapperConfig.builder().step(stepNode).build();
    steps.add(stepWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    // When & Then
    try {
      containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);
      // Should not reach here
      assertThat(false).as("Expected ContainerStepExecutionException to be thrown").isTrue();
    } catch (ContainerStepExecutionException ex) {
      assertThat(ex.getMessage()).contains("No module found for step");
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetStepType_WithInsertBlockAndFlexibleTemplatesDisabled() {
    // Given
    boolean flexibleTemplatesEnabled = false;
    List<ExecutionWrapperConfig> steps = new ArrayList<>();

    // Create INSERT step
    String insertJson =
        "{ \"identifier\": \"Insert_Here\", \"name\": \"Insert_Here\", \"steps\": [{ \"step\": { \"identifier\": "
        + "\"Run_1\", \"type\": \"Run\", \"name\": \"Run_1\", \"spec\": { \"image\": \"ubuntu:latest\" } } }] }";
    JsonNode insertNode = createJsonNode(insertJson);
    ExecutionWrapperConfig insertWrapper = ExecutionWrapperConfig.builder().insert(insertNode).build();
    steps.add(insertWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    // When
    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    // Then - INSERT blocks should be ignored when flexible templates are disabled
    assertThat(stepInfos).isEmpty();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetStepType_WithNullStep() {
    // Given
    boolean flexibleTemplatesEnabled = false;
    List<ExecutionWrapperConfig> steps = new ArrayList<>();

    // Create wrapper with null step
    ExecutionWrapperConfig nullStepWrapper = ExecutionWrapperConfig.builder().step(null).build();
    steps.add(nullStepWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    // When
    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    // Then - should handle null step gracefully
    assertThat(stepInfos).isEmpty();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetStepType_WithEmptyParallelSections() {
    // Given
    boolean flexibleTemplatesEnabled = false;
    List<ExecutionWrapperConfig> steps = new ArrayList<>();

    // Create parallel step with empty sections
    String parallelJson = "{ \"sections\": [] }";
    JsonNode parallelNode = createJsonNode(parallelJson);
    ExecutionWrapperConfig parallelWrapper = ExecutionWrapperConfig.builder().parallel(parallelNode).build();
    steps.add(parallelWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    // When
    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    // Then - should handle empty sections gracefully
    assertThat(stepInfos).isEmpty();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetStepType_WithNestedInsertBlocksAndStepGroupIdOfParent() {
    // Given
    boolean flexibleTemplatesEnabled = true;
    List<ExecutionWrapperConfig> steps = new ArrayList<>();

    // Create simple INSERT structure to test step identifier prefixing
    String insertJson =
        "{ \"identifier\": \"Insert_Here\", \"name\": \"Insert_Here\", \"steps\": [{ \"step\": { \"identifier\": "
        + "\"Run_1\", \"type\": \"Run\", \"name\": \"Run_1\", \"spec\": { \"image\": \"ubuntu:latest\" } } }, { "
        + "\"step\": { \"identifier\": \"Run_2\", \"type\": \"Run\", \"name\": \"Run_2\", \"spec\": { \"image\": "
        + "\"alpine:latest\" } } }] }";
    JsonNode insertNode = createJsonNode(insertJson);
    ExecutionWrapperConfig insertWrapper = ExecutionWrapperConfig.builder().insert(insertNode).build();
    steps.add(insertWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    // When
    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    // Then
    assertThat(stepInfos).hasSize(2);

    // Verify that step identifiers include the INSERT block prefix
    List<String> identifiers = stepInfos.stream().map(StepInfo::getStepIdentifier).sorted().collect(toList());
    assertThat(identifiers).containsExactly("Insert_Here_Run_1", "Insert_Here_Run_2");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetStepType_WithInsertBlockEmptySteps() {
    // Given
    boolean flexibleTemplatesEnabled = true;
    List<ExecutionWrapperConfig> steps = new ArrayList<>();

    // Create INSERT step with empty steps collection
    String insertJson = "{ \"identifier\": \"Empty_Insert\", \"name\": \"Empty_Insert\", \"steps\": [] }";
    JsonNode insertNode = createJsonNode(insertJson);
    ExecutionWrapperConfig insertWrapper = ExecutionWrapperConfig.builder().insert(insertNode).build();
    steps.add(insertWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    // When
    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    // Then - should handle empty steps gracefully
    assertThat(stepInfos).isEmpty();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetStepType_WithParallelStepsAndStepGroupIdOfParent() {
    // Given - simulate parallel steps within a step group context
    boolean flexibleTemplatesEnabled = false;
    List<ExecutionWrapperConfig> steps = new ArrayList<>();

    // Create parallel steps
    String parallelJson =
        "{ \"sections\": [{ \"step\": { \"identifier\": \"Run_1\", \"type\": \"Run\", \"name\": \"Run_1\", \"spec\": { "
        + "\"image\": \"ubuntu:latest\" } } }, { \"step\": { \"identifier\": \"Run_2\", \"type\": \"Run\", \"name\": "
        + "\"Run_2\", \"spec\": { \"image\": \"alpine:latest\" } } }] }";
    JsonNode parallelNode = createJsonNode(parallelJson);
    ExecutionWrapperConfig parallelWrapper = ExecutionWrapperConfig.builder().parallel(parallelNode).build();
    steps.add(parallelWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    // When - call getStepInfos which internally calls getStepType with empty stepGroupIdOfParent
    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    // Then
    assertThat(stepInfos).hasSize(2);
    List<String> identifiers = stepInfos.stream().map(StepInfo::getStepIdentifier).sorted().collect(toList());
    // Parallel steps should not have prefix when no parent step group ID is provided
    assertThat(identifiers).containsExactly("Run_1", "Run_2");

    // Verify all steps have correct module type
    for (StepInfo stepInfo : stepInfos) {
      assertThat(stepInfo.getModuleType()).isEqualTo("CI");
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetStepType_WithInsertBlockAndNullStepGroupIdOfParent() {
    // Given
    boolean flexibleTemplatesEnabled = true;
    List<ExecutionWrapperConfig> steps = new ArrayList<>();

    // Create INSERT step
    String insertJson =
        "{ \"identifier\": \"Insert_Here\", \"name\": \"Insert_Here\", \"steps\": [{ \"step\": { \"identifier\": "
        + "\"Run_1\", \"type\": \"Run\", \"name\": \"Run_1\", \"spec\": { \"image\": \"ubuntu:latest\" } } }] }";
    JsonNode insertNode = createJsonNode(insertJson);
    ExecutionWrapperConfig insertWrapper = ExecutionWrapperConfig.builder().insert(insertNode).build();
    steps.add(insertWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    // When
    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    // Then - should use INSERT block identifier when stepGroupIdOfParent is null/empty
    assertThat(stepInfos).hasSize(1);
    StepInfo stepInfo = stepInfos.iterator().next();
    assertThat(stepInfo.getStepIdentifier()).isEqualTo("Insert_Here_Run_1");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetModuleForStep_fixModuleResolutionFF_approvalStepResolvedToPms() {
    Map<String, List<String>> moduleToSupportedSteps = new HashMap<>();
    moduleToSupportedSteps.put("cd", List.of("DownloadManifests", "AwsSamBuild"));
    moduleToSupportedSteps.put("ci", List.of("Run", "Background"));
    moduleToSupportedSteps.put("pms", List.of("OPAEvaluation", "HarnessApproval"));
    when(containerExecutionConfig.getModuleToSupportedSteps()).thenReturn(moduleToSupportedSteps);

    when(pmsFeatureFlagHelper.isEnabled(
             "testAccountId", io.harness.beans.FeatureName.CDS_CONTAINER_STEP_GROUP_FIX_MODULE_RESOLUTION))
        .thenReturn(true);

    boolean flexibleTemplatesEnabled = false;
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    String stepJson =
        "{ \"identifier\": \"Approval_1\", \"type\": \"HarnessApproval\", \"name\": \"Approval_1\", \"spec\": {} }";
    JsonNode stepNode = createJsonNode(stepJson);
    ExecutionWrapperConfig stepWrapper = ExecutionWrapperConfig.builder().step(stepNode).build();
    steps.add(stepWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    assertThat(stepInfos).hasSize(1);
    StepInfo stepInfo = stepInfos.iterator().next();
    assertThat(stepInfo.getStepType()).isEqualTo("HarnessApproval");
    assertThat(stepInfo.getModuleType()).isEqualTo("pms");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetModuleForStep_ffDisabled_approvalStepDefaultsToCi() {
    Map<String, List<String>> moduleToSupportedSteps = new HashMap<>();
    moduleToSupportedSteps.put("cd", List.of("DownloadManifests", "AwsSamBuild"));
    moduleToSupportedSteps.put("ci", List.of("Run", "Background"));
    moduleToSupportedSteps.put("pms", List.of("OPAEvaluation", "HarnessApproval"));
    when(containerExecutionConfig.getModuleToSupportedSteps()).thenReturn(moduleToSupportedSteps);

    when(pmsFeatureFlagHelper.isEnabled("testAccountId", io.harness.beans.FeatureName.OPA_RUN_ON_CUSTOMER_INFRA))
        .thenReturn(false);
    when(pmsFeatureFlagHelper.isEnabled(
             "testAccountId", io.harness.beans.FeatureName.CDS_CONTAINER_STEP_GROUP_FIX_MODULE_RESOLUTION))
        .thenReturn(false);

    boolean flexibleTemplatesEnabled = false;
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    String stepJson =
        "{ \"identifier\": \"Approval_1\", \"type\": \"HarnessApproval\", \"name\": \"Approval_1\", \"spec\": {} }";
    JsonNode stepNode = createJsonNode(stepJson);
    ExecutionWrapperConfig stepWrapper = ExecutionWrapperConfig.builder().step(stepNode).build();
    steps.add(stepWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    assertThat(stepInfos).hasSize(1);
    StepInfo stepInfo = stepInfos.iterator().next();
    assertThat(stepInfo.getStepType()).isEqualTo("HarnessApproval");
    assertThat(stepInfo.getModuleType()).isNotEqualTo("pms");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetModuleForStep_fixModuleResolutionFF_cdStepResolvedCorrectly() {
    Map<String, List<String>> moduleToSupportedSteps = new HashMap<>();
    moduleToSupportedSteps.put("cd", List.of("DownloadManifests", "AwsSamBuild"));
    moduleToSupportedSteps.put("ci", List.of("Run", "Background"));
    moduleToSupportedSteps.put("pms", List.of("OPAEvaluation", "HarnessApproval"));
    when(containerExecutionConfig.getModuleToSupportedSteps()).thenReturn(moduleToSupportedSteps);

    when(pmsFeatureFlagHelper.isEnabled(
             "testAccountId", io.harness.beans.FeatureName.CDS_CONTAINER_STEP_GROUP_FIX_MODULE_RESOLUTION))
        .thenReturn(true);

    boolean flexibleTemplatesEnabled = false;
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    String stepJson =
        "{ \"identifier\": \"Download_1\", \"type\": \"DownloadManifests\", \"name\": \"Download_1\", \"spec\": {} }";
    JsonNode stepNode = createJsonNode(stepJson);
    ExecutionWrapperConfig stepWrapper = ExecutionWrapperConfig.builder().step(stepNode).build();
    steps.add(stepWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    assertThat(stepInfos).hasSize(1);
    StepInfo stepInfo = stepInfos.iterator().next();
    assertThat(stepInfo.getStepType()).isEqualTo("DownloadManifests");
    assertThat(stepInfo.getModuleType()).isEqualTo("cd");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetModuleForStep_fixModuleResolutionFF_unknownStepDefaultsToCi() {
    Map<String, List<String>> moduleToSupportedSteps = new HashMap<>();
    moduleToSupportedSteps.put("cd", List.of("DownloadManifests", "AwsSamBuild"));
    moduleToSupportedSteps.put("ci", List.of("Run", "Background"));
    moduleToSupportedSteps.put("pms", List.of("OPAEvaluation", "HarnessApproval"));
    when(containerExecutionConfig.getModuleToSupportedSteps()).thenReturn(moduleToSupportedSteps);

    when(pmsFeatureFlagHelper.isEnabled(
             "testAccountId", io.harness.beans.FeatureName.CDS_CONTAINER_STEP_GROUP_FIX_MODULE_RESOLUTION))
        .thenReturn(true);

    boolean flexibleTemplatesEnabled = false;
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    String stepJson =
        "{ \"identifier\": \"Unknown_1\", \"type\": \"SomeUnknownStep\", \"name\": \"Unknown_1\", \"spec\": {} }";
    JsonNode stepNode = createJsonNode(stepJson);
    ExecutionWrapperConfig stepWrapper = ExecutionWrapperConfig.builder().step(stepNode).build();
    steps.add(stepWrapper);

    when(stepsExecutionConfig.getSteps()).thenReturn(steps);

    Set<StepInfo> stepInfos =
        containerStepV2PluginProvider.getStepInfos(stepsExecutionConfig, flexibleTemplatesEnabled, ambiance);

    assertThat(stepInfos).hasSize(1);
    StepInfo stepInfo = stepInfos.iterator().next();
    assertThat(stepInfo.getModuleType()).isEqualTo("ci");
  }
}
