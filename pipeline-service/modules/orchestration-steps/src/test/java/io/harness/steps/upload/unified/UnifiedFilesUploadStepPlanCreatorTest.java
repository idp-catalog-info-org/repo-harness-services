/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.upload.unified;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.unified.UnifiedPmsAbstractStepNode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.upload.FilesUploadStepParameters;
import io.harness.yaml.core.variables.v1.NGVariableV1Wrapper;
import io.harness.yaml.core.variables.v1.StringNGVariableV1;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class UnifiedFilesUploadStepPlanCreatorTest extends CategoryTest {
  // Test-specific subclass that exposes the protected method
  static class TestableUnifiedFilesUploadStepPlanCreator extends UnifiedFilesUploadStepPlanCreator {
    @Override
    protected SpecParameters getSpec(UnifiedPmsAbstractStepNode stepNode) {
      return super.getSpec(stepNode);
    }
  }

  private TestableUnifiedFilesUploadStepPlanCreator unifiedFilesUploadStepPlanCreator;

  private String filesUploadStepYaml;

  @Before
  public void setUp() {
    filesUploadStepYaml = getFilesUploadStepYaml();
    unifiedFilesUploadStepPlanCreator = new TestableUnifiedFilesUploadStepPlanCreator();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    assertThat(unifiedFilesUploadStepPlanCreator.getSupportedStepTypes()).hasSize(1);
    assertThat(unifiedFilesUploadStepPlanCreator.getSupportedStepTypes())
        .contains(YAMLFieldNameConstants.FILES_UPLOAD_V1);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(unifiedFilesUploadStepPlanCreator.getFieldClass()).isEqualTo(UnifiedFilesUploadStepNode.class);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetStepType() {
    StepType stepType = unifiedFilesUploadStepPlanCreator.getStepType();
    assertThat(stepType.getType()).isEqualTo(StepSpecTypeConstants.UPLOAD);
    assertThat(stepType.getStepCategory()).isEqualTo(StepCategory.STEP);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetFieldObject() throws IOException {
    YamlField yamlField = YamlUtils.readTree(filesUploadStepYaml);
    UnifiedFilesUploadStepNode stepNode = unifiedFilesUploadStepPlanCreator.getFieldObject(yamlField);

    // Verify node structure
    assertThat(stepNode).isNotNull();
    assertThat(stepNode.getType()).isEqualTo(StepSpecTypeConstants.UPLOAD);
    assertThat(stepNode.getFacilitatorType()).isEqualTo(OrchestrationFacilitatorType.ASYNC);

    // Verify files upload step info
    assertThat(stepNode.getUnifiedFilesUploadStepInfo()).isNotNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testInvalidYaml() {
    String invalidYaml = "invalid: yaml: format";
    YamlField mockField = mock(YamlField.class);
    YamlNode mockNode = mock(YamlNode.class);
    when(mockField.getNode()).thenReturn(mockNode);
    when(mockNode.toString()).thenReturn(invalidYaml);

    assertThatThrownBy(() -> unifiedFilesUploadStepPlanCreator.getFieldObject(mockField))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Unable to parse files upload step yaml.");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetSpecWithNullStepInfo() {
    UnifiedFilesUploadStepNode stepNode = UnifiedFilesUploadStepNode.builder().unifiedFilesUploadStepInfo(null).build();

    SpecParameters specParameters = unifiedFilesUploadStepPlanCreator.getSpec(stepNode);

    assertThat(specParameters).isNotNull();
    assertThat(specParameters).isInstanceOf(FilesUploadStepParameters.class);
    FilesUploadStepParameters filesUploadStepParameters = (FilesUploadStepParameters) specParameters;
    assertThat(filesUploadStepParameters.getInputVariables()).isEmpty();
    assertThat(filesUploadStepParameters.getOutputVariables()).isEmpty();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetSpecWithNullInputs() {
    UnifiedFilesUploadStepInfo stepInfo = new UnifiedFilesUploadStepInfo(null);
    UnifiedFilesUploadStepNode stepNode =
        UnifiedFilesUploadStepNode.builder().unifiedFilesUploadStepInfo(stepInfo).build();

    SpecParameters specParameters = unifiedFilesUploadStepPlanCreator.getSpec(stepNode);

    assertThat(specParameters).isNotNull();
    assertThat(specParameters).isInstanceOf(FilesUploadStepParameters.class);
    FilesUploadStepParameters filesUploadStepParameters = (FilesUploadStepParameters) specParameters;
    assertThat(filesUploadStepParameters.getInputVariables()).isEmpty();
    assertThat(filesUploadStepParameters.getOutputVariables()).isEmpty();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetSpecWithEmptyInputs() {
    NGVariableV1Wrapper inputs = new NGVariableV1Wrapper();
    inputs.getMap().clear();
    UnifiedFilesUploadStepInfo stepInfo = new UnifiedFilesUploadStepInfo(inputs);
    UnifiedFilesUploadStepNode stepNode =
        UnifiedFilesUploadStepNode.builder().unifiedFilesUploadStepInfo(stepInfo).build();

    SpecParameters specParameters = unifiedFilesUploadStepPlanCreator.getSpec(stepNode);

    assertThat(specParameters).isNotNull();
    assertThat(specParameters).isInstanceOf(FilesUploadStepParameters.class);
    FilesUploadStepParameters filesUploadStepParameters = (FilesUploadStepParameters) specParameters;
    assertThat(filesUploadStepParameters.getInputVariables()).isEmpty();
    assertThat(filesUploadStepParameters.getOutputVariables()).isEmpty();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetSpecWithInputs() {
    // Create test variables
    StringNGVariableV1 var1 = StringNGVariableV1.builder()
                                  .uuid("uuid1")
                                  .value(ParameterField.createValueField("value1"))
                                  .required(false)
                                  .build();
    StringNGVariableV1 var2 = StringNGVariableV1.builder()
                                  .uuid("uuid2")
                                  .value(ParameterField.createValueField("value2"))
                                  .required(false)
                                  .build();

    Map<String, io.harness.yaml.core.variables.NGVariableV1> variablesMap = new HashMap<>();
    variablesMap.put("var1", var1);
    variablesMap.put("var2", var2);

    NGVariableV1Wrapper inputs = new NGVariableV1Wrapper();
    inputs.getMap().putAll(variablesMap);

    UnifiedFilesUploadStepInfo stepInfo = new UnifiedFilesUploadStepInfo(inputs);
    UnifiedFilesUploadStepNode stepNode =
        UnifiedFilesUploadStepNode.builder().unifiedFilesUploadStepInfo(stepInfo).build();

    SpecParameters specParameters = unifiedFilesUploadStepPlanCreator.getSpec(stepNode);

    assertThat(specParameters).isNotNull();
    assertThat(specParameters).isInstanceOf(FilesUploadStepParameters.class);
    FilesUploadStepParameters filesUploadStepParameters = (FilesUploadStepParameters) specParameters;
    assertThat(filesUploadStepParameters.getInputVariables()).isNotNull();
    assertThat(filesUploadStepParameters.getInputVariables().size()).isEqualTo(2);
    assertThat(filesUploadStepParameters.getInputVariables().containsKey("var1")).isTrue();
    assertThat(filesUploadStepParameters.getInputVariables().containsKey("var2")).isTrue();
    assertThat(filesUploadStepParameters.getOutputVariables()).isEmpty();
  }

  private String getFilesUploadStepYaml() {
    String filesUploadStepYaml = "upload:\n"
        + "  inputs:\n"
        + "    var1:\n"
        + "      type: string\n"
        + "      value: \"value1\"\n"
        + "    var2:\n"
        + "      type: string\n"
        + "      value: \"value2\"\n"
        + "timeout: 10m\n";
    return filesUploadStepYaml;
  }
}
