/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.steps.execution.plan;

import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.idp.steps.Constants;
import io.harness.idp.steps.beans.stepnode.IdpCreateProjectStepNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.HarnessYamlVersion;

import com.google.common.collect.Sets;
import java.util.Set;

public class IdpCreateProjectStepPlanCreator extends CIPMSStepPlanCreatorV2<IdpCreateProjectStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(Constants.CREATE_PROJECT);
  }

  @Override
  public Class<IdpCreateProjectStepNode> getFieldClass() {
    return IdpCreateProjectStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, IdpCreateProjectStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V0);
  }
}
