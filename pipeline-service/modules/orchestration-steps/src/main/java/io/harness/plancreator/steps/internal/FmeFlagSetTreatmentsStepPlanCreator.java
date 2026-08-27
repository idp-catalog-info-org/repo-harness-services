/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Set;

public class FmeFlagSetTreatmentsStepPlanCreator extends PMSStepPlanCreatorV2<FmeFlagSetTreatmentsNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.FME_FLAG_SET_TREATMENTS_STEP_TYPE.getType());
  }

  @Override
  public Class<FmeFlagSetTreatmentsNode> getFieldClass() {
    return FmeFlagSetTreatmentsNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, FmeFlagSetTreatmentsNode field) {
    return super.createPlanForField(ctx, field);
  }
}
