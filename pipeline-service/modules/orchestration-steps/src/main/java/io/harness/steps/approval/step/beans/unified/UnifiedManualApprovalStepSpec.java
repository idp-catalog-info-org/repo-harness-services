/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans.unified;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.approval.step.harness.HarnessApprovalSpecParameters;
import io.harness.steps.approval.step.harness.beans.ApproverInputInfo;
import io.harness.steps.approval.step.harness.beans.Approvers;
import io.harness.steps.approval.step.harness.beans.AutoApprovalAction;
import io.harness.steps.approval.step.harness.beans.AutoApprovalParams;
import io.harness.steps.approval.step.harness.beans.ScheduledDeadline;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CI)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@Value
@Builder
@JsonTypeName(YAMLFieldNameConstants.UNIFIED_MANUAL_APPROVAL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnifiedManualApprovalStepSpec extends UnifiedAbstractApprovalStepSpec {
  ParameterField<String> message;
  @JsonProperty(YAMLFieldNameConstants.CALLBACK_ID) ParameterField<String> callbackId;
  @JsonProperty(YAMLFieldNameConstants.USER_GROUP)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  ParameterField<List<String>> userGroups;
  @JsonProperty(YAMLFieldNameConstants.SERVICE_ACCOUNTS)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  ParameterField<List<String>> serviceAccounts;
  @JsonProperty(YAMLFieldNameConstants.APPROVERS_MIN_COUNT) ParameterField<Integer> approverMinCount;
  @JsonProperty(YAMLFieldNameConstants.BLOCK_EXECUTOR) ParameterField<Boolean> blockExecutor;
  @JsonProperty(YAMLFieldNameConstants.DISALLOWED_USER_EMAILS)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  ParameterField<List<String>> disallowedUserEmails;
  @JsonProperty(YAMLFieldNameConstants.EXECUTION_DETAILS) ParameterField<Boolean> executionDetails;

  @JsonProperty(YAMLFieldNameConstants.AUTO_REJECT) ParameterField<Boolean> autoReject;

  @JsonProperty(YAMLFieldNameConstants.AUTO_APPROVE) Boolean autoApprove;
  ParameterField<String> timezone;
  ParameterField<String> deadline;
  ParameterField<String> comments;
  UnifiedManualApprovalApproverInfoWrapper inputs;

  @Override
  public SpecParameters getSpecParameters() {
    return HarnessApprovalSpecParameters.builder()
        .callbackId(callbackId)
        .autoApproval(getAutoApprovalParam())
        .approvalMessage(message)
        .includePipelineExecutionHistory(executionDetails)
        .approvers(getApprovers())
        .approverInputs(getApproverInputInfo())
        .isAutoRejectEnabled(autoReject)
        .build();
  }

  private Approvers getApprovers() {
    return Approvers.builder()
        .userGroups(userGroups)
        .serviceAccounts(serviceAccounts)
        .disallowPipelineExecutor(blockExecutor)
        .disallowedUserEmails(disallowedUserEmails)
        .minimumCount(approverMinCount)
        .build();
  }

  private List<ApproverInputInfo> getApproverInputInfo() {
    List<ApproverInputInfo> approverInputInfos = new ArrayList<>();
    if (inputs == null || isEmpty(inputs.getMap())) {
      return approverInputInfos;
    }

    for (Map.Entry<String, UnifiedManualApprovalApproverInfo> entry : inputs.getMap().entrySet()) {
      var approverInputInfo = ApproverInputInfo.builder();
      approverInputInfo.name(entry.getKey());
      approverInputInfo.description(entry.getValue().getDescription());
      approverInputInfo.defaultValue(entry.getValue().getDefaultValue());
      boolean multiSelect = entry.getValue().isMultiSelect();
      approverInputInfo.regex(entry.getValue().getRegex());
      if (!multiSelect) {
        approverInputInfo.selectOneFrom(YamlUtils.sliceListToString(entry.getValue().getEnumList()));
      } else {
        approverInputInfo.allowedValues(YamlUtils.sliceListToString(entry.getValue().getEnumList()));
      }
      approverInputInfo.required(entry.getValue().isRequired());
      approverInputInfos.add(approverInputInfo.build());
    }
    return approverInputInfos;
  }

  private AutoApprovalParams getAutoApprovalParam() {
    if (Boolean.TRUE.equals(autoApprove)) {
      ScheduledDeadline scheduledDeadline = ScheduledDeadline.builder().time(deadline).timeZone(timezone).build();
      return AutoApprovalParams.builder()
          .action(AutoApprovalAction.APPROVE)
          .scheduledDeadline(scheduledDeadline)
          .comments(comments)
          .build();
    }
    return null;
  }
}
