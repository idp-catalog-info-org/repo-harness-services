/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputs.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.ngpipeline.inputs.beans.entity.InputEntity;
import io.harness.pms.ngpipeline.inputs.beans.entity.InputEntityType;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PMSInputsServiceImplTest extends PipelineServiceTestBase {
  @Inject PMSInputsServiceImpl pmsInputsService;
  @Inject ObjectMapper objectMapper;
  String pipelineYaml;

  private String readFile(String filename) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read file " + filename, e);
    }
  }

  @Before
  public void setUp() throws IOException {
    String pipelineYamlFileName = "pipeline-v1.yaml";
    pipelineYaml = readFile(pipelineYamlFileName);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputs() throws JsonProcessingException {
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    assertThat(optionalInputEntityMap.isEmpty()).isFalse();
    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();
    String expectedResponse = readFile("get-inputs-expected-response.json");
    JsonNode jsonNode = objectMapper.readTree(expectedResponse);
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(inputEntityMap)))
        .isEqualTo(jsonNode.get("inputs"));
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testInputEntityWithDescription() throws JsonProcessingException {
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    assertThat(optionalInputEntityMap).isPresent();

    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();

    // Verify description field is correctly parsed
    assertThat(inputEntityMap.get("image").getDescription()).isEqualTo("image name");
    assertThat(inputEntityMap.get("repo").getDescription()).isEqualTo("repository name");
    assertThat(inputEntityMap.get("password").getDescription()).isEqualTo("docker password");
    assertThat(inputEntityMap.get("enableLogging").getDescription()).isEqualTo("enable debug logging");
    assertThat(inputEntityMap.get("tags").getDescription()).isEqualTo("list of tags for the build");
    assertThat(inputEntityMap.get("dockerConnector").getDescription()).isEqualTo("docker registry connector");
    assertThat(inputEntityMap.get("buildConfig").getDescription()).isEqualTo("build configuration options");
    assertThat(inputEntityMap.get("count").getDescription()).isNull();
    assertThat(inputEntityMap.get("kubeconfigPath").getDescription()).isNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testInputEntityTypes() throws JsonProcessingException {
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    assertThat(optionalInputEntityMap).isPresent();

    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();

    // Verify all input types are correctly parsed
    assertThat(inputEntityMap.get("image").getType()).isEqualTo(InputEntityType.STRING);
    assertThat(inputEntityMap.get("repo").getType()).isEqualTo(InputEntityType.STRING);
    assertThat(inputEntityMap.get("count").getType()).isEqualTo(InputEntityType.NUMBER);
    assertThat(inputEntityMap.get("password").getType()).isEqualTo(InputEntityType.SECRET);
    assertThat(inputEntityMap.get("enableLogging").getType()).isEqualTo(InputEntityType.BOOLEAN);
    assertThat(inputEntityMap.get("tags").getType()).isEqualTo(InputEntityType.ARRAY);
    assertThat(inputEntityMap.get("dockerConnector").getType()).isEqualTo(InputEntityType.CONNECTOR);
    assertThat(inputEntityMap.get("buildConfig").getType()).isEqualTo(InputEntityType.OBJECT);
    assertThat(inputEntityMap.get("kubeconfigPath").getType()).isEqualTo(InputEntityType.STRING);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testInputEntityRequiredField() throws JsonProcessingException {
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    assertThat(optionalInputEntityMap).isPresent();

    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();

    // Verify required fields
    assertThat(inputEntityMap.get("repo").isRequired()).isTrue();
    assertThat(inputEntityMap.get("password").isRequired()).isTrue();
    assertThat(inputEntityMap.get("dockerConnector").isRequired()).isTrue();
    assertThat(inputEntityMap.get("image").isRequired()).isFalse();
    assertThat(inputEntityMap.get("count").isRequired()).isFalse();
    assertThat(inputEntityMap.get("enableLogging").isRequired()).isFalse();
    assertThat(inputEntityMap.get("tags").isRequired()).isFalse();
    assertThat(inputEntityMap.get("buildConfig").isRequired()).isFalse();
    assertThat(inputEntityMap.get("kubeconfigPath").isRequired()).isFalse();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testInputEntityDefaultValue() throws JsonProcessingException {
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    assertThat(optionalInputEntityMap).isPresent();

    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();

    // Verify default values
    assertThat(inputEntityMap.get("image").getDefaultValue()).isEqualTo("golang");
    assertThat(inputEntityMap.get("count").getDefaultValue()).isEqualTo(5);
    assertThat(inputEntityMap.get("enableLogging").getDefaultValue()).isEqualTo(false);
    assertThat(inputEntityMap.get("tags").getDefaultValue()).isEqualTo(Arrays.asList("dev"));
    assertThat(inputEntityMap.get("repo").getDefaultValue()).isNull();
    assertThat(inputEntityMap.get("password").getDefaultValue()).isNull();
    assertThat(inputEntityMap.get("dockerConnector").getDefaultValue()).isNull();
    assertThat(inputEntityMap.get("buildConfig").getDefaultValue()).isNull();
    assertThat(inputEntityMap.get("kubeconfigPath").getDefaultValue()).isEqualTo("/path/to/kubeconfig");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testInputEntityWithEnums() throws JsonProcessingException {
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    assertThat(optionalInputEntityMap).isPresent();

    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();

    // Verify enum values
    assertThat(inputEntityMap.get("password").getEnums()).isNotNull();
    assertThat(inputEntityMap.get("password").getEnums()).hasSize(2);
    assertThat(inputEntityMap.get("password").getEnums()).containsExactly("secret1", "secret2");

    // Inputs without enums
    assertThat(inputEntityMap.get("image").getEnums()).isNull();
    assertThat(inputEntityMap.get("repo").getEnums()).isNull();
    assertThat(inputEntityMap.get("tags").getEnums()).isNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetInputsWithEmptyInputs() {
    String pipelineYamlNoInputs =
        "pipeline:\n  stages:\n    - id: test\n      steps:\n        - run:\n            script: echo test";
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYamlNoInputs);
    assertThat(optionalInputEntityMap).isPresent();
    assertThat(optionalInputEntityMap.get()).isEmpty();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testInputEntityPatternField() throws JsonProcessingException {
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    assertThat(optionalInputEntityMap).isPresent();

    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();

    // Verify pattern field for regex validation
    assertThat(inputEntityMap.get("image").getPattern()).isEqualTo("^[a-z0-9]+$");
    assertThat(inputEntityMap.get("repo").getPattern()).isNull();
    assertThat(inputEntityMap.get("count").getPattern()).isNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testInputEntityUiField() throws JsonProcessingException {
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    assertThat(optionalInputEntityMap).isPresent();

    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();

    // Verify ui field for buildConfig
    InputEntity buildConfigInput = inputEntityMap.get("buildConfig");
    assertThat(buildConfigInput.getUi()).isNotNull();
    // UI is parsed as a Map with tooltip and hidden fields
    Map<String, Object> uiMap = (Map<String, Object>) buildConfigInput.getUi();
    assertThat(uiMap.get("tooltip")).isEqualTo("Advanced build configuration");
    assertThat(uiMap.get("hidden")).isEqualTo(false);

    // Verify other inputs don't have ui field
    assertThat(inputEntityMap.get("image").getUi()).isNull();
    assertThat(inputEntityMap.get("repo").getUi()).isNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testInputEntityItemsField() throws JsonProcessingException {
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    assertThat(optionalInputEntityMap).isPresent();

    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();

    // Verify items field for array type input
    InputEntity tagsInput = inputEntityMap.get("tags");
    assertThat(tagsInput.getItems()).isNotNull();
    assertThat(tagsInput.getItems()).hasSize(3);
    assertThat(tagsInput.getItems()).containsExactly("dev", "staging", "production");

    // Verify other inputs don't have items field
    assertThat(inputEntityMap.get("image").getItems()).isNull();
    assertThat(inputEntityMap.get("count").getItems()).isNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testInputEntityOptionsField() throws JsonProcessingException {
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    assertThat(optionalInputEntityMap).isPresent();

    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();

    // Verify options field for connector type input
    InputEntity dockerConnectorInput = inputEntityMap.get("dockerConnector");
    assertThat(dockerConnectorInput.getOptions()).isNotNull();
    assertThat(dockerConnectorInput.getOptions()).hasSize(3);
    assertThat(dockerConnectorInput.getOptions()).containsExactly("connector1", "connector2", "connector3");

    // Verify other inputs don't have options field
    assertThat(inputEntityMap.get("image").getOptions()).isNull();
    assertThat(inputEntityMap.get("repo").getOptions()).isNull();
    assertThat(inputEntityMap.get("tags").getOptions()).isNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetInputsDoesNotContainUuidKeys() {
    // Verify that __uuid fields injected during YAML parsing are removed
    // and don't appear as keys in the resulting map
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    assertThat(optionalInputEntityMap).isPresent();

    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();

    // Ensure __uuid is not present as a key in the map
    assertThat(inputEntityMap.containsKey(YamlNode.UUID_FIELD_NAME)).isFalse();

    // Verify all expected input keys are present
    assertThat(inputEntityMap.keySet())
        .containsExactlyInAnyOrder("image", "repo", "count", "password", "enableLogging", "tags", "dockerConnector",
            "buildConfig", "kubeconfigPath");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetInputsWithUiAndLabelFields() throws JsonProcessingException {
    // Test that UI field and other extended properties are correctly preserved
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    assertThat(optionalInputEntityMap).isPresent();

    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();

    // Verify __uuid is not a key
    assertThat(inputEntityMap.containsKey(YamlNode.UUID_FIELD_NAME)).isFalse();

    // Verify the kubeconfigPath input is correctly parsed with ui field
    assertThat(inputEntityMap.containsKey("kubeconfigPath")).isTrue();
    InputEntity kubeconfigInput = inputEntityMap.get("kubeconfigPath");
    assertThat(kubeconfigInput.getType()).isEqualTo(InputEntityType.STRING);
    assertThat(kubeconfigInput.getDefaultValue()).isEqualTo("/path/to/kubeconfig");
    assertThat(kubeconfigInput.getUi()).isNotNull();
    Map<String, Object> uiMap = (Map<String, Object>) kubeconfigInput.getUi();
    assertThat(uiMap.get("tooltip")).isEqualTo("Enter the path to the kubeconfig file");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testAllInputEntityFieldsCoverage() throws JsonProcessingException {
    // Comprehensive test to verify all InputEntity fields are properly handled
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    assertThat(optionalInputEntityMap).isPresent();

    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();

    // Verify all 9 inputs are present
    assertThat(inputEntityMap).hasSize(9);

    // Test image input - has pattern and default
    InputEntity imageInput = inputEntityMap.get("image");
    assertThat(imageInput.getType()).isEqualTo(InputEntityType.STRING);
    assertThat(imageInput.getDescription()).isEqualTo("image name");
    assertThat(imageInput.getDefaultValue()).isEqualTo("golang");
    assertThat(imageInput.getPattern()).isEqualTo("^[a-z0-9]+$");
    assertThat(imageInput.isRequired()).isFalse();

    // Test tags input - array type with items and default
    InputEntity tagsInput = inputEntityMap.get("tags");
    assertThat(tagsInput.getType()).isEqualTo(InputEntityType.ARRAY);
    assertThat(tagsInput.getItems()).containsExactly("dev", "staging", "production");
    assertThat(tagsInput.getDefaultValue()).isEqualTo(Arrays.asList("dev"));

    // Test dockerConnector input - connector type with options
    InputEntity dockerConnectorInput = inputEntityMap.get("dockerConnector");
    assertThat(dockerConnectorInput.getType()).isEqualTo(InputEntityType.CONNECTOR);
    assertThat(dockerConnectorInput.getOptions()).containsExactly("connector1", "connector2", "connector3");
    assertThat(dockerConnectorInput.isRequired()).isTrue();

    // Test buildConfig input - object type with ui
    InputEntity buildConfigInput = inputEntityMap.get("buildConfig");
    assertThat(buildConfigInput.getType()).isEqualTo(InputEntityType.OBJECT);
    assertThat(buildConfigInput.getUi()).isNotNull();
  }
}
