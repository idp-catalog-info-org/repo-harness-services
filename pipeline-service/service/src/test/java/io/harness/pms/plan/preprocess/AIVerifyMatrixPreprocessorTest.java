/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.preprocess;

import static io.harness.rule.OwnerRule.ABHISHEK_ARYAN;
import static io.harness.rule.OwnerRule.MM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AIVerifyMatrixPreprocessorTest extends CategoryTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String ACCOUNT_ID = "account-id";
  private static final String ORG_ID = "org-id";
  private static final String PROJECT_ID = "project-id";
  private static final String EXECUTION_UUID = "exec-uuid";
  private static final String PIPELINE_ID = "pipeline-id";

  private AIVerifyMatrixPreprocessor preprocessor;

  @Before
  public void setUp() {
    preprocessor = new AIVerifyMatrixPreprocessor();
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testNullPipelineNode_returnsNull() {
    JsonNode result = preprocessor.preprocessPipelineYaml(
        null, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testNoAIVerifySteps_returnsOriginalNode() {
    String yaml = "{\"pipeline\":{\"stages\":[{\"stage\":{\"spec\":{\"execution\":{\"steps\":["
        + "{\"step\":{\"identifier\":\"run1\",\"type\":\"Run\",\"spec\":{\"command\":\"echo hi\"}}}"
        + "]}}}}]}}";
    JsonNode original = readTree(yaml);

    JsonNode result = preprocessor.preprocessPipelineYaml(
        original, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isSameAs(original);
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testMultiHealthSource_injectsMatrixAndRewritesRefs() {
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep(healthSources("test", "dynahs")));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isNotSameAs(pipelineNode);
    JsonNode stepNode = aiVerifyStep(result);
    assertThat(stepNode.path("strategy").path("matrix").path("hs").isArray()).isTrue();
    assertThat(stepNode.path("strategy").path("matrix").path("hs").get(0).asText()).isEqualTo("test");
    assertThat(stepNode.path("strategy").path("matrix").path("hs").get(1).asText()).isEqualTo("dynahs");
    assertThat(stepNode.path("strategy").path("matrix").path("nodeName").asText())
        .isEqualTo(AIVerifyMatrixPreprocessor.MATRIX_EXPRESSION);
    assertThat(stepNode.path("strategy").path(YamlNode.UUID_FIELD_NAME).isTextual()).isTrue();
    assertThat(stepNode.path("spec").path("healthSources").get(0).path("healthSourceRef").asText())
        .isEqualTo(AIVerifyMatrixPreprocessor.MATRIX_EXPRESSION);
    // The synthetic healthSources entry must be stamped with a UUID for FQN/log-key generation.
    assertThat(stepNode.path("spec").path("healthSources").get(0).path(YamlNode.UUID_FIELD_NAME).isTextual()).isTrue();
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testSingleHealthSource_injectsOneElementMatrix() {
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep(healthSources("test")));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    JsonNode hsAxis = aiVerifyStep(result).path("strategy").path("matrix").path("hs");
    assertThat(hsAxis).hasSize(1);
    assertThat(hsAxis.get(0).asText()).isEqualTo("test");
    assertThat(aiVerifyStep(result).path("strategy").path("matrix").path("nodeName").asText())
        .isEqualTo(AIVerifyMatrixPreprocessor.MATRIX_EXPRESSION);
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testExistingStrategy_withHealthSources_rejected() {
    String yaml = buildPipelineWithAiVerifyStep(healthSources("test", "dynahs"))
                      .replace("\"identifier\":\"AIVerifyNG_1\"",
                          "\"identifier\":\"AIVerifyNG_1\",\"strategy\":{\"matrix\":{\"hs\":[\"x\"]}}");
    JsonNode original = readTree(yaml);

    assertThatThrownBy(()
                           -> preprocessor.preprocessPipelineYaml(original, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                               EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("must not author a strategy");
  }

  @Test
  @Owner(developers = ABHISHEK_ARYAN)
  @Category(UnitTests.class)
  public void testExistingStrategy_withExpressionHealthSources_rejected() {
    // A user-authored strategy is rejected fail-fast even when healthSources is a whole-field expression (which by
    // itself would be a quiet skip). The backend owns strategy injection.
    String yaml = buildPipelineWithAiVerifyStep("\"<+pipeline.variables.healthSources.splitBy(',')>\"")
                      .replace("\"identifier\":\"AIVerifyNG_1\"",
                          "\"identifier\":\"AIVerifyNG_1\",\"strategy\":{\"matrix\":{\"hs\":[\"x\"]}}");
    JsonNode original = readTree(yaml);

    assertThatThrownBy(()
                           -> preprocessor.preprocessPipelineYaml(original, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                               EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("must not author a strategy");
  }

  @Test
  @Owner(developers = ABHISHEK_ARYAN)
  @Category(UnitTests.class)
  public void testExistingStrategy_withAlreadyInjectedCarrier_isIdempotentSkip() {
    // A repeat pass over already-processed YAML (our own injected strategy + [{healthSourceRef: <+matrix.hs>}]) must be
    // a quiet no-op, not a fail-fast, so idempotency is preserved.
    String yaml = buildPipelineWithAiVerifyStep(healthSources(AIVerifyMatrixPreprocessor.MATRIX_EXPRESSION))
                      .replace("\"identifier\":\"AIVerifyNG_1\"",
                          "\"identifier\":\"AIVerifyNG_1\",\"strategy\":{\"matrix\":{\"hs\":[\"<+matrix.hs>\"]}}");
    JsonNode original = readTree(yaml);

    JsonNode result = preprocessor.preprocessPipelineYaml(
        original, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isSameAs(original);
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testWholeFieldExpressionHealthSources_isPassedThrough() {
    /*
     * A whole-field expression cannot be expanded: the list size is unknown at plan-creation time and the
     * containerized step group init does not resolve an expression-valued matrix axis. It is left untouched so plan
     * creation rejects it with an actionable message.
     */
    JsonNode pipelineNode =
        readTree(buildPipelineWithAiVerifyStep("\"<+pipeline.variables.healthSources.splitBy(',')>\""));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isSameAs(pipelineNode);
    JsonNode stepNode = aiVerifyStep(result);
    assertThat(stepNode.path("strategy").isMissingNode()).isTrue();
    assertThat(stepNode.path("spec").path("healthSources").asText())
        .isEqualTo("<+pipeline.variables.healthSources.splitBy(',')>");
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testExpressionElementInHealthSources_injectsMatrixWithExpressions() {
    JsonNode pipelineNode = readTree(
        buildPipelineWithAiVerifyStep(healthSources("<+pipeline.variables.hs1>", "<+pipeline.variables.hs2>")));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isNotSameAs(pipelineNode);
    JsonNode stepNode = aiVerifyStep(result);
    assertThat(stepNode.path("strategy").path("matrix").path("hs").get(0).asText())
        .isEqualTo("<+pipeline.variables.hs1>");
    assertThat(stepNode.path("strategy").path("matrix").path("hs").get(1).asText())
        .isEqualTo("<+pipeline.variables.hs2>");
    assertThat(stepNode.path("spec").path("healthSources").get(0).path("healthSourceRef").asText())
        .isEqualTo(AIVerifyMatrixPreprocessor.MATRIX_EXPRESSION);
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testRuntimeInputElementsInHealthSources_injectsMatrix() {
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep(healthSources("<+input>", "<+input>")));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isNotSameAs(pipelineNode);
    JsonNode stepNode = aiVerifyStep(result);
    assertThat(stepNode.path("strategy").path("matrix").path("hs")).hasSize(2);
    assertThat(stepNode.path("strategy").path("matrix").path("hs").get(0).asText()).isEqualTo("<+input>");
    assertThat(stepNode.path("spec").path("healthSources").get(0).path("healthSourceRef").asText())
        .isEqualTo(AIVerifyMatrixPreprocessor.MATRIX_EXPRESSION);
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testMixedLiteralAndExpressionElements_injectsMatrix() {
    JsonNode pipelineNode =
        readTree(buildPipelineWithAiVerifyStep(healthSources("ddglogs", "<+pipeline.variables.hs2>")));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isNotSameAs(pipelineNode);
    JsonNode hsAxis = aiVerifyStep(result).path("strategy").path("matrix").path("hs");
    assertThat(hsAxis).hasSize(2);
    assertThat(hsAxis.get(0).asText()).isEqualTo("ddglogs");
    assertThat(hsAxis.get(1).asText()).isEqualTo("<+pipeline.variables.hs2>");
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testEmptyRefInHealthSources_isPassedThrough() {
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep(healthSources("", "ddglogs")));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isSameAs(pipelineNode);
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testNonObjectElementInHealthSources_isPassedThrough() {
    // Plain string elements are not objects carrying healthSourceRef; injection is skipped and the step left untouched.
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep("[\"hs1\",\"hs2\"]"));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isSameAs(pipelineNode);
    assertThat(aiVerifyStep(result).path("strategy").isMissingNode()).isTrue();
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testMissingHealthSourceRefKeyInElement_isPassedThrough() {
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep("[{\"foo\":\"bar\"}]"));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isSameAs(pipelineNode);
    assertThat(aiVerifyStep(result).path("strategy").isMissingNode()).isTrue();
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testNonTextualHealthSourceRefInElement_isPassedThrough() {
    // healthSourceRef present but not a scalar string (object value) -> skipped and left untouched.
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep("[{\"healthSourceRef\":{\"nested\":\"x\"}}]"));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isSameAs(pipelineNode);
    assertThat(aiVerifyStep(result).path("strategy").isMissingNode()).isTrue();
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testEmptyHealthSourcesList_isPassedThrough() {
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep("[]"));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isSameAs(pipelineNode);
    assertThat(aiVerifyStep(result).path("strategy").isMissingNode()).isTrue();
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testNestedInContainerStepGroup_isFoundAndRewritten() {
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep(healthSources("hs1", "hs2")));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    JsonNode stepNode = aiVerifyStep(result);
    assertThat(stepNode.path("type").asText()).isEqualTo(AIVerifyMatrixPreprocessor.AI_VERIFY_STEP_TYPE);
    assertThat(stepNode.path("strategy").path("matrix").path("hs")).hasSize(2);
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testIdempotency_secondPassDoesNotChangeYaml() {
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep(healthSources("test", "dynahs")));

    JsonNode firstPass = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);
    JsonNode secondPass = preprocessor.preprocessPipelineYaml(
        firstPass, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(secondPass).isSameAs(firstPass);
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testPlanCreationBlockSequence_updatesProcessedYamlAndRootField() throws Exception {
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep(healthSources("test", "dynahs")));
    YamlUtils.injectUuid(pipelineNode);
    String processedYaml = JsonPipelineUtils.getJsonString(pipelineNode);
    JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(processedYaml);
    JsonNode updatedJsonNode = preprocessor.preprocessPipelineYaml(
        pipelineJsonNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(updatedJsonNode).isNotSameAs(pipelineJsonNode);

    String updatedProcessedYaml = JsonPipelineUtils.getJsonString(updatedJsonNode);
    assertThat(updatedProcessedYaml).contains("strategy");
    assertThat(updatedProcessedYaml).contains("<+matrix.hs>");
    assertThat(updatedProcessedYaml).contains("nodeName");

    assertThat(YamlUtils.extractPipelineField(updatedProcessedYaml).getNode().getUuid()).isNotNull();
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testAiVerifyInStepGroupRollbackSteps_injectsMatrix() {
    // AIVerifyNG in a step group's rollbackSteps must also receive matrix injection.
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStepInRollback(healthSources("hs1", "hs2")));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isNotSameAs(pipelineNode);
    JsonNode stepNode = result.path("pipeline")
                            .path("stages")
                            .get(0)
                            .path("stage")
                            .path("spec")
                            .path("execution")
                            .path("steps")
                            .get(0)
                            .path("stepGroup")
                            .path("rollbackSteps")
                            .get(0)
                            .path("step");
    assertThat(stepNode.path("type").asText()).isEqualTo(AIVerifyMatrixPreprocessor.AI_VERIFY_STEP_TYPE);
    assertThat(stepNode.path("strategy").path("matrix").path("hs")).hasSize(2);
    assertThat(stepNode.path("strategy").path("matrix").path("hs").get(0).asText()).isEqualTo("hs1");
    assertThat(stepNode.path("spec").path("healthSources").get(0).path("healthSourceRef").asText())
        .isEqualTo(AIVerifyMatrixPreprocessor.MATRIX_EXPRESSION);
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testSingleHealthSource_withHealthSourceInputs_preservesInputsByRefMap() {
    String inputsJson = "{\"variables\":[{\"name\":\"window\",\"type\":\"Number\",\"value\":\"10\"}]}";
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep(healthSourcesWithInputs("hs_rt_test", inputsJson)));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    JsonNode stepNode = aiVerifyStep(result);
    JsonNode byRef = stepNode.path("spec").path(AIVerifyMatrixPreprocessor.HEALTH_SOURCE_INPUTS_BY_REF);
    assertThat(byRef.isObject()).isTrue();
    assertThat(byRef.path("hs_rt_test").path("variables").get(0).path("name").asText()).isEqualTo("window");
    assertThat(byRef.path("hs_rt_test").path("variables").get(0).path("value").asText()).isEqualTo("10");
    assertThat(stepNode.path("spec").path("healthSources").get(0).path("healthSourceRef").asText())
        .isEqualTo(AIVerifyMatrixPreprocessor.MATRIX_EXPRESSION);
    assertThat(stepNode.path("spec").path("healthSources").get(0).path("healthSourceInputs").isMissingNode()).isTrue();
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testMultiHealthSource_differentInputsPerRef_preservesBothInByRefMap() {
    String inputs1 = "{\"variables\":[{\"name\":\"window\",\"value\":\"10\"}]}";
    String inputs2 = "{\"variables\":[{\"name\":\"window\",\"value\":\"5\"}]}";
    String sourcesJson =
        "[" + healthSourceWithInputs("hs1", inputs1) + "," + healthSourceWithInputs("hs2", inputs2) + "]";
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep(sourcesJson));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    JsonNode byRef = aiVerifyStep(result).path("spec").path(AIVerifyMatrixPreprocessor.HEALTH_SOURCE_INPUTS_BY_REF);
    assertThat(byRef.path("hs1").path("variables").get(0).path("value").asText()).isEqualTo("10");
    assertThat(byRef.path("hs2").path("variables").get(0).path("value").asText()).isEqualTo("5");
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testExpressionRefHealthSourceInputs_preservesByExpressionKey() {
    String inputsJson = "{\"variables\":[{\"name\":\"window\",\"value\":\"10\"}]}";
    JsonNode pipelineNode =
        readTree(buildPipelineWithAiVerifyStep(healthSourcesWithInputs("<+pipeline.variables.hsRef>", inputsJson)));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    JsonNode byRef = aiVerifyStep(result).path("spec").path(AIVerifyMatrixPreprocessor.HEALTH_SOURCE_INPUTS_BY_REF);
    assertThat(byRef.has("<+pipeline.variables.hsRef>")).isTrue();
    assertThat(byRef.path("<+pipeline.variables.hsRef>").path("variables").get(0).path("value").asText())
        .isEqualTo("10");
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testNoHealthSourceInputs_doesNotAddByRefMap() {
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep(healthSources("test")));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(
        aiVerifyStep(result).path("spec").path(AIVerifyMatrixPreprocessor.HEALTH_SOURCE_INPUTS_BY_REF).isMissingNode())
        .isTrue();
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testIdempotency_preservesHealthSourceInputsByRef() {
    String inputsJson = "{\"variables\":[{\"name\":\"window\",\"value\":\"10\"}]}";
    JsonNode pipelineNode = readTree(buildPipelineWithAiVerifyStep(healthSourcesWithInputs("hs1", inputsJson)));

    JsonNode firstPass = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);
    JsonNode secondPass = preprocessor.preprocessPipelineYaml(
        firstPass, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(secondPass).isSameAs(firstPass);
    assertThat(aiVerifyStep(secondPass)
                   .path("spec")
                   .path(AIVerifyMatrixPreprocessor.HEALTH_SOURCE_INPUTS_BY_REF)
                   .path("hs1")
                   .path("variables")
                   .get(0)
                   .path("value")
                   .asText())
        .isEqualTo("10");
  }

  @Test
  @Owner(developers = MM)
  @Category(UnitTests.class)
  public void testExistingStrategy_withInlineHealthSourceInputs_rejected() {
    String inputsJson = "{\"variables\":[{\"name\":\"window\",\"value\":\"10\"}]}";
    String yaml = buildPipelineWithAiVerifyStep(healthSourcesWithInputs("test", inputsJson))
                      .replace("\"identifier\":\"AIVerifyNG_1\"",
                          "\"identifier\":\"AIVerifyNG_1\",\"strategy\":{\"matrix\":{\"hs\":[\"x\"]}}");
    JsonNode original = readTree(yaml);

    assertThatThrownBy(()
                           -> preprocessor.preprocessPipelineYaml(original, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                               EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("must not author a strategy");
  }

  private static JsonNode aiVerifyStep(JsonNode pipelineResult) {
    return pipelineResult.path("pipeline")
        .path("stages")
        .get(0)
        .path("stage")
        .path("spec")
        .path("execution")
        .path("steps")
        .get(0)
        .path("stepGroup")
        .path("steps")
        .get(0)
        .path("step");
  }

  // Builds a healthSources array of objects, e.g. [{"healthSourceRef":"hs1"},{"healthSourceRef":"hs2"}]
  private static String healthSources(String... refs) {
    return Stream.of(refs)
        .map(ref -> "{\"healthSourceRef\":\"" + ref + "\"}")
        .collect(Collectors.joining(",", "[", "]"));
  }

  private static String healthSourceWithInputs(String ref, String healthSourceInputsJson) {
    return "{\"healthSourceRef\":\"" + ref + "\",\"healthSourceInputs\":" + healthSourceInputsJson + "}";
  }

  private static String healthSourcesWithInputs(String ref, String healthSourceInputsJson) {
    return "[" + healthSourceWithInputs(ref, healthSourceInputsJson) + "]";
  }

  private static String buildPipelineWithAiVerifyStep(String healthSourcesJson) {
    return "{\"pipeline\":{\"stages\":[{\"stage\":{\"identifier\":\"deploy\",\"type\":\"Deployment\","
        + "\"spec\":{\"execution\":{\"steps\":[{\"stepGroup\":{\"identifier\":\"aiVerifyGroup\",\"steps\":["
        + "{\"step\":{\"type\":\"AIVerifyNG\",\"identifier\":\"AIVerifyNG_1\",\"name\":\"AIVerifyNG_1\","
        + "\"spec\":{\"healthSources\":" + healthSourcesJson + ",\"dataCollectionWindow\":\"2m\"}}}"
        + "]}}]}}}}]}}";
  }

  // Builds a pipeline whose AIVerifyNG step lives in a step group's rollbackSteps (with an unrelated step in steps).
  private static String buildPipelineWithAiVerifyStepInRollback(String healthSourcesJson) {
    return "{\"pipeline\":{\"stages\":[{\"stage\":{\"identifier\":\"deploy\",\"type\":\"Deployment\","
        + "\"spec\":{\"execution\":{\"steps\":[{\"stepGroup\":{\"identifier\":\"aiVerifyGroup\","
        + "\"steps\":[{\"step\":{\"type\":\"Run\",\"identifier\":\"run1\",\"spec\":{\"command\":\"echo hi\"}}}],"
        + "\"rollbackSteps\":[{\"step\":{\"type\":\"AIVerifyNG\",\"identifier\":\"AIVerifyNG_1\","
        + "\"name\":\"AIVerifyNG_1\",\"spec\":{\"healthSources\":" + healthSourcesJson
        + ",\"dataCollectionWindow\":\"2m\"}}}]}}]}}}}]}}";
  }

  private static JsonNode readTree(String json) {
    try {
      return OBJECT_MAPPER.readTree(json);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
