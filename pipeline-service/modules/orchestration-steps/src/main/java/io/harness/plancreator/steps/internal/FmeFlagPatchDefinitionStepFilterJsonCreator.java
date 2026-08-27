/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import io.harness.filters.GenericStepPMSFilterJsonCreatorV2;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Set;

public class FmeFlagPatchDefinitionStepFilterJsonCreator extends GenericStepPMSFilterJsonCreatorV2 {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.FME_FLAG_PATCH_DEFINITION_STEP_TYPE.getType());
  }

  public String getFieldName() {
    return "step";
  }
}
