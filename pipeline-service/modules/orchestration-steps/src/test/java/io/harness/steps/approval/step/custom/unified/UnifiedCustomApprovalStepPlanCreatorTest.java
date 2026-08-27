/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.custom.unified;

import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.steps.stepinfo.OutputV1;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.approval.step.beans.CriteriaSpecType;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapper;
import io.harness.steps.approval.step.beans.JexlCriteriaSpec;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpec;
import io.harness.steps.approval.step.beans.unified.UnifiedApprovalType;
import io.harness.steps.approval.step.beans.unified.UnifiedCriteriaMapper;
import io.harness.steps.approval.step.beans.unified.UnifiedCustomApprovalStepSpec;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UnifiedCustomApprovalStepPlanCreatorTest extends CategoryTest {
  private UnifiedCustomApprovalStepPlanCreator unifiedCustomApprovalStepPlanCreator;

  @Before
  public void setUp() {
    unifiedCustomApprovalStepPlanCreator = new UnifiedCustomApprovalStepPlanCreator();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> supportedStepTypes = unifiedCustomApprovalStepPlanCreator.getSupportedStepTypes();
    assertThat(supportedStepTypes).hasSize(1);
    assertThat(supportedStepTypes).contains(YAMLFieldNameConstants.UNIFIED_CUSTOM_APPROVAL);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(unifiedCustomApprovalStepPlanCreator.getFieldClass()).isEqualTo(UnifiedCustomApprovalStepNode.class);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetStepType() {
    StepType stepType = unifiedCustomApprovalStepPlanCreator.getStepType();
    assertThat(stepType.getType()).isEqualTo(StepSpecTypeConstants.CUSTOM_APPROVAL);
    assertThat(stepType.getStepCategory()).isEqualTo(StepCategory.STEP);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetStepExpressionMode() {
    assertThat(unifiedCustomApprovalStepPlanCreator.getStepExpressionMode())
        .isEqualTo(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetCustomApprovalObject1() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL customApprovalYAMLFile = classLoader.getResource("custom-approval-unified-1.yaml");
    assertThat(customApprovalYAMLFile).isNotNull();
    String customApprovalYAML = Resources.toString(customApprovalYAMLFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(customApprovalYAML);
    UnifiedCustomApprovalStepNode stepNode = unifiedCustomApprovalStepPlanCreator.getFieldObject(yamlField);

    // Verify node structure
    assertThat(stepNode).isNotNull();
    assertThat(stepNode.getType()).isEqualTo(StepSpecTypeConstants.CUSTOM_APPROVAL);
    assertThat(stepNode.getFacilitatorType()).isEqualTo(StepSpecTypeConstants.APPROVAL_FACILITATOR);

    // Verify approval info
    assertThat(stepNode.getUnifiedApprovalStepInfo()).isNotNull();
    assertThat(stepNode.getUnifiedApprovalStepInfo().getType()).isEqualTo(UnifiedApprovalType.UNIFIED_CUSTOM_APPROVAL);

    // Verify spec
    UnifiedCustomApprovalStepSpec spec =
        (UnifiedCustomApprovalStepSpec) stepNode.getUnifiedApprovalStepInfo().getSpec();
    assertThat(spec).isNotNull();

    // Verify script properties
    assertThat(spec.getScriptTimeout()).isEqualTo("3m");
    assertThat(spec.getRetry()).isEqualTo("15s");

    // Verify approve criteria
    assertThat(spec.getApprove()).isNotNull();
    CriteriaSpecWrapper approveWrapper = UnifiedCriteriaMapper.toCriteriaSpecWrapper(spec.getApprove());
    assertThat(approveWrapper).isNotNull();
    assertThat(approveWrapper.getType()).isEqualTo(CriteriaSpecType.KEY_VALUES);

    KeyValuesCriteriaSpec keyValueSpec = (KeyValuesCriteriaSpec) approveWrapper.getCriteriaSpec();
    assertThat(keyValueSpec).isNotNull();
    assertThat(keyValueSpec.getMatchAnyCondition().getValue()).isEqualTo(true);
    assertThat(keyValueSpec.getConditions()).hasSize(2);
    assertThat(keyValueSpec.getConditions().get(0).getKey()).isEqualTo("var1");
    assertThat(keyValueSpec.getConditions().get(0).getValue().obtainValue()).isEqualTo("approved");
    assertThat(keyValueSpec.getConditions().get(0).getOperator().getDisplayName()).isEqualTo("equals");
    assertThat(keyValueSpec.getConditions().get(1).getKey()).isEqualTo("shobhit");
    assertThat(keyValueSpec.getConditions().get(1).getValue().obtainValue()).isEqualTo("singhs");
    assertThat(keyValueSpec.getConditions().get(1).getOperator().getDisplayName()).isEqualTo("equals");

    // Verify reject criteria
    assertThat(spec.getReject()).isNotNull();
    CriteriaSpecWrapper rejectWrapper = UnifiedCriteriaMapper.toCriteriaSpecWrapper(spec.getReject());
    assertThat(rejectWrapper).isNotNull();
    assertThat(rejectWrapper.getType()).isEqualTo(CriteriaSpecType.JEXL);

    JexlCriteriaSpec jexlSpec = (JexlCriteriaSpec) rejectWrapper.getCriteriaSpec();
    assertThat(jexlSpec).isNotNull();
    assertThat(jexlSpec.getExpression().getValue()).isEqualTo("<+<+pipeline.name> == \"CustomApprovalV1\">");

    // Verify output variables
    assertThat(spec.getRunStep().getOutput()).hasSize(2);
    OutputV1 variable = spec.getRunStep().getOutput().get(0);
    assertThat(variable.getName()).isEqualTo("var1");
    assertThat(variable.getMask()).isEqualTo(null);
    OutputV1 variable2 = spec.getRunStep().getOutput().get(1);
    assertThat(variable2.getName()).isEqualTo("shobhit");
    assertThat(variable2.getMask()).isEqualTo(null);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetCustomApprovalObject2() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL customApprovalYAMLFile = classLoader.getResource("custom-approval-unified-2.yaml");
    assertThat(customApprovalYAMLFile).isNotNull();
    String customApprovalYAML = Resources.toString(customApprovalYAMLFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(customApprovalYAML);
    UnifiedCustomApprovalStepNode stepNode = unifiedCustomApprovalStepPlanCreator.getFieldObject(yamlField);

    // Verify node structure
    assertThat(stepNode).isNotNull();
    assertThat(stepNode.getType()).isEqualTo(StepSpecTypeConstants.CUSTOM_APPROVAL);
    assertThat(stepNode.getFacilitatorType()).isEqualTo(StepSpecTypeConstants.APPROVAL_FACILITATOR);

    // Verify approval info
    assertThat(stepNode.getUnifiedApprovalStepInfo()).isNotNull();
    assertThat(stepNode.getUnifiedApprovalStepInfo().getType()).isEqualTo(UnifiedApprovalType.UNIFIED_CUSTOM_APPROVAL);

    // Verify spec
    UnifiedCustomApprovalStepSpec spec =
        (UnifiedCustomApprovalStepSpec) stepNode.getUnifiedApprovalStepInfo().getSpec();
    assertThat(spec).isNotNull();

    // Verify script properties
    assertThat(spec.getScriptTimeout()).isEqualTo("4m 30s");
    assertThat(spec.getRetry()).isEqualTo("1m 30s");

    // Verify approve criteria using the mapper
    assertThat(spec.getApprove()).isNotNull();
    CriteriaSpecWrapper approveWrapper = UnifiedCriteriaMapper.toCriteriaSpecWrapper(spec.getApprove());
    assertThat(approveWrapper).isNotNull();
    assertThat(approveWrapper.getType()).isEqualTo(CriteriaSpecType.KEY_VALUES);

    KeyValuesCriteriaSpec keyValueSpec = (KeyValuesCriteriaSpec) approveWrapper.getCriteriaSpec();
    assertThat(keyValueSpec).isNotNull();
    assertThat(keyValueSpec.getMatchAnyCondition().getValue()).isEqualTo(false);
    assertThat(keyValueSpec.getConditions()).hasSize(3);
    assertThat(keyValueSpec.getConditions().get(0).getKey()).isEqualTo("input1");
    assertThat(keyValueSpec.getConditions().get(0).getValue().obtainValue()).isEqualTo("test1");
    assertThat(keyValueSpec.getConditions().get(0).getOperator().getDisplayName()).isEqualTo("in");
    assertThat(keyValueSpec.getConditions().get(1).getKey()).isEqualTo("input2");
    assertThat(keyValueSpec.getConditions().get(1).getValue().obtainValue()).isEqualTo("test2");
    assertThat(keyValueSpec.getConditions().get(1).getOperator().getDisplayName()).isEqualTo("not in");
    assertThat(keyValueSpec.getConditions().get(2).getKey()).isEqualTo("input3");
    assertThat(keyValueSpec.getConditions().get(2).getValue().obtainValue()).isEqualTo("test3");
    assertThat(keyValueSpec.getConditions().get(2).getOperator().getDisplayName()).isEqualTo("not equals");

    // Verify reject criteria
    assertThat(spec.getReject()).isNull();

    // Verify output variables
    assertThat(spec.getRunStep().getOutput()).hasSize(2);
    OutputV1 stringVariable = spec.getRunStep().getOutput().get(0);
    assertThat(stringVariable.getName()).isEqualTo("var1");
    assertThat(stringVariable.getMask()).isEqualTo(null);
    OutputV1 secVariable = spec.getRunStep().getOutput().get(1);
    assertThat(secVariable.getName()).isEqualTo("var2");
    assertThat(secVariable.getMask().booleanValue()).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testInvalidYaml() {
    String invalidYaml = "invalid: yaml: format";
    YamlField mockField = mock(YamlField.class);
    YamlNode mockNode = mock(YamlNode.class);
    when(mockField.getNode()).thenReturn(mockNode);
    when(mockNode.toString()).thenReturn(invalidYaml);

    assertThatThrownBy(() -> unifiedCustomApprovalStepPlanCreator.getFieldObject(mockField))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Unable to parse approval step yaml");
  }
}
