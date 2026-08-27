/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineYamlUtilsTest {
  String orgIdentifier = "orgIdentifier";
  String projectIdentifier = "projectIdentifier";

  String injectYaml = "insert:\n"
      + "  identifier: inject1\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        name: stage1\n"
      + "    - stage:\n"
      + "        name: stage2";

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetBasicPipelineObject() {
    String pipelineYaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  description: test\n"
        + "  projectIdentifier: projectIdentifier\n"
        + "  orgIdentifier: orgIdentifier\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        description: <+input>\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "        description: <+input>\n";
    BasicPipeline basicPipeline = PipelineYamlUtils.getBasicPipelineObject(pipelineYaml);
    assertThat(basicPipeline.getIdentifier()).isEqualTo("test");
    assertThat(basicPipeline.getName()).isEqualTo("test");
    assertThat(basicPipeline.getDescription()).isEqualTo("test");
    assertThat(basicPipeline.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(basicPipeline.getOrgIdentifier()).isEqualTo(orgIdentifier);

    String invalidPipelineYaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  description: test\n"
        + "  projectIdentifier: projectIdentifier\n"
        + "  orgIdentifier: "
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        description: <+input>\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "        description: <+input>\n";
    assertThatThrownBy(() -> PipelineYamlUtils.getBasicPipelineObject(invalidPipelineYaml))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Could not parse the pipelineYaml. It maybe be invalid.");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetStagesNodeFromInjectNode() {
    String yaml = "pipeline:\n";
    assertThatThrownBy(() -> PipelineYamlUtils.getStagesNodeFromInjectNode(YamlUtils.readAsJsonNode(yaml), "1"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Inject is not supported for V1 pipeline version.");

    assertThatThrownBy(() -> PipelineYamlUtils.getStagesNodeFromInjectNode(YamlUtils.readAsJsonNode(yaml), "0"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Inject not cannot be null");

    JsonNode stagesNode = PipelineYamlUtils.getStagesNodeFromInjectNode(YamlUtils.readAsJsonNode(injectYaml), "0");
    assertThat(stagesNode).isNotNull();
    assertThat(stagesNode.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testIsInjectNode() {
    String yaml = "pipeline:\n";
    assertThat(PipelineYamlUtils.isInjectNode(YamlUtils.readAsJsonNode(yaml), "1")).isFalse();
    assertThat(PipelineYamlUtils.isInjectNode(YamlUtils.readAsJsonNode(yaml), "0")).isFalse();
    assertThat(PipelineYamlUtils.isInjectNode(YamlUtils.readAsJsonNode(injectYaml), "0")).isTrue();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testIsFixedInputsOnRerun() {
    // fixedInputsOnRerun as false
    String pipelineYamlWithFixedInputsOnRerunAsFalse = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        description: <+input>\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "        description: <+input>\n"
        + "  fixedInputsOnRerun: false\n";
    assertThat(PipelineYamlUtils.isFixedInputsOnRerun(pipelineYamlWithFixedInputsOnRerunAsFalse)).isFalse();

    // fixedInputsOnRerun as true
    String pipelineYamlWithFixedInputsOnRerunAsTrue = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        description: <+input>\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "        description: <+input>\n"
        + "  fixedInputsOnRerun: true\n";
    assertThat(PipelineYamlUtils.isFixedInputsOnRerun(pipelineYamlWithFixedInputsOnRerunAsTrue)).isTrue();

    // IOException case
    String invalidPipelineYaml = "stages:\n"
        + "  - stage:\n"
        + " identifier: s1\n"
        + "  fixedInputsOnRerun: true\n";
    assertThatThrownBy(() -> PipelineYamlUtils.isFixedInputsOnRerun(invalidPipelineYaml))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Could not parse the pipelineYaml. It maybe be invalid.");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testConvertSequentialPipelineToDAG_SingleStage() {
    // Given: Pipeline with single stage
    String singleStagePipelineYaml = "pipeline:\n"
        + "  name: SingleStageTest\n"
        + "  identifier: singleStageTest\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: stage1\n"
        + "        name: Stage 1\n"
        + "        type: Deployment\n";

    // When: Converting to DAG
    String convertedYaml = PipelineYamlUtils.convertSequentialPipelineToDAG(singleStagePipelineYaml);

    // Then: Verify the converted YAML structure
    JsonNode convertedPipeline = YamlUtils.readAsJsonNode(convertedYaml);
    JsonNode stages = convertedPipeline.get("pipeline").get("stages");

    assertThat(stages).isNotNull();
    assertThat(stages.size()).isEqualTo(1);

    // Verify first stage has empty dependsOn array
    JsonNode stage1 = stages.get(0).get("stage");
    assertThat(stage1.get("identifier").asText()).isEqualTo("stage1");
    assertThat(stage1.get(YAMLFieldNameConstants.DEPENDS_ON).size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testConvertSequentialPipelineToDAG_MultipleSequentialStages() {
    // Given: Pipeline with multiple sequential stages
    String multiStagePipelineYaml = "pipeline:\n"
        + "  name: MultiStageTest\n"
        + "  identifier: multiStageTest\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: stage1\n"
        + "        name: Stage 1\n"
        + "        type: Deployment\n"
        + "    - stage:\n"
        + "        identifier: stage2\n"
        + "        name: Stage 2\n"
        + "        type: Deployment\n"
        + "    - stage:\n"
        + "        identifier: stage3\n"
        + "        name: Stage 3\n"
        + "        type: Deployment\n";

    // When: Converting to DAG
    String convertedYaml = PipelineYamlUtils.convertSequentialPipelineToDAG(multiStagePipelineYaml);

    // Then: Verify the converted YAML structure
    JsonNode convertedPipeline = YamlUtils.readAsJsonNode(convertedYaml);
    JsonNode stages = convertedPipeline.get("pipeline").get("stages");

    assertThat(stages).isNotNull();
    assertThat(stages.size()).isEqualTo(3);

    // Verify first stage has empty dependsOn array
    JsonNode stage1 = stages.get(0).get("stage");
    assertThat(stage1.get("identifier").asText()).isEqualTo("stage1");
    assertThat(stage1.get(YAMLFieldNameConstants.DEPENDS_ON)).isNotNull();
    assertThat(stage1.get(YAMLFieldNameConstants.DEPENDS_ON).size()).isEqualTo(0);

    // Verify second stage depends on stage1
    JsonNode stage2 = stages.get(1).get("stage");
    assertThat(stage2.get("identifier").asText()).isEqualTo("stage2");
    ArrayNode stage2DependsOn = (ArrayNode) stage2.get(YAMLFieldNameConstants.DEPENDS_ON);
    assertThat(stage2DependsOn.size()).isEqualTo(1);
    assertThat(stage2DependsOn.get(0).asText()).isEqualTo("stage1");

    // Verify third stage depends on stage2
    JsonNode stage3 = stages.get(2).get("stage");
    assertThat(stage3.get("identifier").asText()).isEqualTo("stage3");
    ArrayNode stage3DependsOn = (ArrayNode) stage3.get(YAMLFieldNameConstants.DEPENDS_ON);
    assertThat(stage3DependsOn.size()).isEqualTo(1);
    assertThat(stage3DependsOn.get(0).asText()).isEqualTo("stage2");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testConvertSequentialPipelineToDAG_WithParallelStages() {
    // Given: Pipeline with parallel stages
    String parallelStagePipelineYaml = "pipeline:\n"
        + "  name: ParallelStageTest\n"
        + "  identifier: parallelStageTest\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: stage1\n"
        + "        name: Stage 1\n"
        + "        type: Deployment\n"
        + "    - parallel:\n"
        + "        - stage:\n"
        + "            identifier: stage2a\n"
        + "            name: Stage 2A\n"
        + "            type: Deployment\n"
        + "        - stage:\n"
        + "            identifier: stage2b\n"
        + "            name: Stage 2B\n"
        + "            type: Deployment\n"
        + "        - stage:\n"
        + "            identifier: stage2c\n"
        + "            name: Stage 2C\n"
        + "            type: Deployment\n"
        + "    - stage:\n"
        + "        identifier: stage3\n"
        + "        name: Stage 3\n"
        + "        type: Deployment\n";

    // When: Converting to DAG
    String convertedYaml = PipelineYamlUtils.convertSequentialPipelineToDAG(parallelStagePipelineYaml);

    // Then: Verify the converted YAML structure
    JsonNode convertedPipeline = YamlUtils.readAsJsonNode(convertedYaml);
    JsonNode stages = convertedPipeline.get("pipeline").get("stages");

    assertThat(stages).isNotNull();
    assertThat(stages.size()).isEqualTo(5); // 1 + 3 parallel + 1 = 5 flattened stages

    // Verify first stage has empty dependsOn array
    JsonNode stage1 = stages.get(0).get("stage");
    assertThat(stage1.get("identifier").asText()).isEqualTo("stage1");
    assertThat(stage1.get(YAMLFieldNameConstants.DEPENDS_ON)).isNotNull();
    assertThat(stage1.get(YAMLFieldNameConstants.DEPENDS_ON).size()).isEqualTo(0);

    // Verify parallel stages (stage2a, stage2b, stage2c) all depend on stage1
    JsonNode stage2a = stages.get(1).get("stage");
    assertThat(stage2a.get("identifier").asText()).isEqualTo("stage2a");
    ArrayNode stage2aDependsOn = (ArrayNode) stage2a.get(YAMLFieldNameConstants.DEPENDS_ON);
    assertThat(stage2aDependsOn.size()).isEqualTo(1);
    assertThat(stage2aDependsOn.get(0).asText()).isEqualTo("stage1");

    JsonNode stage2b = stages.get(2).get("stage");
    assertThat(stage2b.get("identifier").asText()).isEqualTo("stage2b");
    ArrayNode stage2bDependsOn = (ArrayNode) stage2b.get(YAMLFieldNameConstants.DEPENDS_ON);
    assertThat(stage2bDependsOn.size()).isEqualTo(1);
    assertThat(stage2bDependsOn.get(0).asText()).isEqualTo("stage1");

    JsonNode stage2c = stages.get(3).get("stage");
    assertThat(stage2c.get("identifier").asText()).isEqualTo("stage2c");
    ArrayNode stage2cDependsOn = (ArrayNode) stage2c.get(YAMLFieldNameConstants.DEPENDS_ON);
    assertThat(stage2cDependsOn.size()).isEqualTo(1);
    assertThat(stage2cDependsOn.get(0).asText()).isEqualTo("stage1");

    // Verify final stage depends on all parallel stages
    JsonNode stage3 = stages.get(4).get("stage");
    assertThat(stage3.get("identifier").asText()).isEqualTo("stage3");
    ArrayNode stage3DependsOn = (ArrayNode) stage3.get(YAMLFieldNameConstants.DEPENDS_ON);
    assertThat(stage3DependsOn.size()).isEqualTo(3);

    // Verify stage3 depends on all parallel stages (order may vary)
    List<String> stage3Dependencies = new ArrayList<>();
    for (int i = 0; i < stage3DependsOn.size(); i++) {
      stage3Dependencies.add(stage3DependsOn.get(i).asText());
    }
    assertThat(stage3Dependencies).containsExactlyInAnyOrder("stage2a", "stage2b", "stage2c");
  }
}
