/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.changeadvisor.v1;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.steps.internal.v1.PmsStepPlanCreator;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.steps.changeadvisor.v1.ChangeAdvisorStepNodeV1;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Set;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
public class ChangeAdvisorStepPlanCreator extends PmsStepPlanCreator<ChangeAdvisorStepNodeV1> {
  @Override
  public ChangeAdvisorStepNodeV1 getFieldObject(YamlField field) {
    try {
      return YamlUtils.read(field.getNode().toString(), ChangeAdvisorStepNodeV1.class);
    } catch (IOException e) {
      throw new InvalidYamlException("Unable to parse change-advisor step yaml. Please ensure format is correct.", e);
    }
  }

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstantsV1.CHANGE_ADVISOR);
  }

  @Override
  public Class<ChangeAdvisorStepNodeV1> getFieldClass() {
    return ChangeAdvisorStepNodeV1.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, ChangeAdvisorStepNodeV1 field) {
    return super.createPlanForField(ctx, field);
  }
}
