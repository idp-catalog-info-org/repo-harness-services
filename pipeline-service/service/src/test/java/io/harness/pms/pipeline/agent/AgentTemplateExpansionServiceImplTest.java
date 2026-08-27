/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipeline.agent;

import static io.harness.rule.OwnerRule.FJUNIOR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.agent.expansion.AgentTemplateProcessor;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.template.remote.TemplateResourceClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class AgentTemplateExpansionServiceImplTest extends CategoryTest {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private TemplateResourceClient templateServiceClient;
  @Mock private ConnectorInputsMapper connectorInputsMapper;
  @Mock private AgentTemplateProcessor mockAgentTemplateProcessor;

  private AgentTemplateProcessor processor;
  private AgentTemplateExpansionServiceImpl service;
  private AgentTemplateExpansionServiceImpl serviceWithMockAgentTemplateProcessor;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    processor = new AgentTemplateProcessor(connectorInputsMapper);
    service = new AgentTemplateExpansionServiceImpl(templateServiceClient, processor);
    serviceWithMockAgentTemplateProcessor =
        new AgentTemplateExpansionServiceImpl(templateServiceClient, mockAgentTemplateProcessor);
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testExpandAgentStep_happyPathBuildsV1RequestAndReturnsProcessedJson() throws Exception {
    String accountId = "acc1";
    String orgId = "org1";
    String projectId = "proj1";
    String templateId = "my-template";
    Map<String, JsonNode> userInputs = Map.of("inputA", objectMapper.valueToTree("valueA"));
    String syntheticYaml = "pipeline:\n  stages: []";
    String mergedYaml = "pipeline:\n  stages:\n    - steps: []";
    JsonNode expectedExpandedStep = objectMapper.readTree("{\"step\":{\"type\":\"Run\"}}");

    when(mockAgentTemplateProcessor.buildSyntheticV1Yaml(templateId, userInputs)).thenReturn(syntheticYaml);
    mockApplyTemplatesResponse(mergedYaml);
    when(mockAgentTemplateProcessor.processExpandedTemplate(
             mergedYaml, templateId, accountId, orgId, projectId, userInputs))
        .thenReturn(expectedExpandedStep);

    JsonNode result =
        serviceWithMockAgentTemplateProcessor.expandAgentStep(accountId, orgId, projectId, templateId, userInputs);

    assertThat(result).isEqualTo(expectedExpandedStep);

    ArgumentCaptor<TemplateApplyRequestDTO> requestCaptor = ArgumentCaptor.forClass(TemplateApplyRequestDTO.class);
    verify(templateServiceClient)
        .applyTemplatesOnGivenYamlV2(eq(accountId), eq(orgId), eq(projectId), isNull(), isNull(), isNull(), isNull(),
            isNull(), isNull(), isNull(), isNull(), eq("true"), requestCaptor.capture(), eq(false));

    TemplateApplyRequestDTO requestDTO = requestCaptor.getValue();
    assertThat(requestDTO.getOriginalEntityYaml()).isEqualTo(syntheticYaml);
    assertThat(requestDTO.isCheckForAccess()).isFalse();
    assertThat(requestDTO.isGetMergedYamlWithTemplateField()).isFalse();
    assertThat(requestDTO.getYamlVersion()).isEqualTo(HarnessYamlVersion.V1);

    verify(mockAgentTemplateProcessor)
        .processExpandedTemplate(mergedYaml, templateId, accountId, orgId, projectId, userInputs);
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testExpandAgentStep_rethrowsInvalidRequestExceptionFromProcessor() throws Exception {
    String templateId = "my-template";
    Map<String, JsonNode> userInputs = Map.of("a", objectMapper.valueToTree("b"));
    when(mockAgentTemplateProcessor.buildSyntheticV1Yaml(templateId, userInputs)).thenReturn("syntheticYaml");
    mockApplyTemplatesResponse("mergedYaml");

    InvalidRequestException expected = new InvalidRequestException("processor validation failed");
    when(mockAgentTemplateProcessor.processExpandedTemplate(
             "mergedYaml", templateId, "acc1", "org1", "proj1", userInputs))
        .thenThrow(expected);

    assertThatThrownBy(
        () -> serviceWithMockAgentTemplateProcessor.expandAgentStep("acc1", "org1", "proj1", templateId, userInputs))
        .isSameAs(expected);
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testExpandAgentStep_wrapsTemplateClientFailureWithTemplateContext() {
    String templateId = "my-template";
    Map<String, JsonNode> userInputs = Map.of("a", objectMapper.valueToTree("b"));
    when(mockAgentTemplateProcessor.buildSyntheticV1Yaml(templateId, userInputs)).thenReturn("syntheticYaml");
    when(templateServiceClient.applyTemplatesOnGivenYamlV2(anyString(), any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), anyString(), any(TemplateApplyRequestDTO.class), anyBoolean()))
        .thenThrow(new RuntimeException("template service unavailable"));

    assertThatThrownBy(
        () -> serviceWithMockAgentTemplateProcessor.expandAgentStep("acc1", "org1", "proj1", templateId, userInputs))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Failed to expand agent template 'my-template' via applyTemplates")
        .hasMessageContaining("template service unavailable");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testExpandAgentStep_wrapsUnexpectedProcessorFailure() throws Exception {
    String templateId = "my-template";
    Map<String, JsonNode> userInputs = Map.of("a", objectMapper.valueToTree("b"));
    when(mockAgentTemplateProcessor.buildSyntheticV1Yaml(templateId, userInputs)).thenReturn("syntheticYaml");
    mockApplyTemplatesResponse("mergedYaml");
    when(mockAgentTemplateProcessor.processExpandedTemplate(
             "mergedYaml", templateId, "acc1", "org1", "proj1", userInputs))
        .thenThrow(new RuntimeException("processing failed"));

    assertThatThrownBy(
        () -> serviceWithMockAgentTemplateProcessor.expandAgentStep("acc1", "org1", "proj1", templateId, userInputs))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Failed to process agent template 'my-template'")
        .hasMessageContaining("processing failed");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testExpandAgentStep_handlesNullMergedYamlResponse_asInvalidRequest() throws Exception {
    Map<String, JsonNode> userInputs = Map.of("a", objectMapper.valueToTree("b"));
    mockApplyTemplatesResponse(null);

    assertThatThrownBy(() -> service.expandAgentStep("acc1", "org1", "proj1", "my-template", userInputs))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Agent template 'my-template' expansion returned empty response");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testExpandAgentStep_buildSyntheticYamlFailure_bubblesWithoutWrapping() {
    String templateId = "my-template";
    Map<String, JsonNode> userInputs = Map.of("a", objectMapper.valueToTree("b"));
    when(mockAgentTemplateProcessor.buildSyntheticV1Yaml(templateId, userInputs))
        .thenThrow(new RuntimeException("invalid inputs"));

    assertThatThrownBy(
        () -> serviceWithMockAgentTemplateProcessor.expandAgentStep("acc1", "org1", "proj1", templateId, userInputs))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("invalid inputs");

    verify(templateServiceClient, never())
        .applyTemplatesOnGivenYamlV2(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            anyString(), any(TemplateApplyRequestDTO.class), anyBoolean());
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testExpandConnectorEnvVars_injectsConnectorVars() throws Exception {
    String expandedJson = "{"
        + "\"group\": {\"steps\": [{"
        + "  \"name\": \"Agent\", \"id\": \"agent\","
        + "  \"run\": {"
        + "    \"script\": \"run-agent\","
        + "    \"env\": {"
        + "      \"PLUGIN_HARNESS_CONNECTOR\": \"account.myAnthropicConn\","
        + "      \"OTHER_VAR\": \"keep-me\""
        + "    },"
        + "    \"container\": {\"image\": \"agent:latest\"}"
        + "  }"
        + "}]}"
        + "}";
    JsonNode expandedStep = objectMapper.readTree(expandedJson);

    Map<String, String> connectorVars = new HashMap<>();
    connectorVars.put("PLUGIN_ANTHROPIC_NAME", "myAnthropicConn");
    connectorVars.put("PLUGIN_ANTHROPIC_USE_BEDROCK", "1");
    connectorVars.put("CLAUDE_CODE_USE_BEDROCK", "1");
    connectorVars.put("AWS_BEARER_TOKEN_BEDROCK", "<+secrets.getValue('bedrockKey')>");
    connectorVars.put("AWS_REGION", "us-east-1");
    when(
        connectorInputsMapper.getConnectorEnvVariables(eq("myAnthropicConn"), eq("acc1"), isNull(), isNull(), isNull()))
        .thenReturn(connectorVars);

    processor.expandConnectorEnvVars(expandedStep, "acc1", null, null);

    JsonNode envNode = expandedStep.path("group").path("steps").get(0).path("run").path("env");
    assertThat(envNode.path("PLUGIN_ANTHROPIC_NAME").asText()).isEqualTo("myAnthropicConn");
    assertThat(envNode.path("AWS_BEARER_TOKEN_BEDROCK").asText()).isEqualTo("<+secrets.getValue('bedrockKey')>");
    assertThat(envNode.path("AWS_REGION").asText()).isEqualTo("us-east-1");
    assertThat(envNode.path("CLAUDE_CODE_USE_BEDROCK").asText()).isEqualTo("1");
    assertThat(envNode.path("OTHER_VAR").asText()).isEqualTo("keep-me");
    assertThat(envNode.path("PLUGIN_HARNESS_CONNECTOR").asText()).isEqualTo("account.myAnthropicConn");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testExpandConnectorEnvVars_noConnectorEnvVar() throws Exception {
    String expandedJson = "{"
        + "\"group\": {\"steps\": [{"
        + "  \"name\": \"Agent\", \"id\": \"agent\","
        + "  \"run\": {"
        + "    \"script\": \"echo hello\","
        + "    \"env\": {\"SOME_VAR\": \"value\"},"
        + "    \"container\": {\"image\": \"img\"}"
        + "  }"
        + "}]}"
        + "}";
    JsonNode expandedStep = objectMapper.readTree(expandedJson);

    processor.expandConnectorEnvVars(expandedStep, "acc1", null, null);

    JsonNode envNode = expandedStep.path("group").path("steps").get(0).path("run").path("env");
    assertThat(envNode.path("SOME_VAR").asText()).isEqualTo("value");
    assertThat(envNode.has("PLUGIN_ANTHROPIC_NAME")).isFalse();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testExpandConnectorEnvVars_connectorInWith() throws Exception {
    String expandedJson = "{"
        + "\"group\": {\"steps\": [{"
        + "  \"name\": \"Agent\", \"id\": \"agent\","
        + "  \"run\": {"
        + "    \"script\": \"run-agent\","
        + "    \"with\": {"
        + "      \"PLUGIN_HARNESS_CONNECTOR\": \"org.githubConn\""
        + "    },"
        + "    \"container\": {\"image\": \"agent:latest\"}"
        + "  }"
        + "}]}"
        + "}";
    JsonNode expandedStep = objectMapper.readTree(expandedJson);

    Map<String, String> connectorVars = new HashMap<>();
    connectorVars.put("PLUGIN_GITHUB_NAME", "githubConn");
    connectorVars.put("PLUGIN_GITHUB_TOKEN", "<+secrets.getValue('ghToken')>");
    when(connectorInputsMapper.getConnectorEnvVariables(eq("githubConn"), eq("acc1"), eq("org1"), isNull(), isNull()))
        .thenReturn(connectorVars);

    processor.expandConnectorEnvVars(expandedStep, "acc1", "org1", null);

    JsonNode envNode = expandedStep.path("group").path("steps").get(0).path("run").path("env");
    assertThat(envNode.path("PLUGIN_GITHUB_NAME").asText()).isEqualTo("githubConn");
    assertThat(envNode.path("PLUGIN_GITHUB_TOKEN").asText()).isEqualTo("<+secrets.getValue('ghToken')>");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testExpandConnectorEnvVars_multipleSteps() throws Exception {
    String expandedJson = "{"
        + "\"group\": {\"steps\": ["
        + "  {\"name\": \"Step1\", \"id\": \"s1\", \"run\": {"
        + "    \"script\": \"echo 1\","
        + "    \"env\": {\"PLUGIN_HARNESS_CONNECTOR\": \"account.conn1\"},"
        + "    \"container\": {\"image\": \"img\"}"
        + "  }},"
        + "  {\"name\": \"Step2\", \"id\": \"s2\", \"run\": {"
        + "    \"script\": \"echo 2\","
        + "    \"env\": {\"FOO\": \"bar\"},"
        + "    \"container\": {\"image\": \"img\"}"
        + "  }}"
        + "]}"
        + "}";
    JsonNode expandedStep = objectMapper.readTree(expandedJson);

    Map<String, String> connectorVars = new HashMap<>();
    connectorVars.put("PLUGIN_OPENAI_NAME", "conn1");
    connectorVars.put("OPENAI_API_KEY", "<+secrets.getValue('openaiKey')>");
    when(connectorInputsMapper.getConnectorEnvVariables(eq("conn1"), eq("acc1"), isNull(), isNull(), isNull()))
        .thenReturn(connectorVars);

    processor.expandConnectorEnvVars(expandedStep, "acc1", null, null);

    JsonNode env1 = expandedStep.path("group").path("steps").get(0).path("run").path("env");
    assertThat(env1.path("OPENAI_API_KEY").asText()).isEqualTo("<+secrets.getValue('openaiKey')>");

    JsonNode env2 = expandedStep.path("group").path("steps").get(1).path("run").path("env");
    assertThat(env2.path("FOO").asText()).isEqualTo("bar");
    assertThat(env2.has("OPENAI_API_KEY")).isFalse();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testExpandConnectorEnvVars_explicitEnvTakesPrecedence() throws Exception {
    String expandedJson = "{"
        + "\"group\": {\"steps\": [{"
        + "  \"name\": \"Agent\", \"id\": \"agent\","
        + "  \"run\": {"
        + "    \"script\": \"run-agent\","
        + "    \"env\": {"
        + "      \"PLUGIN_HARNESS_CONNECTOR\": \"account.myConn\","
        + "      \"AWS_REGION\": \"eu-west-1\""
        + "    },"
        + "    \"container\": {\"image\": \"agent:latest\"}"
        + "  }"
        + "}]}"
        + "}";
    JsonNode expandedStep = objectMapper.readTree(expandedJson);

    Map<String, String> connectorVars = new HashMap<>();
    connectorVars.put("AWS_REGION", "us-east-1");
    connectorVars.put("AWS_BEARER_TOKEN_BEDROCK", "token123");
    when(connectorInputsMapper.getConnectorEnvVariables(eq("myConn"), eq("acc1"), isNull(), isNull(), isNull()))
        .thenReturn(connectorVars);

    processor.expandConnectorEnvVars(expandedStep, "acc1", null, null);

    JsonNode envNode = expandedStep.path("group").path("steps").get(0).path("run").path("env");
    assertThat(envNode.path("AWS_REGION").asText()).isEqualTo("eu-west-1");
    assertThat(envNode.path("AWS_BEARER_TOKEN_BEDROCK").asText()).isEqualTo("token123");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testExpandConnectorEnvVars_connectorResolutionFailsGracefully() throws Exception {
    String expandedJson = "{"
        + "\"group\": {\"steps\": [{"
        + "  \"name\": \"Agent\", \"id\": \"agent\","
        + "  \"run\": {"
        + "    \"script\": \"run-agent\","
        + "    \"env\": {\"PLUGIN_HARNESS_CONNECTOR\": \"account.badConn\"},"
        + "    \"container\": {\"image\": \"agent:latest\"}"
        + "  }"
        + "}]}"
        + "}";
    JsonNode expandedStep = objectMapper.readTree(expandedJson);

    when(connectorInputsMapper.getConnectorEnvVariables(any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("Connector not found"));

    processor.expandConnectorEnvVars(expandedStep, "acc1", null, null);

    JsonNode envNode = expandedStep.path("group").path("steps").get(0).path("run").path("env");
    assertThat(envNode.path("PLUGIN_HARNESS_CONNECTOR").asText()).isEqualTo("account.badConn");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testExpandConnectorEnvVars_projectScopedConnector() throws Exception {
    String expandedJson = "{"
        + "\"group\": {\"steps\": [{"
        + "  \"name\": \"Agent\", \"id\": \"agent\","
        + "  \"run\": {"
        + "    \"script\": \"run-agent\","
        + "    \"env\": {\"PLUGIN_HARNESS_CONNECTOR\": \"myProjectConn\"},"
        + "    \"container\": {\"image\": \"agent:latest\"}"
        + "  }"
        + "}]}"
        + "}";
    JsonNode expandedStep = objectMapper.readTree(expandedJson);

    Map<String, String> connectorVars = new HashMap<>();
    connectorVars.put("PLUGIN_NAME", "myProjectConn");
    when(connectorInputsMapper.getConnectorEnvVariables(
             eq("myProjectConn"), eq("acc1"), eq("org1"), eq("proj1"), isNull()))
        .thenReturn(connectorVars);

    processor.expandConnectorEnvVars(expandedStep, "acc1", "org1", "proj1");

    JsonNode envNode = expandedStep.path("group").path("steps").get(0).path("run").path("env");
    assertThat(envNode.path("PLUGIN_NAME").asText()).isEqualTo("myProjectConn");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testExpandConnectorEnvVars_nullExpandedStep() {
    processor.expandConnectorEnvVars(null, "acc1", null, null);
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testFailsOnUnresolvedExpression_singleField() throws Exception {
    String json = "{\"run\": {\"env\": {\"ANTHROPIC_MODEL\": \"${{inputs.modelName}}\"}}}";
    JsonNode node = objectMapper.readTree(json);

    assertThatThrownBy(() -> processor.failOnUnresolvedExpressions(node))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("run.env.ANTHROPIC_MODEL")
        .hasMessageContaining("${{inputs.modelName}}");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testFailsOnUnresolvedExpression_multipleFields() throws Exception {
    String json = "{"
        + "\"run\": {"
        + "  \"env\": {\"ANTHROPIC_MODEL\": \"${{inputs.modelName}}\"},"
        + "  \"with\": {\"task\": \"Do ${{inputs.PipelineID}}\"}"
        + "}}";
    JsonNode node = objectMapper.readTree(json);

    assertThatThrownBy(() -> processor.failOnUnresolvedExpressions(node))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("run.env.ANTHROPIC_MODEL")
        .hasMessageContaining("${{inputs.modelName}}")
        .hasMessageContaining("run.with.task")
        .hasMessageContaining("${{inputs.PipelineID}}");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testNoErrorWhenAllExpressionsResolved() throws Exception {
    String json = "{"
        + "\"run\": {"
        + "  \"env\": {\"ANTHROPIC_MODEL\": \"claude-sonnet-4-6\"},"
        + "  \"with\": {\"task\": \"Do something\"},"
        + "  \"container\": {\"image\": \"agent:latest\"}"
        + "}}";
    JsonNode node = objectMapper.readTree(json);

    processor.failOnUnresolvedExpressions(node);
  }

  private void mockApplyTemplatesResponse(String mergedYaml) throws Exception {
    Call<ResponseDTO<TemplateMergeResponseDTO>> callRequest = mock(Call.class);
    when(templateServiceClient.applyTemplatesOnGivenYamlV2(anyString(), any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), anyString(), any(TemplateApplyRequestDTO.class), anyBoolean()))
        .thenReturn(callRequest);
    when(callRequest.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedYaml).build())));
  }
}
