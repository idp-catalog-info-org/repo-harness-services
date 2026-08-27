/*
 * Copyright 2026 Harness Inc. All rights reserved.
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

@lombok.Generated
public class FmeSegmentSetTargetingRulesStepPlanCreator extends PMSStepPlanCreatorV2<FmeSegmentSetTargetingRulesNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.FME_SEGMENT_SET_TARGETING_RULES_STEP_TYPE.getType());
  }

  @Override
  public Class<FmeSegmentSetTargetingRulesNode> getFieldClass() {
    return FmeSegmentSetTargetingRulesNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, FmeSegmentSetTargetingRulesNode field) {
    return super.createPlanForField(ctx, field);
  }
}
