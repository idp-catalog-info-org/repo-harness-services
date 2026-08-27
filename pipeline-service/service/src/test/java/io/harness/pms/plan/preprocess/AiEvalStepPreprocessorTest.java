/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.preprocess;

import static io.harness.rule.OwnerRule.AI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.agent.convert.V1ToV0StepGroupConverter;
import io.harness.agent.expansion.AgentTemplateExpansionService;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AiEvalStepPreprocessorTest extends CategoryTest {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private AgentTemplateExpansionService agentTemplateExpansionService;
  @Mock private V1ToV0StepGroupConverter v1ToV0StepGroupConverter;

  private AiEvalStepPreprocessor preprocessor;

  private static final String ACCOUNT_ID = "account-id";
  private static final String ORG_ID = "org-id";
  private static final String PROJECT_ID = "project-id";
  private static final String EXECUTION_UUID = "exec-uuid";
  private static final String PIPELINE_ID = "pipeline-id";

  @Before
  public void setUp() {
    preprocessor = new AiEvalStepPreprocessor(agentTemplateExpansionService, v1ToV0StepGroupConverter);
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testNullPipelineNode_returnsNull() {
    JsonNode result = preprocessor.preprocessPipelineYaml(
        null, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testNoAiEvalSteps_returnsOriginalNode() {
    String yaml = "{\"pipeline\":{\"stages\":[{\"stage\":{\"spec\":{\"execution\":{\"steps\":["
        + "{\"step\":{\"identifier\":\"run1\",\"type\":\"Run\",\"spec\":{\"command\":\"echo hi\"}}}"
        + "]},\"infrastructure\":{\"type\":\"KubernetesDirect\"}}}}]}}";
    JsonNode original = readTree(yaml);

    JsonNode result = preprocessor.preprocessPipelineYaml(
        original, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isSameAs(original);
    verify(agentTemplateExpansionService, never())
        .expandAgentStep(anyString(), anyString(), anyString(), anyString(), anyMap());
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testAiEvalStep_k8sInfra_expandedToStepGroup() throws Exception {
    String yaml = buildPipelineWithAiEvalStep("KubernetesDirect");
    JsonNode pipelineNode = readTree(yaml);

    JsonNode expandedTemplate =
        objectMapper.readTree("{\"group\":{\"steps\":[{\"id\":\"runEval\",\"name\":\"Run Eval\","
            + "\"run\":{\"script\":\"echo running "
            + "evals\",\"container\":{\"image\":\"harnessdev/ai-evals-runner:0.6.0\"}}}]}}");
    when(agentTemplateExpansionService.expandAgentStep(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("aiEvalStep"), anyMap()))
        .thenReturn(expandedTemplate);

    ObjectNode v0Step = objectMapper.createObjectNode();
    v0Step.put("identifier", "runEval");
    v0Step.put("name", "Run Eval");
    v0Step.put("type", "Run");
    ObjectNode spec = objectMapper.createObjectNode();
    spec.put("command", "echo running evals");
    v0Step.set("spec", spec);
    ExecutionWrapperConfig wrapper = ExecutionWrapperConfig.builder().step(v0Step).build();

    when(v1ToV0StepGroupConverter.convertToV0Steps(eq(expandedTemplate), any(), eq(true))).thenReturn(List.of(wrapper));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isNotSameAs(pipelineNode);

    JsonNode firstStep =
        result.path("pipeline").path("stages").get(0).path("stage").path("spec").path("execution").path("steps").get(0);
    assertThat(firstStep.has("stepGroup")).isTrue();
    assertThat(firstStep.path("stepGroup").path("identifier").asText()).isEqualTo("aiEval1");
    assertThat(firstStep.path("stepGroup").path("name").asText()).isEqualTo("Run AI Eval");

    verify(v1ToV0StepGroupConverter).convertToV0Steps(eq(expandedTemplate), any(), eq(true));
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testAiEvalStep_vmInfra_passesIsK8sFalse() throws Exception {
    String yaml = buildPipelineWithAiEvalStep("VM");
    JsonNode pipelineNode = readTree(yaml);

    JsonNode expandedTemplate = objectMapper.readTree(
        "{\"group\":{\"steps\":[{\"id\":\"runEval\",\"name\":\"Run Eval\",\"run\":{\"script\":\"echo\"}}]}}");
    when(agentTemplateExpansionService.expandAgentStep(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("aiEvalStep"), anyMap()))
        .thenReturn(expandedTemplate);

    ObjectNode v0Step = objectMapper.createObjectNode();
    v0Step.put("identifier", "runEval");
    v0Step.put("type", "Run");
    ExecutionWrapperConfig wrapper = ExecutionWrapperConfig.builder().step(v0Step).build();

    when(v1ToV0StepGroupConverter.convertToV0Steps(eq(expandedTemplate), any(), eq(false)))
        .thenReturn(List.of(wrapper));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isNotSameAs(pipelineNode);
    verify(v1ToV0StepGroupConverter).convertToV0Steps(eq(expandedTemplate), any(), eq(false));
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testAiEvalStep_templateInputsMappedCorrectly() throws Exception {
    String yaml = "{\"pipeline\":{\"stages\":[{\"stage\":{\"spec\":{\"execution\":{\"steps\":["
        + "{\"step\":{\"identifier\":\"eval1\",\"name\":\"Eval\",\"type\":\"AiEval\","
        + "\"spec\":{"
        + "\"evalId\":\"eval-123\","
        + "\"suiteId\":\"suite-456\","
        + "\"suitePath\":\"./suite.yaml\","
        + "\"apiKey\":\"<+secrets.getValue(\\\"api_key\\\")>\","
        + "\"apiEndpoint\":\"https://app.harness.io\","
        + "\"concurrency\":\"10\","
        + "\"repoFlags\":\"repo1=/workspace/repo1\","
        + "\"targetId\":\"target-789\","
        + "\"datasetId\":\"dataset-012\","
        + "\"llmConnectorRef\":\"account.connector_OpenAI_1234\","
        + "\"model\":\"gpt-4o\""
        + "}}}"
        + "]},\"infrastructure\":{\"type\":\"KubernetesDirect\"}}}}]}}";
    JsonNode pipelineNode = readTree(yaml);

    JsonNode expandedTemplate = objectMapper.readTree(
        "{\"group\":{\"steps\":[{\"id\":\"s1\",\"name\":\"S1\",\"run\":{\"script\":\"echo\"}}]}}");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, JsonNode>> inputsCaptor = ArgumentCaptor.forClass(Map.class);
    when(agentTemplateExpansionService.expandAgentStep(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("aiEvalStep"), inputsCaptor.capture()))
        .thenReturn(expandedTemplate);

    ObjectNode v0Step = objectMapper.createObjectNode();
    v0Step.put("identifier", "s1");
    v0Step.put("type", "Run");
    when(v1ToV0StepGroupConverter.convertToV0Steps(any(), any(), anyBoolean()))
        .thenReturn(List.of(ExecutionWrapperConfig.builder().step(v0Step).build()));

    preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    Map<String, JsonNode> capturedInputs = inputsCaptor.getValue();
    assertThat(capturedInputs).containsKey("eval_id");
    assertThat(capturedInputs.get("eval_id").asText()).isEqualTo("eval-123");
    assertThat(capturedInputs).containsKey("suite_id");
    assertThat(capturedInputs.get("suite_id").asText()).isEqualTo("suite-456");
    assertThat(capturedInputs).containsKey("suite_path");
    assertThat(capturedInputs.get("suite_path").asText()).isEqualTo("./suite.yaml");
    assertThat(capturedInputs).containsKey("api_key");
    assertThat(capturedInputs.get("api_key").asText()).isEqualTo("<+secrets.getValue(\"api_key\")>");
    assertThat(capturedInputs).doesNotContainKey("run_id");
    assertThat(capturedInputs).doesNotContainKey("suite_run_id");
    assertThat(capturedInputs).containsKey("api_endpoint");
    assertThat(capturedInputs.get("api_endpoint").asText()).isEqualTo("https://app.harness.io");
    assertThat(capturedInputs).containsKey("concurrency");
    assertThat(capturedInputs.get("concurrency").asText()).isEqualTo("10");
    assertThat(capturedInputs).containsKey("repo_flags");
    assertThat(capturedInputs).containsKey("target_id");
    assertThat(capturedInputs.get("target_id").asText()).isEqualTo("target-789");
    assertThat(capturedInputs).containsKey("dataset_id");
    assertThat(capturedInputs.get("dataset_id").asText()).isEqualTo("dataset-012");
    assertThat(capturedInputs).containsKey("llm_connector_ref");
    assertThat(capturedInputs.get("llm_connector_ref").asText()).isEqualTo("account.connector_OpenAI_1234");
    assertThat(capturedInputs).containsKey("model");
    assertThat(capturedInputs.get("model").asText()).isEqualTo("gpt-4o");
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testAiEvalStep_optionalFieldsOmitted_notInInputs() throws Exception {
    String yaml = "{\"pipeline\":{\"stages\":[{\"stage\":{\"spec\":{\"execution\":{\"steps\":["
        + "{\"step\":{\"identifier\":\"eval1\",\"name\":\"Eval\",\"type\":\"AiEval\","
        + "\"spec\":{\"evalId\":\"eval-123\",\"apiKey\":\"mykey\"}}}"
        + "]},\"infrastructure\":{\"type\":\"KubernetesDirect\"}}}}]}}";
    JsonNode pipelineNode = readTree(yaml);

    JsonNode expandedTemplate = objectMapper.readTree(
        "{\"group\":{\"steps\":[{\"id\":\"s1\",\"name\":\"S1\",\"run\":{\"script\":\"echo\"}}]}}");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, JsonNode>> inputsCaptor = ArgumentCaptor.forClass(Map.class);
    when(agentTemplateExpansionService.expandAgentStep(
             anyString(), anyString(), anyString(), eq("aiEvalStep"), inputsCaptor.capture()))
        .thenReturn(expandedTemplate);

    ObjectNode v0Step = objectMapper.createObjectNode();
    v0Step.put("identifier", "s1");
    v0Step.put("type", "Run");
    when(v1ToV0StepGroupConverter.convertToV0Steps(any(), any(), anyBoolean()))
        .thenReturn(List.of(ExecutionWrapperConfig.builder().step(v0Step).build()));

    preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    Map<String, JsonNode> capturedInputs = inputsCaptor.getValue();
    assertThat(capturedInputs).containsKey("eval_id");
    assertThat(capturedInputs).containsKey("api_key");
    assertThat(capturedInputs).doesNotContainKey("suite_id");
    assertThat(capturedInputs).doesNotContainKey("suite_path");
    assertThat(capturedInputs).doesNotContainKey("repo_flags");
    assertThat(capturedInputs).doesNotContainKey("target_id");
    assertThat(capturedInputs).doesNotContainKey("dataset_id");
    assertThat(capturedInputs).doesNotContainKey("llm_connector_ref");
    assertThat(capturedInputs).doesNotContainKey("model");
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testAiEvalStep_expansionFailure_throwsException() throws Exception {
    String yaml = buildPipelineWithAiEvalStep("KubernetesDirect");
    JsonNode pipelineNode = readTree(yaml);

    when(agentTemplateExpansionService.expandAgentStep(anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenThrow(new RuntimeException("Template not found"));

    assertThatThrownBy(()
                           -> preprocessor.preprocessPipelineYaml(pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                               EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Template not found");
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testAiEvalStep_emptyExpandedSteps_skipped() throws Exception {
    String yaml = buildPipelineWithAiEvalStep("KubernetesDirect");
    JsonNode pipelineNode = readTree(yaml);

    JsonNode expandedTemplate = objectMapper.readTree("{\"group\":{\"steps\":[]}}");
    when(agentTemplateExpansionService.expandAgentStep(anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(expandedTemplate);
    when(v1ToV0StepGroupConverter.convertToV0Steps(any(), any(), anyBoolean())).thenReturn(Collections.emptyList());

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isSameAs(pipelineNode);
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testAiEvalStep_inParallelBlock_expanded() throws Exception {
    String yaml = "{\"pipeline\":{\"stages\":[{\"stage\":{\"spec\":{\"execution\":{\"steps\":["
        + "{\"parallel\":["
        + "{\"step\":{\"identifier\":\"eval1\",\"name\":\"Eval\",\"type\":\"AiEval\","
        + "\"spec\":{\"evalId\":\"eval-1\",\"apiKey\":\"key1\"}}}"
        + "]}"
        + "]},\"infrastructure\":{\"type\":\"KubernetesDirect\"}}}}]}}";
    JsonNode pipelineNode = readTree(yaml);

    JsonNode expandedTemplate = objectMapper.readTree(
        "{\"group\":{\"steps\":[{\"id\":\"s1\",\"name\":\"S1\",\"run\":{\"script\":\"echo\"}}]}}");
    when(agentTemplateExpansionService.expandAgentStep(anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(expandedTemplate);

    ObjectNode v0Step = objectMapper.createObjectNode();
    v0Step.put("identifier", "s1");
    v0Step.put("type", "Run");
    when(v1ToV0StepGroupConverter.convertToV0Steps(any(), any(), eq(true)))
        .thenReturn(List.of(ExecutionWrapperConfig.builder().step(v0Step).build()));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isNotSameAs(pipelineNode);
    JsonNode parallelBlock =
        result.path("pipeline").path("stages").get(0).path("stage").path("spec").path("execution").path("steps").get(0);
    assertThat(parallelBlock.path("parallel").get(0).has("stepGroup")).isTrue();
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testAiEvalStep_inContainerizedStepGroup_emitsAiEvalGroup() throws Exception {
    String yaml = "{\"pipeline\":{\"stages\":[{\"stage\":{\"spec\":{\"execution\":{\"steps\":["
        + "{\"stepGroup\":{\"identifier\":\"sg1\",\"name\":\"SG\","
        + "\"stepGroupInfra\":{\"type\":\"KubernetesDirect\"},"
        + "\"steps\":["
        + "{\"step\":{\"identifier\":\"eval1\",\"name\":\"Eval\",\"type\":\"AiEval\","
        + "\"spec\":{\"evalId\":\"eval-1\",\"apiKey\":\"key1\"}}}"
        + "]}}"
        + "]}}}}]}}";
    JsonNode pipelineNode = readTree(yaml);

    JsonNode expandedTemplate = objectMapper.readTree(
        "{\"group\":{\"steps\":[{\"id\":\"s1\",\"name\":\"S1\",\"run\":{\"script\":\"echo\"}}]}}");
    when(agentTemplateExpansionService.expandAgentStep(anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(expandedTemplate);

    ObjectNode v0Step = objectMapper.createObjectNode();
    v0Step.put("identifier", "s1");
    v0Step.put("type", "Run");
    when(v1ToV0StepGroupConverter.convertToV0Steps(any(), any(), eq(true)))
        .thenReturn(List.of(ExecutionWrapperConfig.builder().step(v0Step).build()));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isNotSameAs(pipelineNode);
    JsonNode sgSteps = result.path("pipeline")
                           .path("stages")
                           .get(0)
                           .path("stage")
                           .path("spec")
                           .path("execution")
                           .path("steps")
                           .get(0)
                           .path("stepGroup")
                           .path("steps");
    assertThat(sgSteps.get(0).has("step")).isTrue();
    JsonNode aiEvalGroupStep = sgSteps.get(0).path("step");
    assertThat(aiEvalGroupStep.path("type").asText()).isEqualTo("AiEvalGroup");
    assertThat(aiEvalGroupStep.path("identifier").asText()).isEqualTo("eval1");
    assertThat(aiEvalGroupStep.path("name").asText()).isEqualTo("Eval");
    JsonNode expandedSteps = aiEvalGroupStep.path("spec").path("steps");
    assertThat(expandedSteps.isArray()).isTrue();
    assertThat(expandedSteps.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testNoStagesNode_returnsOriginal() {
    String yaml = "{\"pipeline\":{\"identifier\":\"test\"}}";
    JsonNode pipelineNode = readTree(yaml);

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isSameAs(pipelineNode);
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testAiEvalStep_doesNotExpandAgentSteps() {
    String yaml = "{\"pipeline\":{\"stages\":[{\"stage\":{\"spec\":{\"execution\":{\"steps\":["
        + "{\"step\":{\"identifier\":\"agent1\",\"type\":\"Agent\",\"name\":\"Agent\","
        + "\"spec\":{\"agentName\":\"my-agent\"}}}"
        + "]},\"infrastructure\":{\"type\":\"KubernetesDirect\"}}}}]}}";
    JsonNode pipelineNode = readTree(yaml);

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isSameAs(pipelineNode);
    verify(agentTemplateExpansionService, never())
        .expandAgentStep(anyString(), anyString(), anyString(), anyString(), anyMap());
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testAiEvalStep_inParallelStages_expanded() throws Exception {
    String yaml = "{\"pipeline\":{\"stages\":[{\"parallel\":["
        + "{\"stage\":{\"spec\":{\"execution\":{\"steps\":["
        + "{\"step\":{\"identifier\":\"eval1\",\"name\":\"Eval\",\"type\":\"AiEval\","
        + "\"spec\":{\"evalId\":\"eval-1\",\"apiKey\":\"key1\"}}}"
        + "]},\"infrastructure\":{\"type\":\"VM\"}}}}"
        + "]}]}}";
    JsonNode pipelineNode = readTree(yaml);

    JsonNode expandedTemplate = objectMapper.readTree(
        "{\"group\":{\"steps\":[{\"id\":\"s1\",\"name\":\"S1\",\"run\":{\"script\":\"echo\"}}]}}");
    when(agentTemplateExpansionService.expandAgentStep(anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(expandedTemplate);

    ObjectNode v0Step = objectMapper.createObjectNode();
    v0Step.put("identifier", "s1");
    v0Step.put("type", "Run");
    when(v1ToV0StepGroupConverter.convertToV0Steps(any(), any(), eq(false)))
        .thenReturn(List.of(ExecutionWrapperConfig.builder().step(v0Step).build()));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isNotSameAs(pipelineNode);
    verify(v1ToV0StepGroupConverter).convertToV0Steps(any(), any(), eq(false));
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testAiEvalStep_expandedWithParallelWrappers() throws Exception {
    String yaml = buildPipelineWithAiEvalStep("KubernetesDirect");
    JsonNode pipelineNode = readTree(yaml);

    JsonNode expandedTemplate = objectMapper.readTree(
        "{\"group\":{\"steps\":[{\"id\":\"s1\",\"name\":\"S1\",\"run\":{\"script\":\"echo\"}}]}}");
    when(agentTemplateExpansionService.expandAgentStep(anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(expandedTemplate);

    ObjectNode parallelContent = objectMapper.createObjectNode();
    parallelContent.put("identifier", "p1");
    ExecutionWrapperConfig parallelWrapper = ExecutionWrapperConfig.builder().parallel(parallelContent).build();

    ObjectNode stepGroupContent = objectMapper.createObjectNode();
    stepGroupContent.put("identifier", "sg1");
    ExecutionWrapperConfig stepGroupWrapper = ExecutionWrapperConfig.builder().stepGroup(stepGroupContent).build();

    when(v1ToV0StepGroupConverter.convertToV0Steps(any(), any(), eq(true)))
        .thenReturn(List.of(parallelWrapper, stepGroupWrapper));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isNotSameAs(pipelineNode);
    JsonNode steps =
        result.path("pipeline").path("stages").get(0).path("stage").path("spec").path("execution").path("steps").get(0);
    assertThat(steps.path("stepGroup").path("steps").isArray()).isTrue();
    assertThat(steps.path("stepGroup").path("steps").size()).isEqualTo(2);
    assertThat(steps.path("stepGroup").path("steps").get(0).has("parallel")).isTrue();
    assertThat(steps.path("stepGroup").path("steps").get(1).has("stepGroup")).isTrue();
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testAiEvalStep_inRollbackSteps_expanded() throws Exception {
    String yaml = "{\"pipeline\":{\"stages\":[{\"stage\":{\"spec\":{\"execution\":{"
        + "\"steps\":["
        + "{\"step\":{\"identifier\":\"run1\",\"type\":\"Run\",\"spec\":{\"command\":\"echo hi\"}}}"
        + "],"
        + "\"rollbackSteps\":["
        + "{\"step\":{\"identifier\":\"rollbackEval\",\"name\":\"Rollback Eval\",\"type\":\"AiEval\","
        + "\"spec\":{\"evalId\":\"eval-rollback\",\"apiKey\":\"key\"}}}"
        + "]"
        + "},\"infrastructure\":{\"type\":\"KubernetesDirect\"}}}}]}}";
    JsonNode pipelineNode = readTree(yaml);

    JsonNode expandedTemplate = objectMapper.readTree(
        "{\"group\":{\"steps\":[{\"id\":\"s1\",\"name\":\"S1\",\"run\":{\"script\":\"echo\"}}]}}");
    when(agentTemplateExpansionService.expandAgentStep(anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(expandedTemplate);

    ObjectNode v0Step = objectMapper.createObjectNode();
    v0Step.put("identifier", "s1");
    v0Step.put("type", "Run");
    when(v1ToV0StepGroupConverter.convertToV0Steps(any(), any(), eq(true)))
        .thenReturn(List.of(ExecutionWrapperConfig.builder().step(v0Step).build()));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isNotSameAs(pipelineNode);
    JsonNode rollbackSteps = result.path("pipeline")
                                 .path("stages")
                                 .get(0)
                                 .path("stage")
                                 .path("spec")
                                 .path("execution")
                                 .path("rollbackSteps");
    assertThat(rollbackSteps.isArray()).isTrue();
    assertThat(rollbackSteps.get(0).has("stepGroup")).isTrue();
    assertThat(rollbackSteps.get(0).path("stepGroup").path("identifier").asText()).isEqualTo("rollbackEval");
  }

  private String buildPipelineWithAiEvalStep(String infraType) {
    return "{\"pipeline\":{\"stages\":[{\"stage\":{\"spec\":{\"execution\":{\"steps\":["
        + "{\"step\":{\"identifier\":\"aiEval1\",\"name\":\"Run AI Eval\",\"type\":\"AiEval\","
        + "\"spec\":{\"evalId\":\"eval-123\",\"apiKey\":\"mykey\"}}}"
        + "]},\"infrastructure\":{\"type\":\"" + infraType + "\"}}}}]}}";
  }

  private static JsonNode readTree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
