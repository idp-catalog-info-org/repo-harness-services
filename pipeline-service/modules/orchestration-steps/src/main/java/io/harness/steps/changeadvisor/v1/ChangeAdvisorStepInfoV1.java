/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.changeadvisor.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.steps.approval.step.harness.v1.Approvers;
import io.harness.walktree.visitor.helper.Visitable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Value;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Value
@JsonTypeName(StepSpecTypeConstantsV1.CHANGE_ADVISOR)
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.steps.changeadvisor.v1.ChangeAdvisorStepInfoV1")
public class ChangeAdvisorStepInfoV1 implements PMSStepInfo, Visitable {
  ParameterField<String> mode;
  @JsonProperty("policy-pack") ParameterField<String> policyPack;
  @JsonProperty("timeout-minutes") ParameterField<Integer> timeoutMinutes;
  ParameterField<List<String>> presets;
  ParameterField<String> env;
  Approvers approvers;

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return StepSpecTypeConstantsV1.CHANGE_ADVISOR_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return StepSpecTypeConstants.APPROVAL_FACILITATOR;
  }
}
