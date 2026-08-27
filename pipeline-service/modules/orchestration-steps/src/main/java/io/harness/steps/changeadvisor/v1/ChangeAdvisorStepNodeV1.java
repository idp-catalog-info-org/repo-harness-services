/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.changeadvisor.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.internal.v1.PmsAbstractStepNodeV1;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.steps.approval.step.harness.beans.Approvers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Value;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Value
@JsonTypeName(StepSpecTypeConstantsV1.CHANGE_ADVISOR)
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.steps.changeadvisor.v1.ChangeAdvisorStepNodeV1")
public class ChangeAdvisorStepNodeV1 extends PmsAbstractStepNodeV1 {
  String type = StepSpecTypeConstantsV1.CHANGE_ADVISOR;

  @JsonProperty("change-advisor")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  ChangeAdvisorStepInfoV1 spec;

  @Override
  @JsonIgnore
  public ChangeAdvisorStepInfoV1 getSpec() {
    return spec;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return ChangeAdvisorStepParameters.builder()
        .mode(spec.getMode())
        .policyPack(spec.getPolicyPack())
        .timeoutMinutes(spec.getTimeoutMinutes())
        .presets(spec.getPresets())
        .env(spec.getEnv())
        .approvers(toApprovers(spec.getApprovers()))
        .build();
  }

  private static Approvers toApprovers(io.harness.steps.approval.step.harness.v1.Approvers approvers) {
    if (approvers == null) {
      return null;
    }
    return Approvers.builder()
        .userGroups(approvers.getUser_groups())
        .serviceAccounts(approvers.getService_accounts())
        .minimumCount(approvers.getMin_count())
        .disallowPipelineExecutor(approvers.getDisallow_executor())
        .build();
  }
}
