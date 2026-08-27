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
import io.harness.ci.execution.plancreator.V1.RenderingPlanCreator;
import io.harness.ci.states.V1.cd.RenderingStepParameters;
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

public class RenderingPlanCreatorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks private RenderingPlanCreator renderingPlanCreator;

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
  public void testAddRenderingNode_shouldAddNodeToResponseMapAndReturnUuid() {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    YamlField curr = createYamlFieldWithIdAndName("step-id", "step-name", "test-uuid");
    String nextId = "next-node-id";

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      String resultUuid = renderingPlanCreator.addRenderingNode(responseMap, curr, nextId, null, false, null);

      assertThat(resultUuid).as("should return the uuid of the created node").isNotNull();
      assertThat(responseMap).as("should add one entry to the response map").hasSize(1);
      assertThat(responseMap.containsKey(resultUuid)).as("should use uuid as key").isTrue();

      PlanCreationResponse response = responseMap.get(resultUuid);
      PlanNode planNode = response.getPlanNode();
      assertThat(planNode).as("plan node should not be null").isNotNull();
      assertThat(planNode.getName()).as("should use step name").isEqualTo("step-name");
      assertThat(planNode.getIdentifier()).as("should use step id").isEqualTo("step-id");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddRenderingNode_whenStepEnvNodeHasFetchFile_shouldSetFetch() {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    YamlField curr = createYamlFieldWithIdAndName("step-id", "step-name", "test-uuid");

    ObjectNode stepEnvNode = objectMapper.createObjectNode();
    stepEnvNode.put("PLUGIN_FETCH_FILE", true);

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      String resultUuid = renderingPlanCreator.addRenderingNode(responseMap, curr, "next-id", stepEnvNode, false, null);

      assertThat(responseMap).as("should have one entry").hasSize(1);
      PlanNode planNode = responseMap.get(resultUuid).getPlanNode();
      assertThat(planNode.getStepParameters()).isInstanceOf(RenderingStepParameters.class);
      RenderingStepParameters params = (RenderingStepParameters) planNode.getStepParameters();
      assertThat(params.isFetch()).as("fetch should be true").isTrue();
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddRenderingNode_whenStepEnvNodeHasAddOnFilePaths_shouldSetAddOnFiles() {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    YamlField curr = createYamlFieldWithIdAndName("step-id", "step-name", "test-uuid");

    ObjectNode stepEnvNode = objectMapper.createObjectNode();
    stepEnvNode.put("PLUGIN_ADD_ON_FILE_PATHS", "path/file1.yaml, path/file2.yaml");

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      String resultUuid = renderingPlanCreator.addRenderingNode(responseMap, curr, "next-id", stepEnvNode, false, null);

      PlanNode planNode = responseMap.get(resultUuid).getPlanNode();
      assertThat(planNode.getStepParameters()).isInstanceOf(RenderingStepParameters.class);
      RenderingStepParameters params = (RenderingStepParameters) planNode.getStepParameters();
      assertThat(params.getAddOnFiles())
          .as("should split and trim file paths")
          .containsExactly("path/file1.yaml", "path/file2.yaml");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddRenderingNode_whenIdNodeIsNotTextNode_shouldThrowInvalidYamlException() {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();

    ObjectNode rootNode = objectMapper.createObjectNode();
    rootNode.set("id", new IntNode(123));
    rootNode.set("name", new TextNode("step-name"));
    YamlNode yamlNode = new YamlNode(rootNode);
    YamlField curr = new YamlField(yamlNode);

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      assertThatThrownBy(() -> renderingPlanCreator.addRenderingNode(responseMap, curr, "next-id", null, false, null))
          .as("should throw when id is not a TextNode")
          .isInstanceOf(InvalidYamlException.class)
          .hasMessageContaining("Rendering step node id is not configured");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddRenderingNode_whenNameNodeIsNotTextNode_shouldThrowInvalidYamlException() {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();

    ObjectNode rootNode = objectMapper.createObjectNode();
    rootNode.set("id", new TextNode("step-id"));
    rootNode.set("name", new IntNode(456));
    YamlNode yamlNode = new YamlNode(rootNode);
    YamlField curr = new YamlField(yamlNode);

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      assertThatThrownBy(() -> renderingPlanCreator.addRenderingNode(responseMap, curr, "next-id", null, false, null))
          .as("should throw when name is not a TextNode")
          .isInstanceOf(InvalidYamlException.class)
          .hasMessageContaining("Rendering step node name is not configured");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddRenderingNode_whenIsStepInsideRollback_shouldPassRollbackFlag() {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    YamlField curr = createYamlFieldWithIdAndName("step-id", "step-name", "test-uuid");

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(null, true)).thenReturn("<+OnRollback>");

      String resultUuid = renderingPlanCreator.addRenderingNode(responseMap, curr, "next-id", null, true, null);

      PlanNode planNode = responseMap.get(resultUuid).getPlanNode();
      assertThat(planNode.getWhenCondition()).as("should use rollback when condition").isEqualTo("<+OnRollback>");
    }
  }
}
