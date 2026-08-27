/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator.V1;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.CIStepInfo;
import io.harness.beans.steps.stepinfo.StepNodeV1;
import io.harness.ci.plan.creator.step.v1.AbstractStepPlanCreatorV1;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;

import com.google.common.collect.Sets;
import java.util.Set;

public class RunTestsStepPlanCreatorV1 extends AbstractStepPlanCreatorV1 {
  private static final StepType STEP_TYPE =
      StepType.newBuilder().setType(CIStepInfoType.TESTV2.getDisplayName()).setStepCategory(StepCategory.STEP).build();

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet("run-test");
  }

  @Override
  protected StepType getStepType() {
    return STEP_TYPE;
  }

  @Override
  protected CIStepInfo getSpec(StepNodeV1 stepElementConfig) {
    return stepElementConfig.getRunTest().getValue();
  }
}
