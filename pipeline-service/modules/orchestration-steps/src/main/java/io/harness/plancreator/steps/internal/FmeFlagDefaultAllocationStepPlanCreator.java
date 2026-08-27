/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Set;

public class FmeFlagDefaultAllocationStepPlanCreator extends PMSStepPlanCreatorV2<FmeFlagDefaultAllocationStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.FME_FLAG_DEFAULT_ALLOCATION_STEP_TYPE.getType());
  }

  @Override
  public Class<FmeFlagDefaultAllocationStepNode> getFieldClass() {
    return FmeFlagDefaultAllocationStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, FmeFlagDefaultAllocationStepNode field) {
    return super.createPlanForField(ctx, field);
  }
}
