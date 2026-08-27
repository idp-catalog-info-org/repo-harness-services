/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.v1;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.steps.approval.step.harness.beans.ApproverInput;
import io.harness.steps.approval.step.harness.beans.EmbeddedUserDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivity;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalBaseActivityDTO;

import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_APPROVALS})
@RecasterAlias("io.harness.steps.approval.step.harness.v1.HarnessApprovalActivityDTO")
public class HarnessApprovalActivityDTO extends HarnessApprovalBaseActivityDTO {
  @NotNull EmbeddedUserDTO user;
  @NotNull HarnessApprovalAction action;
  List<ApproverInput> inputs;
  String comments;
  Date approved_at;

  public static HarnessApprovalActivityDTO fromHarnessApprovalActivity(HarnessApprovalActivity activity) {
    if (activity == null) {
      return null;
    }
    return HarnessApprovalActivityDTO.builder()
        .user(EmbeddedUserDTO.fromEmbeddedUser(activity.getUser()))
        .action(activity.getAction())
        .inputs(activity.getApproverInputs())
        .comments(activity.getComments())
        .approved_at(activity.getApprovedAt() <= 0 ? null : new Date(activity.getApprovedAt()))
        .build();
  }

  @Override
  public List<ApproverInput> getApproverInputs() {
    return inputs;
  }

  @Override
  public Date getApprovedAt() {
    return approved_at;
  }
}
