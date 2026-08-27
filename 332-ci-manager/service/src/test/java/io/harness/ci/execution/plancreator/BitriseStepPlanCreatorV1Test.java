/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.V1.BitriseStepNodeV1;
import io.harness.beans.steps.stepinfo.V1.BitriseStepInfoV1;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.plancreator.V1.BitriseStepPlanCreatorV1;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class BitriseStepPlanCreatorV1Test extends CategoryTest {
  private BitriseStepPlanCreatorV1 bitriseStepPlanCreatorV1;

  @Before
  public void setUp() {
    bitriseStepPlanCreatorV1 = new BitriseStepPlanCreatorV1();
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes_shouldReturnBitriseV1DisplayName() {
    Set<String> supportedStepTypes = bitriseStepPlanCreatorV1.getSupportedStepTypes();

    assertThat(supportedStepTypes)
        .as("should contain BITRISE_V1 display name")
        .containsExactlyInAnyOrder(CIStepInfoType.BITRISE_V1.getDisplayName());
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedYamlVersions_shouldReturnV1() {
    Set<String> supportedVersions = bitriseStepPlanCreatorV1.getSupportedYamlVersions();

    assertThat(supportedVersions).as("should contain only V1").containsExactlyInAnyOrder(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFieldObject_shouldParseBitriseStepNodeV1() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String json = "{\"type\":\"bitrise\",\"spec\":{\"uses\":\"step/"
        + "activate-ssh-key@4.0.5\",\"with\":{\"key\":\"value\"},\"envs\":{\"ENV_KEY\":\"ENV_VALUE\"}}}";
    JsonNode jsonNode = mapper.readTree(json);
    YamlField yamlField = new YamlField(new YamlNode(jsonNode));

    BitriseStepNodeV1 result = bitriseStepPlanCreatorV1.getFieldObject(yamlField);

    assertThat(result).as("should parse BitriseStepNodeV1 from YAML field").isNotNull();
    assertThat(result.getBitriseStepInfoV1()).as("should have step info").isNotNull();
    assertThat(result.getBitriseStepInfoV1().getUses().getValue())
        .as("should parse uses field")
        .isEqualTo("step/activate-ssh-key@4.0.5");
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFieldObject_whenInvalidYaml_shouldThrowInvalidYamlException() {
    YamlNode mockNode = mock(YamlNode.class);
    when(mockNode.toString()).thenReturn("not valid json {{{");
    YamlField yamlField = new YamlField(mockNode);

    assertThatThrownBy(() -> bitriseStepPlanCreatorV1.getFieldObject(yamlField))
        .as("should throw InvalidYamlException for invalid YAML")
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Unable to parse bitrise step yaml");
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepNode_shouldBuildBitriseStepNodeFromV1() {
    ParameterField<String> uses = ParameterField.createValueField("step/activate-ssh-key@4.0.5");
    ParameterField<Map<String, String>> with = ParameterField.createValueField(Map.of("key", "value"));
    ParameterField<Map<String, String>> envs = ParameterField.createValueField(Map.of("ENV_KEY", "ENV_VALUE"));

    BitriseStepInfoV1 stepInfo = BitriseStepInfoV1.builder().uses(uses).with(with).envs(envs).build();

    BitriseStepNodeV1 stepNodeV1 = new BitriseStepNodeV1();
    stepNodeV1.setBitriseStepInfoV1(stepInfo);
    stepNodeV1.setName("my-bitrise-step");
    stepNodeV1.setUuid("test-uuid");

    CIAbstractStepNode result = bitriseStepPlanCreatorV1.getStepNode(stepNodeV1);

    assertThat(result).as("should return non-null step node").isNotNull();
    assertThat(result.getUuid()).as("should preserve uuid").isEqualTo("test-uuid");
    assertThat(result.getName()).as("should preserve name").isEqualTo("my-bitrise-step");
    assertThat(result.getIdentifier()).as("should generate identifier from name").isNotBlank();
  }
}
