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
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.plan.creation.creators.children.PartialPlanCreator;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeFlagsetPlanCreatorRegistrarTest extends CategoryTest {
  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testGetPlanCreatorsReturnsThreeEntries() {
    List<PartialPlanCreator<?>> planCreators = FmeFlagsetPlanCreatorRegistrar.getPlanCreators();
    assertThat(planCreators).hasSize(3);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testGetFilterJsonCreatorsReturnsThreeEntries() {
    List<FilterJsonCreator> filterJsonCreators = FmeFlagsetPlanCreatorRegistrar.getFilterJsonCreators();
    assertThat(filterJsonCreators).hasSize(3);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testGetStepTypeStringsReturnsThreeEntries() {
    Set<String> stepTypes = FmeFlagsetPlanCreatorRegistrar.getStepTypeStrings();
    assertThat(stepTypes).hasSize(3);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testGetStepTypeStringsContainsExpectedTypes() {
    Set<String> stepTypes = FmeFlagsetPlanCreatorRegistrar.getStepTypeStrings();
    assertThat(stepTypes).contains(StepSpecTypeConstants.FME_FLAGSET_CREATE_STEP_TYPE.getType());
    assertThat(stepTypes).contains(StepSpecTypeConstants.FME_FLAGSET_DELETE_STEP_TYPE.getType());
    assertThat(stepTypes).contains(StepSpecTypeConstants.FME_FLAG_ADD_REMOVE_FLAGSETS_STEP_TYPE.getType());
  }
}
