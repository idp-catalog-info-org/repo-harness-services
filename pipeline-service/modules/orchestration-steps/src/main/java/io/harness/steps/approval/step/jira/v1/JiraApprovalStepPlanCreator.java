/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.jira.v1;

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

import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
public class JiraApprovalStepPlanCreator extends PmsStepPlanCreator<JiraApprovalStepNodeV1> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstantsV1.JIRA_APPROVAL);
  }

  @Override
  public JiraApprovalStepNodeV1 getFieldObject(YamlField field) {
    try {
      return YamlUtils.read(field.getNode().toString(), JiraApprovalStepNodeV1.class);
    } catch (IOException e) {
      throw new InvalidYamlException(
          "Unable to parse jira approval step yaml. Please ensure that it is in correct format", e);
    }
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, JiraApprovalStepNodeV1 field) {
    return super.createPlanForField(ctx, field);
  }
}
