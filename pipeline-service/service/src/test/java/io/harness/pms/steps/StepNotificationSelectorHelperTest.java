/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidYamlException;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class StepNotificationSelectorHelperTest extends CategoryTest {
  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Could not read resource file: " + filename, e);
    }
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetStepInfoList() {
    String pipelineYaml = readFile("pipeline-with-steps-for-notification.yaml");
    List<BasicStepInfo> stepInfoList = StepNotificationSelectorHelper.getStepInfoList(pipelineYaml);

    List<String> fqns = stepInfoList.stream().map(BasicStepInfo::getStepFqn).collect(Collectors.toList());
    List<String> labels = stepInfoList.stream().map(BasicStepInfo::getLabel).collect(Collectors.toList());

    // Simple step in deploy stage
    assertThat(fqns).contains("deploy.http_call");

    // Steps inside stepGroup
    assertThat(fqns).contains("deploy.sg1.inner_step");
    assertThat(fqns).contains("deploy.sg1.another_inner");

    // Label is same as FQN
    assertThat(labels).contains("deploy.http_call");
    assertThat(labels).contains("deploy.sg1.inner_step");

    // Parallel steps (parallel is transparent in FQN)
    assertThat(fqns).contains("deploy.parallel_a");
    assertThat(fqns).contains("deploy.parallel_b");

    // Steps in parallel stages
    assertThat(fqns).contains("build.run_tests");
    assertThat(fqns).contains("qa.verify");

    // Template stage should be skipped entirely
    assertThat(fqns.stream().anyMatch(f -> f.startsWith("template_stage"))).isFalse();

    // Nested step groups
    assertThat(fqns).contains("nested_sg.outer_sg.inner_sg.deep_step");

    // Template step should be skipped
    assertThat(fqns.stream().anyMatch(f -> f.contains("template_step"))).isFalse();

    // Step-level insert: insert identifier IS part of FQN (like stepGroup)
    assertThat(fqns).contains("insert_stage.before_insert");
    assertThat(fqns).contains("insert_stage.my_insert.injected_step1");
    assertThat(fqns).contains("insert_stage.my_insert.injected_step2");
    assertThat(fqns).contains("insert_stage.after_insert");

    // Total count: http_call, inner_step, another_inner, parallel_a, parallel_b, run_tests, verify, deep_step,
    //              before_insert, injected_step1, injected_step2, after_insert = 12
    assertThat(stepInfoList).hasSize(12);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetStepInfoListWithEmptyPipeline() {
    String pipelineYaml = "pipeline:\n  name: Empty\n  identifier: empty\n  stages: []";
    List<BasicStepInfo> stepInfoList = StepNotificationSelectorHelper.getStepInfoList(pipelineYaml);
    assertThat(stepInfoList).isEmpty();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetStepInfoListWithInvalidYaml() {
    assertThatThrownBy(() -> StepNotificationSelectorHelper.getStepInfoList("invalid yaml: ["))
        .isInstanceOf(InvalidYamlException.class);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetStepInfoListWithStageWithoutSteps() {
    String pipelineYaml = "pipeline:\n"
        + "  name: NoSteps\n"
        + "  identifier: noSteps\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: approval\n"
        + "        name: Approval\n"
        + "        type: Approval\n"
        + "        spec:\n"
        + "          approvalType: HarnessApproval\n";
    List<BasicStepInfo> stepInfoList = StepNotificationSelectorHelper.getStepInfoList(pipelineYaml);
    assertThat(stepInfoList).isEmpty();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testStepFqnUniqueness() {
    String pipelineYaml = "pipeline:\n"
        + "  name: DuplicateIds\n"
        + "  identifier: dup\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        name: Stage 1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: my_step\n"
        + "                  name: My Step\n"
        + "                  type: ShellScript\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "        name: Stage 2\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: my_step\n"
        + "                  name: My Step\n"
        + "                  type: ShellScript\n";
    List<BasicStepInfo> stepInfoList = StepNotificationSelectorHelper.getStepInfoList(pipelineYaml);
    assertThat(stepInfoList).hasSize(2);

    List<String> fqns = stepInfoList.stream().map(BasicStepInfo::getStepFqn).collect(Collectors.toList());
    assertThat(fqns).contains("s1.my_step");
    assertThat(fqns).contains("s2.my_step");
    // Even though identifiers are the same, FQNs are different because of stage prefix
    assertThat(fqns.get(0)).isNotEqualTo(fqns.get(1));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testStepLevelInsertIncludesIdentifierInFqn() {
    String pipelineYaml = "pipeline:\n"
        + "  name: InsertTest\n"
        + "  identifier: insertTest\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        name: Stage 1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - insert:\n"
        + "                  identifier: my_insert\n"
        + "                  name: My Insert\n"
        + "                  steps:\n"
        + "                    - step:\n"
        + "                        identifier: step_in_insert\n"
        + "                        name: Step In Insert\n"
        + "                        type: ShellScript\n";
    List<BasicStepInfo> stepInfoList = StepNotificationSelectorHelper.getStepInfoList(pipelineYaml);
    assertThat(stepInfoList).hasSize(1);

    List<String> fqns = stepInfoList.stream().map(BasicStepInfo::getStepFqn).collect(Collectors.toList());
    // Insert identifier IS part of the FQN (like stepGroup)
    assertThat(fqns).contains("s1.my_insert.step_in_insert");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testStepLevelInsertWithRuntimeInputIsSkipped() {
    String pipelineYaml = "pipeline:\n"
        + "  name: InsertInputTest\n"
        + "  identifier: insertInputTest\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        name: Stage 1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: normal_step\n"
        + "                  name: Normal Step\n"
        + "                  type: ShellScript\n"
        + "              - insert:\n"
        + "                  identifier: insert1\n"
        + "                  name: Insert 1\n"
        + "                  steps: <+input>\n";
    List<BasicStepInfo> stepInfoList = StepNotificationSelectorHelper.getStepInfoList(pipelineYaml);
    // Only the normal step should appear; insert with <+input> steps is skipped
    assertThat(stepInfoList).hasSize(1);

    List<String> fqns = stepInfoList.stream().map(BasicStepInfo::getStepFqn).collect(Collectors.toList());
    assertThat(fqns).contains("s1.normal_step");
    assertThat(fqns.stream().anyMatch(f -> f.contains("insert1"))).isFalse();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testStageLevelInsertIsTransparent() {
    String pipelineYaml = "pipeline:\n"
        + "  name: StageInsertTest\n"
        + "  identifier: stageInsertTest\n"
        + "  stages:\n"
        + "    - insert:\n"
        + "        identifier: stage_insert\n"
        + "        name: Stage Insert\n"
        + "        stages:\n"
        + "          - stage:\n"
        + "              identifier: injected_stage\n"
        + "              name: Injected Stage\n"
        + "              type: Custom\n"
        + "              spec:\n"
        + "                execution:\n"
        + "                  steps:\n"
        + "                    - step:\n"
        + "                        identifier: step1\n"
        + "                        name: Step 1\n"
        + "                        type: ShellScript\n";
    List<BasicStepInfo> stepInfoList = StepNotificationSelectorHelper.getStepInfoList(pipelineYaml);
    assertThat(stepInfoList).hasSize(1);

    List<String> fqns = stepInfoList.stream().map(BasicStepInfo::getStepFqn).collect(Collectors.toList());
    // Stage-level insert identifier must NOT appear in the FQN; the injected stage's own identifier is used
    assertThat(fqns).contains("injected_stage.step1");
    assertThat(fqns.stream().anyMatch(f -> f.contains("stage_insert"))).isFalse();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetStepInfoListV1ParallelAndSequentialStages() {
    String pipelineYaml = readFile("v1-pipeline-with-parallel-stages-all-with-ids.yaml");
    List<BasicStepInfo> stepInfoList = StepNotificationSelectorHelper.getStepInfoList(pipelineYaml);
    List<String> fqns = stepInfoList.stream().map(BasicStepInfo::getStepFqn).collect(Collectors.toList());

    assertThat(fqns).contains("st1.run1", "st2.http2", "stage1_1.shellscript_1");
    assertThat(stepInfoList).hasSize(3);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetStepInfoListV1StepGroupAndNestedParallelSteps() {
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: deploy\n"
        + "      name: Deploy\n"
        + "      steps:\n"
        + "        - group:\n"
        + "            id: sg1\n"
        + "            name: Step Group\n"
        + "            steps:\n"
        + "              - parallel:\n"
        + "                  steps:\n"
        + "                    - id: parallel_a\n"
        + "                      name: Parallel A\n"
        + "                      run:\n"
        + "                        script: echo a\n"
        + "                    - id: parallel_b\n"
        + "                      run:\n"
        + "                        script: echo b\n"
        + "              - id: after_parallel\n"
        + "                name: After\n"
        + "                run:\n"
        + "                  script: echo c\n";
    List<BasicStepInfo> stepInfoList = StepNotificationSelectorHelper.getStepInfoList(pipelineYaml);
    List<String> fqns = stepInfoList.stream().map(BasicStepInfo::getStepFqn).collect(Collectors.toList());
    assertThat(fqns).contains("deploy.sg1.parallel_a", "deploy.sg1.parallel_b", "deploy.sg1.after_parallel");
    assertThat(stepInfoList).hasSize(3);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetStepInfoListV1WithStageGroupGroupStages() {
    // V1 stage group (GroupStages): stages[].group.{ id, stages } — distinct from V0 stepGroup / V1 step group.steps
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - group:\n"
        + "        id: my_stage_group\n"
        + "        name: Stage Group\n"
        + "        stages:\n"
        + "          - id: inner_deploy\n"
        + "            name: Inner Deploy\n"
        + "            steps:\n"
        + "              - id: shell1\n"
        + "                name: Shell\n"
        + "                run:\n"
        + "                  script: echo hi\n";
    List<BasicStepInfo> stepInfoList = StepNotificationSelectorHelper.getStepInfoList(pipelineYaml);
    assertThat(stepInfoList).hasSize(1);
    assertThat(stepInfoList.get(0).getStepFqn()).isEqualTo("my_stage_group.inner_deploy.shell1");
  }
}
