/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.preprocess;

import static io.harness.rule.OwnerRule.AI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.agent.convert.V1ToV0StepGroupConverter;
import io.harness.agent.expansion.AgentTemplateExpansionService;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AiEvalStepPreprocessorCDTest extends CategoryTest {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private AgentTemplateExpansionService agentTemplateExpansionService;
  private V1ToV0StepGroupConverter v1ToV0StepGroupConverter;
  private AiEvalStepPreprocessor preprocessor;

  @Before
  public void setUp() {
    v1ToV0StepGroupConverter = new V1ToV0StepGroupConverter();
    preprocessor = new AiEvalStepPreprocessor(agentTemplateExpansionService, v1ToV0StepGroupConverter);
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testContainerizedStepGroupEmitsAiEvalGroup() throws Exception {
    String v1ExpandedJson = "{\"group\": {\"steps\": ["
        + "{\"name\": \"Run Eval\", \"id\": \"runEval\", \"run\": {\"script\": \"echo running\", \"container\": "
        + "{\"image\": "
        + "\"harnessdev/ai-evals-runner:0.6.0\"}}},"
        + "{\"name\": \"Collect Reports\", \"id\": \"collectReports\", \"run\": {\"script\": \"echo done\", "
        + "\"container\": {\"image\": "
        + "\"alpine\"}}}"
        + "]}}";
    when(agentTemplateExpansionService.expandAgentStep(eq("acctId"), eq("orgId"), eq("projId"), anyString(), anyMap()))
        .thenReturn(objectMapper.readTree(v1ExpandedJson));

    String pipelineJson = "{"
        + "\"pipeline\": {"
        + "  \"stages\": [{"
        + "    \"stage\": {"
        + "      \"spec\": {"
        + "        \"execution\": {"
        + "          \"steps\": [{"
        + "            \"stepGroup\": {"
        + "              \"identifier\": \"sg1\","
        + "              \"stepGroupInfra\": {\"type\": \"KubernetesDirect\"},"
        + "              \"steps\": [{"
        + "                \"step\": {"
        + "                  \"type\": \"AiEval\","
        + "                  \"identifier\": \"eval1\","
        + "                  \"name\": \"My Eval\","
        + "                  \"spec\": {"
        + "                    \"evalId\": \"eval-123\","
        + "                    \"apiKey\": \"mykey\""
        + "                  }"
        + "                }"
        + "              }]"
        + "            }"
        + "          }]"
        + "        }"
        + "      }"
        + "    }"
        + "  }]"
        + "}"
        + "}";

    JsonNode pipelineNode = objectMapper.readTree(pipelineJson);
    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, "acctId", "orgId", "projId", "execId", "pipeId", ExecutionMode.NORMAL);

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

    assertThat(sgSteps.isArray()).isTrue();
    assertThat(sgSteps.size()).isEqualTo(1);
    JsonNode aiEvalGroupWrapper = sgSteps.get(0);
    assertThat(aiEvalGroupWrapper.has("step")).isTrue();

    JsonNode aiEvalGroupStep = aiEvalGroupWrapper.path("step");
    assertThat(aiEvalGroupStep.path("type").asText()).isEqualTo("AiEvalGroup");
    assertThat(aiEvalGroupStep.path("identifier").asText()).isEqualTo("eval1");
    assertThat(aiEvalGroupStep.path("name").asText()).isEqualTo("My Eval");

    JsonNode expandedSteps = aiEvalGroupStep.path("spec").path("steps");
    assertThat(expandedSteps.isArray()).isTrue();
    assertThat(expandedSteps.size()).isEqualTo(2);
    assertThat(expandedSteps.get(0).has("step")).isTrue();
    assertThat(expandedSteps.get(1).has("step")).isTrue();
  }

  @Test
  @Owner(developers = AI)
  @Category(UnitTests.class)
  public void testCIStageWrapsInStepGroup() throws Exception {
    String v1ExpandedJson = "{\"group\": {\"steps\": ["
        + "{\"name\": \"Run Eval\", \"id\": \"runEval\", \"run\": {\"script\": \"echo running\", \"container\": "
        + "{\"image\": "
        + "\"harnessdev/ai-evals-runner:0.6.0\"}}}"
        + "]}}";
    when(agentTemplateExpansionService.expandAgentStep(eq("acctId"), eq("orgId"), eq("projId"), anyString(), anyMap()))
        .thenReturn(objectMapper.readTree(v1ExpandedJson));

    String pipelineJson = "{"
        + "\"pipeline\": {"
        + "  \"stages\": [{"
        + "    \"stage\": {"
        + "      \"spec\": {"
        + "        \"execution\": {"
        + "          \"steps\": [{"
        + "            \"step\": {"
        + "              \"type\": \"AiEval\","
        + "              \"identifier\": \"eval1\","
        + "              \"name\": \"My Eval\","
        + "              \"spec\": {\"evalId\": \"eval-123\", \"apiKey\": \"mykey\"}"
        + "            }"
        + "          }]"
        + "        }"
        + "      }"
        + "    }"
        + "  }]"
        + "}"
        + "}";

    JsonNode pipelineNode = objectMapper.readTree(pipelineJson);
    JsonNode result = preprocessor.preprocessPipelineYaml(
        pipelineNode, "acctId", "orgId", "projId", "execId", "pipeId", ExecutionMode.NORMAL);

    JsonNode firstWrapper =
        result.path("pipeline").path("stages").get(0).path("stage").path("spec").path("execution").path("steps").get(0);

    assertThat(firstWrapper.has("stepGroup")).isTrue();
    assertThat(firstWrapper.path("stepGroup").path("steps").isArray()).isTrue();
  }
}
