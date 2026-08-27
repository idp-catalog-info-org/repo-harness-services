/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.telemetry.helpers.InstrumentationConstants.ACCOUNT;
import static io.harness.telemetry.helpers.InstrumentationConstants.ORG;
import static io.harness.telemetry.helpers.InstrumentationConstants.PIPELINE_ID;
import static io.harness.telemetry.helpers.InstrumentationConstants.PROJECT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.entities.ServiceNowApprovalInstance;
import io.harness.steps.approval.step.harness.beans.ApproverInputInfoDTO;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class ApprovalInstrumentationHelper extends InstrumentationHelper {
  public static final String APPROVAL_TYPE = "approval_type";
  public static final String RETRY_INTERNAL = "retry_interval";
  public static final String REJECTION_CRITERIA_SPEC_TYPE = "rejection_criteria_spec_type";
  public static final String APPROVAL_CRITERIA_SPEC_TYPE = "approval_criteria_spec_type";
  public static final String AUTO_APPROVAL = "auto_approval";
  public static final String APPROVAL_STEP = "approval_step";
  public static final String APPROVAL_INPUT_CONSTRAINTS = "approval_input_constraints";

  public void sendApprovalEvent(ApprovalInstance approvalInstance, ScopeInfo scopeInfo) {
    if (approvalInstance != null) {
      HashMap<String, Object> eventPropertiesMap = new HashMap<>();
      eventPropertiesMap.put(ACCOUNT, approvalInstance.getAccountId());
      eventPropertiesMap.put(ORG, scopeInfo.getOrgIdentifier());
      eventPropertiesMap.put(PROJECT, scopeInfo.getProjectIdentifier());
      eventPropertiesMap.put(APPROVAL_TYPE, approvalInstance.getType());
      eventPropertiesMap.put(PIPELINE_ID, approvalInstance.getPipelineIdentifier());
      switch (approvalInstance.getType()) {
        case JIRA_APPROVAL:
          publishJiraApprovalInfo((JiraApprovalInstance) approvalInstance, APPROVAL_STEP, eventPropertiesMap);
          break;
        case CUSTOM_APPROVAL:
          publishCustomApprovalInfo((CustomApprovalInstance) approvalInstance, APPROVAL_STEP, eventPropertiesMap);
          break;
        case HARNESS_APPROVAL:
          publishHarnessApprovalInfo((HarnessApprovalInstance) approvalInstance, APPROVAL_STEP, eventPropertiesMap);
          break;
        case SERVICENOW_APPROVAL:
          publishServiceNowApprovalInfo(
              (ServiceNowApprovalInstance) approvalInstance, APPROVAL_STEP, eventPropertiesMap);
          break;
        default:
          break;
      }
    }
  }

  private void publishCustomApprovalInfo(
      CustomApprovalInstance approvalInstance, String eventName, HashMap<String, Object> eventPropertiesMap) {
    String accountId = approvalInstance.getAccountId();
    if (approvalInstance.getRetryInterval() != null) {
      eventPropertiesMap.put(RETRY_INTERNAL, approvalInstance.getRetryInterval().fetchFinalValue());
    }
    if (approvalInstance.getRejectionCriteria() != null) {
      eventPropertiesMap.put(REJECTION_CRITERIA_SPEC_TYPE, approvalInstance.getRejectionCriteria().getType());
    }
    eventPropertiesMap.put(APPROVAL_CRITERIA_SPEC_TYPE, approvalInstance.getApprovalCriteria().getType());
    sendEvent(eventName, accountId, eventPropertiesMap);
  }

  private void publishServiceNowApprovalInfo(
      ServiceNowApprovalInstance approvalInstance, String eventName, HashMap<String, Object> eventPropertiesMap) {
    String accountId = approvalInstance.getAccountId();
    if (approvalInstance.getRetryInterval() != null) {
      eventPropertiesMap.put(RETRY_INTERNAL, approvalInstance.getRetryInterval().fetchFinalValue());
    }
    if (approvalInstance.getRejectionCriteria() != null) {
      eventPropertiesMap.put(REJECTION_CRITERIA_SPEC_TYPE, approvalInstance.getRejectionCriteria().getType());
    }
    eventPropertiesMap.put(APPROVAL_CRITERIA_SPEC_TYPE, approvalInstance.getApprovalCriteria().getType());
    sendEvent(eventName, accountId, eventPropertiesMap);
  }

  private void publishHarnessApprovalInfo(
      HarnessApprovalInstance approvalInstance, String eventName, HashMap<String, Object> eventPropertiesMap) {
    eventPropertiesMap.put(AUTO_APPROVAL, approvalInstance.getAutoApproval() != null);
    List<ApproverInputInfoDTO> approverInputInfoList = approvalInstance.getApproverInputs();
    boolean isApprovalInputConstraintSet = false;
    if (isNotEmpty(approverInputInfoList)) {
      for (ApproverInputInfoDTO approverInputInfoDTO : approverInputInfoList) {
        if (isNotEmpty(approverInputInfoDTO.getAllowedValues()) || isNotEmpty(approverInputInfoDTO.getSelectOneFrom())
            || isNotEmpty(approverInputInfoDTO.getRegex()) || Boolean.TRUE.equals(approverInputInfoDTO.getRequired())) {
          isApprovalInputConstraintSet = true;
          break;
        }
      }
    }
    eventPropertiesMap.put(APPROVAL_INPUT_CONSTRAINTS, isApprovalInputConstraintSet);
    sendEvent(eventName, approvalInstance.getAccountId(), eventPropertiesMap);
  }

  private void publishJiraApprovalInfo(
      JiraApprovalInstance approvalInstance, String eventName, HashMap<String, Object> eventPropertiesMap) {
    String accountId = approvalInstance.getAccountId();
    if (approvalInstance.getRetryInterval() != null) {
      eventPropertiesMap.put(RETRY_INTERNAL, approvalInstance.getRetryInterval().fetchFinalValue());
    }
    if (approvalInstance.getRejectionCriteria() != null) {
      eventPropertiesMap.put(REJECTION_CRITERIA_SPEC_TYPE, approvalInstance.getRejectionCriteria().getType());
    }
    eventPropertiesMap.put(APPROVAL_CRITERIA_SPEC_TYPE, approvalInstance.getApprovalCriteria().getType());
    sendEvent(eventName, accountId, eventPropertiesMap);
  }
}
