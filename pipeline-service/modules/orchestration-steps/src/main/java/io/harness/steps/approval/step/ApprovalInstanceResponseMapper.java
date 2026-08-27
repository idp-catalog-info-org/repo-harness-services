/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.execution.states.helpers.CDStepsEnvironmentVarsHelper;
import io.harness.delegate.beans.connector.JiraConnectorDTO;
import io.harness.delegate.beans.connector.ServiceNowConnectorDTO;
import io.harness.expression.ConnectorVariableConstants;
import io.harness.jira.JiraIssueKeyNG;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.servicenow.ServiceNowTicketKeyNG;
import io.harness.steps.approval.step.beans.ApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.beans.HarnessApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.beans.JiraApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.beans.ServiceNowApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.custom.beans.CustomApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalUtils;
import io.harness.steps.approval.step.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.entities.JiraApprovalHelperService;
import io.harness.steps.approval.step.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.entities.ServiceNowApprovalInstance;
import io.harness.steps.approval.step.harness.beans.ApproverInput;
import io.harness.steps.approval.step.harness.beans.ApproverInputInfoDTO;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalHelperService;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

@OwnedBy(CDC)
@Singleton
public class ApprovalInstanceResponseMapper {
  private final JiraApprovalHelperService jiraApprovalHelperService;
  private final ServiceNowApprovalHelperService serviceNowApprovalHelperService;

  @Inject
  public ApprovalInstanceResponseMapper(JiraApprovalHelperService jiraApprovalHelperService,
      ServiceNowApprovalHelperService serviceNowApprovalHelperService) {
    this.jiraApprovalHelperService = jiraApprovalHelperService;
    this.serviceNowApprovalHelperService = serviceNowApprovalHelperService;
  }

  public ApprovalInstanceResponseDTO toApprovalInstanceResponseDTO(
      ApprovalInstance instance, boolean shouldSendLastApprovalActivity) {
    if (instance == null) {
      return null;
    }

    return ApprovalInstanceResponseDTO.builder()
        .id(instance.getId())
        .type(instance.getType())
        .status(instance.getStatus())
        .deadline(instance.getDeadline())
        .details(toApprovalInstanceDetailsDTO(instance, false, shouldSendLastApprovalActivity))
        .createdAt(instance.getCreatedAt())
        .lastModifiedAt(instance.getLastModifiedAt())
        .errorMessage(instance.getErrorMessage())
        .build();
  }

  /**
   * shouldAddDelegateMetadata adds information related to delegate task such as latest delegate task id and task name
   * to the DTO
   *
   * preferably shouldAddDelegateMetadata should only be set true when mapping for internal APIs where delegate
   * information is required such as getApprovalInstance
   *
   *
   * *
   */
  public ApprovalInstanceResponseDTO toApprovalInstanceResponseDTO(
      ApprovalInstance instance, boolean shouldAddDelegateMetadata, boolean shouldSendLastApprovalActivity) {
    if (instance == null) {
      return null;
    }

    return ApprovalInstanceResponseDTO.builder()
        .id(instance.getId())
        .type(instance.getType())
        .status(instance.getStatus())
        .deadline(instance.getDeadline())
        .details(toApprovalInstanceDetailsDTO(instance, shouldAddDelegateMetadata, shouldSendLastApprovalActivity))
        .createdAt(instance.getCreatedAt())
        .lastModifiedAt(instance.getLastModifiedAt())
        .errorMessage(instance.getErrorMessage())
        .build();
  }

  private ApprovalInstanceDetailsDTO toApprovalInstanceDetailsDTO(
      ApprovalInstance instance, boolean shouldAddDelegateMetadata, boolean shouldSendLastApprovalActivity) {
    switch (instance.getType()) {
      case HARNESS_APPROVAL:
        return toHarnessApprovalInstanceDetailsDTO((HarnessApprovalInstance) instance, shouldSendLastApprovalActivity);
      case JIRA_APPROVAL:
        return toJiraApprovalInstanceDetailsDTO((JiraApprovalInstance) instance, shouldAddDelegateMetadata);
      case SERVICENOW_APPROVAL:
        return toServiceNowApprovalInstanceDetailsDTO((ServiceNowApprovalInstance) instance, shouldAddDelegateMetadata);
      case CUSTOM_APPROVAL:
        return toCustomApprovalInstanceDetailsDTO((CustomApprovalInstance) instance, shouldAddDelegateMetadata);
      default:
        return null;
    }
  }

  private ApprovalInstanceDetailsDTO toHarnessApprovalInstanceDetailsDTO(
      HarnessApprovalInstance instance, boolean shouldSendLastApprovalActivity) {
    return HarnessApprovalInstanceDetailsDTO.builder()
        .approvalMessage(instance.getApprovalMessage())
        .includePipelineExecutionHistory(instance.isIncludePipelineExecutionHistory())
        .approvers(instance.getApprovers())
        .approvalActivities(instance.getApprovalActivities())
        .autoApprovalParams(instance.getAutoApproval())
        .approverInputs(instance.fetchLastApprovalActivity()
                            .map(approvalActivity
                                -> approvalActivity.getApproverInputs() == null
                                    ? new ArrayList<ApproverInputInfoDTO>()
                                    : resolveApproverInputInfo(approvalActivity.getApproverInputs(), instance,
                                          shouldSendLastApprovalActivity))
                            .orElse(instance.getApproverInputs()))
        .validatedApprovalUserGroups(instance.getValidatedApprovalUserGroups())
        .validatedApprovalServiceAccounts(instance.getValidatedApprovalServiceAccounts())
        .isAutoRejectEnabled(
            instance.getIsAutoRejectEnabled() == null ? Boolean.FALSE : instance.getIsAutoRejectEnabled())
        .build();
  }

  // Previously, we were sending the response in last approval activity in approver inputs if present
  // This gave incorrect value in case of multi approvals for default value, input constraints were also absent
  // Changed code to return approver input constraints and default value in case of get
  // For post, we continue to send the approver inputs in last approval activity along with approver input constraints
  private List<ApproverInputInfoDTO> resolveApproverInputInfo(
      List<ApproverInput> approverInputs, HarnessApprovalInstance instance, boolean shouldSendLastApprovalActivity) {
    if (shouldSendLastApprovalActivity) {
      return approverInputs.stream()
          .map(approverInput -> {
            if (isNotEmpty(instance.getApproverInputs())) {
              return instance.getApproverInputs()
                  .stream()
                  .filter(approverInputInfoDTO -> approverInputInfoDTO.getName().equals(approverInput.getName()))
                  .findFirst()
                  .map(approverInputInfoDTO
                      -> ApproverInputInfoDTO.builder()
                             .name(approverInput.getName())
                             .defaultValue(approverInput.getValue())
                             .allowedValues(approverInputInfoDTO.getAllowedValues())
                             .selectOneFrom(approverInputInfoDTO.getSelectOneFrom())
                             .regex(approverInputInfoDTO.getRegex())
                             .required(approverInputInfoDTO.getRequired())
                             .description(approverInputInfoDTO.getDescription())
                             .build())
                  .orElse(approverInput.toApproverInputInfoDTO());
            }
            return approverInput.toApproverInputInfoDTO();
          })
          .collect(Collectors.toList());
    }
    return instance.getApproverInputs();
  }

  private ApprovalInstanceDetailsDTO toJiraApprovalInstanceDetailsDTO(
      JiraApprovalInstance instance, boolean shouldAddDelegateMetadata) {
    String jiraUrl;
    if (HarnessYamlVersion.isV1(AmbianceUtils.getPipelineVersion(instance.getAmbiance()))) {
      jiraUrl = CDStepsEnvironmentVarsHelper.getEnvVar(
          instance.getRunStepInfoV1Outcome(), ConnectorVariableConstants.PLUGIN_JIRA_URL);
    } else {
      JiraConnectorDTO connectorDTO = jiraApprovalHelperService.getJiraConnector(
          AmbianceUtils.getAccountId(instance.getAmbiance()), AmbianceUtils.getOrgIdentifier(instance.getAmbiance()),
          AmbianceUtils.getProjectIdentifier(instance.getAmbiance()), instance.getConnectorRef());
      jiraUrl = connectorDTO.getJiraUrl();
    }

    JiraApprovalInstanceDetailsDTO jiraApprovalInstanceDetailsDTO =
        JiraApprovalInstanceDetailsDTO.builder()
            .connectorRef(instance.getConnectorRef())
            .issue(new JiraIssueKeyNG(jiraUrl, instance.getIssueKey(), instance.getTicketFields()))
            .approvalCriteria(instance.getApprovalCriteria())
            .rejectionCriteria(instance.getRejectionCriteria())
            .retryInterval(checkForRetryIntervalNullOrReturnValue(instance.getRetryInterval()))
            .build();

    if (shouldAddDelegateMetadata) {
      jiraApprovalInstanceDetailsDTO.setLatestDelegateTaskId(instance.getLatestDelegateTaskId());
      jiraApprovalInstanceDetailsDTO.setDelegateTaskName(ApprovalUtils.getDelegateTaskName(instance));
    }
    return jiraApprovalInstanceDetailsDTO;
  }

  private ApprovalInstanceDetailsDTO toServiceNowApprovalInstanceDetailsDTO(
      ServiceNowApprovalInstance instance, boolean shouldAddDelegateMetadata) {
    String serviceNowUrl;
    if (HarnessYamlVersion.isV1(AmbianceUtils.getPipelineVersion(instance.getAmbiance()))) {
      serviceNowUrl = CDStepsEnvironmentVarsHelper.getEnvVar(
          instance.getRunStepInfoV1Outcome(), ConnectorVariableConstants.PLUGIN_SERVICENOW_URL);
    } else {
      ServiceNowConnectorDTO connectorDTO = serviceNowApprovalHelperService.getServiceNowConnector(
          AmbianceUtils.getAccountId(instance.getAmbiance()), AmbianceUtils.getOrgIdentifier(instance.getAmbiance()),
          AmbianceUtils.getProjectIdentifier(instance.getAmbiance()), instance.getConnectorRef());
      serviceNowUrl = connectorDTO.getServiceNowUrl();
    }

    Map<String, String> fields;
    if (!isEmpty(instance.getTicketFields())) {
      fields = new HashMap<>();
      instance.getTicketFields().forEach((k, v) -> fields.put(k, v.getDisplayValue()));
    } else {
      fields = null;
    }

    ServiceNowApprovalInstanceDetailsDTO serviceNowApprovalInstanceDetailsDTO =
        ServiceNowApprovalInstanceDetailsDTO.builder()
            .connectorRef(instance.getConnectorRef())
            .ticket(
                new ServiceNowTicketKeyNG(serviceNowUrl, instance.getTicketNumber(), instance.getTicketType(), fields))
            .approvalCriteria(instance.getApprovalCriteria())
            .rejectionCriteria(instance.getRejectionCriteria())
            .retryInterval(checkForRetryIntervalNullOrReturnValue(instance.getRetryInterval()))
            .changeWindowSpec(instance.getChangeWindow())
            .build();

    if (shouldAddDelegateMetadata) {
      serviceNowApprovalInstanceDetailsDTO.setLatestDelegateTaskId(instance.getLatestDelegateTaskId());
      serviceNowApprovalInstanceDetailsDTO.setDelegateTaskName(ApprovalUtils.getDelegateTaskName(instance));
    }
    return serviceNowApprovalInstanceDetailsDTO;
  }

  private ApprovalInstanceDetailsDTO toCustomApprovalInstanceDetailsDTO(
      CustomApprovalInstance instance, boolean shouldAddDelegateMetadata) {
    CustomApprovalInstanceDetailsDTO customApprovalInstanceDetailsDTO =
        CustomApprovalInstanceDetailsDTO.builder()
            .approvalCriteria(instance.getApprovalCriteria())
            .rejectionCriteria(instance.getRejectionCriteria())
            .retryInterval(checkForRetryIntervalNullOrReturnValue(instance.getRetryInterval()))
            .build();

    if (shouldAddDelegateMetadata) {
      customApprovalInstanceDetailsDTO.setLatestDelegateTaskId(instance.getLatestDelegateTaskId());
      customApprovalInstanceDetailsDTO.setDelegateTaskName(ApprovalUtils.getDelegateTaskName(instance));
    }
    return customApprovalInstanceDetailsDTO;
  }

  @Nullable
  private Timeout checkForRetryIntervalNullOrReturnValue(ParameterField<Timeout> instance) {
    return instance != null ? instance.getValue() : null;
  }
}
