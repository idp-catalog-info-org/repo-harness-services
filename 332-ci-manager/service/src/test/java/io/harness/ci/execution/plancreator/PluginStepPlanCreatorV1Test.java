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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.PluginStepNode;
import io.harness.beans.steps.nodes.V1.PluginStepNodeV1;
import io.harness.beans.steps.stepinfo.CIStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfoV1;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.beans.PullPolicy;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.execution.plancreator.V1.PluginStepPlanCreatorV1;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.yaml.core.timeout.Timeout;

import java.io.IOException;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;

public class PluginStepPlanCreatorV1Test extends CategoryTest {
  private PluginStepPlanCreatorV1 pluginStepPlanCreatorV1;

  @Before
  public void setUp() {
    pluginStepPlanCreatorV1 = new PluginStepPlanCreatorV1();
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes_shouldReturnPluginV1DisplayName() {
    Set<String> supportedStepTypes = pluginStepPlanCreatorV1.getSupportedStepTypes();

    assertThat(supportedStepTypes)
        .as("should contain PLUGIN_V1 display name")
        .containsExactlyInAnyOrder(CIStepInfoType.PLUGIN_V1.getDisplayName());
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedYamlVersions_shouldReturnV1() {
    Set<String> versions = pluginStepPlanCreatorV1.getSupportedYamlVersions();

    assertThat(versions).as("should contain only V1").containsExactlyInAnyOrder(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFieldObject_shouldParseYamlField() throws IOException {
    YamlField yamlField = mock(YamlField.class);
    YamlNode yamlNode = mock(YamlNode.class);
    PluginStepNodeV1 expected = new PluginStepNodeV1();

    when(yamlField.getNode()).thenReturn(yamlNode);
    when(yamlNode.toString()).thenReturn("{}");

    try (MockedStatic<YamlUtils> yamlUtilsMock = mockStatic(YamlUtils.class)) {
      yamlUtilsMock.when(() -> YamlUtils.read(eq("{}"), eq(PluginStepNodeV1.class))).thenReturn(expected);

      PluginStepNodeV1 result = pluginStepPlanCreatorV1.getFieldObject(yamlField);

      assertThat(result).as("should return parsed PluginStepNodeV1").isEqualTo(expected);
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFieldObject_whenIOException_shouldThrowInvalidYamlException() throws IOException {
    YamlField yamlField = mock(YamlField.class);
    YamlNode yamlNode = mock(YamlNode.class);

    when(yamlField.getNode()).thenReturn(yamlNode);
    when(yamlNode.toString()).thenReturn("invalid");

    try (MockedStatic<YamlUtils> yamlUtilsMock = mockStatic(YamlUtils.class)) {
      yamlUtilsMock.when(() -> YamlUtils.read(eq("invalid"), eq(PluginStepNodeV1.class)))
          .thenThrow(new IOException("parse error"));

      assertThatThrownBy(() -> pluginStepPlanCreatorV1.getFieldObject(yamlField))
          .as("should throw InvalidYamlException on IOException")
          .isInstanceOf(InvalidYamlException.class)
          .hasMessageContaining("Unable to parse plugin step yaml");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepNode_shouldBuildPluginStepNodeWithCorrectFieldMappings() {
    PluginStepInfoV1 stepInfoV1 = PluginStepInfoV1.builder()
                                      .image(ParameterField.createValueField("harness/drone-git:latest"))
                                      .uses(ParameterField.createValueField("plugin-name"))
                                      .privileged(ParameterField.createValueField(true))
                                      .user(ParameterField.createValueField(1000))
                                      .pull(ParameterField.createValueField(PullPolicy.ALWAYS))
                                      .build();

    PluginStepNodeV1 stepElement = new PluginStepNodeV1();
    stepElement.setUuid("test-uuid");
    stepElement.setName("my-plugin-step");
    stepElement.setPluginStepInfo(stepInfoV1);
    stepElement.setTimeout(ParameterField.createValueField(Timeout.fromString("10m")));

    try (MockedStatic<IdentifierGeneratorUtils> idGenMock = mockStatic(IdentifierGeneratorUtils.class);
         MockedStatic<CIPlanCreatorUtils> planUtilsMock = mockStatic(CIPlanCreatorUtils.class)) {
      idGenMock.when(() -> IdentifierGeneratorUtils.getId("my-plugin-step")).thenReturn("my_plugin_step");
      planUtilsMock.when(() -> CIPlanCreatorUtils.getImagePullPolicy(any()))
          .thenReturn(ParameterField.createValueField(ImagePullPolicy.ALWAYS));

      CIAbstractStepNode result = pluginStepPlanCreatorV1.getStepNode(stepElement);

      assertThat(result).as("should not be null").isNotNull();
      assertThat(result.getUuid()).as("should have correct uuid").isEqualTo("test-uuid");
      assertThat(result.getIdentifier()).as("should have generated identifier").isEqualTo("my_plugin_step");
      assertThat(result.getName()).as("should have correct name").isEqualTo("my-plugin-step");

      PluginStepNode pluginStepNode = (PluginStepNode) result;
      PluginStepInfo pluginStepInfo = pluginStepNode.getPluginStepInfo();
      assertThat(pluginStepInfo).as("pluginStepInfo should not be null").isNotNull();
      assertThat(pluginStepInfo.getImage().getValue()).as("should map image").isEqualTo("harness/drone-git:latest");
      assertThat(pluginStepInfo.getUses().getValue()).as("should map uses").isEqualTo("plugin-name");
      assertThat(pluginStepInfo.getPrivileged().getValue()).as("should map privileged").isEqualTo(true);
      assertThat(pluginStepInfo.getRunAsUser().getValue()).as("should map runAsUser").isEqualTo(1000);
      assertThat(pluginStepInfo.getRetry())
          .as("retry should equal CIStepInfo.DEFAULT_RETRY since PluginStepInfoV1 inherits the interface default")
          .isEqualTo(CIStepInfo.DEFAULT_RETRY);
      assertThat(pluginStepInfo.getImagePullPolicy().getValue())
          .as("should map imagePullPolicy")
          .isEqualTo(ImagePullPolicy.ALWAYS);
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepNode_whenMinimalFields_shouldBuildWithNulls() {
    PluginStepInfoV1 stepInfoV1 = PluginStepInfoV1.builder().build();

    PluginStepNodeV1 stepElement = new PluginStepNodeV1();
    stepElement.setUuid("uuid-1");
    stepElement.setName("step1");
    stepElement.setPluginStepInfo(stepInfoV1);

    try (MockedStatic<IdentifierGeneratorUtils> idGenMock = mockStatic(IdentifierGeneratorUtils.class);
         MockedStatic<CIPlanCreatorUtils> planUtilsMock = mockStatic(CIPlanCreatorUtils.class)) {
      idGenMock.when(() -> IdentifierGeneratorUtils.getId("step1")).thenReturn("step1");
      planUtilsMock.when(() -> CIPlanCreatorUtils.getImagePullPolicy(any())).thenReturn(ParameterField.ofNull());

      CIAbstractStepNode result = pluginStepPlanCreatorV1.getStepNode(stepElement);

      assertThat(result).as("should not be null even with minimal fields").isNotNull();
      assertThat(result.getUuid()).as("should have uuid").isEqualTo("uuid-1");
      assertThat(result.getName()).as("should have name").isEqualTo("step1");

      PluginStepNode pluginStepNode = (PluginStepNode) result;
      PluginStepInfo pluginStepInfo = pluginStepNode.getPluginStepInfo();
      assertThat(pluginStepInfo).as("pluginStepInfo should not be null").isNotNull();
      assertThat(pluginStepInfo.getImage()).as("image should be null").isNull();
      assertThat(pluginStepInfo.getUses()).as("uses should be null").isNull();
      assertThat(pluginStepInfo.getPrivileged()).as("privileged should be null").isNull();
      assertThat(pluginStepInfo.getRunAsUser()).as("runAsUser should be null").isNull();
    }
  }
}
