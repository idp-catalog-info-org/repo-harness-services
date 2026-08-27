/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.ROHITPAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeFlagsetStepRegistrarTest extends CategoryTest {
  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testGetEngineStepsReturnsThreeEntries() {
    Map<StepType, Class<? extends Step>> steps = FmeFlagsetStepRegistrar.getEngineSteps();
    assertThat(steps).hasSize(3);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testGetEngineStepsContainsCreateStep() {
    Map<StepType, Class<? extends Step>> steps = FmeFlagsetStepRegistrar.getEngineSteps();
    assertThat(steps).containsKey(FmeFlagsetCreateStep.STEP_TYPE);
    assertThat(steps.get(FmeFlagsetCreateStep.STEP_TYPE)).isEqualTo(FmeFlagsetCreateStep.class);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testGetEngineStepsContainsDeleteStep() {
    Map<StepType, Class<? extends Step>> steps = FmeFlagsetStepRegistrar.getEngineSteps();
    assertThat(steps).containsKey(FmeFlagsetDeleteStep.STEP_TYPE);
    assertThat(steps.get(FmeFlagsetDeleteStep.STEP_TYPE)).isEqualTo(FmeFlagsetDeleteStep.class);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testGetEngineStepsContainsAddRemoveFlagsStep() {
    Map<StepType, Class<? extends Step>> steps = FmeFlagsetStepRegistrar.getEngineSteps();
    assertThat(steps).containsKey(FmeFlagAddRemoveFlagsetsStep.STEP_TYPE);
    assertThat(steps.get(FmeFlagAddRemoveFlagsetsStep.STEP_TYPE)).isEqualTo(FmeFlagAddRemoveFlagsetsStep.class);
  }
}
