/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.policy.unified;

import static io.harness.rule.OwnerRule.SIDDHARTHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class UnifiedPolicyStepPlanCreatorTest extends CategoryTest {
  private UnifiedPolicyStepPlanCreator unifiedPolicyStepPlanCreator;

  private String policyStepYaml;

  @Before
  public void setUp() {
    policyStepYaml = getPolicyStepYaml();
    unifiedPolicyStepPlanCreator = new UnifiedPolicyStepPlanCreator();
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    assertThat(unifiedPolicyStepPlanCreator.getSupportedStepTypes()).hasSize(1);
    assertThat(unifiedPolicyStepPlanCreator.getSupportedStepTypes()).contains(YAMLFieldNameConstants.UNIFIED_POLICY);
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(unifiedPolicyStepPlanCreator.getFieldClass()).isEqualTo(UnifiedPolicyStepNode.class);
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGetStepType() {
    StepType stepType = unifiedPolicyStepPlanCreator.getStepType();
    assertThat(stepType.getType()).isEqualTo(StepSpecTypeConstants.POLICY_STEP);
    assertThat(stepType.getStepCategory()).isEqualTo(StepCategory.STEP);
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGetFieldObject() throws IOException {
    YamlField yamlField = YamlUtils.readTree(policyStepYaml);
    UnifiedPolicyStepNode stepNode = unifiedPolicyStepPlanCreator.getFieldObject(yamlField);

    // Verify node structure
    assertThat(stepNode).isNotNull();
    assertThat(stepNode.getType()).isEqualTo(StepSpecTypeConstants.POLICY_STEP);
    assertThat(stepNode.getFacilitatorType()).isEqualTo(OrchestrationFacilitatorType.SYNC);

    // Verify policy step info
    assertThat(stepNode.getUnifiedPolicyStepInfo()).isNotNull();
    assertThat(stepNode.getUnifiedPolicyStepInfo().getSpecParameters()).isNotNull();
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testInvalidYaml() {
    String invalidYaml = "invalid: yaml: format";
    YamlField mockField = mock(YamlField.class);
    YamlNode mockNode = mock(YamlNode.class);
    when(mockField.getNode()).thenReturn(mockNode);
    when(mockNode.toString()).thenReturn(invalidYaml);

    assertThatThrownBy(() -> unifiedPolicyStepPlanCreator.getFieldObject(mockField))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Unable to parse policy step yaml.");
  }

  private String getPolicyStepYaml() {
    String policyStepYaml = "policy:\n"
        + "  sets: thisthat\n"
        + "  type: Custom\n"
        + "  payload: |-\n"
        + "    {\n"
        + "      \"this\" : \"that\"\n"
        + "    }\n"
        + "on-failure:\n"
        + "  errors: all\n"
        + "  action: abort\n"
        + "timeout: 10m";
    return policyStepYaml;
  }
}
