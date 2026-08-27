/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.CIStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfoV1;
import io.harness.beans.steps.stepinfo.StepNodeV1;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.plancreator.V1.RunTestsStepPlanCreatorV1;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class RunTestsStepPlanCreatorV1Test extends CategoryTest {
  private TestableRunTestsStepPlanCreatorV1 planCreator;

  @Before
  public void setUp() {
    planCreator = new TestableRunTestsStepPlanCreatorV1();
  }

  private static class TestableRunTestsStepPlanCreatorV1 extends RunTestsStepPlanCreatorV1 {
    public StepType exposedGetStepType() {
      return getStepType();
    }

    public CIStepInfo exposedGetSpec(StepNodeV1 stepElementConfig) {
      return getSpec(stepElementConfig);
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes_shouldReturnRunTestIdentifier() {
    Set<String> supportedStepTypes = planCreator.getSupportedStepTypes();

    assertThat(supportedStepTypes).as("should contain only 'run-test'").containsExactlyInAnyOrder("run-test");
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes_shouldReturnImmutableSingleElementSet() {
    Set<String> supportedStepTypes = planCreator.getSupportedStepTypes();

    assertThat(supportedStepTypes).as("should have exactly one element").hasSize(1);
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepType_shouldReturnTestV2StepType() {
    StepType stepType = planCreator.exposedGetStepType();

    assertThat(stepType.getType())
        .as("should use TESTV2 display name")
        .isEqualTo(CIStepInfoType.TESTV2.getDisplayName());
    assertThat(stepType.getStepCategory()).as("should use STEP category").isEqualTo(StepCategory.STEP);
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSpec_shouldReturnRunTestInfoFromStepNode() {
    StepNodeV1 stepNode = mock(StepNodeV1.class);
    RunTestsStepInfoV1 expectedSpec = RunTestsStepInfoV1.builder().build();
    ParameterField<RunTestsStepInfoV1> runTestField = ParameterField.createValueField(expectedSpec);

    when(stepNode.getRunTest()).thenReturn(runTestField);

    CIStepInfo result = planCreator.exposedGetSpec(stepNode);

    assertThat(result).as("should return the RunTestsStepInfoV1 from the step node").isEqualTo(expectedSpec);
  }
}
