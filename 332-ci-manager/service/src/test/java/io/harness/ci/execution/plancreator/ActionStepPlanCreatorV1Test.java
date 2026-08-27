/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.ActionStepInfoV1;
import io.harness.beans.steps.stepinfo.CIStepInfo;
import io.harness.beans.steps.stepinfo.StepNodeV1;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.plancreator.V1.ActionStepPlanCreatorV1;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ActionStepPlanCreatorV1Test extends CategoryTest {
  private TestableActionStepPlanCreatorV1 actionStepPlanCreatorV1;

  private static class TestableActionStepPlanCreatorV1 extends ActionStepPlanCreatorV1 {
    public StepType exposedGetStepType() {
      return getStepType();
    }

    public CIStepInfo exposedGetSpec(StepNodeV1 stepElementConfig) {
      return getSpec(stepElementConfig);
    }
  }

  @Before
  public void setUp() {
    actionStepPlanCreatorV1 = new TestableActionStepPlanCreatorV1();
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes_shouldReturnActionV1DisplayName() {
    Set<String> supportedStepTypes = actionStepPlanCreatorV1.getSupportedStepTypes();

    assertThat(supportedStepTypes)
        .as("should contain ACTION_V1 display name")
        .containsExactlyInAnyOrder(CIStepInfoType.ACTION_V1.getDisplayName());
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepType_shouldReturnActionStepInfoV1StepType() {
    StepType stepType = actionStepPlanCreatorV1.exposedGetStepType();

    assertThat(stepType).as("should return ActionStepInfoV1.STEP_TYPE").isEqualTo(ActionStepInfoV1.STEP_TYPE);
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSpec_shouldReturnActionFromStepNode() {
    ActionStepInfoV1 expectedAction = ActionStepInfoV1.builder().build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().action(expectedAction).build();

    CIStepInfo result = actionStepPlanCreatorV1.exposedGetSpec(stepNodeV1);

    assertThat(result).as("should return the action from StepNodeV1").isEqualTo(expectedAction);
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSpec_whenActionIsNull_shouldReturnNull() {
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().build();

    CIStepInfo result = actionStepPlanCreatorV1.exposedGetSpec(stepNodeV1);

    assertThat(result).as("should return null when no action is set").isNull();
  }
}
