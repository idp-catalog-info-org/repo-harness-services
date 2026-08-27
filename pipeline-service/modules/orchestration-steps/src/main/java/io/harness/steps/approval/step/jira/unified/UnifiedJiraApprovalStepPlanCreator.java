/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.jira.unified;

import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.beans.steps.stepinfo.RunStepInfoV1Mixin;
import io.harness.ci.plan.creator.step.unified.UnifiedPmsAbstractStepPlanCreator;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.unified.UnifiedPmsAbstractStepNode;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Set;

public class UnifiedJiraApprovalStepPlanCreator extends UnifiedPmsAbstractStepPlanCreator<UnifiedJiraApprovalStepNode> {
  private static final StepType STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.JIRA_APPROVAL).setStepCategory(StepCategory.STEP).build();

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(YAMLFieldNameConstants.UNIFIED_JIRA_APPROVAL);
  }

  @Override
  protected SpecParameters getSpec(UnifiedPmsAbstractStepNode stepNode) {
    return ((UnifiedJiraApprovalStepNode) stepNode).getUnifiedApprovalStepInfo().getSpec().getSpecParameters();
  }

  @Override
  protected StepType getStepType() {
    return STEP_TYPE;
  }

  // Approval criteria reference the fetched Jira ticket fields, which are available only after the ticket is polled.
  // Preserve unresolved expressions so they survive to the callback evaluation instead of collapsing to a constant or
  // null during step-parameter resolution. This is how expression for approval handled in NG
  @Override
  protected ExpressionMode getStepExpressionMode() {
    return ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED;
  }

  @Override
  public Class<UnifiedJiraApprovalStepNode> getFieldClass() {
    return UnifiedJiraApprovalStepNode.class;
  }

  @Override
  public UnifiedJiraApprovalStepNode getFieldObject(YamlField field) {
    try {
      return YamlUtils.readWithMixIn(
          field.getNode().toString(), RunStepInfoV1.class, RunStepInfoV1Mixin.class, UnifiedJiraApprovalStepNode.class);
    } catch (IOException e) {
      throw new InvalidYamlException("Unable to parse Jira approval step yaml", e);
    }
  }
}
