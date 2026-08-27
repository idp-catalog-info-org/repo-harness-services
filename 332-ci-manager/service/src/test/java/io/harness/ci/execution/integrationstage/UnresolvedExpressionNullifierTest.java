/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.rule.OwnerRule.GARGI;
import static io.harness.rule.OwnerRule.SATYAKOTA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class UnresolvedExpressionNullifierTest extends CIExecutionTestBase {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testProcessRunStep_NullifiesUnresolvedExpressions() {
    ObjectNode runNode = objectMapper.createObjectNode();
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("RESOLVED_VAR", "some_value");
    envNode.put("UNRESOLVED_VAR", "${{input.some_field}}");
    envNode.put("ANOTHER_UNRESOLVED", "<+artifact.tag>");
    runNode.set("env", envNode);

    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("run", runNode);

    ExecutionWrapperConfig wrapper = ExecutionWrapperConfig.builder().step(stepNode).build();
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(wrapper);

    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(steps).build();

    UnresolvedExpressionNullifier.processInitializeStepInfo(executionElementConfig, null);

    assertThat(envNode.get("RESOLVED_VAR").asText()).isEqualTo("some_value");
    assertThat(envNode.get("UNRESOLVED_VAR").isNull()).isTrue();
    assertThat(envNode.get("ANOTHER_UNRESOLVED").isNull()).isTrue();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testProcessBackgroundStep_NullifiesUnresolvedExpressions() {
    ObjectNode backgroundNode = objectMapper.createObjectNode();
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("RESOLVED_VAR", "some_value");
    envNode.put("UNRESOLVED_VAR", "${{input.some_field}}");
    envNode.put("SERVICE_EXPR", "<+service.name>");
    backgroundNode.set("env", envNode);

    ObjectNode withNode = objectMapper.createObjectNode();
    withNode.put("RESOLVED_WITH", "actual_value");
    withNode.put("UNRESOLVED_WITH", "${{manifest.values}}");
    backgroundNode.set("with", withNode);

    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("background", backgroundNode);

    ExecutionWrapperConfig wrapper = ExecutionWrapperConfig.builder().step(stepNode).build();
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(wrapper);

    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(steps).build();

    UnresolvedExpressionNullifier.processInitializeStepInfo(executionElementConfig, null);

    assertThat(envNode.get("RESOLVED_VAR").asText()).isEqualTo("some_value");
    assertThat(envNode.get("UNRESOLVED_VAR").isNull()).isTrue();
    assertThat(envNode.get("SERVICE_EXPR").isNull()).isTrue();
    assertThat(withNode.get("RESOLVED_WITH").asText()).isEqualTo("actual_value");
    assertThat(withNode.get("UNRESOLVED_WITH").isNull()).isTrue();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testProcessBackgroundStep_WithFieldNullification() {
    ObjectNode backgroundNode = objectMapper.createObjectNode();
    ObjectNode withNode = objectMapper.createObjectNode();
    withNode.put("RESOLVED", "real_value");
    withNode.put("CEL_EXPR", "${{cel.expression}}");
    withNode.put("INFRA_EXPR", "<+infra.namespace>");
    backgroundNode.set("with", withNode);

    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("background", backgroundNode);

    ExecutionWrapperConfig wrapper = ExecutionWrapperConfig.builder().step(stepNode).build();
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(wrapper);

    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(steps).build();

    UnresolvedExpressionNullifier.processInitializeStepInfo(executionElementConfig, null);

    assertThat(withNode.get("RESOLVED").asText()).isEqualTo("real_value");
    assertThat(withNode.get("CEL_EXPR").isNull()).isTrue();
    assertThat(withNode.get("INFRA_EXPR").isNull()).isTrue();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testProcessModuleImplicitSteps_BackgroundStep() {
    ObjectNode backgroundNode = objectMapper.createObjectNode();
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("VALID", "value");
    envNode.put("RUNTIME_EXPR", "${{runtime.input}}");
    backgroundNode.set("env", envNode);

    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("background", backgroundNode);

    ExecutionWrapperConfig wrapper = ExecutionWrapperConfig.builder().step(stepNode).build();
    List<ExecutionWrapperConfig> moduleImplicitSteps = new ArrayList<>();
    moduleImplicitSteps.add(wrapper);

    UnresolvedExpressionNullifier.processInitializeStepInfo(null, moduleImplicitSteps);

    assertThat(envNode.get("VALID").asText()).isEqualTo("value");
    assertThat(envNode.get("RUNTIME_EXPR").isNull()).isTrue();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testNonNullifiableExpression_NotModified() {
    ObjectNode runNode = objectMapper.createObjectNode();
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("SAFE_EXPR", "${{steps.build.output}}");
    envNode.put("PLAIN_VALUE", "hello");
    runNode.set("env", envNode);

    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("run", runNode);

    ExecutionWrapperConfig wrapper = ExecutionWrapperConfig.builder().step(stepNode).build();
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(wrapper);

    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(steps).build();

    UnresolvedExpressionNullifier.processInitializeStepInfo(executionElementConfig, null);

    assertThat(envNode.get("SAFE_EXPR").asText()).isEqualTo("${{steps.build.output}}");
    assertThat(envNode.get("PLAIN_VALUE").asText()).isEqualTo("hello");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testNullInputs_DoesNotThrow() {
    UnresolvedExpressionNullifier.processInitializeStepInfo(null, null);
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testMixedRunAndBackgroundSteps() {
    // Run step
    ObjectNode runNode = objectMapper.createObjectNode();
    ObjectNode runEnvNode = objectMapper.createObjectNode();
    runEnvNode.put("RUN_RESOLVED", "value1");
    runEnvNode.put("RUN_UNRESOLVED", "${{input.field}}");
    runNode.set("env", runEnvNode);
    ObjectNode runStepNode = objectMapper.createObjectNode();
    runStepNode.set("run", runNode);

    // Background step
    ObjectNode backgroundNode = objectMapper.createObjectNode();
    ObjectNode bgEnvNode = objectMapper.createObjectNode();
    bgEnvNode.put("BG_RESOLVED", "value2");
    bgEnvNode.put("BG_UNRESOLVED", "${{artifact.image}}");
    backgroundNode.set("env", bgEnvNode);
    ObjectNode bgStepNode = objectMapper.createObjectNode();
    bgStepNode.set("background", backgroundNode);

    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(runStepNode).build());
    steps.add(ExecutionWrapperConfig.builder().step(bgStepNode).build());

    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(steps).build();

    UnresolvedExpressionNullifier.processInitializeStepInfo(executionElementConfig, null);

    assertThat(runEnvNode.get("RUN_RESOLVED").asText()).isEqualTo("value1");
    assertThat(runEnvNode.get("RUN_UNRESOLVED").isNull()).isTrue();
    assertThat(bgEnvNode.get("BG_RESOLVED").asText()).isEqualTo("value2");
    assertThat(bgEnvNode.get("BG_UNRESOLVED").isNull()).isTrue();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testRollbackSteps_NullifiesUnresolvedExpressions() {
    ObjectNode runNode = objectMapper.createObjectNode();
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("RESOLVED", "value");
    envNode.put("UNRESOLVED", "${{input.rollback}}");
    runNode.set("env", envNode);

    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("run", runNode);

    ExecutionWrapperConfig wrapper = ExecutionWrapperConfig.builder().step(stepNode).build();
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(wrapper);

    ExecutionElementConfig executionElementConfig =
        ExecutionElementConfig.builder().rollbackSteps(rollbackSteps).build();

    UnresolvedExpressionNullifier.processInitializeStepInfo(executionElementConfig, null);

    assertThat(envNode.get("RESOLVED").asText()).isEqualTo("value");
    assertThat(envNode.get("UNRESOLVED").isNull()).isTrue();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testProcessParallelSteps_NullifiesUnresolvedExpressions() {
    // Create a step with unresolved expression inside parallel
    ObjectNode runNode = objectMapper.createObjectNode();
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("RESOLVED", "value");
    envNode.put("UNRESOLVED", "${{input.parallel}}");
    runNode.set("env", envNode);

    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("run", runNode);

    ObjectNode sectionNode = objectMapper.createObjectNode();
    sectionNode.set("step", stepNode);

    com.fasterxml.jackson.databind.node.ArrayNode sectionsArray = objectMapper.createArrayNode();
    sectionsArray.add(sectionNode);

    ObjectNode parallelNode = objectMapper.createObjectNode();
    parallelNode.set("sections", sectionsArray);

    ExecutionWrapperConfig wrapper = ExecutionWrapperConfig.builder().parallel(parallelNode).build();
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(wrapper);

    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(steps).build();

    UnresolvedExpressionNullifier.processInitializeStepInfo(executionElementConfig, null);

    assertThat(envNode.get("RESOLVED").asText()).isEqualTo("value");
    assertThat(envNode.get("UNRESOLVED").isNull()).isTrue();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testProcessStepGroup_NullifiesUnresolvedExpressions() {
    // Create a step with unresolved expression inside step group
    ObjectNode backgroundNode = objectMapper.createObjectNode();
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("RESOLVED", "value");
    envNode.put("UNRESOLVED", "${{service.id}}");
    backgroundNode.set("env", envNode);

    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("background", backgroundNode);

    ObjectNode innerStepNode = objectMapper.createObjectNode();
    innerStepNode.set("step", stepNode);

    com.fasterxml.jackson.databind.node.ArrayNode stepsArray = objectMapper.createArrayNode();
    stepsArray.add(innerStepNode);

    ObjectNode stepGroupNode = objectMapper.createObjectNode();
    stepGroupNode.set("steps", stepsArray);

    ExecutionWrapperConfig wrapper = ExecutionWrapperConfig.builder().stepGroup(stepGroupNode).build();
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(wrapper);

    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(steps).build();

    UnresolvedExpressionNullifier.processInitializeStepInfo(executionElementConfig, null);

    assertThat(envNode.get("RESOLVED").asText()).isEqualTo("value");
    assertThat(envNode.get("UNRESOLVED").isNull()).isTrue();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testDepthLimit_StopsProcessingAtDepth3() {
    // Create nested structure: parallel > stepGroup > parallel > stepGroup > step (depth 5)
    // Depth counting: parallel(0) -> stepGroup(1) -> parallel(2) -> stepGroup(3) -> step(4)
    // Since depth > 3 stops processing, depth 4 should not be processed
    ObjectNode runNode = objectMapper.createObjectNode();
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("SHOULD_NOT_BE_NULLIFIED", "${{input.deep}}");
    runNode.set("env", envNode);

    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("run", runNode);

    ObjectNode innerMostStepWrapper = objectMapper.createObjectNode();
    innerMostStepWrapper.set("step", stepNode);

    com.fasterxml.jackson.databind.node.ArrayNode innerMostStepsArray = objectMapper.createArrayNode();
    innerMostStepsArray.add(innerMostStepWrapper);

    ObjectNode innerMostStepGroupNode = objectMapper.createObjectNode();
    innerMostStepGroupNode.set("steps", innerMostStepsArray);

    ObjectNode innerMostStepGroupWrapper = objectMapper.createObjectNode();
    innerMostStepGroupWrapper.set("stepGroup", innerMostStepGroupNode);

    com.fasterxml.jackson.databind.node.ArrayNode innerParallelSectionsArray = objectMapper.createArrayNode();
    innerParallelSectionsArray.add(innerMostStepGroupWrapper);

    ObjectNode innerParallelNode = objectMapper.createObjectNode();
    innerParallelNode.set("sections", innerParallelSectionsArray);

    ObjectNode innerParallelWrapper = objectMapper.createObjectNode();
    innerParallelWrapper.set("parallel", innerParallelNode);

    com.fasterxml.jackson.databind.node.ArrayNode stepsArray = objectMapper.createArrayNode();
    stepsArray.add(innerParallelWrapper);

    ObjectNode stepGroupNode = objectMapper.createObjectNode();
    stepGroupNode.set("steps", stepsArray);

    ObjectNode stepGroupWrapper = objectMapper.createObjectNode();
    stepGroupWrapper.set("stepGroup", stepGroupNode);

    com.fasterxml.jackson.databind.node.ArrayNode outerSectionsArray = objectMapper.createArrayNode();
    outerSectionsArray.add(stepGroupWrapper);

    ObjectNode outerParallelNode = objectMapper.createObjectNode();
    outerParallelNode.set("sections", outerSectionsArray);

    ExecutionWrapperConfig wrapper = ExecutionWrapperConfig.builder().parallel(outerParallelNode).build();
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(wrapper);

    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(steps).build();

    UnresolvedExpressionNullifier.processInitializeStepInfo(executionElementConfig, null);

    // At depth 4+, the expression should NOT be nullified due to depth limit
    assertThat(envNode.get("SHOULD_NOT_BE_NULLIFIED").asText()).isEqualTo("${{input.deep}}");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testExpressionWithoutClosingBracket_NotNullified() {
    ObjectNode runNode = objectMapper.createObjectNode();
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("MALFORMED", "<+artifact");
    runNode.set("env", envNode);

    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("run", runNode);

    ExecutionWrapperConfig wrapper = ExecutionWrapperConfig.builder().step(stepNode).build();
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(wrapper);

    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(steps).build();

    UnresolvedExpressionNullifier.processInitializeStepInfo(executionElementConfig, null);

    // Expression without closing '>' will extract "artifact" and should be nullified
    assertThat(envNode.get("MALFORMED").isNull()).isTrue();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testExpressionWithoutDot_NullifiesIfInSet() {
    ObjectNode runNode = objectMapper.createObjectNode();
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("NO_DOT_NULLIFIABLE", "${{input}}");
    envNode.put("NO_DOT_SAFE", "${{steps}}");
    runNode.set("env", envNode);

    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("run", runNode);

    ExecutionWrapperConfig wrapper = ExecutionWrapperConfig.builder().step(stepNode).build();
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(wrapper);

    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(steps).build();

    UnresolvedExpressionNullifier.processInitializeStepInfo(executionElementConfig, null);

    assertThat(envNode.get("NO_DOT_NULLIFIABLE").isNull()).isTrue();
    assertThat(envNode.get("NO_DOT_SAFE").asText()).isEqualTo("${{steps}}");
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testV1ParallelBareArrayOfTemplateSteps_NullifiesUnresolvedExpressions() {
    ObjectNode envNode = objectMapper.createObjectNode();
    ExecutionWrapperConfig wrapper = buildV1ParallelOfTemplateStepsWrapper(envNode);
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(wrapper);
    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(steps).build();

    UnresolvedExpressionNullifier.processInitializeStepInfo(executionElementConfig, null);

    assertThat(envNode.get("RESOLVED").asText()).isEqualTo("value");
    assertThat(envNode.get("UNRESOLVED").isNull()).isTrue();
  }

  // PIPE-35776: V1 parallel is serialized as a bare JSON array of wrappers, and the
  // template-based-step shape places a stepGroup at each parallel slot.
  // Shape: parallel(array) -> stepGroup -> step -> run.env with ${{inputs.*}}
  private ExecutionWrapperConfig buildV1ParallelOfTemplateStepsWrapper(ObjectNode envNodeOut) {
    ObjectNode runNode = objectMapper.createObjectNode();
    envNodeOut.put("RESOLVED", "value");
    envNodeOut.put("UNRESOLVED", "${{inputs.build.tag}}");
    runNode.set("env", envNodeOut);

    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("run", runNode);

    ObjectNode innerStepWrapper = objectMapper.createObjectNode();
    innerStepWrapper.set("step", stepNode);

    com.fasterxml.jackson.databind.node.ArrayNode stepsArray = objectMapper.createArrayNode();
    stepsArray.add(innerStepWrapper);

    ObjectNode stepGroupNode = objectMapper.createObjectNode();
    stepGroupNode.set("steps", stepsArray);

    ObjectNode stepGroupWrapper = objectMapper.createObjectNode();
    stepGroupWrapper.set("stepGroup", stepGroupNode);

    com.fasterxml.jackson.databind.node.ArrayNode parallelArray = objectMapper.createArrayNode();
    parallelArray.add(stepGroupWrapper);

    return ExecutionWrapperConfig.builder().parallel(parallelArray).build();
  }
}
