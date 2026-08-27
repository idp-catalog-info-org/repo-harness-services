/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.opa;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.opa.OPAEvaluationAggregatorStepNode;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(PIPELINE)
public class OPAEvaluationAggregatorStepPlanCreator extends PMSStepPlanCreatorV2<OPAEvaluationAggregatorStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR);
  }

  @Override
  public Class<OPAEvaluationAggregatorStepNode> getFieldClass() {
    return OPAEvaluationAggregatorStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, OPAEvaluationAggregatorStepNode field) {
    return super.createPlanForField(ctx, field);
  }
}
