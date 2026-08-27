/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.plancreator.steps.common.element.WithStepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.steps.StepUtils;
import io.harness.yaml.core.StepSpecType;

@OwnedBy(PIPELINE)
public interface PMSStepInfo extends StepSpecType, WithStepElementParameters {
  default StepParameters getStepParameters(
      PmsAbstractStepNode stepElementConfig, OnFailRollbackParameters failRollbackParameters, PlanCreationContext ctx) {
    StepElementParametersBuilder stepParametersBuilder =
        StepParametersUtils.getStepParameters(stepElementConfig, failRollbackParameters);
    StepUtils.appendDelegateSelectorsToSpecParameters(stepElementConfig.getStepSpecType(), ctx);
    stepParametersBuilder.spec(getSpecParameters());
    return stepParametersBuilder.build();
  }
}
