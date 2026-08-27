/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.v1;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.approval.step.harness.HarnessApprovalSpecParameters;
import io.harness.steps.approval.step.harness.beans.AutoApprovalAction;
import io.harness.steps.approval.step.harness.beans.ScheduledDeadline;

import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder(builderMethodName = "infoBuilder")
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@RecasterAlias("io.harness.steps.approval.step.harness.v1.HarnessApprovalStepParameters")
public class HarnessApprovalStepParameters implements SpecParameters {
  ParameterField<String> message;
  ParameterField<String> callback_id;
  Boolean exclude_history;
  AutoApprovalParams auto_approval;
  Approvers approvers;
  List<ApproverInputInfo> inputs;
  ParameterField<Boolean> reject_previous;

  @Override
  public String getVersion() {
    return HarnessYamlVersion.V1;
  }

  public HarnessApprovalSpecParameters toHarnessApprovalStepParameterV0() {
    return HarnessApprovalSpecParameters.builder()
        .approvalMessage(getMessage())
        .callbackId(getCallback_id())
        .excludePipelineExecutionHistory(getExclude_history())
        .autoApproval(toAutoApproval(getAuto_approval()))
        .approvers(toApprovers(getApprovers()))
        .approverInputs(toInputsList(getInputs()))
        .isAutoRejectEnabled(getReject_previous())
        .build();
  }

  private List<io.harness.steps.approval.step.harness.beans.ApproverInputInfo> toInputsList(
      List<ApproverInputInfo> inputs) {
    if (inputs == null) {
      return null;
    }
    return inputs.stream().map(this::toInputs).collect(Collectors.toList());
  }

  private io.harness.steps.approval.step.harness.beans.ApproverInputInfo toInputs(ApproverInputInfo approverInputInfo) {
    if (approverInputInfo == null) {
      return null;
    }
    return io.harness.steps.approval.step.harness.beans.ApproverInputInfo.builder()
        .name(approverInputInfo.getName())
        .defaultValue(approverInputInfo.getDefaultValue())
        .build();
  }

  private io.harness.steps.approval.step.harness.beans.Approvers toApprovers(Approvers approvers) {
    if (approvers == null) {
      return null;
    }
    return io.harness.steps.approval.step.harness.beans.Approvers.builder()
        .userGroups(approvers.getUser_groups())
        .serviceAccounts(approvers.getService_accounts())
        .minimumCount(approvers.getMin_count())
        .disallowPipelineExecutor(approvers.getDisallow_executor())
        .build();
  }

  private io.harness.steps.approval.step.harness.beans.AutoApprovalParams toAutoApproval(
      AutoApprovalParams autoApproval) {
    if (autoApproval == null) {
      return null;
    }
    return io.harness.steps.approval.step.harness.beans.AutoApprovalParams.builder()
        .scheduledDeadline(toScheduledDeadline(autoApproval.getDeadline()))
        .comments(autoApproval.getComments())
        .action(toAction(autoApproval.getAction()))
        .build();
  }

  private AutoApprovalAction toAction(io.harness.steps.approval.step.harness.v1.AutoApprovalAction action) {
    if (action.equals(io.harness.steps.approval.step.harness.v1.AutoApprovalAction.approve)) {
      return AutoApprovalAction.APPROVE;
    }
    return null;
  }

  private ScheduledDeadline toScheduledDeadline(io.harness.steps.approval.step.harness.v1.ScheduledDeadline deadline) {
    if (deadline == null) {
      return null;
    }
    return ScheduledDeadline.builder().timeZone(deadline.getZone()).time(deadline.getTime()).build();
  }
}
