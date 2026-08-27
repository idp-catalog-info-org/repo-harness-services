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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.plancreator.V1.TemplatingPlanCreator;
import io.harness.ci.states.V1.cd.TemplatingStep;
import io.harness.ci.states.V1.cd.TemplatingStepParameters;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;
import io.harness.when.utils.v1.RunInfoUtilsV1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.LinkedHashMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class TemplatingPlanCreatorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks private TemplatingPlanCreator templatingPlanCreator;

  private ObjectMapper objectMapper;

  @Before
  public void setUp() {
    objectMapper = new ObjectMapper();
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {1, 2, 3});
  }

  private YamlField createYamlFieldWithIdAndName(String id, String name, String uuid) {
    ObjectNode rootNode = objectMapper.createObjectNode();
    rootNode.set("id", new TextNode(id));
    rootNode.set("name", new TextNode(name));
    rootNode.set("__uuid", new TextNode(uuid));

    YamlNode yamlNode = new YamlNode(rootNode);
    return new YamlField(yamlNode);
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddTemplatingNode_shouldAddNodeToResponseMapAndReturnUuid() {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    YamlField curr = createYamlFieldWithIdAndName("step-id", "step-name", "test-uuid");

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      String resultUuid = templatingPlanCreator.addTemplatingNode(responseMap, curr, "next-id", false, null);

      assertThat(resultUuid).as("should return the uuid of the created node").isNotNull();
      assertThat(responseMap).as("should add one entry to the response map").hasSize(1);
      assertThat(responseMap.containsKey(resultUuid)).as("should use uuid as key").isTrue();

      PlanCreationResponse response = responseMap.get(resultUuid);
      PlanNode planNode = response.getPlanNode();
      assertThat(planNode).as("plan node should not be null").isNotNull();
      assertThat(planNode.getStepType()).as("should be TemplatingStep type").isEqualTo(TemplatingStep.STEP_TYPE);
      assertThat(planNode.getName()).as("should use step name").isEqualTo("step-name");
      assertThat(planNode.getIdentifier()).as("should use step id").isEqualTo("step-id");

      assertThat(planNode.getStepParameters()).isInstanceOf(TemplatingStepParameters.class);
      TemplatingStepParameters params = (TemplatingStepParameters) planNode.getStepParameters();
      assertThat(params.getId()).as("params should have id").isEqualTo("step-id");
      assertThat(params.getName()).as("params should have name").isEqualTo("step-name");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddTemplatingNode_whenIdIsEmpty_shouldUseFallbackIdentifier() {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    ObjectNode rootNode = objectMapper.createObjectNode();
    rootNode.set("id", new TextNode(""));
    rootNode.set("name", new TextNode(""));
    rootNode.set("__uuid", new TextNode("test-uuid"));
    YamlNode yamlNode = new YamlNode(rootNode);
    YamlField curr = new YamlField(yamlNode);

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      String resultUuid = templatingPlanCreator.addTemplatingNode(responseMap, curr, "next-id", false, null);

      PlanNode planNode = responseMap.get(resultUuid).getPlanNode();
      assertThat(planNode.getName()).as("should use default name when empty").isEqualTo("Templating");
      assertThat(planNode.getIdentifier()).as("should use default identifier when empty").isEqualTo("templating");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddTemplatingNode_whenIdNodeIsNotTextNode_shouldThrowInvalidYamlException() {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    ObjectNode rootNode = objectMapper.createObjectNode();
    rootNode.set("id", new IntNode(123));
    rootNode.set("name", new TextNode("step-name"));
    rootNode.set("__uuid", new TextNode("test-uuid"));
    YamlNode yamlNode = new YamlNode(rootNode);
    YamlField curr = new YamlField(yamlNode);

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      assertThatThrownBy(() -> templatingPlanCreator.addTemplatingNode(responseMap, curr, "next-id", false, null))
          .as("should throw when id is not a TextNode")
          .isInstanceOf(InvalidYamlException.class)
          .hasMessageContaining("Templating step node id is not configured");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddTemplatingNode_whenNameNodeIsNotTextNode_shouldThrowInvalidYamlException() {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    ObjectNode rootNode = objectMapper.createObjectNode();
    rootNode.set("id", new TextNode("step-id"));
    rootNode.set("name", new IntNode(456));
    rootNode.set("__uuid", new TextNode("test-uuid"));
    YamlNode yamlNode = new YamlNode(rootNode);
    YamlField curr = new YamlField(yamlNode);

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      assertThatThrownBy(() -> templatingPlanCreator.addTemplatingNode(responseMap, curr, "next-id", false, null))
          .as("should throw when name is not a TextNode")
          .isInstanceOf(InvalidYamlException.class)
          .hasMessageContaining("Templating step node name is not configured");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddTemplatingNode_whenIsStepInsideRollback_shouldSetRollbackWhenCondition() {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    YamlField curr = createYamlFieldWithIdAndName("step-id", "step-name", "test-uuid");

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(null, true)).thenReturn("<+OnRollback>");

      String resultUuid = templatingPlanCreator.addTemplatingNode(responseMap, curr, "next-id", true, null);

      PlanNode planNode = responseMap.get(resultUuid).getPlanNode();
      assertThat(planNode.getWhenCondition()).as("should use rollback when condition").isEqualTo("<+OnRollback>");
    }
  }
}
