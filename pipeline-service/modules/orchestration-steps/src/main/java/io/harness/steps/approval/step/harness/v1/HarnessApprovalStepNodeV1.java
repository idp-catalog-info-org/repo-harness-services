/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.v1;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.internal.v1.PmsAbstractStepNodeV1;
import io.harness.steps.StepSpecTypeConstantsV1;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Value;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Value
@JsonTypeName(StepSpecTypeConstantsV1.HARNESS_APPROVAL)
public class HarnessApprovalStepNodeV1 extends PmsAbstractStepNodeV1 {
  String type = StepSpecTypeConstantsV1.HARNESS_APPROVAL;

  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  HarnessApprovalStepInfoV1 spec;

  @Override
  public SpecParameters getSpecParameters() {
    return HarnessApprovalStepParameters.infoBuilder()
        .approvers(spec.getApprovers())
        .message(spec.getMessage())
        .auto_approval(spec.getAuto_approval())
        .reject_previous(spec.getAuto_reject())
        .callback_id(spec.getCallback_id())
        .exclude_history(spec.getExclude_history())
        .inputs(spec.getInputs())
        .build();
  }
}
