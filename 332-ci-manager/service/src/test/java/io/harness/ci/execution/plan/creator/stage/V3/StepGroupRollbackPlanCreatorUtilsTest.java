/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.stage.V3;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.fork.NGForkStep;
import io.harness.steps.rollback.RollbackNode;
import io.harness.steps.rollback.RollbackOptionalChildChainStepParameters;
import io.harness.steps.rollback.RollbackStepsStep;
import io.harness.steps.rollback.StepGroupRollbackChainStep;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StepGroupRollbackPlanCreatorUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCollectAllStepGroupRollbackGroups_whenNullField_shouldReturnEmpty() {
    List<List<RollbackNode>> result = StepGroupRollbackPlanCreatorUtils.collectAllStepGroupRollbackGroups(null);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCollectAllStepGroupRollbackGroups_whenEmptySteps_shouldReturnEmpty() throws Exception {
    String yaml = "steps: []\n";
    YamlField stepsField = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("steps");

    List<List<RollbackNode>> result = StepGroupRollbackPlanCreatorUtils.collectAllStepGroupRollbackGroups(stepsField);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCollectAllStepGroupRollbackGroups_whenNoGroupsOnlySteps_shouldReturnEmpty() throws Exception {
    String yaml = "steps:\n"
        + "  - step:\n"
        + "      name: run1\n"
        + "      type: Run\n"
        + "      spec:\n"
        + "        command: echo hello\n";
    YamlField stepsField = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("steps");

    List<List<RollbackNode>> result = StepGroupRollbackPlanCreatorUtils.collectAllStepGroupRollbackGroups(stepsField);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCollectAllStepGroupRollbackGroups_whenGroupWithoutId_shouldSkip() throws Exception {
    String yaml = "steps:\n"
        + "  - group:\n"
        + "      name: sg1\n"
        + "      steps:\n"
        + "        - step:\n"
        + "            name: run1\n"
        + "            type: Run\n"
        + "            spec:\n"
        + "              command: echo hello\n"
        + "      rollback:\n"
        + "        - step:\n"
        + "            name: rb1\n"
        + "            type: Run\n"
        + "            spec:\n"
        + "              command: echo rollback\n";
    YamlField stepsField = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("steps");

    List<List<RollbackNode>> result = StepGroupRollbackPlanCreatorUtils.collectAllStepGroupRollbackGroups(stepsField);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCollectAllStepGroupRollbackGroups_whenGroupWithRollback_shouldReturnSingletonList() throws Exception {
    String yaml = "steps:\n"
        + "  - id: sg1\n"
        + "    group:\n"
        + "      name: sg1\n"
        + "      steps:\n"
        + "        - step:\n"
        + "            name: run1\n"
        + "            type: Run\n"
        + "            spec:\n"
        + "              command: echo hello\n"
        + "      rollback:\n"
        + "        - step:\n"
        + "            name: rb1\n"
        + "            type: Run\n"
        + "            spec:\n"
        + "              command: echo rollback\n";
    YamlField stepsField = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("steps");

    List<List<RollbackNode>> result = StepGroupRollbackPlanCreatorUtils.collectAllStepGroupRollbackGroups(stepsField);

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).hasSize(1);
    RollbackNode node = result.get(0).get(0);
    assertThat(node.getNodeId()).endsWith(NGCommonUtilPlanCreationConstants.ROLLBACK_STEPS_NODE_ID_SUFFIX);
    assertThat(node.getDependentNodeIdentifier()).isEqualTo("stage.steps.sg1");
    assertThat(node.isInsideStrategyAncestor()).isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCollectAllStepGroupRollbackGroups_whenGroupWithStrategyAndRollback_shouldUseStrategySuffix()
      throws Exception {
    String yaml = "steps:\n"
        + "  - id: sg1\n"
        + "    strategy:\n"
        + "      repeat:\n"
        + "        count: 2\n"
        + "    group:\n"
        + "      name: sg1\n"
        + "      steps:\n"
        + "        - step:\n"
        + "            name: run1\n"
        + "            type: Run\n"
        + "            spec:\n"
        + "              command: echo hello\n"
        + "      rollback:\n"
        + "        - step:\n"
        + "            name: rb1\n"
        + "            type: Run\n"
        + "            spec:\n"
        + "              command: echo rollback\n";
    YamlField stepsField = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("steps");

    List<List<RollbackNode>> result = StepGroupRollbackPlanCreatorUtils.collectAllStepGroupRollbackGroups(stepsField);

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).hasSize(1);
    RollbackNode node = result.get(0).get(0);
    assertThat(node.getNodeId()).endsWith(NGCommonUtilPlanCreationConstants.STRATEGY_ROLLBACK_NODE_ID_SUFFIX);
    assertThat(node.getDependentNodeIdentifier()).isEqualTo("stage.steps.sg1");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCollectAllStepGroupRollbackGroups_whenMultipleGroups_shouldReverseOrder() throws Exception {
    String yaml = "steps:\n"
        + "  - id: sg1\n"
        + "    group:\n"
        + "      name: sg1\n"
        + "      steps:\n"
        + "        - step:\n"
        + "            name: run1\n"
        + "            type: Run\n"
        + "            spec:\n"
        + "              command: echo hello\n"
        + "      rollback:\n"
        + "        - step:\n"
        + "            name: rb1\n"
        + "            type: Run\n"
        + "            spec:\n"
        + "              command: echo rollback1\n"
        + "  - id: sg2\n"
        + "    group:\n"
        + "      name: sg2\n"
        + "      steps:\n"
        + "        - step:\n"
        + "            name: run2\n"
        + "            type: Run\n"
        + "            spec:\n"
        + "              command: echo hello2\n"
        + "      rollback:\n"
        + "        - step:\n"
        + "            name: rb2\n"
        + "            type: Run\n"
        + "            spec:\n"
        + "              command: echo rollback2\n";
    YamlField stepsField = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("steps");

    List<List<RollbackNode>> result = StepGroupRollbackPlanCreatorUtils.collectAllStepGroupRollbackGroups(stepsField);

    assertThat(result).hasSize(2);
    // Reversed order: sg2 first (last defined runs first during rollback)
    assertThat(result.get(0).get(0).getDependentNodeIdentifier()).isEqualTo("stage.steps.sg2");
    assertThat(result.get(1).get(0).getDependentNodeIdentifier()).isEqualTo("stage.steps.sg1");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCollectAllStepGroupRollbackGroups_whenParallelGroupsWithRollback_shouldReturnMultiElementList()
      throws Exception {
    String yaml = "steps:\n"
        + "  - parallel:\n"
        + "      steps:\n"
        + "        - id: sgA\n"
        + "          group:\n"
        + "            name: sgA\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: runA\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    command: echo A\n"
        + "            rollback:\n"
        + "              - step:\n"
        + "                  name: rbA\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    command: echo rollbackA\n"
        + "        - id: sgB\n"
        + "          group:\n"
        + "            name: sgB\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: runB\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    command: echo B\n"
        + "            rollback:\n"
        + "              - step:\n"
        + "                  name: rbB\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    command: echo rollbackB\n";
    YamlField stepsField = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("steps");

    List<List<RollbackNode>> result = StepGroupRollbackPlanCreatorUtils.collectAllStepGroupRollbackGroups(stepsField);

    assertThat(result).hasSize(1);
    // Both parallel groups in one list (size > 1 means parallel execution)
    assertThat(result.get(0)).hasSize(2);
    assertThat(result.get(0).get(0).getDependentNodeIdentifier()).isEqualTo("stage.steps.sgA");
    assertThat(result.get(0).get(1).getDependentNodeIdentifier()).isEqualTo("stage.steps.sgB");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCollectAllStepGroupRollbackGroups_whenNestedGroupWithoutRollback_shouldRecurseIntoChildren()
      throws Exception {
    String yaml = "steps:\n"
        + "  - id: outer\n"
        + "    group:\n"
        + "      name: outer\n"
        + "      steps:\n"
        + "        - id: inner\n"
        + "          group:\n"
        + "            name: inner\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: run1\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    command: echo hello\n"
        + "            rollback:\n"
        + "              - step:\n"
        + "                  name: rb1\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    command: echo rollback\n";
    YamlField stepsField = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("steps");

    List<List<RollbackNode>> result = StepGroupRollbackPlanCreatorUtils.collectAllStepGroupRollbackGroups(stepsField);

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).hasSize(1);
    // Dependent expression includes nested path
    assertThat(result.get(0).get(0).getDependentNodeIdentifier()).isEqualTo("stage.steps.outer.steps.inner");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCollectAllStepGroupRollbackGroups_whenAncestorHasStrategy_shouldSetInsideStrategyAncestor()
      throws Exception {
    // Outer has strategy but no rollback, inner has rollback
    String yaml = "steps:\n"
        + "  - id: outer\n"
        + "    strategy:\n"
        + "      repeat:\n"
        + "        count: 2\n"
        + "    group:\n"
        + "      name: outer\n"
        + "      steps:\n"
        + "        - id: inner\n"
        + "          group:\n"
        + "            name: inner\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: run1\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    command: echo hello\n"
        + "            rollback:\n"
        + "              - step:\n"
        + "                  name: rb1\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    command: echo rollback\n";
    YamlField stepsField = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("steps");

    List<List<RollbackNode>> result = StepGroupRollbackPlanCreatorUtils.collectAllStepGroupRollbackGroups(stepsField);

    // Outer has strategy + descendant rollbacks → synthetic strategy wrapper is created for outer
    assertThat(result).hasSize(1);
    RollbackNode node = result.get(0).get(0);
    assertThat(node.getNodeId()).endsWith(NGCommonUtilPlanCreationConstants.STRATEGY_ROLLBACK_NODE_ID_SUFFIX);
    assertThat(node.getDependentNodeIdentifier()).isEqualTo("stage.steps.outer");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateOuterPerGroupWrapperPlanNode_shouldBuildCorrectPlanNode() {
    RollbackNode rollbackNode = RollbackNode.builder()
                                    .nodeId("test-node-id")
                                    .dependentNodeIdentifier("stage.steps.sg1")
                                    .insideStrategyAncestor(false)
                                    .build();

    PlanNode planNode =
        StepGroupRollbackPlanCreatorUtils.createOuterPerGroupWrapperPlanNode("wrapper-uuid", rollbackNode);

    assertThat(planNode.getUuid()).isEqualTo("wrapper-uuid");
    assertThat(planNode.getName()).isEqualTo(NGCommonUtilPlanCreationConstants.STEP_GROUP_ROLLBACK_NAME);
    assertThat(planNode.getIdentifier())
        .isEqualTo(NGCommonUtilPlanCreationConstants.STEP_GROUP_ROLLBACK_IDENTIFIER + "_expr_"
            + "test-node-id");
    assertThat(planNode.getStepType()).isEqualTo(StepGroupRollbackChainStep.STEP_TYPE);
    assertThat(planNode.getSkipGraphType()).isEqualTo(SkipType.SKIP_NODE);

    RollbackOptionalChildChainStepParameters params =
        (RollbackOptionalChildChainStepParameters) planNode.getStepParameters();
    assertThat(params.getChildNodes()).containsExactly(rollbackNode);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateParallelNodeForOuterStepGroupRollback_shouldBuildForkNode() {
    List<String> childIds = List.of("child-1", "child-2", "child-3");

    PlanNode planNode =
        StepGroupRollbackPlanCreatorUtils.createParallelNodeForOuterStepGroupRollback("fork-uuid", childIds);

    assertThat(planNode.getUuid()).isEqualTo("fork-uuid");
    assertThat(planNode.getName()).isEqualTo(YAMLFieldNameConstants.PARALLEL);
    assertThat(planNode.getIdentifier()).isEqualTo(YAMLFieldNameConstants.PARALLEL + "fork-uuid");
    assertThat(planNode.getStepType()).isEqualTo(NGForkStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateRollbackStepsWrapperNode_shouldBuildRollbackStepsNode() {
    PlanNode planNode =
        StepGroupRollbackPlanCreatorUtils.createRollbackStepsWrapperNode("wrapper-uuid", "init-node-uuid");

    assertThat(planNode.getUuid()).isEqualTo("wrapper-uuid");
    assertThat(planNode.getName()).isEqualTo(NGCommonUtilPlanCreationConstants.ROLLBACK_NODE_NAME);
    assertThat(planNode.getIdentifier()).isEqualTo(YAMLFieldNameConstants.ROLLBACK_STEPS);
    assertThat(planNode.getStepType()).isEqualTo(RollbackStepsStep.STEP_TYPE);
    assertThat(planNode.getSkipGraphType()).isEqualTo(SkipType.SKIP_NODE);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHideInitForStageRollback_whenNormalMode_shouldAddHiddenInitNode() {
    PlanNode initNode = PlanNode.builder()
                            .uuid("init-uuid")
                            .name("init")
                            .identifier("init")
                            .stepType(RollbackStepsStep.STEP_TYPE)
                            .skipGraphType(SkipType.NOOP)
                            .build();
    PlanCreationResponse initResponse = PlanCreationResponse.builder().planNode(initNode).build();
    PlanCreationResponse targetResponse = PlanCreationResponse.builder().build();

    StepGroupRollbackPlanCreatorUtils.hideInitForStageRollback(ExecutionMode.NORMAL, targetResponse, initResponse);

    assertThat(targetResponse.getNodes()).containsKey("init-uuid");
    PlanNode hiddenNode = targetResponse.getNodes().get("init-uuid");
    assertThat(hiddenNode.getSkipGraphType()).isEqualTo(SkipType.SKIP_NODE);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHideInitForStageRollback_whenRollbackMode_shouldNotAddNode() {
    PlanNode initNode = PlanNode.builder()
                            .uuid("init-uuid")
                            .name("init")
                            .identifier("init")
                            .stepType(RollbackStepsStep.STEP_TYPE)
                            .skipGraphType(SkipType.NOOP)
                            .build();
    PlanCreationResponse initResponse = PlanCreationResponse.builder().planNode(initNode).build();
    PlanCreationResponse targetResponse = PlanCreationResponse.builder().build();

    StepGroupRollbackPlanCreatorUtils.hideInitForStageRollback(
        ExecutionMode.PIPELINE_ROLLBACK, targetResponse, initResponse);

    assertThat(targetResponse.getNodes()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHideInitForStageRollback_whenInitResponseHasNullPlanNode_shouldNotAddNode() {
    PlanCreationResponse initResponse = PlanCreationResponse.builder().build();
    PlanCreationResponse targetResponse = PlanCreationResponse.builder().build();

    StepGroupRollbackPlanCreatorUtils.hideInitForStageRollback(ExecutionMode.NORMAL, targetResponse, initResponse);

    assertThat(targetResponse.getNodes()).isEmpty();
  }
}
