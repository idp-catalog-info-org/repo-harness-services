/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.newArrayList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.beans.steps.stepinfo.RunTestsStepInfoV1;
import io.harness.beans.steps.stepinfo.StepNodeV1;
import io.harness.beans.steps.v1.Container;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.DockerInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.ContainerlessPluginConfig;
import io.harness.ci.execution.execution.intfc.CIExecutionConfigService;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CI)
public class DockerInitializeStepUtilsTest {
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private CIExecutionConfigService ciExecutionConfigService;
  @InjectMocks private DockerInitializeStepUtils dockerInitializeStepUtils;
  private static String ACCOUNT_ID = "accountId";
  private static StageInfraDetails stageInfraDetails;
  private static Infrastructure infrastructure;

  @Before
  public void setup() {
    stageInfraDetails = VmStageInfraDetails.builder()
                            .infraInfo(CIInitializeTaskParams.Type.DOCKER)
                            .routeToRunner(true)
                            .isContainerLessWithRunner(true)
                            .build();
    infrastructure = DockerInfraYaml.builder()
                         .spec(DockerInfraSpec.builder()
                                   .platform(ParameterField.createValueField(
                                       Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                                   .build())
                         .build();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testShouldEnableDockerSetupOnUnifiedRunner_RunStep_true() {
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getRunStepWithImageJsonNode()).build());
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    boolean shouldEnableDockerSetup = dockerInitializeStepUtils.shouldEnableDockerSetupOnUnifiedRunner(
        initializeStepInfo, ambiance, ACCOUNT_ID, stageInfraDetails, infrastructure);
    assertThat(shouldEnableDockerSetup).isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testShouldEnableDockerSetupOnUnifiedRunner_RunStep_false() {
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getRunStepWithoutImageJsonNode()).build());
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    boolean shouldEnableDockerSetup = dockerInitializeStepUtils.shouldEnableDockerSetupOnUnifiedRunner(
        initializeStepInfo, ambiance, ACCOUNT_ID, stageInfraDetails, infrastructure);
    assertThat(shouldEnableDockerSetup).isFalse();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testShouldEnableDockerSetupOnUnifiedRunner_ImplicitClone_false() {
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getImplicitGitCloneJsonNode()).build());
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", ACCOUNT_ID).build();
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(eq(CIStepInfoType.GIT_CLONE), any()))
        .thenReturn(ContainerlessPluginConfig.builder().name("drone-git").build());
    boolean shouldEnableDockerSetup = dockerInitializeStepUtils.shouldEnableDockerSetupOnUnifiedRunner(
        initializeStepInfo, ambiance, ACCOUNT_ID, stageInfraDetails, infrastructure);
    assertThat(shouldEnableDockerSetup).isFalse();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testShouldEnableDockerSetupOnUnifiedRunner_ExplicitClone_false() {
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getExplicitGitCloneJsonNode()).build());
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", ACCOUNT_ID).build();
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(eq(CIStepInfoType.GIT_CLONE), any()))
        .thenReturn(ContainerlessPluginConfig.builder().name("drone-git").build());
    boolean shouldEnableDockerSetup = dockerInitializeStepUtils.shouldEnableDockerSetupOnUnifiedRunner(
        initializeStepInfo, ambiance, ACCOUNT_ID, stageInfraDetails, infrastructure);
    assertThat(shouldEnableDockerSetup).isFalse();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testShouldEnableDockerSetupOnUnifiedRunner_V1_FFDisabled_returnsTrue() {
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getV1StepJsonNode()).build());
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = buildV1Ambiance();
    when(featureFlagService.isEnabled(eq(FeatureName.PIPE_OPTIONAL_DOCKER_WITH_RUNNER_V1_STAGES), any()))
        .thenReturn(false);
    boolean shouldEnableDockerSetup = dockerInitializeStepUtils.shouldEnableDockerSetupOnUnifiedRunner(
        initializeStepInfo, ambiance, ACCOUNT_ID, stageInfraDetails, infrastructure);
    assertThat(shouldEnableDockerSetup).isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testShouldEnableDockerSetupOnUnifiedRunner_V1_RunStep_withImage_true() {
    RunStepInfoV1 runStep = RunStepInfoV1.builder()
                                .container(Container.builder().image(ParameterField.createValueField("ubuntu")).build())
                                .build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().run(ParameterField.createValueField(runStep)).build();
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getV1StepJsonNode()).build());
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = buildV1Ambiance();
    when(featureFlagService.isEnabled(eq(FeatureName.PIPE_OPTIONAL_DOCKER_WITH_RUNNER_V1_STAGES), any()))
        .thenReturn(true);
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any())).thenReturn(stepNodeV1);
      boolean shouldEnableDockerSetup = dockerInitializeStepUtils.shouldEnableDockerSetupOnUnifiedRunner(
          initializeStepInfo, ambiance, ACCOUNT_ID, stageInfraDetails, infrastructure);
      assertThat(shouldEnableDockerSetup).isTrue();
    }
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testShouldEnableDockerSetupOnUnifiedRunner_V1_RunStep_withoutImage_false() {
    RunStepInfoV1 runStep = RunStepInfoV1.builder().container(Container.builder().build()).build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().run(ParameterField.createValueField(runStep)).build();
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getV1StepJsonNode()).build());
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = buildV1Ambiance();
    when(featureFlagService.isEnabled(eq(FeatureName.PIPE_OPTIONAL_DOCKER_WITH_RUNNER_V1_STAGES), any()))
        .thenReturn(true);
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any())).thenReturn(stepNodeV1);
      boolean shouldEnableDockerSetup = dockerInitializeStepUtils.shouldEnableDockerSetupOnUnifiedRunner(
          initializeStepInfo, ambiance, ACCOUNT_ID, stageInfraDetails, infrastructure);
      assertThat(shouldEnableDockerSetup).isFalse();
    }
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testShouldEnableDockerSetupOnUnifiedRunner_V1_RunStep_noContainer_false() {
    RunStepInfoV1 runStep = RunStepInfoV1.builder().build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().run(ParameterField.createValueField(runStep)).build();
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getV1StepJsonNode()).build());
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = buildV1Ambiance();
    when(featureFlagService.isEnabled(eq(FeatureName.PIPE_OPTIONAL_DOCKER_WITH_RUNNER_V1_STAGES), any()))
        .thenReturn(true);
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any())).thenReturn(stepNodeV1);
      boolean shouldEnableDockerSetup = dockerInitializeStepUtils.shouldEnableDockerSetupOnUnifiedRunner(
          initializeStepInfo, ambiance, ACCOUNT_ID, stageInfraDetails, infrastructure);
      assertThat(shouldEnableDockerSetup).isFalse();
    }
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testShouldEnableDockerSetupOnUnifiedRunner_V1_BackgroundStep_withImage_true() {
    RunStepInfoV1 backgroundStep =
        RunStepInfoV1.builder()
            .container(Container.builder().image(ParameterField.createValueField("redis")).build())
            .build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().background(ParameterField.createValueField(backgroundStep)).build();
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getV1StepJsonNode()).build());
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = buildV1Ambiance();
    when(featureFlagService.isEnabled(eq(FeatureName.PIPE_OPTIONAL_DOCKER_WITH_RUNNER_V1_STAGES), any()))
        .thenReturn(true);
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any())).thenReturn(stepNodeV1);
      boolean shouldEnableDockerSetup = dockerInitializeStepUtils.shouldEnableDockerSetupOnUnifiedRunner(
          initializeStepInfo, ambiance, ACCOUNT_ID, stageInfraDetails, infrastructure);
      assertThat(shouldEnableDockerSetup).isTrue();
    }
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testShouldEnableDockerSetupOnUnifiedRunner_V1_BackgroundStep_withoutImage_false() {
    RunStepInfoV1 backgroundStep = RunStepInfoV1.builder().container(Container.builder().build()).build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().background(ParameterField.createValueField(backgroundStep)).build();
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getV1StepJsonNode()).build());
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = buildV1Ambiance();
    when(featureFlagService.isEnabled(eq(FeatureName.PIPE_OPTIONAL_DOCKER_WITH_RUNNER_V1_STAGES), any()))
        .thenReturn(true);
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any())).thenReturn(stepNodeV1);
      boolean shouldEnableDockerSetup = dockerInitializeStepUtils.shouldEnableDockerSetupOnUnifiedRunner(
          initializeStepInfo, ambiance, ACCOUNT_ID, stageInfraDetails, infrastructure);
      assertThat(shouldEnableDockerSetup).isFalse();
    }
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testShouldEnableDockerSetupOnUnifiedRunner_V1_RunTestStep_withImage_true() {
    RunTestsStepInfoV1 runTestStep =
        RunTestsStepInfoV1.builder()
            .container(Container.builder().image(ParameterField.createValueField("maven")).build())
            .build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().runTest(ParameterField.createValueField(runTestStep)).build();
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getV1StepJsonNode()).build());
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = buildV1Ambiance();
    when(featureFlagService.isEnabled(eq(FeatureName.PIPE_OPTIONAL_DOCKER_WITH_RUNNER_V1_STAGES), any()))
        .thenReturn(true);
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any())).thenReturn(stepNodeV1);
      boolean shouldEnableDockerSetup = dockerInitializeStepUtils.shouldEnableDockerSetupOnUnifiedRunner(
          initializeStepInfo, ambiance, ACCOUNT_ID, stageInfraDetails, infrastructure);
      assertThat(shouldEnableDockerSetup).isTrue();
    }
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testShouldEnableDockerSetupOnUnifiedRunner_V1_RunTestStep_withoutImage_false() {
    RunTestsStepInfoV1 runTestStep = RunTestsStepInfoV1.builder().container(Container.builder().build()).build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().runTest(ParameterField.createValueField(runTestStep)).build();
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getV1StepJsonNode()).build());
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = buildV1Ambiance();
    when(featureFlagService.isEnabled(eq(FeatureName.PIPE_OPTIONAL_DOCKER_WITH_RUNNER_V1_STAGES), any()))
        .thenReturn(true);
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any())).thenReturn(stepNodeV1);
      boolean shouldEnableDockerSetup = dockerInitializeStepUtils.shouldEnableDockerSetupOnUnifiedRunner(
          initializeStepInfo, ambiance, ACCOUNT_ID, stageInfraDetails, infrastructure);
      assertThat(shouldEnableDockerSetup).isFalse();
    }
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testShouldEnableDockerSetupOnUnifiedRunner_V1_OtherStep_defaultsToTrue() {
    // A step with none of run/background/runTest set defaults to docker required
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().build();
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getV1StepJsonNode()).build());
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = buildV1Ambiance();
    when(featureFlagService.isEnabled(eq(FeatureName.PIPE_OPTIONAL_DOCKER_WITH_RUNNER_V1_STAGES), any()))
        .thenReturn(true);
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any())).thenReturn(stepNodeV1);
      boolean shouldEnableDockerSetup = dockerInitializeStepUtils.shouldEnableDockerSetupOnUnifiedRunner(
          initializeStepInfo, ambiance, ACCOUNT_ID, stageInfraDetails, infrastructure);
      assertThat(shouldEnableDockerSetup).isTrue();
    }
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testShouldEnableDockerSetupOnUnifiedRunner_V1_RollbackSteps_withImage_true() {
    RunStepInfoV1 runStep = RunStepInfoV1.builder()
                                .container(Container.builder().image(ParameterField.createValueField("ubuntu")).build())
                                .build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().run(ParameterField.createValueField(runStep)).build();
    // Main steps have no image, rollback step does
    RunStepInfoV1 mainRunStep = RunStepInfoV1.builder().container(Container.builder().build()).build();
    StepNodeV1 mainStepNodeV1 = StepNodeV1.builder().run(ParameterField.createValueField(mainRunStep)).build();
    List<ExecutionWrapperConfig> mainSteps =
        newArrayList(ExecutionWrapperConfig.builder().step(getV1StepJsonNode()).build());
    List<ExecutionWrapperConfig> rollbackSteps =
        newArrayList(ExecutionWrapperConfig.builder().step(getV1StepJsonNode()).build());
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(
                ExecutionElementConfig.builder().steps(mainSteps).rollbackSteps(rollbackSteps).build())
            .build();
    Ambiance ambiance = buildV1Ambiance();
    when(featureFlagService.isEnabled(eq(FeatureName.PIPE_OPTIONAL_DOCKER_WITH_RUNNER_V1_STAGES), any()))
        .thenReturn(true);
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any()))
          .thenReturn(mainStepNodeV1)
          .thenReturn(stepNodeV1);
      boolean shouldEnableDockerSetup = dockerInitializeStepUtils.shouldEnableDockerSetupOnUnifiedRunner(
          initializeStepInfo, ambiance, ACCOUNT_ID, stageInfraDetails, infrastructure);
      assertThat(shouldEnableDockerSetup).isTrue();
    }
  }

  private static Ambiance buildV1Ambiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", ACCOUNT_ID)
        .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
        .build();
  }

  private static JsonNode getV1StepJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepNode = mapper.createObjectNode();
    stepNode.put("id", "v1-step");
    return stepNode;
  }

  private static JsonNode getRunStepWithImageJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", "step1");
    stepElementConfig.put("name", "step1");
    stepElementConfig.put("type", "Run");
    ObjectNode stepSpec = mapper.createObjectNode();
    stepSpec.put("identifier", "step1");
    stepSpec.put("name", "step1");
    stepSpec.put("command", "command");
    stepSpec.put("image", "image");
    stepSpec.put("connectorRef", "connector");
    stepElementConfig.set("spec", stepSpec);
    return stepElementConfig;
  }

  private static JsonNode getRunStepWithoutImageJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", "step1");
    stepElementConfig.put("name", "step1");
    stepElementConfig.put("type", "Run");
    ObjectNode stepSpec = mapper.createObjectNode();
    stepSpec.put("identifier", "step1");
    stepSpec.put("name", "step1");
    stepSpec.put("command", "command");
    stepSpec.put("connectorRef", "connector");
    stepElementConfig.set("spec", stepSpec);
    return stepElementConfig;
  }

  private static JsonNode getImplicitGitCloneJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", GIT_CLONE_STEP_ID);
    stepElementConfig.put("name", "step1");
    stepElementConfig.put("type", "Plugin");
    ObjectNode stepSpec = mapper.createObjectNode();
    stepSpec.put("identifier", "step1");
    stepSpec.put("name", "step1");
    stepSpec.put("command", "command");
    stepSpec.put("connectorRef", "connector");
    stepSpec.put("harnessManagedImage", true);
    stepElementConfig.set("spec", stepSpec);
    return stepElementConfig;
  }

  private static JsonNode getExplicitGitCloneJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", "ExplicitClone");
    stepElementConfig.put("name", "step1");
    stepElementConfig.put("type", "GitClone");
    ObjectNode stepSpec = mapper.createObjectNode();
    stepSpec.put("identifier", "step1");
    stepSpec.put("name", "step1");
    stepSpec.put("command", "command");
    stepSpec.put("connectorRef", "connector");
    stepElementConfig.set("spec", stepSpec);
    return stepElementConfig;
  }
}
