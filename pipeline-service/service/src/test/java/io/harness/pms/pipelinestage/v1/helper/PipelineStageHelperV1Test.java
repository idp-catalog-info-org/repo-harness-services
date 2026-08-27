/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.v1.helper;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.preprocess.YamlPreProcessorFactory;
import io.harness.pms.yaml.preprocess.YamlV1PreProcessor;
import io.harness.rule.Owner;
import io.harness.yaml.core.failurestrategy.v1.FailureConfigV1;
import io.harness.yaml.core.failurestrategy.v1.action.FailureStrategyActionConfigV1;
import io.harness.yaml.core.failurestrategy.v1.action.PipelineRollbackFailureActionConfigV1;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PipelineStageHelperV1Test extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private YamlPreProcessorFactory yamlPreProcessorFactory;

  @InjectMocks private PipelineStageHelperV1 pipelineStageHelper;

  @Before
  public void setup() {
    YamlV1PreProcessor yamlV1PreProcessor = new YamlV1PreProcessor();
    when(yamlPreProcessorFactory.getProcessorInstance(HarnessYamlVersion.V1)).thenReturn(yamlV1PreProcessor);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateNestedChainedPipeline() {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - runtime: shell\n"
        + "      steps:\n"
        + "        - run: echo first stage\n"
        + "          id: run_1\n"
        + "          name: run_1\n";

    assertThatCode(() -> pipelineStageHelper.containsPipelineStage(yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateNestedChainInSeries() {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - runtime: shell\n"
        + "      steps:\n"
        + "        - run: echo first stage\n"
        + "          id: run_1\n"
        + "          name: run_1\n"
        + "    - chain:\n"
        + "        uses: default/Test/sample_child_pipeline\n";
    assertThatThrownBy(() -> pipelineStageHelper.containsPipelineStage(yaml))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateNestedChainInParallel() {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        stages:\n"
        + "          - chain:\n"
        + "              uses: default/Test/sample_child_pipeline\n";

    assertThatThrownBy(() -> pipelineStageHelper.containsPipelineStage(yaml))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateNestedChainInGroup() {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - group:\n"
        + "        stages:\n"
        + "          - chain:\n"
        + "              uses: default/Test/sample_child_pipeline\n";

    assertThatThrownBy(() -> pipelineStageHelper.containsPipelineStage(yaml))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testNegativeNestedChainInParallel() {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - runtime: shell\n"
        + "      steps:\n"
        + "        - run: echo first stage\n"
        + "          id: run_1\n"
        + "          name: run_1\n"
        + "    - runtime: shell\n"
        + "      steps:\n"
        + "        - run: echo second stage\n"
        + "          id: run_2\n"
        + "          name: run_2\n";

    assertThatCode(() -> pipelineStageHelper.containsPipelineStage(yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetInputSetJsonNode() throws IOException {
    String inputs = "{\"foo\":\"bar\"}";
    YamlField inputsYamlField = YamlUtils.readTreeWithDefaultObjectMapper(inputs);
    JsonNode inputsJson = pipelineStageHelper.getInputSetJsonNode(inputsYamlField);
    assertThat(inputsJson)
        .isEqualTo(YamlUtils.readAsJsonNode("inputs:\n"
            + "  foo: bar"));

    inputsJson = pipelineStageHelper.getInputSetJsonNode(null);
    assertThat(inputsJson).isEqualTo(null);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testContainsPipelineStageWithSampleUnifiedPipeline() {
    String yaml = "pipeline:\n"
        + "  clone:\n"
        + "    disabled: true\n"
        + "  inputs:\n"
        + "    input1:\n"
        + "      type: string\n"
        + "      value: parent_pipeline\n"
        + "  allow-stage-executions: true\n"
        + "  stages:\n"
        + "    - runtime: shell\n"
        + "      steps:\n"
        + "        - run: echo first stage\n"
        + "          id: run_1\n"
        + "          name: run_1\n"
        + "      id: stage_1\n"
        + "      name: stage_1\n"
        + "    - chain:\n"
        + "        uses: default/Test/sample_child_pipeline\n"
        + "      id: chain_1\n"
        + "      name: chain_1\n";

    assertThatThrownBy(() -> pipelineStageHelper.containsPipelineStage(yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Nested pipeline is not supported");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetIdentifierRefFullPath() {
    String uses = "org1/project1/pipeline1";
    String accountId = "acc1";

    IdentifierRef ref = pipelineStageHelper.getIdentifierRef(uses, accountId);

    assertThat(ref.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(ref.getOrgIdentifier()).isEqualTo("org1");
    assertThat(ref.getProjectIdentifier()).isEqualTo("project1");
    assertThat(ref.getIdentifier()).isEqualTo("pipeline1");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testValidateFailureStrategyWithPipelineRollback() {
    FailureStrategyActionConfigV1 action =
        FailureStrategyActionConfigV1.builder()
            .pipelineRollback(PipelineRollbackFailureActionConfigV1.builder().build())
            .build();
    FailureConfigV1 config = FailureConfigV1.builder().action(action).build();

    ParameterField<List<FailureConfigV1>> field = ParameterField.createValueField(Collections.singletonList(config));

    assertThatThrownBy(() -> pipelineStageHelper.validateFailureStrategy(field))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Pipeline Rollback");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testValidateChildGraphToGenerate() {
    Map<String, GraphLayoutNodeDTO> graphLayoutNodeDTO = new HashMap<>();
    assertThat(pipelineStageHelper.validateChildGraphToGenerate(graphLayoutNodeDTO, "someKey")).isFalse();

    graphLayoutNodeDTO.put("key1", GraphLayoutNodeDTO.builder().build());
    assertThat(pipelineStageHelper.validateChildGraphToGenerate(graphLayoutNodeDTO, "key1")).isFalse();

    graphLayoutNodeDTO.put("key2", GraphLayoutNodeDTO.builder().nodeType("Custom").build());
    assertThat(pipelineStageHelper.validateChildGraphToGenerate(graphLayoutNodeDTO, "key2")).isFalse();

    graphLayoutNodeDTO.put("key3", GraphLayoutNodeDTO.builder().nodeType("Pipeline").build());
    assertThat(pipelineStageHelper.validateChildGraphToGenerate(graphLayoutNodeDTO, "key3")).isFalse();

    graphLayoutNodeDTO.put("key4", GraphLayoutNodeDTO.builder().nodeType("chain").build());
    assertThat(pipelineStageHelper.validateChildGraphToGenerate(graphLayoutNodeDTO, "key4")).isTrue();
  }
}
