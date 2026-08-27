/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.rule.OwnerRule.AYUSHMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SOUMYO_PURKAYASTHA;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.expressions.evaluator.AmbianceExpressionEvaluator;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.secrets.ExpressionsObserverFactory;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.VariableResolverTracker;
import io.harness.expression.common.ExpressionMode;
import io.harness.ngtriggers.expressions.TriggerExpressionEvaluator;
import io.harness.observer.Subject;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class YamlExpressionResolveHelperTest extends CategoryTest {
  @Mock private PlanExecutionService planExecutionService;
  @Inject private InputSetValidatorFactory inputSetValidatorFactory;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;

  @Mock private ExpressionsObserverFactory expressionsObserverFactory;

  @InjectMocks YamlExpressionResolveHelper yamlExpressionResolveHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void resolveExpressionForArrayElement() throws IOException {
    String arrayTypeString = "pipeline: \n"
        + " name: pipelineName\n"
        + " delegateSelector: \n"
        + "   - value1\n"
        + "   - <+pipeline.name>\n";

    EngineExpressionEvaluator expressionEvaluator =
        prepareEngineExpressionEvaluator(YamlUtils.read(arrayTypeString, Map.class));
    YamlField yamlField = YamlUtils.readTree(arrayTypeString);
    YamlNode parentNode = yamlField.getNode().getField("pipeline").getNode().getField("delegateSelector").getNode();
    ArrayNode parentArrayNode = (ArrayNode) parentNode.getCurrJsonNode();

    // case1: value passed is not expression
    yamlExpressionResolveHelper.resolveExpressionForArrayElement(
        parentNode, 0, parentArrayNode.get(0).textValue(), expressionEvaluator);
    assertThat(parentArrayNode.get(0).textValue()).isEqualTo("value1");

    // case2: value passed is expression
    yamlExpressionResolveHelper.resolveExpressionForArrayElement(
        parentNode, 1, parentArrayNode.get(1).textValue(), expressionEvaluator);
    assertThat(parentArrayNode.get(1).textValue()).isEqualTo("pipelineName");
    assertThat(parentArrayNode.get(0).textValue()).isEqualTo("value1");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void resolveExpressionsInYamlTestWithNullNodeExecution() {
    Optional<NodeExecution> nodeExecution = Optional.ofNullable(null);
    doReturn(nodeExecution).when(nodeExecutionService).getPipelineNodeExecutionWithProjections(any(), any());
    String yaml = yamlExpressionResolveHelper.resolveExpressionsInYaml(
        "", "planExecutionId", ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, HarnessYamlVersion.V0);
    assertEquals(yaml, "");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void resolveExpressionsInYamlTest() throws IOException {
    String arrayTypeString = "pipeline: \n"
        + " name: pipelineName\n"
        + " delegateSelector: \n"
        + "   - value1\n"
        + "   - <+pipeline.name>\n";
    EngineExpressionEvaluator expressionEvaluator =
        prepareEngineExpressionEvaluator(YamlUtils.read(arrayTypeString, Map.class));
    Optional<NodeExecution> nodeExecution =
        Optional.ofNullable(NodeExecution.builder().ambiance(Ambiance.newBuilder().build()).build());
    doReturn(nodeExecution).when(nodeExecutionService).getPipelineNodeExecutionWithProjections(any(), any());
    doReturn(expressionEvaluator).when(pmsEngineExpressionService).prepareExpressionEvaluator(any());
    doReturn(new Subject<>()).when(expressionsObserverFactory).getSubjectForSecretsRuntimeUsages(any());
    assertThatCode(()
                       -> yamlExpressionResolveHelper.resolveExpressionsInYaml(arrayTypeString, "planExecutionId",
                           ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, HarnessYamlVersion.V0))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void resolveExpressionsInYamlTestWillNullPipelineYaml() throws IOException {
    String arrayTypeString = "pipeline: null";
    EngineExpressionEvaluator expressionEvaluator =
        prepareEngineExpressionEvaluator(YamlUtils.read(arrayTypeString, Map.class));
    Optional<NodeExecution> nodeExecution =
        Optional.ofNullable(NodeExecution.builder().ambiance(Ambiance.newBuilder().build()).build());
    doReturn(nodeExecution).when(nodeExecutionService).getPipelineNodeExecutionWithProjections(any(), any());
    doReturn(expressionEvaluator).when(pmsEngineExpressionService).prepareExpressionEvaluator(any());
    doReturn(new Subject<>()).when(expressionsObserverFactory).getSubjectForSecretsRuntimeUsages(any());
    assertThatCode(()
                       -> yamlExpressionResolveHelper.resolveExpressionsInYaml(arrayTypeString, "planExecutionId",
                           ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, HarnessYamlVersion.V0))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void resolveExpressionsInYamlPassingAmbianceTest() throws IOException {
    String arrayTypeString = "pipeline: \n"
        + " name: pipelineName\n"
        + " delegateSelector: \n"
        + "   - value1\n"
        + "   - <+pipeline.name>\n";
    EngineExpressionEvaluator expressionEvaluator =
        prepareEngineExpressionEvaluator(YamlUtils.read(arrayTypeString, Map.class));
    Ambiance ambiance = Ambiance.newBuilder().build();
    doReturn(expressionEvaluator).when(pmsEngineExpressionService).prepareExpressionEvaluator(any());
    assertThatCode(()
                       -> yamlExpressionResolveHelper.resolveExpressionsInYaml(arrayTypeString,
                           ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, ambiance, HarnessYamlVersion.V0))
        .doesNotThrowAnyException();
  }

  private EngineExpressionEvaluator prepareEngineExpressionEvaluator(Map<String, Object> contextMap) {
    SampleEngineExpressionEvaluator evaluator = new SampleEngineExpressionEvaluator();
    on(evaluator).set("planExecutionService", planExecutionService);
    on(evaluator).set("inputSetValidatorFactory", inputSetValidatorFactory);
    on(evaluator).set("pmsFeatureFlagService", pmsFeatureFlagService);
    on(evaluator).set("expressionsObserverFactory", expressionsObserverFactory);

    if (EmptyPredicate.isEmpty(contextMap)) {
      return evaluator;
    }

    for (Map.Entry<String, Object> entry : contextMap.entrySet()) {
      evaluator.addToContextMap(entry.getKey(), entry.getValue());
    }
    return evaluator;
  }

  public static class SampleEngineExpressionEvaluator extends AmbianceExpressionEvaluator {
    public SampleEngineExpressionEvaluator() {
      super((VariableResolverTracker) null, Ambiance.newBuilder().build(), null, false, null, false);
    }

    public void addToContextMap(@NotNull String name, @NotNull Object object) {
      addToContext(name, object);
    }
    @Override
    protected void initialize() {
      super.initialize();
    }

    @NotNull
    protected List<String> fetchPrefixes() {
      return ImmutableList.of("obj", "");
    }
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void resolveExpressionsInYaml_JsonArrayForHosts_withFeatureFlagEnabled() {
    String yamlString = "pipeline:\n  spec:\n    hosts: <+pipeline.stages.Deploy.variables.hostList>.split(',')>";

    EngineExpressionEvaluator expressionEvaluator = new SampleEngineExpressionEvaluator() {
      @Override
      public String renderExpression(String expression, ExpressionMode mode) {
        return "<+pipeline.stages.Deploy.variables.hostList>.split(',')>".equals(expression)
            ? "[\"host1.example.com\",\"host2.example.com\"]"
            : expression;
      }
    };

    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "test-account").build();
    doReturn(expressionEvaluator).when(pmsEngineExpressionService).prepareExpressionEvaluator(any());
    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled("test-account", FeatureName.CDS_PIPELINE_YAML_EXPRESSION_JSON_ARRAY_PARSING);

    String result = yamlExpressionResolveHelper.resolveExpressionsInYaml(
        yamlString, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, ambiance, HarnessYamlVersion.V0);

    assertThat(result).contains("hosts:");
    assertThat(result).contains("- host1.example.com");
    assertThat(result).contains("- host2.example.com");
    assertThat(result).doesNotContain("[\"host1.example.com\",\"host2.example.com\"]");
    assertThat(result.lines().filter(line -> line.trim().startsWith("- host")).count()).isEqualTo(2);
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void resolveExpressionsInYaml_RegularFieldsUnchanged_withFeatureFlagEnabled() {
    String yamlString = "pipeline:\n  name: <+pipeline.name>";

    EngineExpressionEvaluator expressionEvaluator = new SampleEngineExpressionEvaluator() {
      @Override
      public String renderExpression(String expression, ExpressionMode mode) {
        return "<+pipeline.name>".equals(expression) ? "my-pipeline" : expression;
      }
    };

    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "test-account").build();
    doReturn(expressionEvaluator).when(pmsEngineExpressionService).prepareExpressionEvaluator(any());
    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled("test-account", FeatureName.CDS_PIPELINE_YAML_EXPRESSION_JSON_ARRAY_PARSING);

    String result = yamlExpressionResolveHelper.resolveExpressionsInYaml(
        yamlString, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, ambiance, HarnessYamlVersion.V0);

    assertThat(result).contains("name: my-pipeline");
    assertThat(result).doesNotContain("<+pipeline.name>");
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void resolveExpressionsInYaml_NonArrayJsonIgnored_withFeatureFlagEnabled() {
    String yamlString = "pipeline:\n  config: <+pipeline.variables.config>";

    EngineExpressionEvaluator expressionEvaluator = new SampleEngineExpressionEvaluator() {
      @Override
      public String renderExpression(String expression, ExpressionMode mode) {
        return "<+pipeline.variables.config>".equals(expression) ? "{\"key\": \"value\"}" : expression;
      }
    };

    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "test-account").build();
    doReturn(expressionEvaluator).when(pmsEngineExpressionService).prepareExpressionEvaluator(any());
    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled("test-account", FeatureName.CDS_PIPELINE_YAML_EXPRESSION_JSON_ARRAY_PARSING);

    String result = yamlExpressionResolveHelper.resolveExpressionsInYaml(
        yamlString, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, ambiance, HarnessYamlVersion.V0);

    assertThat(result).contains("config: \"{\\\"key\\\": \\\"value\\\"}\"");
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void resolveExpressionsInYaml_JsonArrayAsString_withFeatureFlagDisabled() {
    String yamlString = "pipeline:\n  spec:\n    hosts: <+pipeline.stages.Deploy.variables.hostList>.split(',')>";

    EngineExpressionEvaluator expressionEvaluator = new SampleEngineExpressionEvaluator() {
      @Override
      public String renderExpression(String expression, ExpressionMode mode) {
        return "<+pipeline.stages.Deploy.variables.hostList>.split(',')>".equals(expression)
            ? "[\"host1.example.com\",\"host2.example.com\"]"
            : expression;
      }
    };

    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "test-account").build();
    doReturn(expressionEvaluator).when(pmsEngineExpressionService).prepareExpressionEvaluator(any());
    doReturn(false)
        .when(pmsFeatureFlagService)
        .isEnabled("test-account", FeatureName.CDS_PIPELINE_YAML_EXPRESSION_JSON_ARRAY_PARSING);

    String result = yamlExpressionResolveHelper.resolveExpressionsInYaml(
        yamlString, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, ambiance, HarnessYamlVersion.V0);

    assertThat(result).contains("hosts: \"[\\\"host1.example.com\\\",\\\"host2.example.com\\\"]\"");
    assertThat(result).doesNotContain("- host1.example.com");
    assertThat(result).doesNotContain("- host2.example.com");
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void resolveExpressionsInYamlWithEvaluatorResolvesTriggerExpression() {
    String yamlString = "pipeline:\n"
        + "  variables:\n"
        + "  - name: var1\n"
        + "    type: String\n"
        + "    value: <+trigger.payload.input>\n";
    TriggerExpressionEvaluator evaluator =
        new TriggerExpressionEvaluator(null, null, "{\"input\": \"hello-world\"}", null);

    String result = yamlExpressionResolveHelper.resolveExpressionsInYaml(yamlString, evaluator, "accountId");

    assertThat(result).contains("value: hello-world");
    assertThat(result).doesNotContain("<+trigger.payload.input>");
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void resolveExpressionsInYamlWithEvaluatorResolvesMethodChainOnTriggerExpression() {
    String yamlString = "pipeline:\n"
        + "  variables:\n"
        + "  - name: var1\n"
        + "    type: String\n"
        + "    value: <+trigger.payload.input.replace('-','')>\n";
    TriggerExpressionEvaluator evaluator =
        new TriggerExpressionEvaluator(null, null, "{\"input\": \"hello-world\"}", null);

    String result = yamlExpressionResolveHelper.resolveExpressionsInYaml(yamlString, evaluator, "accountId");

    assertThat(result).contains("value: helloworld");
    assertThat(result).doesNotContain("hello-world");
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void resolveExpressionsInYamlWithEvaluatorLeavesNonTriggerExpressionsUnchanged() {
    String yamlString = "pipeline:\n"
        + "  variables:\n"
        + "  - name: a\n"
        + "    value: <+secrets.getValue('mySecret')>\n"
        + "  - name: b\n"
        + "    value: <+input>\n"
        + "  - name: c\n"
        + "    value: <+pipeline.variables.other>\n";
    TriggerExpressionEvaluator evaluator =
        new TriggerExpressionEvaluator(null, null, "{\"input\": \"hello-world\"}", null);

    String result = yamlExpressionResolveHelper.resolveExpressionsInYaml(yamlString, evaluator, "accountId");

    assertThat(result).contains("<+secrets.getValue('mySecret')>");
    assertThat(result).contains("<+input>");
    assertThat(result).contains("<+pipeline.variables.other>");
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void resolveExpressionsInYamlWithEvaluatorWritesSpecialCharactersSafely() throws IOException {
    String yamlString = "pipeline:\n"
        + "  variables:\n"
        + "  - name: var1\n"
        + "    type: String\n"
        + "    value: <+trigger.payload.input>\n";
    TriggerExpressionEvaluator evaluator =
        new TriggerExpressionEvaluator(null, null, "{\"input\": \"key: value\\nsecond line\"}", null);

    String result = yamlExpressionResolveHelper.resolveExpressionsInYaml(yamlString, evaluator, "accountId");

    Map<String, Object> resultMap = YamlUtils.read(result, Map.class);
    Map<String, Object> pipeline = (Map<String, Object>) resultMap.get("pipeline");
    List<Map<String, Object>> variables = (List<Map<String, Object>>) pipeline.get("variables");
    assertThat(variables.get(0).get("value")).isEqualTo("key: value\nsecond line");
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void resolveExpressionsInYamlWithEvaluatorResolvesEmbeddedTriggerExpression() {
    String yamlString = "pipeline:\n"
        + "  variables:\n"
        + "  - name: var1\n"
        + "    type: String\n"
        + "    value: prefix-<+trigger.payload.input>-suffix\n";
    TriggerExpressionEvaluator evaluator =
        new TriggerExpressionEvaluator(null, null, "{\"input\": \"hello-world\"}", null);

    String result = yamlExpressionResolveHelper.resolveExpressionsInYaml(yamlString, evaluator, "accountId");

    assertThat(result).contains("value: prefix-hello-world-suffix");
  }
}
