/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.helper;

import static io.harness.beans.FeatureName.PIPE_RETURN_NULL_ON_EXPRESSION_FAIL_PIPELINE_STAGE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SAKSHI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipelinestage.outcome.PipelineStageOutcome;
import io.harness.pms.pipelinestage.v1.helper.PipelineStageHelperV1;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.helper.PipelineStageHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.OnFailureConfig;
import io.harness.yaml.core.failurestrategy.action.AbortFailureActionConfig;
import io.harness.yaml.core.failurestrategy.action.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.action.IgnoreFailureActionConfig;
import io.harness.yaml.core.failurestrategy.action.ManualInterventionFailureActionConfig;
import io.harness.yaml.core.failurestrategy.action.MarkAsSuccessFailureActionConfig;
import io.harness.yaml.core.failurestrategy.action.PipelineRollbackFailureActionConfig;
import io.harness.yaml.core.failurestrategy.action.ProceedWithDefaultValuesFailureActionConfig;
import io.harness.yaml.core.failurestrategy.action.RetryFailureActionConfig;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PipelineStageHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;
  @Mock private PipelineStageHelperV1 pipelineStageHelperV1;
  @Mock private GitAwareEntityHelper gitAwareEntityHelper;
  @InjectMocks private PipelineStageHelper pipelineStageHelper;
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateNestedChainedPipeline() {
    String yaml = "pipeline:\n"
        + "    name: test nested pipeline chain\n"
        + "    identifier: pipeline_chain\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              name: test\n"
        + "              type: Deployment\n"
        + "              identifier: test\n";

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, "true");
    pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity, null);
    verify(pmsPipelineTemplateHelper, times(1)).resolveTemplateRefsInPipeline(pipelineEntity, "true");
    verify(pipelineStageHelperV1, times(0)).containsPipelineStage(yaml);

    pipelineEntity = PipelineEntity.builder().harnessVersion(HarnessYamlVersion.V1).build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, "true");
    pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity, null);
    verify(pipelineStageHelperV1, times(1)).containsPipelineStage(yaml);

    PipelineEntity pipelineEntity1 = PipelineEntity.builder().harnessVersion("V2").build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity1, "true");
    assertThatThrownBy(() -> pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity1, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateNestedChainInSeries() {
    String yaml = "pipeline:\n"
        + "    name: test nested pipeline chain\n"
        + "    identifier: pipeline_chain\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              name: test\n"
        + "              type: Deployment\n"
        + "              identifier: test\n"
        + "        - stage:\n"
        + "              name: test2\n"
        + "              type: Pipeline\n"
        + "              identifier: test\n";

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, "true");

    assertThatThrownBy(() -> pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateNestedChainInParallel() {
    String yaml = "pipeline:\n"
        + "    name: test nested pipeline chain\n"
        + "    identifier: pipeline_chain\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              name: test\n"
        + "              type: Deployment\n"
        + "              identifier: test\n"
        + "        - parallel:\n"
        + "             - stage:\n"
        + "                 name: test\n"
        + "                 type: Pipeline\n"
        + "                 identifier: test\n";

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, "true");

    assertThatThrownBy(() -> pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testNegativeNestedChainInParallel() {
    String yaml = "pipeline:\n"
        + "    name: test nested pipeline chain\n"
        + "    identifier: pipeline_chain\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              name: test\n"
        + "              type: Deployment\n"
        + "              identifier: test\n"
        + "        - parallel:\n"
        + "             - stage:\n"
        + "                 name: test\n"
        + "                 type: Deployment\n"
        + "                 identifier: test\n";

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, "true");

    assertThatCode(() -> pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity, null))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testResolveExpression() {
    Map<String, ParameterField<String>> expressionMap = new HashMap<>();

    String var1 = "var1";
    String var2 = "var2";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
                            .setPlanId("planId")
                            .build();
    expressionMap.put(var1, ParameterField.createExpressionField(true, "<+pipeline.name>", null, false));
    expressionMap.put(var2, ParameterField.createValueField("constant"));

    Map<String, String> resolvedMap = new HashMap<>();
    resolvedMap.put(var1, expressionMap.get(var1).getExpressionValue());
    resolvedMap.put(var2, expressionMap.get(var2).getValue());

    Map<String, String> resolvedExpressionMap = new HashMap<>();
    resolvedExpressionMap.put(var1, "pipelineName");
    resolvedExpressionMap.put(var2, expressionMap.get(var2).getValue());

    doReturn(true)
        .when(pmsFeatureFlagHelper)
        .isEnabled("accountId", PIPE_RETURN_NULL_ON_EXPRESSION_FAIL_PIPELINE_STAGE);
    doReturn(resolvedExpressionMap)
        .when(pmsEngineExpressionService)
        .resolve(ambiance, resolvedMap, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    PipelineStageOutcome outcome = pipelineStageHelper.resolveOutputVariables(expressionMap, ambiance);
    assertThat(outcome.size()).isEqualTo(2);
    assertThat(outcome.get(var1)).isEqualTo("pipelineName");
    assertThat(outcome.get(var2)).isEqualTo("constant");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateFailureStrategy() {
    assertThatCode(() -> pipelineStageHelper.validateFailureStrategy(null)).doesNotThrowAnyException();
    ParameterField<List<FailureStrategyConfig>> failureStrategyConfigs =
        getFailureStrategy(RetryFailureActionConfig.builder().build());

    assertThatThrownBy(() -> pipelineStageHelper.validateFailureStrategy(failureStrategyConfigs))
        .isInstanceOf(InvalidRequestException.class);

    ParameterField<List<FailureStrategyConfig>> miFailureStrategy =
        getFailureStrategy(ManualInterventionFailureActionConfig.builder().build());
    assertThatThrownBy(() -> pipelineStageHelper.validateFailureStrategy(miFailureStrategy))
        .isInstanceOf(InvalidRequestException.class);

    ParameterField<List<FailureStrategyConfig>> pipelineRollbackFailureStrategy =
        getFailureStrategy(PipelineRollbackFailureActionConfig.builder().build());
    assertThatThrownBy(() -> pipelineStageHelper.validateFailureStrategy(pipelineRollbackFailureStrategy))
        .isInstanceOf(InvalidRequestException.class);

    ParameterField<List<FailureStrategyConfig>> defaultFailureStrategy =
        getFailureStrategy(ProceedWithDefaultValuesFailureActionConfig.builder().build());
    assertThatCode(() -> pipelineStageHelper.validateFailureStrategy(defaultFailureStrategy))
        .doesNotThrowAnyException();

    ParameterField<List<FailureStrategyConfig>> ignoreFailureStrategy =
        getFailureStrategy(IgnoreFailureActionConfig.builder().build());
    assertThatCode(() -> pipelineStageHelper.validateFailureStrategy(ignoreFailureStrategy)).doesNotThrowAnyException();

    ParameterField<List<FailureStrategyConfig>> markAsSuccessFailureStrategy =
        getFailureStrategy(MarkAsSuccessFailureActionConfig.builder().build());
    assertThatCode(() -> pipelineStageHelper.validateFailureStrategy(markAsSuccessFailureStrategy))
        .doesNotThrowAnyException();

    ParameterField<List<FailureStrategyConfig>> abortFailureStrategy =
        getFailureStrategy(AbortFailureActionConfig.builder().build());
    assertThatCode(() -> pipelineStageHelper.validateFailureStrategy(abortFailureStrategy)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetInputSetJsonNode() throws IOException {
    YamlField inputSetField = YamlUtils.readTreeWithDefaultObjectMapper("a:\n  b: c");
    JsonNode inputSetJsonNode = pipelineStageHelper.getInputSetJsonNode(inputSetField, HarnessYamlVersion.V0);
    assertThat(inputSetJsonNode).isEqualTo(YamlUtils.readAsJsonNode("pipeline:\n  a:\n    b: c\n"));
    verify(pipelineStageHelperV1, times(0)).getInputSetJsonNode(inputSetField);

    pipelineStageHelper.getInputSetJsonNode(inputSetField, HarnessYamlVersion.V1);
    verify(pipelineStageHelperV1, times(1)).getInputSetJsonNode(inputSetField);

    assertThatThrownBy(() -> pipelineStageHelper.getInputSetJsonNode(inputSetField, "V2"))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testGetInputSetJsonNodeStripsBakedValidatorFromExpression() throws IOException {
    // Chained pipeline bakes the child's validator onto the parent's expression input. The suffix must be stripped
    // while the expression is still unresolved so the child's number schema validation passes after resolution.
    YamlField inputSetField =
        YamlUtils.readTreeWithDefaultObjectMapper("numVar: <+pipeline.variables.foo>.selectOneFrom(0,1)\n"
            + "strVar: hello\n"
            + "literalVar: 2.allowedValues(1,2)\n"
            + "exprVar: <+pipeline.variables.bar>");
    JsonNode result = pipelineStageHelper.getInputSetJsonNode(inputSetField, HarnessYamlVersion.V0);
    JsonNode pipelineNode = result.get("pipeline");
    // Baked validator suffix removed, expression retained.
    assertThat(pipelineNode.get("numVar").asText()).isEqualTo("<+pipeline.variables.foo>");
    // Plain string left untouched.
    assertThat(pipelineNode.get("strVar").asText()).isEqualTo("hello");
    // Concrete value that only looks like a validator (no expression) must NOT be stripped.
    assertThat(pipelineNode.get("literalVar").asText()).isEqualTo("2.allowedValues(1,2)");
    // Plain expression left untouched.
    assertThat(pipelineNode.get("exprVar").asText()).isEqualTo("<+pipeline.variables.bar>");
  }

  @NotNull
  private ParameterField<List<FailureStrategyConfig>> getFailureStrategy(
      FailureStrategyActionConfig failureStrategyActionConfig) {
    return ParameterField.createValueField(
        Collections.singletonList(FailureStrategyConfig.builder()
                                      .onFailure(OnFailureConfig.builder().action(failureStrategyActionConfig).build())
                                      .build()));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateChildGraphToGenerate() {
    Map<String, GraphLayoutNodeDTO> graphLayoutNodeDTO = new HashMap<>();
    assertThat(pipelineStageHelper.validateChildGraphToGenerate(graphLayoutNodeDTO, "someKey", "foo")).isFalse();

    graphLayoutNodeDTO.put("key1", GraphLayoutNodeDTO.builder().build());
    assertThat(pipelineStageHelper.validateChildGraphToGenerate(graphLayoutNodeDTO, "key1", "foo")).isFalse();

    graphLayoutNodeDTO.put("key2", GraphLayoutNodeDTO.builder().nodeType("Custom").build());
    assertThat(pipelineStageHelper.validateChildGraphToGenerate(graphLayoutNodeDTO, "key2", "foo")).isFalse();

    graphLayoutNodeDTO.put("key3", GraphLayoutNodeDTO.builder().nodeType("Pipeline").build());
    assertThat(pipelineStageHelper.validateChildGraphToGenerate(graphLayoutNodeDTO, "key3", "foo")).isTrue();

    graphLayoutNodeDTO.put("key4", GraphLayoutNodeDTO.builder().nodeType("Pipeline").build());
    assertThat(pipelineStageHelper.validateChildGraphToGenerate(graphLayoutNodeDTO, "key41", "foo")).isFalse();

    graphLayoutNodeDTO.put("key5",
        GraphLayoutNodeDTO.builder()
            .nodeType("Pipeline")
            .strategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    assertThat(pipelineStageHelper.validateChildGraphToGenerate(graphLayoutNodeDTO, "key51", "key5")).isTrue();

    graphLayoutNodeDTO.put("key6", GraphLayoutNodeDTO.builder().nodeType("Pipeline").build());
    assertThat(pipelineStageHelper.validateChildGraphToGenerate(graphLayoutNodeDTO, "key61", null)).isFalse();

    graphLayoutNodeDTO.put("key7", GraphLayoutNodeDTO.builder().nodeType("Pipeline").build());
    assertThat(pipelineStageHelper.validateChildGraphToGenerate(graphLayoutNodeDTO, "key51", "key7")).isFalse();
  }
}
