/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.v1;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepSpecTypeConstantsV1;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Value;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Value
@JsonTypeName(StepSpecTypeConstantsV1.HARNESS_APPROVAL)
public class HarnessApprovalStepInfoV1 implements PMSStepInfo {
  ParameterField<String> message;
  ParameterField<String> callback_id;
  Boolean exclude_history;
  Approvers approvers;
  List<ApproverInputInfo> inputs;
  AutoApprovalParams auto_approval;
  ParameterField<Boolean> auto_reject;

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstantsV1.HARNESS_APPROVAL_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return StepSpecTypeConstants.APPROVAL_FACILITATOR;
  }
}
