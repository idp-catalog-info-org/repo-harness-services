/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.variables;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.stages.dynamic.DynamicStageNode;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DynamicStageVariableCreatorTest extends CategoryTest {
  private final DynamicStageVariableCreator creator = new DynamicStageVariableCreator();

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeAddsGitStoreOutputs() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    URL resource = classLoader.getResource("dynamic_stage_git_store.json");
    String json = Resources.toString(resource, Charsets.UTF_8);

    JsonNode jsonNode = JsonUtils.asObject(json, JsonNode.class);
    YamlNode stageYamlNode = new YamlNode("stage", jsonNode.get("pipeline").get("stages").get(0).get("stage"));
    YamlField stageField = new YamlField(stageYamlNode);

    VariableCreationContext context = VariableCreationContext.builder().currentField(stageField).build();
    DynamicStageNode node = YamlUtils.read(stageField.getNode().toString(), DynamicStageNode.class);

    VariableCreationResponse response = creator.createVariablesForParentNodeV2(context, node);
    Map<String, YamlProperties> yamlProperties = response.getYamlProperties();

    // Keys are the literal values of the fields
    assertThat(yamlProperties)
        .containsKeys("account.git", "feature/test", "commit-sha", ".harness/pipeline.yaml", "repo");

    // For this isolated stage snippet, FQNs are relative to the stage root; just verify they point
    // at the correct logical fields and end with the expected output names.
    assertThat(yamlProperties.get("account.git").getFqn()).endsWith("connectorRef");
    assertThat(yamlProperties.get("feature/test").getFqn()).endsWith("branch");
    assertThat(yamlProperties.get("commit-sha").getFqn()).endsWith("commitId");
    assertThat(yamlProperties.get(".harness/pipeline.yaml").getFqn()).endsWith("filePath");
    assertThat(yamlProperties.get("repo").getFqn()).endsWith("repoName");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeSkipsNonGitSourceConfig() throws IOException {
    // Test with sourceConfig type that is NOT "Git"
    // Note: We use YamlField directly to avoid Jackson deserialization issues with unknown types
    String json = "{\n"
        + "  \"pipeline\": {\n"
        + "    \"stages\": [\n"
        + "      {\n"
        + "        \"stage\": {\n"
        + "          \"name\": \"dynamicStage\",\n"
        + "          \"identifier\": \"dynamicStage\",\n"
        + "          \"type\": \"Dynamic\",\n"
        + "          \"spec\": {\n"
        + "            \"sourceConfig\": {\n"
        + "              \"type\": \"Remote\",\n"
        + "              \"spec\": {\n"
        + "                \"connectorRef\": \"account.git\",\n"
        + "                \"branchName\": \"feature/test\",\n"
        + "                \"filePath\": \".harness/pipeline.yaml\",\n"
        + "                \"repoName\": \"repo\"\n"
        + "              }\n"
        + "            }\n"
        + "          }\n"
        + "        }\n"
        + "      }\n"
        + "    ]\n"
        + "  }\n"
        + "}";

    JsonNode jsonNode = JsonUtils.asObject(json, JsonNode.class);
    YamlNode stageYamlNode = new YamlNode("stage", jsonNode.get("pipeline").get("stages").get(0).get("stage"));
    YamlField stageField = new YamlField(stageYamlNode);

    VariableCreationContext context = VariableCreationContext.builder().currentField(stageField).build();
    // Create a minimal DynamicStageNode without deserializing (to avoid Jackson errors with unknown type "Remote")
    // The actual Git store variable logic uses ctx.getCurrentField() which is the YamlField above
    DynamicStageNode node = new DynamicStageNode();

    VariableCreationResponse response = creator.createVariablesForParentNodeV2(context, node);
    Map<String, YamlProperties> yamlProperties = response.getYamlProperties();

    // Git fields should NOT be processed when type is not "Git"
    assertThat(yamlProperties).doesNotContainKeys("account.git", "feature/test", ".harness/pipeline.yaml", "repo");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeSkipsSourceConfigWithoutType() throws IOException {
    // Test with sourceConfig missing type field
    // Note: We use YamlField directly to avoid Jackson deserialization issues
    String json = "{\n"
        + "  \"pipeline\": {\n"
        + "    \"stages\": [\n"
        + "      {\n"
        + "        \"stage\": {\n"
        + "          \"name\": \"dynamicStage\",\n"
        + "          \"identifier\": \"dynamicStage\",\n"
        + "          \"type\": \"Dynamic\",\n"
        + "          \"spec\": {\n"
        + "            \"sourceConfig\": {\n"
        + "              \"spec\": {\n"
        + "                \"connectorRef\": \"account.git\",\n"
        + "                \"branchName\": \"feature/test\",\n"
        + "                \"filePath\": \".harness/pipeline.yaml\",\n"
        + "                \"repoName\": \"repo\"\n"
        + "              }\n"
        + "            }\n"
        + "          }\n"
        + "        }\n"
        + "      }\n"
        + "    ]\n"
        + "  }\n"
        + "}";

    JsonNode jsonNode = JsonUtils.asObject(json, JsonNode.class);
    YamlNode stageYamlNode = new YamlNode("stage", jsonNode.get("pipeline").get("stages").get(0).get("stage"));
    YamlField stageField = new YamlField(stageYamlNode);

    VariableCreationContext context = VariableCreationContext.builder().currentField(stageField).build();
    // Create a minimal DynamicStageNode without deserializing
    // The actual Git store variable logic uses ctx.getCurrentField() which is the YamlField above
    DynamicStageNode node = new DynamicStageNode();

    VariableCreationResponse response = creator.createVariablesForParentNodeV2(context, node);
    Map<String, YamlProperties> yamlProperties = response.getYamlProperties();

    // Git fields should NOT be processed when type field is missing
    assertThat(yamlProperties).doesNotContainKeys("account.git", "feature/test", ".harness/pipeline.yaml", "repo");
  }
}
