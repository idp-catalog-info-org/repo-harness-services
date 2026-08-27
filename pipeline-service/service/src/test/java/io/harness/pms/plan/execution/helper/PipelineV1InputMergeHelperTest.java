/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineV1InputMergeHelperTest extends CategoryTest {
  private static final String PIPELINE_WITH_STAGE_RUNTIME_INPUT = "pipeline:\n"
      + "  stages:\n"
      + "    - id: stage\n"
      + "      name: Stage\n"
      + "      inputs:\n"
      + "        CYPRESS_API_URL:\n"
      + "          type: string\n"
      + "          value: https://director-cy.qa.harness.io/\n"
      + "        SPECS_PATH:\n"
      + "          type: string\n"
      + "          value: <+input>\n"
      + "      steps:\n"
      + "        - id: run_tests\n"
      + "          run: echo test\n";

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testMergeUserInputsToStageEntityInputs_flatOverlayValue() {
    JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(PIPELINE_WITH_STAGE_RUNTIME_INPUT);
    JsonNode inputsJsonNode = YamlUtils.readAsJsonNode("inputs:\n"
        + "  overlay:\n"
        + "    stages:\n"
        + "      - id: stage\n"
        + "        inputs:\n"
        + "          SPECS_PATH: testValueForPath\n");

    JsonNode merged = PipelineV1InputMergeHelper.mergeUserInputsToStageEntityInputs(pipelineJsonNode, inputsJsonNode);

    JsonNode specsPathValue = merged.at("/pipeline/stages/0/inputs/SPECS_PATH/value");
    JsonNode cypressApiUrlValue = merged.at("/pipeline/stages/0/inputs/CYPRESS_API_URL/value");
    assertThat(specsPathValue.asText()).isEqualTo("testValueForPath");
    assertThat(cypressApiUrlValue.asText()).isEqualTo("https://director-cy.qa.harness.io/");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testMergeUserInputsToStageEntityInputs_nestedOverlayValue() {
    JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(PIPELINE_WITH_STAGE_RUNTIME_INPUT);
    JsonNode inputsJsonNode = YamlUtils.readAsJsonNode("inputs:\n"
        + "  overlay:\n"
        + "    stages:\n"
        + "      - id: stage\n"
        + "        inputs:\n"
        + "          SPECS_PATH:\n"
        + "            value: nestedValueForPath\n");

    JsonNode merged = PipelineV1InputMergeHelper.mergeUserInputsToStageEntityInputs(pipelineJsonNode, inputsJsonNode);

    assertThat(merged.at("/pipeline/stages/0/inputs/SPECS_PATH/value").asText()).isEqualTo("nestedValueForPath");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testMergeUserInputsToStageEntityInputs_noOverlayLeavesPipelineUnchanged() {
    JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(PIPELINE_WITH_STAGE_RUNTIME_INPUT);
    JsonNode inputsJsonNode = YamlUtils.readAsJsonNode("inputs:\n"
        + "  SPECS_PATH: testValueForPath\n");

    JsonNode merged = PipelineV1InputMergeHelper.mergeUserInputsToStageEntityInputs(pipelineJsonNode, inputsJsonNode);

    assertThat(merged.at("/pipeline/stages/0/inputs/SPECS_PATH/value").asText()).isEqualTo("<+input>");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testMergeUserInputsToPipelineEntityInputs_flatPipelineInput() {
    String pipelineYaml = "pipeline:\n"
        + "  inputs:\n"
        + "    SPECS_PATH:\n"
        + "      type: string\n"
        + "      value: <+input>\n";
    JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(pipelineYaml);
    JsonNode inputsJsonNode = YamlUtils.readAsJsonNode("inputs:\n"
        + "  SPECS_PATH: testValueForPath\n");

    JsonNode merged =
        PipelineV1InputMergeHelper.mergeUserInputsToPipelineEntityInputs(pipelineJsonNode, inputsJsonNode);

    assertThat(merged.at("/pipeline/inputs/SPECS_PATH/value").asText()).isEqualTo("testValueForPath");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testMergeUserInputsToStageEntityInputs_parallelStage() {
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        stages:\n"
        + "          - id: stage\n"
        + "            inputs:\n"
        + "              SPECS_PATH:\n"
        + "                type: string\n"
        + "                value: <+input>\n";
    JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(pipelineYaml);
    JsonNode inputsJsonNode = YamlUtils.readAsJsonNode("inputs:\n"
        + "  overlay:\n"
        + "    stages:\n"
        + "      - id: stage\n"
        + "        inputs:\n"
        + "          SPECS_PATH: parallelStageValue\n");

    JsonNode merged = PipelineV1InputMergeHelper.mergeUserInputsToStageEntityInputs(pipelineJsonNode, inputsJsonNode);

    assertThat(merged.at("/pipeline/stages/0/parallel/stages/0/inputs/SPECS_PATH/value").asText())
        .isEqualTo("parallelStageValue");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testMergeUserInputsToStageEntityInputs_groupStage() {
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - group:\n"
        + "        stages:\n"
        + "          - id: stage\n"
        + "            inputs:\n"
        + "              SPECS_PATH:\n"
        + "                type: string\n"
        + "                value: <+input>\n";
    JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(pipelineYaml);
    JsonNode inputsJsonNode = YamlUtils.readAsJsonNode("inputs:\n"
        + "  overlay:\n"
        + "    stages:\n"
        + "      - id: stage\n"
        + "        inputs:\n"
        + "          SPECS_PATH: groupStageValue\n");

    JsonNode merged = PipelineV1InputMergeHelper.mergeUserInputsToStageEntityInputs(pipelineJsonNode, inputsJsonNode);

    assertThat(merged.at("/pipeline/stages/0/group/stages/0/inputs/SPECS_PATH/value").asText())
        .isEqualTo("groupStageValue");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testMergeUserInputsToStageEntityInputs_overlayPipelineStagesPath() {
    JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(PIPELINE_WITH_STAGE_RUNTIME_INPUT);
    JsonNode inputsJsonNode = YamlUtils.readAsJsonNode("inputs:\n"
        + "  overlay:\n"
        + "    pipeline:\n"
        + "      stages:\n"
        + "        - id: stage\n"
        + "          inputs:\n"
        + "            SPECS_PATH: templateOverlayValue\n");

    JsonNode merged = PipelineV1InputMergeHelper.mergeUserInputsToStageEntityInputs(pipelineJsonNode, inputsJsonNode);

    assertThat(merged.at("/pipeline/stages/0/inputs/SPECS_PATH/value").asText()).isEqualTo("templateOverlayValue");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testMergeV1UserProvidedInputs_pipelineAndStageInputsTogether() {
    String pipelineYaml = "pipeline:\n"
        + "  inputs:\n"
        + "    PIPELINE_VAR:\n"
        + "      type: string\n"
        + "      value: <+input>\n"
        + "  stages:\n"
        + "    - id: stage\n"
        + "      inputs:\n"
        + "        SPECS_PATH:\n"
        + "          type: string\n"
        + "          value: <+input>\n";
    JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(pipelineYaml);
    JsonNode inputsJsonNode = YamlUtils.readAsJsonNode("inputs:\n"
        + "  PIPELINE_VAR: pipelineValue\n"
        + "  overlay:\n"
        + "    stages:\n"
        + "      - id: stage\n"
        + "        inputs:\n"
        + "          SPECS_PATH: stageValue\n");

    JsonNode merged =
        PipelineV1InputMergeHelper.mergeV1UserProvidedInputs(pipelineJsonNode, inputsJsonNode, false, pipelineYaml);

    assertThat(merged.at("/pipeline/inputs/PIPELINE_VAR/value").asText()).isEqualTo("pipelineValue");
    assertThat(merged.at("/pipeline/stages/0/inputs/SPECS_PATH/value").asText()).isEqualTo("stageValue");
  }
}
