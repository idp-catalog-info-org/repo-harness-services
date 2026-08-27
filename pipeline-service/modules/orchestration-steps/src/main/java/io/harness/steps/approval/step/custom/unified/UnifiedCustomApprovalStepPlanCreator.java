/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.custom.unified;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
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

@OwnedBy(CI)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
public class UnifiedCustomApprovalStepPlanCreator
    extends UnifiedPmsAbstractStepPlanCreator<UnifiedCustomApprovalStepNode> {
  private static final StepType STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.CUSTOM_APPROVAL).setStepCategory(StepCategory.STEP).build();

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(YAMLFieldNameConstants.UNIFIED_CUSTOM_APPROVAL);
  }

  @Override
  protected SpecParameters getSpec(UnifiedPmsAbstractStepNode stepNode) {
    return ((UnifiedCustomApprovalStepNode) stepNode).getUnifiedApprovalStepInfo().getSpec().getSpecParameters();
  }

  @Override
  protected StepType getStepType() {
    return STEP_TYPE;
  }

  // Approval criteria reference the step's own `output`, produced only after the script runs. The plan node must keep
  // unresolved expressions as-is (RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED) instead of collapsing them to null and
  // baking the criteria to a constant during step-parameter resolution. This is how expression for approval handled in
  // NG
  @Override
  protected ExpressionMode getStepExpressionMode() {
    return ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED;
  }

  @Override
  public Class<UnifiedCustomApprovalStepNode> getFieldClass() {
    return UnifiedCustomApprovalStepNode.class;
  }

  @Override
  public UnifiedCustomApprovalStepNode getFieldObject(YamlField field) {
    try {
      return YamlUtils.readWithMixIn(field.getNode().toString(), RunStepInfoV1.class, RunStepInfoV1Mixin.class,
          UnifiedCustomApprovalStepNode.class);
    } catch (IOException e) {
      throw new InvalidYamlException("Unable to parse approval step yaml.", e);
    }
  }
}
