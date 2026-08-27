/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.step;

import static io.harness.beans.FeatureName.CDS_AI_VERIFY_DEMO;
import static io.harness.beans.FeatureName.CI_ENABLE_GENERIC_CACHE_STEPS;
import static io.harness.rule.OwnerRule.ANKUSH_CHATERJEE;
import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.steps.nodes.RunStepNode;
import io.harness.beans.steps.nodes.RunTestStepNode;
import io.harness.beans.steps.nodes.RunTestStepV2Node;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestStepV2Info;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CIStepFilterJsonCreatorV2Test {
  @InjectMocks CIStepFilterJsonCreatorV2 ciStepFilterJsonCreatorV2New;
  @Mock private CIFeatureFlagService ciFeatureFlagService;
  CIStepFilterJsonCreatorV2 ciStepFilterJsonCreatorV2 = new CIStepFilterJsonCreatorV2();

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testValidateRunStepK8() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = FilterCreationContext.builder().currentField(yamlField).build();
    RunStepNode runStepNode =
        RunStepNode.builder()
            .runStepInfo(RunStepInfo.builder()
                             .connectorRef(ParameterField.<String>builder().value("connector").build())
                             .image(ParameterField.<String>builder().value("image").build())
                             .build())
            .build();

    ciStepFilterJsonCreatorV2.validateStep(context, runStepNode);

    RunStepNode runStepNode1 =
        RunStepNode.builder()
            .runStepInfo(RunStepInfo.builder()
                             .connectorRef(ParameterField.<String>builder().value("connector").build())
                             .image(ParameterField.<String>builder().expressionValue("<+matrix.image>").build())
                             .build())
            .build();

    ciStepFilterJsonCreatorV2.validateStep(context, runStepNode1);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testValidateRunStepK8Exception() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = FilterCreationContext.builder().currentField(yamlField).build();
    RunStepNode runStepNode =
        RunStepNode.builder()
            .runStepInfo(RunStepInfo.builder()
                             .connectorRef(ParameterField.<String>builder().value("connector").build())
                             .image(ParameterField.<String>builder().build())
                             .build())
            .build();

    assertThatThrownBy(() -> ciStepFilterJsonCreatorV2.validateStep(context, runStepNode))
        .isInstanceOf(InvalidYamlException.class);

    RunStepNode runStepNode1 = RunStepNode.builder()
                                   .runStepInfo(RunStepInfo.builder()
                                                    .connectorRef(ParameterField.<String>builder().build())
                                                    .image(ParameterField.<String>builder().value("image").build())
                                                    .build())
                                   .build();

    assertThatThrownBy(() -> ciStepFilterJsonCreatorV2.validateStep(context, runStepNode1))
        .isInstanceOf(InvalidYamlException.class);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testValidateRunStepVMImageNull() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("VM"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = FilterCreationContext.builder().currentField(yamlField).build();
    RunStepNode runStepNode = RunStepNode.builder()
                                  .runStepInfo(RunStepInfo.builder()
                                                   .connectorRef(ParameterField.<String>builder().build())
                                                   .image(ParameterField.<String>builder().build())
                                                   .build())
                                  .build();

    ciStepFilterJsonCreatorV2.validateStep(context, runStepNode);
  }
  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testValidateRunTestStepV2K8ExceptionWithEmptyImage() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);

    FilterCreationContext context = getFilterContext(yamlField);
    RunTestStepV2Node runTestStepV2Node =
        RunTestStepV2Node.builder()
            .runTestStepV2Info(RunTestStepV2Info.builder()
                                   .connectorRef(ParameterField.<String>builder().value("connector").build())
                                   .image(ParameterField.<String>builder().build())
                                   .build())
            .build();

    assertThatThrownBy(() -> ciStepFilterJsonCreatorV2New.validateStep(context, runTestStepV2Node))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Test step with Kubernetes infra can't have empty image field");
  }

  @Test
  @Owner(developers = ANKUSH_CHATERJEE)
  @Category(UnitTests.class)
  public void testValidateRunTestStepV2K8ExceptionWithEmptyConnector() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);

    FilterCreationContext context = getFilterContext(yamlField);
    RunTestStepV2Node runTestStepV2Node =
        RunTestStepV2Node.builder()
            .runTestStepV2Info(RunTestStepV2Info.builder()
                                   .connectorRef(ParameterField.<String>builder().build())
                                   .image(ParameterField.<String>builder().value("image").build())
                                   .build())
            .build();

    assertThatThrownBy(() -> ciStepFilterJsonCreatorV2New.validateStep(context, runTestStepV2Node))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Test step with Kubernetes infra can't have empty connector field");
  }

  @Test
  @Owner(developers = ANKUSH_CHATERJEE)
  @Category(UnitTests.class)
  public void testValidateRunTestStepV2K8WithRegistryRef() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);

    FilterCreationContext context = getFilterContext(yamlField);
    RunTestStepV2Node runTestStepV2Node =
        RunTestStepV2Node.builder()
            .runTestStepV2Info(RunTestStepV2Info.builder()
                                   .connectorRef(ParameterField.<String>builder().build())
                                   .registryRef(ParameterField.<String>builder().value("account.myRegistry").build())
                                   .image(ParameterField.<String>builder().value("image").build())
                                   .build())
            .build();

    ciStepFilterJsonCreatorV2New.validateStep(context, runTestStepV2Node);
  }

  @Test
  @Owner(developers = ANKUSH_CHATERJEE)
  @Category(UnitTests.class)
  public void testValidateRunTestStepV2K8ExceptionWithEmptyConnectorAndRegistryRef() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = getFilterContext(yamlField);
    RunTestStepV2Node runTestStepV2Node =
        RunTestStepV2Node.builder()
            .runTestStepV2Info(RunTestStepV2Info.builder()
                                   .connectorRef(ParameterField.<String>builder().build())
                                   .registryRef(ParameterField.<String>builder().build())
                                   .image(ParameterField.<String>builder().value("image").build())
                                   .build())
            .build();
    assertThatThrownBy(() -> ciStepFilterJsonCreatorV2New.validateStep(context, runTestStepV2Node))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Test step with Kubernetes infra can't have empty connector field");
  }

  private FilterCreationContext getFilterContext(YamlField yamlField) {
    return FilterCreationContext.builder()
        .currentField(yamlField)
        .setupMetadata(
            SetupMetadata.newBuilder().setAccountId("accountId").setOrgId("orgId").setProjectId("projectId").build())
        .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes_shouldReturnNonEmptySet() {
    Set<String> supportedStepTypes = ciStepFilterJsonCreatorV2New.getSupportedStepTypes();
    assertThat(supportedStepTypes).as("Supported step types should not be null").isNotNull();
    assertThat(supportedStepTypes).as("Supported step types should not be empty").isNotEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateStep_whenRunTestsType_withK8Infra_shouldValidate() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = FilterCreationContext.builder().currentField(yamlField).build();
    RunTestStepNode runTestStepNode =
        RunTestStepNode.builder()
            .runTestsStepInfo(RunTestsStepInfo.builder()
                                  .connectorRef(ParameterField.<String>builder().value("connector").build())
                                  .image(ParameterField.<String>builder().value("image").build())
                                  .build())
            .build();

    ciStepFilterJsonCreatorV2New.validateStep(context, runTestStepNode);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateStep_whenRunTestsType_withK8Infra_emptyConnector_shouldThrow() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = FilterCreationContext.builder().currentField(yamlField).build();
    RunTestStepNode runTestStepNode =
        RunTestStepNode.builder()
            .runTestsStepInfo(RunTestsStepInfo.builder()
                                  .connectorRef(ParameterField.<String>builder().build())
                                  .image(ParameterField.<String>builder().value("image").build())
                                  .build())
            .build();

    assertThatThrownBy(() -> ciStepFilterJsonCreatorV2New.validateStep(context, runTestStepNode))
        .as("Should throw when connector is empty for RunTests on K8")
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("RunTests step with Kubernetes infra can't have empty connector field");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateStep_whenRunTestsType_withK8Infra_emptyImage_shouldThrow() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = FilterCreationContext.builder().currentField(yamlField).build();
    RunTestStepNode runTestStepNode =
        RunTestStepNode.builder()
            .runTestsStepInfo(RunTestsStepInfo.builder()
                                  .connectorRef(ParameterField.<String>builder().value("connector").build())
                                  .image(ParameterField.<String>builder().build())
                                  .build())
            .build();

    assertThatThrownBy(() -> ciStepFilterJsonCreatorV2New.validateStep(context, runTestStepNode))
        .as("Should throw when image is empty for RunTests on K8")
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("RunTests step with Kubernetes infra can't have empty image field");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateStep_whenRunTestsType_withNonK8Infra_shouldNotThrow() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("VM"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = FilterCreationContext.builder().currentField(yamlField).build();
    RunTestStepNode runTestStepNode = RunTestStepNode.builder()
                                          .runTestsStepInfo(RunTestsStepInfo.builder()
                                                                .connectorRef(ParameterField.<String>builder().build())
                                                                .image(ParameterField.<String>builder().build())
                                                                .build())
                                          .build();

    ciStepFilterJsonCreatorV2New.validateStep(context, runTestStepNode);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateStep_whenAiVerifyType_ffDisabled_shouldThrow() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = getFilterContext(yamlField);

    AbstractStepNode mockNode = mock(AbstractStepNode.class);
    when(mockNode.getType()).thenReturn("AiVerify");
    when(ciFeatureFlagService.isEnabled(eq(CDS_AI_VERIFY_DEMO), eq("accountId"))).thenReturn(false);

    assertThatThrownBy(() -> ciStepFilterJsonCreatorV2New.validateStep(context, mockNode))
        .as("Should throw when AI Verify FF is disabled")
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("AI Verify Step is not enabled for your account");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateStep_whenAiVerifyType_ffEnabled_shouldNotThrow() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = getFilterContext(yamlField);

    AbstractStepNode mockNode = mock(AbstractStepNode.class);
    when(mockNode.getType()).thenReturn("AiVerify");
    when(ciFeatureFlagService.isEnabled(eq(CDS_AI_VERIFY_DEMO), eq("accountId"))).thenReturn(true);

    ciStepFilterJsonCreatorV2New.validateStep(context, mockNode);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateStep_whenSaveCacheType_ffDisabled_shouldThrow() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = getFilterContext(yamlField);

    AbstractStepNode mockNode = mock(AbstractStepNode.class);
    when(mockNode.getType()).thenReturn("SaveCache");
    when(ciFeatureFlagService.isEnabled(eq(CI_ENABLE_GENERIC_CACHE_STEPS), eq("accountId"))).thenReturn(false);

    assertThatThrownBy(() -> ciStepFilterJsonCreatorV2New.validateStep(context, mockNode))
        .as("Should throw when generic cache FF is disabled for SaveCache")
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Generic Cache Steps are not enabled for your account");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateStep_whenRestoreCacheType_ffDisabled_shouldThrow() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = getFilterContext(yamlField);

    AbstractStepNode mockNode = mock(AbstractStepNode.class);
    when(mockNode.getType()).thenReturn("RestoreCache");
    when(ciFeatureFlagService.isEnabled(eq(CI_ENABLE_GENERIC_CACHE_STEPS), eq("accountId"))).thenReturn(false);

    assertThatThrownBy(() -> ciStepFilterJsonCreatorV2New.validateStep(context, mockNode))
        .as("Should throw when generic cache FF is disabled for RestoreCache")
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Generic Cache Steps are not enabled for your account");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateStep_whenSaveCacheType_ffEnabled_shouldNotThrow() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = getFilterContext(yamlField);

    AbstractStepNode mockNode = mock(AbstractStepNode.class);
    when(mockNode.getType()).thenReturn("SaveCache");
    when(ciFeatureFlagService.isEnabled(eq(CI_ENABLE_GENERIC_CACHE_STEPS), eq("accountId"))).thenReturn(true);

    ciStepFilterJsonCreatorV2New.validateStep(context, mockNode);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateStep_whenTestType_withNonK8Infra_shouldNotThrow() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("VM"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = getFilterContext(yamlField);
    RunTestStepV2Node runTestStepV2Node =
        RunTestStepV2Node.builder()
            .runTestStepV2Info(RunTestStepV2Info.builder()
                                   .connectorRef(ParameterField.<String>builder().build())
                                   .image(ParameterField.<String>builder().build())
                                   .build())
            .build();

    ciStepFilterJsonCreatorV2New.validateStep(context, runTestStepV2Node);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateStep_whenDefaultType_shouldNotThrow() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = FilterCreationContext.builder().currentField(yamlField).build();

    AbstractStepNode mockNode = mock(AbstractStepNode.class);
    when(mockNode.getType()).thenReturn("UnknownStepType");

    ciStepFilterJsonCreatorV2New.validateStep(context, mockNode);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStageInfra_whenNoInfrastructureNode_shouldReturnNull() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode nodeWithoutInfra = mapper.createObjectNode();
    nodeWithoutInfra.put("identifier", "identifier");
    nodeWithoutInfra.put("name", "name");

    YamlNode yamlNode = new YamlNode(nodeWithoutInfra);
    YamlField yamlField = new YamlField("step", yamlNode);
    FilterCreationContext context = FilterCreationContext.builder().currentField(yamlField).build();

    AbstractStepNode mockNode = mock(AbstractStepNode.class);
    when(mockNode.getType()).thenReturn("UnknownStepType");

    ciStepFilterJsonCreatorV2New.validateStep(context, mockNode);
  }

  private static JsonNode getRunStepElementConfigAsJsonNode(String infra) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", "identifier");
    stepElementConfig.put("name", "name");

    ObjectNode infraNode = mapper.createObjectNode();
    infraNode.put("type", infra);
    stepElementConfig.set("infrastructure", infraNode);

    return stepElementConfig;
  }
}
