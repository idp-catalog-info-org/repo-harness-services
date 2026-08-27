/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.pms.yaml.YAMLFieldNameConstants.PIPELINE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.TRIGGER;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.yaml.schema.beans.SchemaConstants.PIPELINE_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.PROPERTIES_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.TRIGGER_NODE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.merger.fqn.helpers.FQNMapGenerator;
import io.harness.pms.pipeline.service.yamlschema.SchemaFetcher;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.yaml.individualschema.PipelineSchemaParserFactory;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PMSYamlSchemaServiceImplTest {
  @Mock private SchemaFetcher schemaFetcher;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock YamlSchemaValidator yamlSchemaValidator;

  @Mock PipelineSchemaParserFactory pipelineSchemaParserFactory;

  @InjectMocks private PMSYamlSchemaServiceImpl pmsYamlSchemaService;
  @Mock private ExecutorService yamlSchemaExecutor;

  private static final String ACC_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PRJ_ID = "projectId";

  @Before
  public void setUp() throws ExecutionException, InterruptedException, TimeoutException {
    MockitoAnnotations.initMocks(this);
    pmsYamlSchemaService = new PMSYamlSchemaServiceImpl(
        yamlSchemaValidator, pmsFeatureFlagHelper, schemaFetcher, yamlSchemaExecutor, null);
    Reflect.on(pmsYamlSchemaService).set("pipelineSchemaParserFactory", pipelineSchemaParserFactory);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateUniqueFqn() {
    String yaml = "pipeline:\n"
        + "  identifier: cipipeline\n"
        + "  name: Integration Pipeline\n";
    try (MockedStatic<FQNMapGenerator> aMock = mockStatic(FQNMapGenerator.class)) {
      pmsYamlSchemaService.validateUniqueFqn(yaml, "0");
      aMock.verify(() -> FQNMapGenerator.generateFQNMap(any(JsonNode.class), anyString()));
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateUniqueFqnInvalidYaml() {
    try (MockedStatic<YamlUtils> yamlUtils = mockStatic(YamlUtils.class)) {
      yamlUtils.when(() -> YamlUtils.getErrorNodePartialFQN(any())).thenReturn("DUMMY");
      yamlUtils.when(() -> YamlUtils.readTree(any())).thenThrow(new IOException());
      Assertions.assertThatCode(() -> pmsYamlSchemaService.validateUniqueFqn("", "0"))
          .isInstanceOf(InvalidYamlException.class)
          .hasMessage("Invalid yaml in node [DUMMY]");
    }
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testValidateUniqueFqnInYamlWithDuplicateRef() {
    String yaml = "services:\n"
        + "  values:\n"
        + "    - serviceRef: svc1\n"
        + "    - serviceRef: svc1";
    assertThatThrownBy(() -> pmsYamlSchemaService.validateUniqueFqn(yaml, "0"))
        .isInstanceOf(io.harness.yaml.validator.InvalidYamlException.class);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void staticSchemaForTrigger() throws IOException {
    JsonNode expected = readJsonNode("trigger-short.json");
    when(schemaFetcher.fetchTriggerStaticYamlSchema("v0")).thenReturn(expected);

    final JsonNode result = pmsYamlSchemaService.getStaticSchemaForAllEntities(TRIGGER, null, null, "v0");

    assertThat(result).isNotNull();
    assertThat(result.get(PROPERTIES_NODE).get(TRIGGER_NODE).get("$ref").asText())
        .isEqualTo("#/definitions/trigger/trigger");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void staticSchemaForPipeline() throws IOException {
    JsonNode expected = readJsonNode("pipeline-short.json");
    when(schemaFetcher.fetchPipelineStaticYamlSchema("v0")).thenReturn(expected);

    final JsonNode result = pmsYamlSchemaService.getStaticSchemaForAllEntities(PIPELINE, null, null, "v0");

    assertThat(result).isNotNull();
    assertThat(result.get(PROPERTIES_NODE).get(PIPELINE_NODE).get("$ref").asText())
        .isEqualTo("#/definitions/pipeline/pipeline");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void staticSchemaForPipelineV1() throws IOException {
    JsonNode expected = readJsonNode("pipeline-short.json");
    when(schemaFetcher.fetchPipelineStaticYamlSchema("v1")).thenReturn(expected);

    final JsonNode result = pmsYamlSchemaService.getStaticSchemaForAllEntities(PIPELINE, null, null, "v1");

    assertThat(result).isNotNull();
    assertThat(result.get(PROPERTIES_NODE).get(PIPELINE_NODE).get("$ref").asText())
        .isEqualTo("#/definitions/pipeline/pipeline");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void staticSchemaForPipelineInvalidVersion() {
    when(schemaFetcher.fetchPipelineStaticYamlSchema("v2x"))
        .thenThrow(new InvalidRequestException(
            "[PMS] Incorrect version [v2x] of Pipeline Schema passed, Valid values are [v0, v1]"));
    assertThatThrownBy(() -> pmsYamlSchemaService.getStaticSchemaForAllEntities(PIPELINE, null, null, "v2x"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("[PMS] Incorrect version [v2x] of Pipeline Schema passed, Valid values are [v0, v1]");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void staticSchemaForStepGroupWithNullGrpDifferentiator() {
    assertThatThrownBy(() -> pmsYamlSchemaService.getStaticSchemaForAllEntities("step_group", null, null, "v2x"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("node_group_differentiator cannot be empty for step_group node_group.");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotValidateYamlSchema() throws IOException {
    when(pmsFeatureFlagHelper.isEnabled(ACC_ID, FeatureName.DISABLE_PIPELINE_SCHEMA_VALIDATION)).thenReturn(true);
    pmsYamlSchemaService.validateYamlSchemaInternal(ACC_ID, ORG_ID, PRJ_ID, null, "0", PIPELINE);
    verify(yamlSchemaValidator, never()).validate(anyString(), anyString());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateYamlSchema() throws Throwable {
    final String yaml = "yamlContent";
    when(pmsFeatureFlagHelper.isEnabled(ACC_ID, FeatureName.DISABLE_PIPELINE_SCHEMA_VALIDATION)).thenReturn(false);
    when(schemaFetcher.fetchPipelineStaticYamlSchema("v0")).thenReturn(new TextNode("schemaContent"));
    pmsYamlSchemaService.validateYamlSchemaInternal(
        ACC_ID, ORG_ID, PRJ_ID, YamlUtils.readAsJsonNode(yaml), "0", PIPELINE);
    verify(yamlSchemaValidator).validate(eq(YamlUtils.readAsJsonNode(yaml)), eq(new TextNode("schemaContent")));
  }

  private JsonNode readJsonNode(String resourceName) throws IOException {
    final String resource = IOUtils.resourceToString(resourceName, StandardCharsets.UTF_8, getClass().getClassLoader());
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readTree(resource);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testValidatePdcHostsFieldWithPlainString() throws IOException {
    String yaml = "infrastructure:\n"
        + "  type: Pdc\n"
        + "  spec:\n"
        + "    hosts: plainStringValue\n";
    JsonNode jsonNode = YamlUtils.readAsJsonNode(yaml);
    when(schemaFetcher.fetchPipelineStaticYamlSchema("v0")).thenReturn(new TextNode("schemaContent"));

    assertThatThrownBy(
        () -> pmsYamlSchemaService.validateYamlSchemaInternal(ACC_ID, ORG_ID, PRJ_ID, jsonNode, "0", PIPELINE))
        .isInstanceOf(io.harness.yaml.validator.InvalidYamlException.class)
        .hasMessageContaining("Invalid value for 'hosts'")
        .hasMessageContaining("plainStringValue");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testValidatePdcHostsFieldWithArray() throws IOException {
    String yaml = "infrastructure:\n"
        + "  type: Pdc\n"
        + "  spec:\n"
        + "    hosts:\n"
        + "      - host1\n"
        + "      - host2\n";
    JsonNode jsonNode = YamlUtils.readAsJsonNode(yaml);
    when(schemaFetcher.fetchPipelineStaticYamlSchema("v0")).thenReturn(new TextNode("schemaContent"));

    // Should not throw exception for valid array hosts
    assertThatCode(
        () -> pmsYamlSchemaService.validateYamlSchemaInternal(ACC_ID, ORG_ID, PRJ_ID, jsonNode, "0", PIPELINE))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testValidatePdcHostsFieldWithExpression() throws IOException {
    String yaml = "infrastructure:\n"
        + "  type: Pdc\n"
        + "  spec:\n"
        + "    hosts: <+input>\n";
    JsonNode jsonNode = YamlUtils.readAsJsonNode(yaml);
    when(schemaFetcher.fetchPipelineStaticYamlSchema("v0")).thenReturn(new TextNode("schemaContent"));

    // Should not throw exception for valid expression hosts
    assertThatCode(
        () -> pmsYamlSchemaService.validateYamlSchemaInternal(ACC_ID, ORG_ID, PRJ_ID, jsonNode, "0", PIPELINE))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testValidatePdcHostsFieldWithPipelineExpression() throws IOException {
    String yaml = "infrastructure:\n"
        + "  type: Pdc\n"
        + "  spec:\n"
        + "    hosts: <+pipeline.variables.hosts>\n";
    JsonNode jsonNode = YamlUtils.readAsJsonNode(yaml);
    when(schemaFetcher.fetchPipelineStaticYamlSchema("v0")).thenReturn(new TextNode("schemaContent"));

    // Should not throw exception for valid pipeline expression hosts
    assertThatCode(
        () -> pmsYamlSchemaService.validateYamlSchemaInternal(ACC_ID, ORG_ID, PRJ_ID, jsonNode, "0", PIPELINE))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testValidateHostsFieldIgnoredForNonPdcInfrastructure() throws IOException {
    String yaml = "infrastructure:\n"
        + "  type: KubernetesDirect\n"
        + "  spec:\n"
        + "    hosts: plainStringValue\n";
    JsonNode jsonNode = YamlUtils.readAsJsonNode(yaml);
    when(schemaFetcher.fetchPipelineStaticYamlSchema("v0")).thenReturn(new TextNode("schemaContent"));

    // Should not throw exception for non-PDC infrastructure even with plain string hosts
    assertThatCode(
        () -> pmsYamlSchemaService.validateYamlSchemaInternal(ACC_ID, ORG_ID, PRJ_ID, jsonNode, "0", PIPELINE))
        .doesNotThrowAnyException();
  }
}
