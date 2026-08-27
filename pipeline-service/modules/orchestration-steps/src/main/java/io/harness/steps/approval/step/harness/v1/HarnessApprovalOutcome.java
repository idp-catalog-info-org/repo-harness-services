/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.v1;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.steps.approval.step.harness.HarnessApprovalBaseOutcome;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalBaseActivityDTO;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@RecasterAlias("io.harness.steps.approval.step.harness.v1.HarnessApprovalOutcome")
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_APPROVALS})
public class HarnessApprovalOutcome extends HarnessApprovalBaseOutcome {
  List<HarnessApprovalActivityDTO> activities;
  Map<String, String> inputs;

  @Override
  public List<HarnessApprovalBaseActivityDTO> getApprovalActivities() {
    return activities.stream().map(HarnessApprovalBaseActivityDTO.class ::cast).collect(Collectors.toList());
  }

  @Override
  public Map<String, String> getApproverInputs() {
    return inputs;
  }
}
