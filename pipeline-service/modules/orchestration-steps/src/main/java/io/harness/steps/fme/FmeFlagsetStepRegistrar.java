/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Centralized registrar for all FME Flagset step types.
 * <p>
 * Adding a new FME Flagset step? Just add an entry to {@link #getEngineSteps()}.
 * This avoids touching the shared {@code OrchestrationStepsModuleStepRegistrar}.
 */
@OwnedBy(HarnessTeam.FME)
@UtilityClass
public class FmeFlagsetStepRegistrar {
  public Map<StepType, Class<? extends Step>> getEngineSteps() {
    Map<StepType, Class<? extends Step>> steps = new HashMap<>();
    steps.put(FmeFlagsetCreateStep.STEP_TYPE, FmeFlagsetCreateStep.class);
    steps.put(FmeFlagsetDeleteStep.STEP_TYPE, FmeFlagsetDeleteStep.class);
    steps.put(FmeFlagAddRemoveFlagsetsStep.STEP_TYPE, FmeFlagAddRemoveFlagsetsStep.class);
    return steps;
  }
}
