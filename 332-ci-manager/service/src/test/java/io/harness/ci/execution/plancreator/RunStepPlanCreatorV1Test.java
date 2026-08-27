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
import io.harness.ci.execution.plancreator.V1.RunStepPlanCreatorV1;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class RunStepPlanCreatorV1Test extends CategoryTest {
  private TestableRunStepPlanCreatorV1 runStepPlanCreatorV1;

  private static class TestableRunStepPlanCreatorV1 extends RunStepPlanCreatorV1 {
    public StepType exposedGetStepType() {
      return getStepType();
    }

    public CIStepInfo exposedGetSpec(StepNodeV1 stepElementConfig) {
      return getSpec(stepElementConfig);
    }
  }

  @Before
  public void setUp() {
    runStepPlanCreatorV1 = new TestableRunStepPlanCreatorV1();
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes_shouldReturnRunDisplayName() {
    Set<String> supportedStepTypes = runStepPlanCreatorV1.getSupportedStepTypes();

    assertThat(supportedStepTypes).as("should contain 'run'").containsExactlyInAnyOrder("run");
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepType_shouldReturnRunStepType() {
    StepType stepType = runStepPlanCreatorV1.exposedGetStepType();

    assertThat(stepType).as("should return RUN step type").isNotNull();
    assertThat(stepType.getType()).as("should have RUN display name").isEqualTo(CIStepInfoType.RUN.getDisplayName());
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSpec_shouldReturnRunStepInfoFromStepNode() {
    RunStepInfoV1 expectedRunInfo = RunStepInfoV1.builder().build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().run(ParameterField.createValueField(expectedRunInfo)).build();

    CIStepInfo result = runStepPlanCreatorV1.exposedGetSpec(stepNodeV1);

    assertThat(result).as("should return the RunStepInfoV1 from StepNodeV1").isEqualTo(expectedRunInfo);
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSpec_whenRunIsNull_shouldReturnNull() {
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().run(ParameterField.createValueField(null)).build();

    CIStepInfo result = runStepPlanCreatorV1.exposedGetSpec(stepNodeV1);

    assertThat(result).as("should return null when run value is null").isNull();
  }
}
