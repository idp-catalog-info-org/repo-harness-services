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
import io.harness.beans.steps.stepinfo.CIStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.beans.steps.stepinfo.StepNodeV1;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.plancreator.V1.BackgroundStepPlanCreatorV1;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class BackgroundStepPlanCreatorV1Test extends CategoryTest {
  private TestableBackgroundStepPlanCreatorV1 backgroundStepPlanCreatorV1;

  private static class TestableBackgroundStepPlanCreatorV1 extends BackgroundStepPlanCreatorV1 {
    public StepType exposedGetStepType() {
      return getStepType();
    }

    public CIStepInfo exposedGetSpec(StepNodeV1 stepElementConfig) {
      return getSpec(stepElementConfig);
    }
  }

  @Before
  public void setUp() {
    backgroundStepPlanCreatorV1 = new TestableBackgroundStepPlanCreatorV1();
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes_shouldReturnBackgroundV1DisplayName() {
    Set<String> supportedStepTypes = backgroundStepPlanCreatorV1.getSupportedStepTypes();

    assertThat(supportedStepTypes)
        .as("should contain BACKGROUND_V1 display name")
        .containsExactlyInAnyOrder(CIStepInfoType.BACKGROUND_V1.getDisplayName());
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepType_shouldReturnBackgroundStepType() {
    StepType stepType = backgroundStepPlanCreatorV1.exposedGetStepType();

    StepType expected = StepType.newBuilder()
                            .setType(CIStepInfoType.BACKGROUND.getDisplayName())
                            .setStepCategory(StepCategory.STEP)
                            .build();
    assertThat(stepType).as("should return BACKGROUND step type with STEP category").isEqualTo(expected);
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSpec_shouldReturnBackgroundValueFromStepNode() {
    RunStepInfoV1 expectedBackground = RunStepInfoV1.builder().build();
    StepNodeV1 stepNodeV1 =
        StepNodeV1.builder().background(ParameterField.createValueField(expectedBackground)).build();

    CIStepInfo result = backgroundStepPlanCreatorV1.exposedGetSpec(stepNodeV1);

    assertThat(result).as("should return the background RunStepInfoV1 value").isEqualTo(expectedBackground);
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSpec_whenBothRunAndBackgroundSet_shouldReturnBackgroundNotRun() {
    RunStepInfoV1 expectedBackground =
        RunStepInfoV1.builder().script(ParameterField.createValueField("bg-script")).build();
    RunStepInfoV1 wrongRun = RunStepInfoV1.builder().script(ParameterField.createValueField("run-script")).build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder()
                                .background(ParameterField.createValueField(expectedBackground))
                                .run(ParameterField.createValueField(wrongRun))
                                .build();

    CIStepInfo result = backgroundStepPlanCreatorV1.exposedGetSpec(stepNodeV1);

    assertThat(result).as("should return background, not run").isSameAs(expectedBackground);
  }
}
