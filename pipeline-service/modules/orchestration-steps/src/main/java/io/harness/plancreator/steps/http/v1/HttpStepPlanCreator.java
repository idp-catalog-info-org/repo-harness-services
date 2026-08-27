/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.http.v1;

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
import io.harness.steps.http.v1.HttpStepNodeV1;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Set;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
public class HttpStepPlanCreator extends PmsStepPlanCreator<HttpStepNodeV1> {
  @Override
  public HttpStepNodeV1 getFieldObject(YamlField field) {
    try {
      return YamlUtils.read(field.getNode().toString(), HttpStepNodeV1.class);
    } catch (IOException e) {
      throw new InvalidYamlException("Unable to parse http step yaml. Please ensure that it is in correct format", e);
    }
  }

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstantsV1.HTTP);
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, HttpStepNodeV1 field) {
    return super.createPlanForField(ctx, field);
  }
}
