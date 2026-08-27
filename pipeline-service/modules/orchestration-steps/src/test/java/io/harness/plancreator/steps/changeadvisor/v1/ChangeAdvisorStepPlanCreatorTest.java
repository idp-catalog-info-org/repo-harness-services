/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.changeadvisor.v1;

import static io.harness.rule.OwnerRule.SHUBHENDU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.steps.changeadvisor.v1.ChangeAdvisorStepInfoV1;
import io.harness.steps.changeadvisor.v1.ChangeAdvisorStepNodeV1;
import io.harness.steps.changeadvisor.v1.ChangeAdvisorStepParameters;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ChangeAdvisorStepPlanCreatorTest extends CategoryTest {
  private ChangeAdvisorStepPlanCreator changeAdvisorStepPlanCreator;
  private String changeAdvisorStepYaml;

  @Before
  public void setUp() {
    changeAdvisorStepPlanCreator = new ChangeAdvisorStepPlanCreator();
    changeAdvisorStepYaml = "id: changeAdvisor\n"
        + "name: Change Advisor\n"
        + "type: change-advisor\n"
        + "change-advisor:\n"
        + "  mode: ADVISORY\n"
        + "  policy-pack: balanced\n"
        + "  timeout-minutes: 5\n"
        + "  env: prod\n"
        + "  presets:\n"
        + "    - balanced-default\n"
        + "timeout: 10s\n";
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    assertThat(changeAdvisorStepPlanCreator.getSupportedStepTypes())
        .containsExactly(StepSpecTypeConstantsV1.CHANGE_ADVISOR);
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(changeAdvisorStepPlanCreator.getFieldClass()).isEqualTo(ChangeAdvisorStepNodeV1.class);
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testGetFieldObject() throws IOException {
    YamlField yamlField = YamlUtils.readTree(changeAdvisorStepYaml);
    ChangeAdvisorStepNodeV1 stepNode = changeAdvisorStepPlanCreator.getFieldObject(yamlField);

    assertThat(stepNode).isNotNull();
    assertThat(stepNode.getType()).isEqualTo(StepSpecTypeConstantsV1.CHANGE_ADVISOR);
    assertThat(stepNode.getSpec()).isNotNull();
    assertThat(stepNode.getSpec().getStepType()).isEqualTo(StepSpecTypeConstantsV1.CHANGE_ADVISOR_STEP_TYPE);
    assertThat(stepNode.getSpec().getStepType().getStepCategory()).isEqualTo(StepCategory.STEP);
    assertThat(stepNode.getSpec().getFacilitatorType()).isEqualTo(StepSpecTypeConstantsV1.APPROVAL_FACILITATOR);

    ChangeAdvisorStepInfoV1 info = stepNode.getSpec();
    assertThat(info.getMode().obtainValue()).isEqualTo("ADVISORY");
    assertThat(info.getPolicyPack().obtainValue()).isEqualTo("balanced");
    assertThat(info.getTimeoutMinutes().obtainValue()).isEqualTo(5);
    assertThat(info.getEnv().obtainValue()).isEqualTo("prod");
    assertThat(info.getPresets().obtainValue()).containsExactly("balanced-default");
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testGetSpecParametersWithExpressions() throws IOException {
    String yamlWithExpressions = "type: change-advisor\n"
        + "change-advisor:\n"
        + "  mode: ${{ pipeline.variables.caMode }}\n"
        + "  policy-pack: ${{ pipeline.variables.policyPack }}\n"
        + "  timeout-minutes: ${{ 30 }}\n"
        + "  env: ${{ pipeline.variables.env }}\n";

    ChangeAdvisorStepNodeV1 stepNode =
        changeAdvisorStepPlanCreator.getFieldObject(YamlUtils.readTree(yamlWithExpressions));
    ChangeAdvisorStepParameters specParameters = (ChangeAdvisorStepParameters) stepNode.getSpecParameters();

    assertThat(specParameters.getMode().isExpression()).isTrue();
    assertThat(specParameters.getMode().getExpressionValue()).isEqualTo("${{ pipeline.variables.caMode }}");
    assertThat(specParameters.getPolicyPack().isExpression()).isTrue();
    assertThat(specParameters.getPolicyPack().getExpressionValue()).isEqualTo("${{ pipeline.variables.policyPack }}");
    assertThat(specParameters.getTimeoutMinutes().isExpression()).isTrue();
    assertThat(specParameters.getTimeoutMinutes().getExpressionValue()).isEqualTo("${{ 30 }}");
    assertThat(specParameters.getEnv().isExpression()).isTrue();
    assertThat(specParameters.getEnv().getExpressionValue()).isEqualTo("${{ pipeline.variables.env }}");
    assertThat(specParameters.getVersion()).isEqualTo(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testInvalidYaml() {
    YamlField mockField = mock(YamlField.class);
    YamlNode mockNode = mock(YamlNode.class);
    when(mockField.getNode()).thenReturn(mockNode);
    when(mockNode.toString()).thenReturn("invalid: yaml: format");

    assertThatThrownBy(() -> changeAdvisorStepPlanCreator.getFieldObject(mockField))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Unable to parse change-advisor step yaml.");
  }
}
