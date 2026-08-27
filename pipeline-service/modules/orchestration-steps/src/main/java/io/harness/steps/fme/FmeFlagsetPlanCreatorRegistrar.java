/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.internal.FmeFlagAddRemoveFlagsetsStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagAddRemoveFlagsetsStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagsetCreateStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagsetCreateStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagsetDeleteStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagsetDeleteStepPlanCreator;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.plan.creation.creators.children.PartialPlanCreator;
import io.harness.steps.StepSpecTypeConstants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * Centralized registrar for all FME Flagset plan creators and filter creators.
 * <p>
 * Adding a new FME Flagset step? Add its PlanCreator and FilterJsonCreator here.
 * This avoids touching the shared {@code PipelineServiceInternalInfoProvider}.
 */
@OwnedBy(HarnessTeam.FME)
@UtilityClass
public class FmeFlagsetPlanCreatorRegistrar {
  public List<PartialPlanCreator<?>> getPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = new ArrayList<>();
    planCreators.add(new FmeFlagsetCreateStepPlanCreator());
    planCreators.add(new FmeFlagsetDeleteStepPlanCreator());
    planCreators.add(new FmeFlagAddRemoveFlagsetsStepPlanCreator());
    return planCreators;
  }

  public List<FilterJsonCreator> getFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    filterJsonCreators.add(new FmeFlagsetCreateStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagsetDeleteStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagAddRemoveFlagsetsStepFilterJsonCreator());
    return filterJsonCreators;
  }

  /**
   * Returns the set of step type strings for all Flagset steps.
   * Used by EmptyVariableCreator so these steps don't need individual VariableCreators.
   */
  public Set<String> getStepTypeStrings() {
    Set<String> types = new HashSet<>();
    types.add(StepSpecTypeConstants.FME_FLAGSET_CREATE_STEP_TYPE.getType());
    types.add(StepSpecTypeConstants.FME_FLAGSET_DELETE_STEP_TYPE.getType());
    types.add(StepSpecTypeConstants.FME_FLAG_ADD_REMOVE_FLAGSETS_STEP_TYPE.getType());
    return types;
  }
}
