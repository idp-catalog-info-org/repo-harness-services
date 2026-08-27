/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.DHIRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.utils.ci.CIStepInfoUtils;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InitializeStepGeneratorTest extends CIExecutionTestBase {
  @Inject InitializeStepGenerator initializeStepGenerator;
  @Inject CIExecutionPlanTestHelper ciExecutionPlanTestHelper;
  @Inject CIFeatureFlagService featureFlagService;

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldCreateLiteEngineTaskStepInfoFirstPod() {
    // input
    ExecutionElementConfig executionElementConfig = ciExecutionPlanTestHelper.getExecutionElementConfig();
    IntegrationStageNode stageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    Infrastructure infrastructure = ciExecutionPlanTestHelper.getInfrastructureWithVolume();
    String podName = "pod";
    Integer liteEngineCounter = 1;

    CIExecutionArgs ciExecutionArgs = ciExecutionPlanTestHelper.getCIExecutionArgs();
    InitializeStepInfo actual = initializeStepGenerator.createInitializeStepInfo(executionElementConfig,
        ciExecutionPlanTestHelper.getCICodebase(), stageNode, ciExecutionArgs, infrastructure, "abc");

    InitializeStepInfo expected = ciExecutionPlanTestHelper.getExpectedLiteEngineTaskInfoOnFirstPod(
        ciExecutionArgs.getExecutionSource(), ciExecutionPlanTestHelper.getIntegrationStageElementConfig());

    // The always-on stage-execution strip hands the init step a stage-config copy whose execution has no
    // steps; exclude stageElementConfig from the deep-equality (as executionElementConfig is below) and
    // assert the emptied-copy behavior separately.
    IntegrationStageConfig actualStageConfig = actual.getStageElementConfig();
    actual.setStageElementConfig(null);
    expected.setStageElementConfig(null);
    ExecutionElementConfig actualExecutionElementConfig = actual.getExecutionElementConfig();
    actual.setExecutionElementConfig(null);
    actual.setStrategyExpansionMap(new HashMap<>());
    expected.setExecutionElementConfig(null);
    assertThat(actual).isEqualTo(expected);
    assertThat(actualExecutionElementConfig.getSteps().size()).isEqualTo(3);
    // stage-execution duplicate emptied, non-execution fields (sharedPaths) preserved by toBuilder()
    assertThat(actualStageConfig.getExecution().getSteps()).isEmpty();
    assertThat(actualStageConfig.getSharedPaths().getValue()).contains("share/");
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldCreateLiteEngineTaskStepInfoOtherPod() {
    // input
    ExecutionElementConfig executionElementConfig = ciExecutionPlanTestHelper.getExecutionElementConfig();
    IntegrationStageNode stageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    Infrastructure infrastructure = ciExecutionPlanTestHelper.getInfrastructureWithVolume();

    CIExecutionArgs ciExecutionArgs = ciExecutionPlanTestHelper.getCIExecutionArgs();
    InitializeStepInfo actual = initializeStepGenerator.createInitializeStepInfo(
        executionElementConfig, null, stageNode, ciExecutionArgs, infrastructure, "ABX");

    InitializeStepInfo expected = ciExecutionPlanTestHelper.getExpectedLiteEngineTaskInfoOnOtherPods(
        ciExecutionArgs.getExecutionSource(), stageNode);

    // The always-on stage-execution strip hands the init step a stage-config copy whose execution has no
    // steps; exclude stageElementConfig from the deep-equality (as executionElementConfig is below) and
    // assert the emptied-copy behavior separately.
    IntegrationStageConfig actualStageConfig = actual.getStageElementConfig();
    actual.setStageElementConfig(null);
    expected.setStageElementConfig(null);
    ExecutionElementConfig actualExecutionElementConfig = actual.getExecutionElementConfig();
    actual.setExecutionElementConfig(null);
    actual.setStrategyExpansionMap(new HashMap<>());
    expected.setExecutionElementConfig(null);
    assertThat(actual).isEqualTo(expected);
    assertThat(actualExecutionElementConfig.getSteps().size()).isEqualTo(3);
    // stage-execution duplicate emptied, non-execution fields (sharedPaths) preserved by toBuilder()
    assertThat(actualStageConfig.getExecution().getSteps()).isEmpty();
    assertThat(actualStageConfig.getSharedPaths().getValue()).contains("share/");
  }

  /**
   * Execution config has 3 wrappers: [git-clone step, parallel(Run + Plugin), RunTests step].
   * For K8s, {@code command} must be stripped only from Run step specs — the Run step inside
   * the parallel group is stripped, but the RunTests step and Plugin step are left unchanged.
   */
  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void shouldStripCommandFromRunStepsOnlyForK8sInit() {
    ExecutionElementConfig executionElementConfig = ciExecutionPlanTestHelper.getExecutionElementConfig();

    ExecutionElementConfig stripped = initializeStepGenerator.stripStepCommandsForK8s(executionElementConfig);

    assertThat(stripped.getSteps()).hasSize(3);

    // --- parallel wrapper (index 1) ---
    JsonNode parallelArray = stripped.getSteps().get(1).getParallel();
    assertThat(parallelArray).isNotNull();
    assertThat(parallelArray.isArray()).isTrue();

    // Run step (index 0 inside parallel): command must be absent, image must still be present
    JsonNode runStepSpec = parallelArray.get(0).get("step").get("spec");
    assertThat(runStepSpec.has("command")).isFalse();
    assertThat(runStepSpec.has("image")).isTrue();

    // Plugin step (index 1 inside parallel): no command field, image must still be present
    JsonNode pluginStepSpec = parallelArray.get(1).get("step").get("spec");
    assertThat(pluginStepSpec.has("image")).isTrue();

    // --- RunTests step (index 2) command must NOT be stripped (only Run steps are affected) ---
    JsonNode runTestsStepSpec = stripped.getSteps().get(2).getStep().get("spec");
    assertThat(runTestsStepSpec.has("command")).isTrue();

    // --- original config must be unmodified (deep-copy guarantee) ---
    JsonNode originalRunStepSpec =
        executionElementConfig.getSteps().get(1).getParallel().get(0).get("step").get("spec");
    assertThat(originalRunStepSpec.has("command")).isTrue();
  }

  /**
   * With the feature flag disabled (the default in tests via {@code CIFeatureFlagNoopServiceImpl}),
   * {@link InitializeStepGenerator#createInitializeStepInfo} must leave commands intact.
   */
  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void shouldPreserveCommandsInInitStepInfoWhenFfDisabled() {
    ExecutionElementConfig executionElementConfig = ciExecutionPlanTestHelper.getExecutionElementConfig();
    IntegrationStageNode stageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    Infrastructure k8sInfra = ciExecutionPlanTestHelper.getInfrastructureWithVolume();
    CIExecutionArgs ciExecutionArgs = ciExecutionPlanTestHelper.getCIExecutionArgs();

    InitializeStepInfo initInfo = initializeStepGenerator.createInitializeStepInfo(
        executionElementConfig, ciExecutionPlanTestHelper.getCICodebase(), stageNode, ciExecutionArgs, k8sInfra, "acc");

    // FF off: commands must be preserved in executionElementConfig
    JsonNode parallel = initInfo.getExecutionElementConfig().getSteps().get(1).getParallel();
    assertThat(parallel.get(0).get("step").get("spec").has("command")).isTrue();
    assertThat(initInfo.getExecutionElementConfig().getSteps().get(2).getStep().get("spec").has("command")).isTrue();
  }

  /**
   * Stage-variable opt-in (plan-creation half of the control): with the FF disabled (default noop in tests) but the
   * stage carrying CI_INIT_REQUIRED_FIELDS_ONLY=true, createInitializeStepInfo must apply the required-fields-only
   * strip (Run command removed from the init executionElementConfig) exactly as if the FF were on. The runtime half
   * (scriptSecretsRuntime) is covered by CIInitStripStageVarHelperTest and reads the same stage variables carried
   * into K8StageInfraDetails, so the two halves cannot diverge.
   */
  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldApplyRequiredFieldsStripWhenStageVarEnabledAndFfDisabled() {
    ExecutionElementConfig executionElementConfig = ciExecutionPlanTestHelper.getExecutionElementConfig();
    IntegrationStageNode stageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    stageNode.setVariables(List.of(StringNGVariable.builder()
                                       .name(CIStepInfoUtils.CI_INIT_REQUIRED_FIELDS_ONLY)
                                       .type(NGVariableType.STRING)
                                       .value(ParameterField.createValueField("true"))
                                       .build()));
    Infrastructure k8sInfra = ciExecutionPlanTestHelper.getInfrastructureWithVolume();
    CIExecutionArgs ciExecutionArgs = ciExecutionPlanTestHelper.getCIExecutionArgs();

    InitializeStepInfo initInfo = initializeStepGenerator.createInitializeStepInfo(
        executionElementConfig, ciExecutionPlanTestHelper.getCICodebase(), stageNode, ciExecutionArgs, k8sInfra, "acc");

    // Stage var on (FF off): required-fields strip runs, so the Run step command is removed from the init copy.
    JsonNode parallel = initInfo.getExecutionElementConfig().getSteps().get(1).getParallel();
    assertThat(parallel.get(0).get("step").get("spec").has("command")).isFalse();
    // The shared source graph is untouched (deep-copy guarantee).
    assertThat(executionElementConfig.getSteps().get(1).getParallel().get(0).get("step").get("spec").has("command"))
        .isTrue();
  }

  /**
   * Verifies that {@link InitializeStepGenerator#stripStepCommandsForK8s} (the method enabled by
   * the {@code CI_REMOVE_COMMAND_INIT_PARAMS} feature flag) strips commands from both
   * {@code executionElementConfig} and after direct mutation of {@code stageElementConfig.execution}.
   */
  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void shouldStripCommandsFromBothConfigsWhenFfEnabled() {
    ExecutionElementConfig executionElementConfig = ciExecutionPlanTestHelper.getExecutionElementConfig();
    IntegrationStageNode stageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    Infrastructure k8sInfra = ciExecutionPlanTestHelper.getInfrastructureWithVolume();
    CIExecutionArgs ciExecutionArgs = ciExecutionPlanTestHelper.getCIExecutionArgs();

    InitializeStepInfo initInfo = initializeStepGenerator.createInitializeStepInfo(
        executionElementConfig, ciExecutionPlanTestHelper.getCICodebase(), stageNode, ciExecutionArgs, k8sInfra, "acc");

    // The always-on stage-execution strip now empties initInfo.getStageElementConfig().getExecution() by
    // default (independent of any FF), so it no longer carries steps to strip.
    assertThat(initInfo.getStageElementConfig().getExecution().getSteps()).isEmpty();

    // Simulate FF-enabled path: strip manually (same code path that runs when FF is on). The stage-execution
    // duplicate is emptied by default, so feed a fresh full execution graph to verify stripStepCommandsForK8s
    // still strips the stage-execution shape.
    ExecutionElementConfig strippedExecConfig =
        initializeStepGenerator.stripStepCommandsForK8s(initInfo.getExecutionElementConfig());
    ExecutionElementConfig strippedStageExec =
        initializeStepGenerator.stripStepCommandsForK8s(ciExecutionPlanTestHelper.getExecutionElementConfig());

    // executionElementConfig: Run step inside the parallel has command stripped, image preserved
    JsonNode embeddedParallel = strippedExecConfig.getSteps().get(1).getParallel();
    JsonNode runStepSpec = embeddedParallel.get(0).get("step").get("spec");
    assertThat(runStepSpec.has("command")).isFalse();
    assertThat(runStepSpec.has("image")).isTrue();
    // RunTests step command is preserved (only Run steps are stripped)
    assertThat(strippedExecConfig.getSteps().get(2).getStep().get("spec").has("command")).isTrue();

    // stageElementConfig.execution: same stripping applies to Run steps
    JsonNode stageParallel = strippedStageExec.getSteps().get(1).getParallel();
    assertThat(stageParallel.get(0).get("step").get("spec").has("command")).isFalse();

    // Originals are unmodified
    assertThat(executionElementConfig.getSteps().get(1).getParallel().get(0).get("step").get("spec").has("command"))
        .isTrue();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void shouldStripInitFieldsV2AcrossNestedWrappersWithoutMutatingOriginal() {
    ExecutionElementConfig original = createNestedExecutionElementConfig();

    ExecutionElementConfig stripped = initializeStepGenerator.stripInitExecutionConfigV2(original);

    // Top-level Run step: command/envVariables stripped; image retained.
    JsonNode runSpec = stripped.getSteps().get(0).getStep().get("spec");
    assertThat(runSpec.has("command")).isFalse();
    assertThat(runSpec.has("envVariables")).isFalse();
    assertThat(runSpec.get("image").asText()).isEqualTo("alpine");

    // Parallel RunTests + Plugin steps.
    JsonNode parallel = stripped.getSteps().get(1).getParallel();
    JsonNode runTestsSpec = parallel.get(0).get("step").get("spec");
    assertThat(runTestsSpec.has("args")).isFalse();
    assertThat(runTestsSpec.has("preCommand")).isFalse();
    assertThat(runTestsSpec.has("postCommand")).isFalse();
    assertThat(runTestsSpec.has("testGlobs")).isFalse();
    assertThat(runTestsSpec.has("envVariables")).isFalse();
    assertThat(runTestsSpec.get("image").asText()).isEqualTo("harness/runtests");

    JsonNode pluginSpec = parallel.get(1).get("step").get("spec");
    assertThat(pluginSpec.has("envVariables")).isFalse();
    assertThat(pluginSpec.has("settings")).isTrue();
    assertThat(pluginSpec.get("settings").get("PLUGIN_TAG").asText()).isEqualTo("latest");

    // StepGroup Test + custom unsupported step.
    JsonNode stepGroupSteps = stripped.getSteps().get(2).getStepGroup().get("steps");
    JsonNode testV2Spec = stepGroupSteps.get(0).get("step").get("spec");
    assertThat(testV2Spec.has("command")).isFalse();
    assertThat(testV2Spec.has("globs")).isFalse();
    assertThat(testV2Spec.has("envVariables")).isFalse();
    assertThat(testV2Spec.get("image").asText()).isEqualTo("harness/runtestsv2");

    JsonNode customSpec = stepGroupSteps.get(1).get("step").get("spec");
    assertThat(customSpec.has("command")).isTrue();
    assertThat(customSpec.has("envVariables")).isTrue();

    // Ensure source object is untouched (deep-copy behavior).
    JsonNode originalRunSpec = original.getSteps().get(0).getStep().get("spec");
    assertThat(originalRunSpec.has("command")).isTrue();
    assertThat(originalRunSpec.has("envVariables")).isTrue();
    assertThat(original.getSteps().get(1).getParallel().get(0).get("step").get("spec").has("args")).isTrue();
    assertThat(original.getSteps().get(2).getStepGroup().get("steps").get(0).get("step").get("spec").has("globs"))
        .isTrue();
  }

  /**
   * when is trimmed from the V0 init payload (step, step group, and nested steps). It is safe because
   * the init step never reads when for V0 container creation (that skip is V1-only), and the engine
   * evaluates when from the step/stepGroup plan node (built from the original YAML), not this payload.
   * The original graph keeps every when condition.
   */
  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldStripWhenFromStepsAndStepGroupsButKeepInOriginal() {
    ObjectNode runStep = createStepNode("Run", createSpecNode("command", "echo hi", "image", "alpine"));
    runStep.set("when", createWhenNode("<+pipeline.variables.runIt>"));

    ObjectNode innerStep = createStepNode("Run", createSpecNode("command", "echo inner", "image", "alpine"));
    innerStep.set("when", createWhenNode("<+stage.variables.inner>"));
    ObjectNode innerWrapper = JsonNodeFactory.instance.objectNode();
    innerWrapper.set("step", innerStep);
    ArrayNode groupSteps = JsonNodeFactory.instance.arrayNode();
    groupSteps.add(innerWrapper);

    ObjectNode stepGroup = JsonNodeFactory.instance.objectNode();
    stepGroup.put("identifier", "grp");
    stepGroup.set("when", createWhenNode("<+pipeline.variables.grpIt>"));
    stepGroup.set("steps", groupSteps);

    ExecutionElementConfig original = ExecutionElementConfig.builder()
                                          .uuid("exec-when")
                                          .version("0")
                                          .steps(List.of(ExecutionWrapperConfig.builder().step(runStep).build(),
                                              ExecutionWrapperConfig.builder().stepGroup(stepGroup).build()))
                                          .build();

    ExecutionElementConfig stripped = initializeStepGenerator.stripInitExecutionConfigV2(original);

    // Copy: when removed at top-level step, step group, and nested step; command still stripped.
    JsonNode strippedRun = stripped.getSteps().get(0).getStep();
    assertThat(strippedRun.has("when")).isFalse();
    assertThat(strippedRun.get("spec").has("command")).isFalse();
    JsonNode strippedGroup = stripped.getSteps().get(1).getStepGroup();
    assertThat(strippedGroup.has("when")).isFalse();
    assertThat(strippedGroup.get("steps").get(0).get("step").has("when")).isFalse();

    // Original untouched: all when conditions intact (step/stepGroup plan nodes rely on these).
    assertThat(original.getSteps().get(0).getStep().has("when")).isTrue();
    assertThat(original.getSteps().get(1).getStepGroup().has("when")).isTrue();
    assertThat(original.getSteps().get(1).getStepGroup().get("steps").get(0).get("step").has("when")).isTrue();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldStripWhenEvenForStepTypesWithoutStrippableSpecFields() {
    ObjectNode customStep = createStepNode("CustomUnknownType", createSpecNode("foo", "bar"));
    customStep.set("when", createWhenNode("<+pipeline.variables.x>"));
    ExecutionElementConfig original = ExecutionElementConfig.builder()
                                          .uuid("exec-when-2")
                                          .version("0")
                                          .steps(List.of(ExecutionWrapperConfig.builder().step(customStep).build()))
                                          .build();

    ExecutionElementConfig stripped = initializeStepGenerator.stripInitExecutionConfigV2(original);

    JsonNode strippedStep = stripped.getSteps().get(0).getStep();
    assertThat(strippedStep.has("when")).isFalse();
    // Non-strippable spec field for an unknown type is left intact.
    assertThat(strippedStep.get("spec").get("foo").asText()).isEqualTo("bar");
    assertThat(original.getSteps().get(0).getStep().has("when")).isTrue();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void shouldReturnInputAsIsForNullOrEmptyExecutionInV2() {
    assertThat(initializeStepGenerator.stripInitExecutionConfigV2(null)).isNull();

    ExecutionElementConfig emptyExecution =
        ExecutionElementConfig.builder().uuid("empty").version("v1").steps(List.of()).build();
    assertThat(initializeStepGenerator.stripInitExecutionConfigV2(emptyExecution)).isSameAs(emptyExecution);
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldApplyInitStripOnlyForV0Yaml() {
    ExecutionElementConfig v0Execution =
        ExecutionElementConfig.builder().uuid("v0").version("0").steps(List.of()).build();
    ExecutionElementConfig v1Execution =
        ExecutionElementConfig.builder().uuid("v1").version("1").steps(List.of()).build();
    ExecutionElementConfig nullVersionExecution =
        ExecutionElementConfig.builder().uuid("null-version").version(null).steps(List.of()).build();

    assertThat(initializeStepGenerator.shouldApplyV0InitStrip(v0Execution)).isTrue();
    assertThat(initializeStepGenerator.shouldApplyV0InitStrip(v1Execution)).isFalse();
    assertThat(initializeStepGenerator.shouldApplyV0InitStrip(nullVersionExecution)).isTrue();
    assertThat(initializeStepGenerator.shouldApplyV0InitStrip(null)).isFalse();
  }

  /**
   * The init-strip hands InitializeStepInfo a stage-config COPY whose execution has no steps (to
   * avoid resolving every step expression a second time), while the shared stage node config keeps
   * its full execution. uuid/version on the emptied execution must be preserved because
   * InitializeTaskStepV2#populateStrategyExpansion reads getExecution().getUuid() when it rebuilds
   * the steps from executionElementConfig before RBAC/connector-ref extraction.
   */
  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void buildStageConfigWithoutExecutionStepsEmptiesCopyAndLeavesSharedConfigIntact() {
    ExecutionElementConfig stageExecution =
        ExecutionElementConfig.builder()
            .uuid("stage-exec-uuid")
            .version("0")
            .steps(List.of(ExecutionWrapperConfig.builder()
                               .step(createStepNode("Run",
                                   createSpecNode("command", "echo hi", "image", "alpine", "connectorRef", "docker")))
                               .build()))
            .build();
    IntegrationStageConfigImpl shared =
        IntegrationStageConfigImpl.builder().uuid("stage-uuid").execution(stageExecution).build();

    IntegrationStageConfig initCopy = initializeStepGenerator.buildStageConfigWithoutExecutionSteps(shared);

    // Copy: distinct object, execution has no steps, uuid/version preserved.
    assertThat(initCopy).isNotSameAs(shared);
    assertThat(initCopy.getExecution().getSteps()).isEmpty();
    assertThat(initCopy.getExecution().getUuid()).isEqualTo("stage-exec-uuid");
    assertThat(initCopy.getExecution().getVersion()).isEqualTo("0");
    // Other stage fields carried over by the copy.
    assertThat(((IntegrationStageConfigImpl) initCopy).getUuid()).isEqualTo("stage-uuid");

    // Shared stage node config is untouched — full execution with connectorRef intact.
    assertThat(shared.getExecution()).isSameAs(stageExecution);
    assertThat(shared.getExecution().getSteps()).hasSize(1);
    assertThat(shared.getExecution().getSteps().get(0).getStep().get("spec").get("connectorRef").asText())
        .isEqualTo("docker");
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void buildStageConfigWithoutExecutionStepsFailsOpenWhenExecutionMissing() {
    IntegrationStageConfigImpl shared = IntegrationStageConfigImpl.builder().uuid("stage-uuid").execution(null).build();
    assertThat(initializeStepGenerator.buildStageConfigWithoutExecutionSteps(shared)).isSameAs(shared);
  }

  /**
   * V1 pipelines are not stripped: shouldApplyV0InitStrip is false, so the init step keeps the full stage
   * execution (the always-on strip is V0-only).
   */
  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void createInitializeStepInfoKeepsStageExecutionForV1() {
    ExecutionElementConfig v1Execution = ExecutionElementConfig.builder()
                                             .version("1")
                                             .steps(ciExecutionPlanTestHelper.getExecutionWrapperConfigList())
                                             .build();
    IntegrationStageNode stageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    Infrastructure k8sInfra = ciExecutionPlanTestHelper.getInfrastructureWithVolume();
    CIExecutionArgs ciExecutionArgs = ciExecutionPlanTestHelper.getCIExecutionArgs();

    InitializeStepInfo initInfo = initializeStepGenerator.createInitializeStepInfo(
        v1Execution, ciExecutionPlanTestHelper.getCICodebase(), stageNode, ciExecutionArgs, k8sInfra, "acc");

    // V1: stage execution retains its full steps (not stripped)
    assertThat(initInfo.getStageElementConfig().getExecution().getSteps()).hasSize(3);
  }

  /**
   * Reverse kill-switch: with CI_DISABLE_INIT_STAGE_EXECUTION_STRIP enabled, the init step keeps the full
   * stage execution (old behavior) even on a V0 stage.
   */
  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void createInitializeStepInfoKeepsStageExecutionWhenKillSwitchEnabled() {
    ExecutionElementConfig executionElementConfig = ciExecutionPlanTestHelper.getExecutionElementConfig();
    IntegrationStageNode stageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    Infrastructure k8sInfra = ciExecutionPlanTestHelper.getInfrastructureWithVolume();
    CIExecutionArgs ciExecutionArgs = ciExecutionPlanTestHelper.getCIExecutionArgs();

    CIFeatureFlagService killSwitchFf = mock(CIFeatureFlagService.class);
    when(killSwitchFf.isEnabled(eq(FeatureName.CI_DISABLE_INIT_STAGE_EXECUTION_STRIP), anyString())).thenReturn(true);
    CIFeatureFlagService originalFf = on(initializeStepGenerator).get("featureFlagService");
    on(initializeStepGenerator).set("featureFlagService", killSwitchFf);
    try {
      InitializeStepInfo initInfo = initializeStepGenerator.createInitializeStepInfo(executionElementConfig,
          ciExecutionPlanTestHelper.getCICodebase(), stageNode, ciExecutionArgs, k8sInfra, "acc");

      // kill-switch ON: stage execution is NOT stripped; full steps retained
      assertThat(initInfo.getStageElementConfig().getExecution().getSteps()).hasSize(3);
    } finally {
      on(initializeStepGenerator).set("featureFlagService", originalFf);
    }
  }

  private ExecutionElementConfig createNestedExecutionElementConfig() {
    ArrayNode parallelSteps = JsonNodeFactory.instance.arrayNode();
    parallelSteps.add(createStepWrapperNode("RunTests",
        createSpecNode("args", "mvn test", "preCommand", "echo pre", "postCommand", "echo post", "testGlobs",
            "**/*Test.java", "envVariables", createSimpleEnvVarsNode(), "image", "harness/runtests")));
    parallelSteps.add(createStepWrapperNode("Plugin",
        createSpecNode("settings", createPluginSettingsNode(), "envVariables", createSimpleEnvVarsNode(), "image",
            "plugins/docker")));

    ArrayNode stepGroupSteps = JsonNodeFactory.instance.arrayNode();
    stepGroupSteps.add(createStepWrapperNode("Test",
        createSpecNode("command", "go test ./...", "globs", createGlobsNode(), "envVariables",
            createSimpleEnvVarsNode(), "image", "harness/runtestsv2")));
    stepGroupSteps.add(createStepWrapperNode("CustomStep",
        createSpecNode(
            "command", "echo should-stay", "envVariables", createSimpleEnvVarsNode(), "image", "custom/image")));

    ObjectNode stepGroup = JsonNodeFactory.instance.objectNode();
    stepGroup.set("steps", stepGroupSteps);

    return ExecutionElementConfig.builder()
        .uuid("exec-1")
        .version("v1")
        .steps(List.of(ExecutionWrapperConfig.builder()
                           .step(createStepNode("Run",
                               createSpecNode("command", "echo run", "envVariables", createSimpleEnvVarsNode(), "image",
                                   "alpine")))
                           .build(),
            ExecutionWrapperConfig.builder().parallel(parallelSteps).build(),
            ExecutionWrapperConfig.builder().stepGroup(stepGroup).build()))
        .build();
  }

  private ObjectNode createStepWrapperNode(String type, ObjectNode specNode) {
    ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
    wrapper.set("step", createStepNode(type, specNode));
    return wrapper;
  }

  private ObjectNode createStepNode(String type, ObjectNode specNode) {
    ObjectNode stepNode = JsonNodeFactory.instance.objectNode();
    stepNode.put("type", type);
    stepNode.set("spec", specNode);
    return stepNode;
  }

  private ObjectNode createSimpleEnvVarsNode() {
    ObjectNode env = JsonNodeFactory.instance.objectNode();
    env.put("TOKEN", "<+secrets.getValue(\"token\")>");
    return env;
  }

  private ObjectNode createWhenNode(String condition) {
    ObjectNode when = JsonNodeFactory.instance.objectNode();
    when.put("stageStatus", "Success");
    when.put("condition", condition);
    return when;
  }

  private ObjectNode createPluginSettingsNode() {
    ObjectNode settings = JsonNodeFactory.instance.objectNode();
    settings.put("PLUGIN_TAG", "latest");
    return settings;
  }

  private ArrayNode createGlobsNode() {
    ArrayNode globs = JsonNodeFactory.instance.arrayNode();
    globs.add("**/*_test.go");
    return globs;
  }

  private ObjectNode createSpecNode(Object... keyValues) {
    ObjectNode spec = JsonNodeFactory.instance.objectNode();
    for (int i = 0; i < keyValues.length; i += 2) {
      String key = (String) keyValues[i];
      Object value = keyValues[i + 1];
      if (value instanceof JsonNode jsonNode) {
        spec.set(key, jsonNode);
      } else if (value instanceof String stringValue) {
        spec.put(key, stringValue);
      } else {
        throw new IllegalArgumentException("Unsupported value type for key " + key + ": " + value.getClass());
      }
    }
    return spec;
  }
}
