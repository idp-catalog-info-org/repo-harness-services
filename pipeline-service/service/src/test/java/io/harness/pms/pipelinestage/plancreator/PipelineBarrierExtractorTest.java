/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.plancreator;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PipelineBarrierExtractorTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private PlanCreationContext mockContext;

  private PipelineBarrierExtractor pipelineBarrierExtractor = new PipelineBarrierExtractor();

  // Test YAML with various step types (step, stepGroup, insert, parallel) containing Barrier steps
  String inputsYaml = "inputs:\n"
      + "  identifier: childBarrierTest1\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: var1\n"
      + "        type: Custom\n"
      + "        spec:\n"
      + "          execution:\n"
      + "            steps:\n"
      + "              - parallel:\n"
      + "                  - stepGroup:\n"
      + "                      identifier: stpgrp1\n"
      + "                      steps:\n"
      + "                        - parallel:\n"
      + "                            - step:\n"
      + "                                identifier: Barrier_1\n"
      + "                                type: Barrier\n"
      + "                                spec:\n"
      + "                                  barrierRef: parent.barrier3\n"
      + "                  - insert:\n"
      + "                      identifier: stpgrp2\n"
      + "                      steps:\n"
      + "                        - parallel:\n"
      + "                            - step:\n"
      + "                                identifier: Barrier_2\n"
      + "                                type: Barrier\n"
      + "                                spec:\n"
      + "                                  barrierRef: parent.barrier2\n"
      + "              - step:\n"
      + "                  identifier: Barrier_3\n"
      + "                  type: Barrier\n"
      + "                  spec:\n"
      + "                      barrierRef: parent.barrier3";

  String inputsYaml2 = "inputs:\n"
      + "  identifier: testCHildPipeline\n"
      + "  template:\n"
      + "    templateInputs:\n"
      + "      stages:\n"
      + "        - insert:\n"
      + "            identifier: tedst\n"
      + "            stages:\n"
      + "              - parallel:\n"
      + "                  - stage:\n"
      + "                      identifier: vd\n"
      + "                      type: Custom\n"
      + "                      spec:\n"
      + "                        execution:\n"
      + "                          steps:\n"
      + "                            - step:\n"
      + "                                identifier: Barrier_1\n"
      + "                                type: Barrier\n"
      + "                                spec:\n"
      + "                                  barrierRef: bar1\n"
      + "                  - stage:\n"
      + "                      identifier: vd2\n"
      + "                      type: Custom\n"
      + "                      spec:\n"
      + "                        execution:\n"
      + "                          steps:\n"
      + "                            - step:\n"
      + "                                identifier: Barrier_1\n"
      + "                                type: Barrier\n"
      + "                                spec:\n"
      + "                                  barrierRef: bar2\n"
      + "        - parallel:\n"
      + "            - stage:\n"
      + "                identifier: fds\n"
      + "                type: Custom\n"
      + "                spec:\n"
      + "                  execution:\n"
      + "                    steps:\n"
      + "                      - step:\n"
      + "                          identifier: Barrier_1\n"
      + "                          type: Barrier\n"
      + "                          spec:\n"
      + "                            barrierRef: bar3\n"
      + "            - stage:\n"
      + "                identifier: dfs2\n"
      + "                type: Custom\n"
      + "                spec:\n"
      + "                  execution:\n"
      + "                    steps:\n"
      + "                      - step:\n"
      + "                          identifier: Barrier_1\n"
      + "                          type: Barrier\n"
      + "                          spec:\n"
      + "                            barrierRef: bar4";

  // YAML with stage-level template wrapping the spec (stage.template.templateInputs.spec)
  String stageTemplateYaml = "inputs:\n"
      + "  identifier: child13apr\n"
      + "  template:\n"
      + "    templateInputs:\n"
      + "      stages:\n"
      + "        - insert:\n"
      + "            identifier: s\n"
      + "            stages:\n"
      + "              - stage:\n"
      + "                  identifier: sda\n"
      + "                  template:\n"
      + "                    templateInputs:\n"
      + "                      type: Custom\n"
      + "                      spec:\n"
      + "                        execution:\n"
      + "                          steps:\n"
      + "                            - step:\n"
      + "                                identifier: Barrier_1\n"
      + "                                type: Barrier\n"
      + "                                spec:\n"
      + "                                  barrierRef: parent.test1";

  // YAML with step-level template wrapping the type/spec (step.template.templateInputs.type/spec)
  String stepTemplateYaml = "inputs:\n"
      + "  identifier: child13apr\n"
      + "  template:\n"
      + "    templateInputs:\n"
      + "      stages:\n"
      + "        - insert:\n"
      + "            identifier: s\n"
      + "            stages:\n"
      + "              - stage:\n"
      + "                  identifier: sda\n"
      + "                  template:\n"
      + "                    templateInputs:\n"
      + "                      type: Custom\n"
      + "                      spec:\n"
      + "                        execution:\n"
      + "                          steps:\n"
      + "                            - step:\n"
      + "                                identifier: Barrier_1\n"
      + "                                template:\n"
      + "                                  templateInputs:\n"
      + "                                    type: Barrier\n"
      + "                                    spec:\n"
      + "                                      barrierRef: parent.test2";

  // Simple YAML with empty stages
  String emptyStagesYaml = "inputs:\n"
      + "  identifier: emptyStages\n"
      + "  stages: []";

  // Simple YAML with no execution block
  String noExecutionYaml = "inputs:\n"
      + "  identifier: noExecution\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: var1\n"
      + "        type: Custom\n"
      + "        spec: {}";

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetAllBarriersUsedInChildPipeline() throws IOException {
    // Parse the YAML input
    YamlField inputField = YamlUtils.readTree(inputsYaml);

    // Extract barrier references
    List<String> barrierRefs = pipelineBarrierExtractor.getAllBarriersUsedInChildPipeline(
        inputField.getNode().getField(YAMLFieldNameConstants.INPUTS));

    // Verify results;
    assertThat(barrierRefs).isNotNull();
    assertThat(barrierRefs.size()).isEqualTo(2);
    assertThat(barrierRefs.contains("parent.barrier2")).isTrue();
    assertThat(barrierRefs.contains("parent.barrier3")).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetAllBarriersUsedInChildPipelineWithPipelineTemplate() throws IOException {
    // Parse the YAML input
    YamlField inputField = YamlUtils.readTree(inputsYaml2);

    // Extract barrier references
    List<String> barrierRefs = pipelineBarrierExtractor.getAllBarriersUsedInChildPipeline(
        inputField.getNode().getField(YAMLFieldNameConstants.INPUTS));

    // Verify results;
    assertThat(barrierRefs).isNotNull();
    assertThat(barrierRefs.size()).isEqualTo(4);
    assertThat(barrierRefs.contains("bar1")).isTrue();
    assertThat(barrierRefs.contains("bar2")).isTrue();
    assertThat(barrierRefs.contains("bar3")).isTrue();
    assertThat(barrierRefs.contains("bar4")).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetAllBarriersUsedInChildPipeline_StageTemplate() throws IOException {
    YamlField inputField = YamlUtils.readTree(stageTemplateYaml);

    List<String> barrierRefs = pipelineBarrierExtractor.getAllBarriersUsedInChildPipeline(
        inputField.getNode().getField(YAMLFieldNameConstants.INPUTS));

    assertThat(barrierRefs).isNotNull();
    assertThat(barrierRefs).hasSize(1);
    assertThat(barrierRefs).contains("parent.test1");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetAllBarriersUsedInChildPipeline_StepTemplate() throws IOException {
    YamlField inputField = YamlUtils.readTree(stepTemplateYaml);

    List<String> barrierRefs = pipelineBarrierExtractor.getAllBarriersUsedInChildPipeline(
        inputField.getNode().getField(YAMLFieldNameConstants.INPUTS));

    assertThat(barrierRefs).isNotNull();
    assertThat(barrierRefs).hasSize(1);
    assertThat(barrierRefs).contains("parent.test2");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetAllBarriersUsedInChildPipeline_EmptyStages() throws IOException {
    // Parse the YAML input with empty stages
    YamlField inputField = YamlUtils.readTree(emptyStagesYaml);

    // Extract barrier references
    List<String> barrierRefs = pipelineBarrierExtractor.getAllBarriersUsedInChildPipeline(
        inputField.getNode().getField(YAMLFieldNameConstants.INPUTS));

    // Verify results
    assertThat(barrierRefs).isNotNull();
    assertThat(barrierRefs.isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetAllBarriersUsedInChildPipeline_NoExecution() throws IOException {
    // Parse the YAML input with no execution block
    YamlField inputField = YamlUtils.readTree(noExecutionYaml);

    // Extract barrier references
    List<String> barrierRefs = pipelineBarrierExtractor.getAllBarriersUsedInChildPipeline(
        inputField.getNode().getField(YAMLFieldNameConstants.INPUTS));

    // Verify results
    assertThat(barrierRefs).isNotNull();
    assertThat(barrierRefs.isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetAllBarriersUsedInChildPipeline_NullInput() {
    // Extract barrier references with null input
    List<String> barrierRefs = pipelineBarrierExtractor.getAllBarriersUsedInChildPipeline(null);

    // Verify results
    assertThat(barrierRefs).isNotNull();
    assertThat(barrierRefs.isEmpty()).isTrue();
  }
}
