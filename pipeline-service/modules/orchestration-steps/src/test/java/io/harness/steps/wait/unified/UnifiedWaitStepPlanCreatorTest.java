/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.wait.unified;

import static io.harness.rule.OwnerRule.RISHIKESH;

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

@OwnedBy(HarnessTeam.CI)
public class UnifiedWaitStepPlanCreatorTest extends CategoryTest {
  private UnifiedWaitStepPlanCreator unifiedWaitStepPlanCreator;

  private String waitStepYaml;

  @Before
  public void setUp() {
    waitStepYaml = getWaitStepYaml();
    unifiedWaitStepPlanCreator = new UnifiedWaitStepPlanCreator();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    assertThat(unifiedWaitStepPlanCreator.getSupportedStepTypes()).hasSize(1);
    assertThat(unifiedWaitStepPlanCreator.getSupportedStepTypes()).contains(YAMLFieldNameConstants.WAIT_V1);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(unifiedWaitStepPlanCreator.getFieldClass()).isEqualTo(UnifiedWaitStepNode.class);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetStepType() {
    StepType stepType = unifiedWaitStepPlanCreator.getStepType();
    assertThat(stepType.getType()).isEqualTo(StepSpecTypeConstants.WAIT_STEP);
    assertThat(stepType.getStepCategory()).isEqualTo(StepCategory.STEP);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetFieldObject() throws IOException {
    YamlField yamlField = YamlUtils.readTree(waitStepYaml);
    UnifiedWaitStepNode stepNode = unifiedWaitStepPlanCreator.getFieldObject(yamlField);

    // Verify node structure
    assertThat(stepNode).isNotNull();
    assertThat(stepNode.getType()).isEqualTo(StepSpecTypeConstants.WAIT_STEP);
    assertThat(stepNode.getFacilitatorType()).isEqualTo(OrchestrationFacilitatorType.WAIT_STEP);

    // Verify wait step info
    assertThat(stepNode.getUnifiedWaitStepInfo()).isNotNull();
    assertThat(stepNode.getUnifiedWaitStepInfo().getSpecParameters()).isNotNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testInvalidYaml() {
    String invalidYaml = "invalid: yaml: format";
    YamlField mockField = mock(YamlField.class);
    YamlNode mockNode = mock(YamlNode.class);
    when(mockField.getNode()).thenReturn(mockNode);
    when(mockNode.toString()).thenReturn(invalidYaml);

    assertThatThrownBy(() -> unifiedWaitStepPlanCreator.getFieldObject(mockField))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Unable to parse wait step yaml.");
  }

  private String getWaitStepYaml() {
    String waitStepYaml = "wait:\n"
        + "  duration: 2m\n"
        + "timeout: 10m\n"
        + "strategy:\n"
        + "  matrix:\n"
        + "    version:\n"
        + "      - \"1.0\"\n"
        + "      - \"1.1\"\n"
        + "on-failure:\n"
        + "  errors: all\n"
        + "  action: abort";
    return waitStepYaml;
  }
}
