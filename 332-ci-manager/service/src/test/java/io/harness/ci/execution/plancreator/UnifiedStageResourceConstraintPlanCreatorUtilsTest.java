/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.plancreator.V1.UnifiedStageResourceConstraintPlanCreatorUtils;
import io.harness.exception.YamlException;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.utilities.ResourceConstraintUtility;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;

public class UnifiedStageResourceConstraintPlanCreatorUtilsTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private PlanCreationContext context;
  private YamlField currentField;

  @Before
  public void setUp() {
    objectMapper = new ObjectMapper();

    ObjectNode parentNode = objectMapper.createObjectNode();
    parentNode.set("__uuid", new TextNode("parent-uuid"));

    ObjectNode currentNode = objectMapper.createObjectNode();
    currentNode.set("__uuid", new TextNode("current-uuid"));

    YamlNode parentYamlNode = new YamlNode(parentNode);
    YamlNode currentYamlNode = new YamlNode("steps", currentNode, parentYamlNode);
    currentField = new YamlField(currentYamlNode);

    context = PlanCreationContext.builder()
                  .currentField(currentField)
                  .globalContext("metadata",
                      PlanCreationContextValue.newBuilder()
                          .setAccountIdentifier("accountId")
                          .setOrgIdentifier("orgId")
                          .setProjectIdentifier("projectId")
                          .build())
                  .build();
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddResourceConstraintDependency_shouldReturnResponseWithDependenciesAndYamlUpdates() {
    ObjectNode rcJsonNode = objectMapper.createObjectNode();
    rcJsonNode.set("__uuid", new TextNode("rc-uuid"));
    rcJsonNode.put("identifier", "rc-id");

    try (MockedStatic<ResourceConstraintUtility> rcUtilMock = mockStatic(ResourceConstraintUtility.class);
         MockedStatic<YamlUtils> yamlUtilsMock = mockStatic(YamlUtils.class)) {
      rcUtilMock.when(() -> ResourceConstraintUtility.getResourceConstraintJsonNode(any(), any()))
          .thenReturn(rcJsonNode);
      yamlUtilsMock.when(() -> YamlUtils.writeYamlString(any(YamlField.class))).thenReturn("---\nsome: yaml");

      LinkedHashMap<String, PlanCreationResponse> result =
          UnifiedStageResourceConstraintPlanCreatorUtils.addResourceConstraintDependency(
              context, "<+OnPipelineSuccess>", false, "next-node-id");

      assertThat(result).as("should contain one entry").hasSize(1);

      Map.Entry<String, PlanCreationResponse> entry = result.entrySet().iterator().next();
      PlanCreationResponse response = entry.getValue();

      assertThat(response.getYamlUpdates()).as("should have yaml updates").isNotNull();
      assertThat(response.getYamlUpdates().getFqnToYamlMap()).as("should have fqn to yaml mapping").isNotEmpty();

      Dependencies deps = response.getDependencies();
      assertThat(deps).as("should have dependencies").isNotNull();
      assertThat(deps.getDependenciesMap()).as("should have dependency entry").isNotEmpty();
      assertThat(deps.getDependencyMetadataMap()).as("should have dependency metadata").isNotEmpty();
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddResourceConstraintDependency_whenProjectScoped_shouldAppendHashToResourceUnit() {
    ObjectNode rcJsonNode = objectMapper.createObjectNode();
    rcJsonNode.set("__uuid", new TextNode("rc-uuid"));

    String expectedResourceUnit = "<+INFRA_KEY>_" + String.valueOf("accountId_orgId_projectId".hashCode());

    try (MockedStatic<ResourceConstraintUtility> rcUtilMock = mockStatic(ResourceConstraintUtility.class);
         MockedStatic<YamlUtils> yamlUtilsMock = mockStatic(YamlUtils.class)) {
      rcUtilMock.when(() -> ResourceConstraintUtility.getResourceConstraintJsonNode(eq(expectedResourceUnit), any()))
          .thenReturn(rcJsonNode);
      yamlUtilsMock.when(() -> YamlUtils.writeYamlString(any(YamlField.class))).thenReturn("---\nsome: yaml");

      LinkedHashMap<String, PlanCreationResponse> result =
          UnifiedStageResourceConstraintPlanCreatorUtils.addResourceConstraintDependency(
              context, "<+OnPipelineSuccess>", true, "next-id");

      assertThat(result).as("should return non-empty response").isNotEmpty();
      rcUtilMock.verify(() -> ResourceConstraintUtility.getResourceConstraintJsonNode(eq(expectedResourceUnit), any()));
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddResourceConstraintDependency_whenNotProjectScoped_shouldUseInfraKeyOnly() {
    ObjectNode rcJsonNode = objectMapper.createObjectNode();
    rcJsonNode.set("__uuid", new TextNode("rc-uuid"));

    try (MockedStatic<ResourceConstraintUtility> rcUtilMock = mockStatic(ResourceConstraintUtility.class);
         MockedStatic<YamlUtils> yamlUtilsMock = mockStatic(YamlUtils.class)) {
      rcUtilMock.when(() -> ResourceConstraintUtility.getResourceConstraintJsonNode(any(), any()))
          .thenReturn(rcJsonNode);
      yamlUtilsMock.when(() -> YamlUtils.writeYamlString(any(YamlField.class))).thenReturn("---\nsome: yaml");

      LinkedHashMap<String, PlanCreationResponse> result =
          UnifiedStageResourceConstraintPlanCreatorUtils.addResourceConstraintDependency(
              context, "<+OnPipelineSuccess>", false, "next-id");

      assertThat(result).as("should return non-empty response").isNotEmpty();
      rcUtilMock.verify(() -> ResourceConstraintUtility.getResourceConstraintJsonNode(eq("<+INFRA_KEY>"), any()));
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddResourceConstraintDependency_whenYamlWriteThrowsIOException_shouldThrowYamlException() {
    ObjectNode rcJsonNode = objectMapper.createObjectNode();
    rcJsonNode.set("__uuid", new TextNode("rc-uuid"));

    try (MockedStatic<ResourceConstraintUtility> rcUtilMock = mockStatic(ResourceConstraintUtility.class);
         MockedStatic<YamlUtils> yamlUtilsMock = mockStatic(YamlUtils.class)) {
      rcUtilMock.when(() -> ResourceConstraintUtility.getResourceConstraintJsonNode(any(), any()))
          .thenReturn(rcJsonNode);
      yamlUtilsMock.when(() -> YamlUtils.writeYamlString(any(YamlField.class)))
          .thenThrow(new IOException("serialization failed"));

      assertThatThrownBy(()
                             -> UnifiedStageResourceConstraintPlanCreatorUtils.addResourceConstraintDependency(
                                 context, "<+OnPipelineSuccess>", false, "next-id"))
          .as("should throw YamlException when writeYamlString fails")
          .isInstanceOf(YamlException.class)
          .hasMessageContaining("could not be converted into a yaml string");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddResourceConstraintDependency_shouldStripYamlDocumentMarker() {
    ObjectNode rcJsonNode = objectMapper.createObjectNode();
    rcJsonNode.set("__uuid", new TextNode("rc-uuid"));

    try (MockedStatic<ResourceConstraintUtility> rcUtilMock = mockStatic(ResourceConstraintUtility.class);
         MockedStatic<YamlUtils> yamlUtilsMock = mockStatic(YamlUtils.class)) {
      rcUtilMock.when(() -> ResourceConstraintUtility.getResourceConstraintJsonNode(any(), any()))
          .thenReturn(rcJsonNode);
      yamlUtilsMock.when(() -> YamlUtils.writeYamlString(any(YamlField.class)))
          .thenReturn("---\nname: \"Resource Constraint\"");

      LinkedHashMap<String, PlanCreationResponse> result =
          UnifiedStageResourceConstraintPlanCreatorUtils.addResourceConstraintDependency(
              context, "<+OnPipelineSuccess>", false, "next-id");

      PlanCreationResponse response = result.values().iterator().next();
      String yamlContent = response.getYamlUpdates().getFqnToYamlMap().values().iterator().next();
      assertThat(yamlContent).as("should strip the --- document marker").doesNotContain("---\n");
      assertThat(yamlContent).as("should preserve the yaml content").contains("name: \"Resource Constraint\"");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddResourceConstraintDependency_shouldSetNextIdInDependencyMetadata() {
    ObjectNode rcJsonNode = objectMapper.createObjectNode();
    rcJsonNode.set("__uuid", new TextNode("rc-uuid"));

    try (MockedStatic<ResourceConstraintUtility> rcUtilMock = mockStatic(ResourceConstraintUtility.class);
         MockedStatic<YamlUtils> yamlUtilsMock = mockStatic(YamlUtils.class)) {
      rcUtilMock.when(() -> ResourceConstraintUtility.getResourceConstraintJsonNode(any(), any()))
          .thenReturn(rcJsonNode);
      yamlUtilsMock.when(() -> YamlUtils.writeYamlString(any(YamlField.class))).thenReturn("---\nsome: yaml");

      LinkedHashMap<String, PlanCreationResponse> result =
          UnifiedStageResourceConstraintPlanCreatorUtils.addResourceConstraintDependency(
              context, null, false, "target-next-id");

      PlanCreationResponse response = result.values().iterator().next();
      Dependencies deps = response.getDependencies();

      String uuid = deps.getDependenciesMap().keySet().iterator().next();
      assertThat(
          deps.getDependencyMetadataMap().get(uuid).getNodeMetadata().getDataMap().get("nextId").getStringValue())
          .as("should set nextId in dependency metadata")
          .isEqualTo("target-next-id");
    }
  }
}
