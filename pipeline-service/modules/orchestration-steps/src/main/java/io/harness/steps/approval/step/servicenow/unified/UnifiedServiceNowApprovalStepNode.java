/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.steps.approval.step.servicenow.unified;
import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.unified.UnifiedPmsAbstractStepNode;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.approval.step.beans.unified.UnifiedApprovalStepInfoWrapper;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
@OwnedBy(CI)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@Data
@Builder
public class UnifiedServiceNowApprovalStepNode extends UnifiedPmsAbstractStepNode {
  @JsonProperty(YAMLFieldNameConstants.APPROVAL_V1) UnifiedApprovalStepInfoWrapper unifiedApprovalStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.SERVICENOW_APPROVAL;
  }

  @Override
  public String getFacilitatorType() {
    return StepSpecTypeConstants.APPROVAL_FACILITATOR;
  }
}
