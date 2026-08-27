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

import java.util.Collections;
import java.util.Set;

public class FmeFlagSetImpressionTrackingStepPlanCreator
    extends PMSStepPlanCreatorV2<FmeFlagSetImpressionTrackingNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.FME_FLAG_SET_IMPRESSION_TRACKING_STEP_TYPE.getType());
  }

  @Override
  public Class<FmeFlagSetImpressionTrackingNode> getFieldClass() {
    return FmeFlagSetImpressionTrackingNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, FmeFlagSetImpressionTrackingNode field) {
    return super.createPlanForField(ctx, field);
  }
}
