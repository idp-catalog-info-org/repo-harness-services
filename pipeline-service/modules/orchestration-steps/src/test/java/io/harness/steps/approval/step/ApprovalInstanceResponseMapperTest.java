/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.HARSHIT;
import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.JiraConnectorDTO;
import io.harness.delegate.beans.connector.ServiceNowConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraAuthType;
import io.harness.delegate.beans.connector.jira.JiraAuthenticationDTO;
import io.harness.delegate.beans.connector.jira.JiraUserNamePasswordDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthenticationDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowUserNamePasswordDTO;
import io.harness.jira.JiraIssueUtilsNG;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.servicenow.ServiceNowUtils;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapperDTO;
import io.harness.steps.approval.step.beans.HarnessApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.beans.JiraApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.beans.ServiceNowApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.beans.ServiceNowChangeWindowSpecDTO;
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
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.beans.AutoApprovalDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.steps.approval.step.harness.beans.ScheduledDeadlineDTO;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalHelperService;
import io.harness.steps.shellscript.ShellType;
import io.harness.yaml.core.timeout.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class ApprovalInstanceResponseMapperTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";
  private static final String CONNECTOR_IDENTIFIER = "connId";
  private static final String INSTANCE_ID = "id";
  private static final String TASK_ID = "task_id";
  private static final Long CREATED_AT = 1L;
  private static final Long UPDATED_AT = 2L;
  private static final Long DEADLINE = 3L;
  private static final String ERROR_MESSAGE = "errorMessage";
  private static final String ISSUE_KEY = "HNS-123";
  private static final String SNOW_TICKET_TYPE = "INCIDENT";
  private static final String HARNESS_APPROVAL_MESSAGE = "Approval Message";
  private static final String SERVICENOW_URL = "https://venhrn.service-now.com";
  private static final String JIRA_URL = "https://venhrn.atlassian.com";
  private static final String APPROVAL_NAME = "Admin";
  private static final String APPROVAL_EMAIL = "admin@harness.io";
  private List<ApproverInput> approverInputs;
  private List<ApproverInputInfoDTO> approverInputInfoDTOList;
  @Mock JiraApprovalHelperService jiraApprovalHelperService;
  @Mock ServiceNowApprovalHelperService serviceNowApprovalHelperService;

  @InjectMocks ApprovalInstanceResponseMapper approvalInstanceResponseMapper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Mockito
        .when(jiraApprovalHelperService.getJiraConnector(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, CONNECTOR_IDENTIFIER))
        .thenReturn(buildJiraConnectorDTO());
    Mockito
        .when(serviceNowApprovalHelperService.getServiceNowConnector(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, CONNECTOR_IDENTIFIER))
        .thenReturn(buildServiceNowConnectorDTO());
    ApproverInput approverInput1 = buildApproverInput("var1", "val1");
    ApproverInput approverInput2 = buildApproverInput("var2", "val2");
    approverInputs = Arrays.asList(approverInput1, approverInput2);
    ApproverInputInfoDTO approverInputInfoDTO1 =
        buildApproverInputInfoDTO("var1", "def1", "val[^.]+", new ArrayList<>(), new ArrayList<>(), true);
    ApproverInputInfoDTO approverInputInfoDTO2 =
        buildApproverInputInfoDTO("var2", "def2", null, Collections.singletonList("val1"), new ArrayList<>(), true);
    approverInputInfoDTOList = Arrays.asList(approverInputInfoDTO1, approverInputInfoDTO2);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testToApprovalInstanceResponseDTOWithDelegateMetadataAsFalse() {
    assertThat(approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(null, false, true)).isNull();
    ApprovalInstanceResponseDTO approvalInstanceResponseDTO =
        approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
            buildApprovalInstance(ApprovalType.SERVICENOW_APPROVAL), false, true);
    // common fields
    assertThat(approvalInstanceResponseDTO.getId()).isEqualTo(INSTANCE_ID);
    assertThat(approvalInstanceResponseDTO.getType()).isEqualTo(ApprovalType.SERVICENOW_APPROVAL);
    assertThat(approvalInstanceResponseDTO.getStatus()).isEqualTo(ApprovalStatus.WAITING);
    assertThat(approvalInstanceResponseDTO.getDeadline()).isEqualTo(DEADLINE);
    assertThat(approvalInstanceResponseDTO.getLastModifiedAt()).isEqualTo(UPDATED_AT);
    assertThat(approvalInstanceResponseDTO.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(approvalInstanceResponseDTO.getErrorMessage()).isEqualTo(ERROR_MESSAGE);

    // snow approval specific fields
    ServiceNowApprovalInstanceDetailsDTO serviceNowApprovalInstanceDetailsDTO =
        (ServiceNowApprovalInstanceDetailsDTO) approvalInstanceResponseDTO.getDetails();
    assertThat(serviceNowApprovalInstanceDetailsDTO.getConnectorRef()).isEqualTo(CONNECTOR_IDENTIFIER);
    assertThat(serviceNowApprovalInstanceDetailsDTO.getTicket().getUrl())
        .isEqualTo(ServiceNowUtils.prepareTicketUrlFromTicketNumber(SERVICENOW_URL, ISSUE_KEY, SNOW_TICKET_TYPE));
    assertThat(serviceNowApprovalInstanceDetailsDTO.getTicket().getKey()).isEqualTo(ISSUE_KEY);
    assertThat(serviceNowApprovalInstanceDetailsDTO.getTicket().getTicketType()).isEqualTo(SNOW_TICKET_TYPE);
    assertThat(serviceNowApprovalInstanceDetailsDTO.getTicket().getTicketFields()).isNull();
    assertThat(serviceNowApprovalInstanceDetailsDTO.getLatestDelegateTaskId()).isNull();
    assertThat(serviceNowApprovalInstanceDetailsDTO.getDelegateTaskName()).isNull();
    assertThat(serviceNowApprovalInstanceDetailsDTO.getRetryInterval().getTimeoutInMillis()).isEqualTo(60000);

    approvalInstanceResponseDTO = approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
        buildApprovalInstance(ApprovalType.JIRA_APPROVAL), false, true);
    assertThat(approvalInstanceResponseDTO.getType()).isEqualTo(ApprovalType.JIRA_APPROVAL);

    // jira approval specific fields
    JiraApprovalInstanceDetailsDTO jiraApprovalInstanceDetailsDTO =
        (JiraApprovalInstanceDetailsDTO) approvalInstanceResponseDTO.getDetails();
    assertThat(jiraApprovalInstanceDetailsDTO.getConnectorRef()).isEqualTo(CONNECTOR_IDENTIFIER);
    assertThat(jiraApprovalInstanceDetailsDTO.getIssue().getUrl())
        .isEqualTo(JiraIssueUtilsNG.prepareIssueUrl(JIRA_URL, ISSUE_KEY));
    assertThat(jiraApprovalInstanceDetailsDTO.getIssue().getKey()).isEqualTo(ISSUE_KEY);
    assertThat(jiraApprovalInstanceDetailsDTO.getIssue().getTicketFields()).isNull();
    assertThat(jiraApprovalInstanceDetailsDTO.getLatestDelegateTaskId()).isNull();
    assertThat(jiraApprovalInstanceDetailsDTO.getDelegateTaskName()).isNull();
    assertThat(jiraApprovalInstanceDetailsDTO.getRetryInterval().getTimeoutInMillis()).isEqualTo(60000);
    approvalInstanceResponseDTO = approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
        buildApprovalInstance(ApprovalType.CUSTOM_APPROVAL), false, true);
    assertThat(approvalInstanceResponseDTO.getType()).isEqualTo(ApprovalType.CUSTOM_APPROVAL);

    // custom approval specific fields
    CustomApprovalInstanceDetailsDTO customApprovalInstanceDetailsDTO =
        (CustomApprovalInstanceDetailsDTO) approvalInstanceResponseDTO.getDetails();
    assertThat(customApprovalInstanceDetailsDTO.getLatestDelegateTaskId()).isNull();
    assertThat(customApprovalInstanceDetailsDTO.getDelegateTaskName()).isNull();
    assertThat(customApprovalInstanceDetailsDTO.getRetryInterval().getTimeoutInMillis()).isEqualTo(60000);
    approvalInstanceResponseDTO = approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
        buildApprovalInstance(ApprovalType.HARNESS_APPROVAL), false, true);
    assertThat(approvalInstanceResponseDTO.getType()).isEqualTo(ApprovalType.HARNESS_APPROVAL);

    // harness approval specific fields
    HarnessApprovalInstanceDetailsDTO harnessApprovalInstanceDetailsDTO =
        (HarnessApprovalInstanceDetailsDTO) approvalInstanceResponseDTO.getDetails();
    assertThat(harnessApprovalInstanceDetailsDTO.getApprovalMessage()).isEqualTo(HARNESS_APPROVAL_MESSAGE);
    assertThat(harnessApprovalInstanceDetailsDTO.isAutoRejectEnabled()).isFalse();
    assertThat(harnessApprovalInstanceDetailsDTO.isIncludePipelineExecutionHistory()).isTrue();
    assertThat(harnessApprovalInstanceDetailsDTO.getAutoApprovalParams()).isNotNull();
    assertThat(harnessApprovalInstanceDetailsDTO.getAutoApprovalParams().getScheduledDeadline().getTime())
        .isEqualTo("2023-05-05 04:24 am");
    assertThat(harnessApprovalInstanceDetailsDTO.getAutoApprovalParams().getScheduledDeadline().getTimeZone())
        .isEqualTo("Asia/Kolkata");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testToApprovalInstanceResponseDTOWithDelegateMetadataAsTrue() {
    assertThat(approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(null, true, true)).isNull();
    ApprovalInstanceResponseDTO approvalInstanceResponseDTO =
        approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
            buildApprovalInstance(ApprovalType.SERVICENOW_APPROVAL), true, true);
    // common fields
    assertThat(approvalInstanceResponseDTO.getId()).isEqualTo(INSTANCE_ID);
    assertThat(approvalInstanceResponseDTO.getType()).isEqualTo(ApprovalType.SERVICENOW_APPROVAL);
    assertThat(approvalInstanceResponseDTO.getStatus()).isEqualTo(ApprovalStatus.WAITING);
    assertThat(approvalInstanceResponseDTO.getDeadline()).isEqualTo(DEADLINE);
    assertThat(approvalInstanceResponseDTO.getLastModifiedAt()).isEqualTo(UPDATED_AT);
    assertThat(approvalInstanceResponseDTO.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(approvalInstanceResponseDTO.getErrorMessage()).isEqualTo(ERROR_MESSAGE);

    // snow approval specific fields
    ServiceNowApprovalInstanceDetailsDTO serviceNowApprovalInstanceDetailsDTO =
        (ServiceNowApprovalInstanceDetailsDTO) approvalInstanceResponseDTO.getDetails();
    assertThat(serviceNowApprovalInstanceDetailsDTO.getConnectorRef()).isEqualTo(CONNECTOR_IDENTIFIER);
    assertThat(serviceNowApprovalInstanceDetailsDTO.getTicket().getUrl())
        .isEqualTo(ServiceNowUtils.prepareTicketUrlFromTicketNumber(SERVICENOW_URL, ISSUE_KEY, SNOW_TICKET_TYPE));
    assertThat(serviceNowApprovalInstanceDetailsDTO.getTicket().getKey()).isEqualTo(ISSUE_KEY);
    assertThat(serviceNowApprovalInstanceDetailsDTO.getTicket().getTicketType()).isEqualTo(SNOW_TICKET_TYPE);
    assertThat(serviceNowApprovalInstanceDetailsDTO.getTicket().getTicketFields()).isNull();
    assertThat(serviceNowApprovalInstanceDetailsDTO.getLatestDelegateTaskId()).isEqualTo(TASK_ID);
    assertThat(serviceNowApprovalInstanceDetailsDTO.getDelegateTaskName())
        .isEqualTo(ApprovalUtils.SERVICENOW_DELEGATE_TASK_NAME);

    approvalInstanceResponseDTO = approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
        buildApprovalInstance(ApprovalType.JIRA_APPROVAL), true, true);
    assertThat(approvalInstanceResponseDTO.getType()).isEqualTo(ApprovalType.JIRA_APPROVAL);

    // jira approval specific fields
    JiraApprovalInstanceDetailsDTO jiraApprovalInstanceDetailsDTO =
        (JiraApprovalInstanceDetailsDTO) approvalInstanceResponseDTO.getDetails();
    assertThat(jiraApprovalInstanceDetailsDTO.getConnectorRef()).isEqualTo(CONNECTOR_IDENTIFIER);
    assertThat(jiraApprovalInstanceDetailsDTO.getIssue().getUrl())
        .isEqualTo(JiraIssueUtilsNG.prepareIssueUrl(JIRA_URL, ISSUE_KEY));
    assertThat(jiraApprovalInstanceDetailsDTO.getIssue().getKey()).isEqualTo(ISSUE_KEY);
    assertThat(jiraApprovalInstanceDetailsDTO.getIssue().getTicketFields()).isNull();
    assertThat(jiraApprovalInstanceDetailsDTO.getLatestDelegateTaskId()).isEqualTo(TASK_ID);
    assertThat(jiraApprovalInstanceDetailsDTO.getDelegateTaskName()).isEqualTo(ApprovalUtils.JIRA_DELEGATE_TASK_NAME);

    approvalInstanceResponseDTO = approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
        buildApprovalInstance(ApprovalType.CUSTOM_APPROVAL), true, true);
    assertThat(approvalInstanceResponseDTO.getType()).isEqualTo(ApprovalType.CUSTOM_APPROVAL);

    // custom approval specific fields
    CustomApprovalInstanceDetailsDTO customApprovalInstanceDetailsDTO =
        (CustomApprovalInstanceDetailsDTO) approvalInstanceResponseDTO.getDetails();
    assertThat(customApprovalInstanceDetailsDTO.getLatestDelegateTaskId()).isEqualTo(TASK_ID);
    assertThat(customApprovalInstanceDetailsDTO.getDelegateTaskName())
        .isEqualTo(ApprovalUtils.getDelegateTaskName(buildApprovalInstance(ApprovalType.CUSTOM_APPROVAL)));

    approvalInstanceResponseDTO = approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
        buildApprovalInstance(ApprovalType.HARNESS_APPROVAL), true, true);
    assertThat(approvalInstanceResponseDTO.getType()).isEqualTo(ApprovalType.HARNESS_APPROVAL);

    // harness approval specific fields
    HarnessApprovalInstanceDetailsDTO harnessApprovalInstanceDetailsDTO =
        (HarnessApprovalInstanceDetailsDTO) approvalInstanceResponseDTO.getDetails();
    assertThat(harnessApprovalInstanceDetailsDTO.getApprovalMessage()).isEqualTo(HARNESS_APPROVAL_MESSAGE);
    assertThat(harnessApprovalInstanceDetailsDTO.isAutoRejectEnabled()).isFalse();
    assertThat(harnessApprovalInstanceDetailsDTO.isIncludePipelineExecutionHistory()).isTrue();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testToApprovalInstanceResponseDTOWithoutDelegateMetadataA() {
    assertThat(approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(null, true)).isNull();
    ApprovalInstanceResponseDTO approvalInstanceResponseDTO =
        approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
            buildApprovalInstance(ApprovalType.SERVICENOW_APPROVAL), true);
    // common fields
    assertThat(approvalInstanceResponseDTO.getId()).isEqualTo(INSTANCE_ID);
    assertThat(approvalInstanceResponseDTO.getType()).isEqualTo(ApprovalType.SERVICENOW_APPROVAL);
    assertThat(approvalInstanceResponseDTO.getStatus()).isEqualTo(ApprovalStatus.WAITING);
    assertThat(approvalInstanceResponseDTO.getDeadline()).isEqualTo(DEADLINE);
    assertThat(approvalInstanceResponseDTO.getLastModifiedAt()).isEqualTo(UPDATED_AT);
    assertThat(approvalInstanceResponseDTO.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(approvalInstanceResponseDTO.getErrorMessage()).isEqualTo(ERROR_MESSAGE);

    // snow approval specific fields
    ServiceNowApprovalInstanceDetailsDTO serviceNowApprovalInstanceDetailsDTO =
        (ServiceNowApprovalInstanceDetailsDTO) approvalInstanceResponseDTO.getDetails();
    assertThat(serviceNowApprovalInstanceDetailsDTO.getConnectorRef()).isEqualTo(CONNECTOR_IDENTIFIER);
    assertThat(serviceNowApprovalInstanceDetailsDTO.getTicket().getUrl())
        .isEqualTo(ServiceNowUtils.prepareTicketUrlFromTicketNumber(SERVICENOW_URL, ISSUE_KEY, SNOW_TICKET_TYPE));
    assertThat(serviceNowApprovalInstanceDetailsDTO.getTicket().getKey()).isEqualTo(ISSUE_KEY);
    assertThat(serviceNowApprovalInstanceDetailsDTO.getTicket().getTicketType()).isEqualTo(SNOW_TICKET_TYPE);
    assertThat(serviceNowApprovalInstanceDetailsDTO.getTicket().getTicketFields()).isNull();
    assertThat(serviceNowApprovalInstanceDetailsDTO.getLatestDelegateTaskId()).isNull();
    assertThat(serviceNowApprovalInstanceDetailsDTO.getDelegateTaskName()).isNull();

    approvalInstanceResponseDTO = approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
        buildApprovalInstance(ApprovalType.JIRA_APPROVAL), true);
    assertThat(approvalInstanceResponseDTO.getType()).isEqualTo(ApprovalType.JIRA_APPROVAL);

    // jira approval specific fields
    JiraApprovalInstanceDetailsDTO jiraApprovalInstanceDetailsDTO =
        (JiraApprovalInstanceDetailsDTO) approvalInstanceResponseDTO.getDetails();
    assertThat(jiraApprovalInstanceDetailsDTO.getConnectorRef()).isEqualTo(CONNECTOR_IDENTIFIER);
    assertThat(jiraApprovalInstanceDetailsDTO.getIssue().getUrl())
        .isEqualTo(JiraIssueUtilsNG.prepareIssueUrl(JIRA_URL, ISSUE_KEY));
    assertThat(jiraApprovalInstanceDetailsDTO.getIssue().getKey()).isEqualTo(ISSUE_KEY);
    assertThat(jiraApprovalInstanceDetailsDTO.getIssue().getTicketFields()).isNull();
    assertThat(jiraApprovalInstanceDetailsDTO.getLatestDelegateTaskId()).isNull();
    assertThat(jiraApprovalInstanceDetailsDTO.getDelegateTaskName()).isNull();

    approvalInstanceResponseDTO = approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
        buildApprovalInstance(ApprovalType.CUSTOM_APPROVAL), true);
    assertThat(approvalInstanceResponseDTO.getType()).isEqualTo(ApprovalType.CUSTOM_APPROVAL);

    // custom approval specific fields
    CustomApprovalInstanceDetailsDTO customApprovalInstanceDetailsDTO =
        (CustomApprovalInstanceDetailsDTO) approvalInstanceResponseDTO.getDetails();
    assertThat(customApprovalInstanceDetailsDTO.getLatestDelegateTaskId()).isNull();
    assertThat(customApprovalInstanceDetailsDTO.getDelegateTaskName()).isNull();

    approvalInstanceResponseDTO = approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
        buildApprovalInstance(ApprovalType.HARNESS_APPROVAL), true);
    assertThat(approvalInstanceResponseDTO.getType()).isEqualTo(ApprovalType.HARNESS_APPROVAL);

    // harness approval specific fields
    HarnessApprovalInstanceDetailsDTO harnessApprovalInstanceDetailsDTO =
        (HarnessApprovalInstanceDetailsDTO) approvalInstanceResponseDTO.getDetails();
    assertThat(harnessApprovalInstanceDetailsDTO.getApprovalMessage()).isEqualTo(HARNESS_APPROVAL_MESSAGE);
    assertThat(harnessApprovalInstanceDetailsDTO.isAutoRejectEnabled()).isFalse();
    assertThat(harnessApprovalInstanceDetailsDTO.isIncludePipelineExecutionHistory()).isTrue();
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void
  testToApprovalInstanceResponseReturnsLastApprovalActivityInApproverInputWhenFlagIsTrueAndApprovalActivityPresent() {
    ApprovalInstanceResponseDTO approvalInstanceResponseDTO =
        approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
            buildHarnessApprovalInstanceWithApproverInputInfo(true, approverInputInfoDTOList, approverInputs), true);
    List<ApproverInputInfoDTO> approverInputInfoInResponse =
        ((HarnessApprovalInstanceDetailsDTO) approvalInstanceResponseDTO.getDetails()).getApproverInputs();
    assertThat(approverInputInfoInResponse.size() == 2);
    assertThat(approverInputInfoInResponse.get(0).getDefaultValue()).isEqualTo("val1");
    assertThat(approverInputInfoInResponse.get(1).getDefaultValue()).isEqualTo("val2");
    assertThat(approverInputInfoInResponse.get(0).getRegex()).isEqualTo("val[^.]+");
    assertThat(approverInputInfoInResponse.get(1).getAllowedValues().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testToApprovalInstanceResponseDoesNotReturnLastApprovalActivityInApproverInputWhenFlagIsFalse() {
    ApprovalInstanceResponseDTO approvalInstanceResponseDTO =
        approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
            buildHarnessApprovalInstanceWithApproverInputInfo(true, approverInputInfoDTOList, approverInputs), false);
    List<ApproverInputInfoDTO> approverInputInfoInResponse =
        ((HarnessApprovalInstanceDetailsDTO) approvalInstanceResponseDTO.getDetails()).getApproverInputs();
    assertThat(approverInputInfoInResponse.size() == 2);
    assertThat(approverInputInfoInResponse.get(0).getDefaultValue()).isEqualTo("def1");
    assertThat(approverInputInfoInResponse.get(1).getDefaultValue()).isEqualTo("def2");
    assertThat(approverInputInfoInResponse.get(0).getRegex()).isEqualTo("val[^.]+");
    assertThat(approverInputInfoInResponse.get(1).getAllowedValues().size()).isEqualTo(1);
  }

  private ApproverInput buildApproverInput(String name, String value) {
    return ApproverInput.builder().name(name).value(value).build();
  }

  private ApproverInputInfoDTO buildApproverInputInfoDTO(String name, String value, String regex,
      List<String> allowedValues, List<String> selectOneFrom, Boolean required) {
    return ApproverInputInfoDTO.builder()
        .name(name)
        .defaultValue(value)
        .regex(regex)
        .allowedValues(allowedValues)
        .selectOneFrom(selectOneFrom)
        .required(required)
        .build();
  }

  private ApprovalInstance buildHarnessApprovalInstanceWithApproverInputInfo(boolean addHarnessApprovalActivity,
      List<ApproverInputInfoDTO> approverInputs, List<ApproverInput> approverInputsInLastActivity) {
    HarnessApprovalInstance harnessApprovalInstance = HarnessApprovalInstance.builder()
                                                          .approvalMessage(HARNESS_APPROVAL_MESSAGE)
                                                          .includePipelineExecutionHistory(true)
                                                          .isAutoRejectEnabled(false)
                                                          .approvers(ApproversDTO.builder().build())
                                                          .approverInputs(approverInputs)
                                                          .build();
    if (addHarnessApprovalActivity) {
      harnessApprovalInstance.addApprovalActivity(
          EmbeddedUser.builder().email(APPROVAL_EMAIL).name(APPROVAL_NAME).build(),
          HarnessApprovalActivityRequestDTO.builder()
              .action(HarnessApprovalAction.APPROVE)
              .approverInputs(approverInputsInLastActivity)
              .build());
    }
    harnessApprovalInstance.setId(INSTANCE_ID);
    harnessApprovalInstance.setType(ApprovalType.HARNESS_APPROVAL);
    return harnessApprovalInstance;
  }

  private ApprovalInstance buildApprovalInstance(ApprovalType approvalType) {
    ApprovalInstance approvalInstance;
    switch (approvalType) {
      case JIRA_APPROVAL:
        JiraApprovalInstance jiraApprovalInstance =
            JiraApprovalInstance.builder()
                .approvalCriteria(CriteriaSpecWrapperDTO.builder().build())
                .rejectionCriteria(CriteriaSpecWrapperDTO.builder().build())
                .issueKey(ISSUE_KEY)
                .latestDelegateTaskId(TASK_ID)
                .connectorRef(CONNECTOR_IDENTIFIER)
                .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                .build();
        jiraApprovalInstance.setId(INSTANCE_ID);
        jiraApprovalInstance.setType(ApprovalType.JIRA_APPROVAL);
        approvalInstance = jiraApprovalInstance;
        break;
      case SERVICENOW_APPROVAL:
        ServiceNowApprovalInstance serviceNowApprovalInstance =
            ServiceNowApprovalInstance.builder()
                .approvalCriteria(CriteriaSpecWrapperDTO.builder().build())
                .rejectionCriteria(CriteriaSpecWrapperDTO.builder().build())
                .ticketNumber(ISSUE_KEY)
                .ticketType(SNOW_TICKET_TYPE)
                .changeWindow(ServiceNowChangeWindowSpecDTO.builder().build())
                .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                .latestDelegateTaskId(TASK_ID)
                .connectorRef(CONNECTOR_IDENTIFIER)
                .build();
        serviceNowApprovalInstance.setId(INSTANCE_ID);
        serviceNowApprovalInstance.setType(ApprovalType.SERVICENOW_APPROVAL);
        approvalInstance = serviceNowApprovalInstance;
        break;
      case CUSTOM_APPROVAL:
        CustomApprovalInstance customApprovalInstance =
            CustomApprovalInstance.builder()
                .approvalCriteria(CriteriaSpecWrapperDTO.builder().build())
                .rejectionCriteria(CriteriaSpecWrapperDTO.builder().build())
                .latestDelegateTaskId(TASK_ID)
                .shellType(ShellType.Bash)
                .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                .build();
        customApprovalInstance.setId(INSTANCE_ID);
        customApprovalInstance.setType(ApprovalType.CUSTOM_APPROVAL);
        approvalInstance = customApprovalInstance;
        break;
      case HARNESS_APPROVAL:
        HarnessApprovalInstance harnessApprovalInstance =
            HarnessApprovalInstance.builder()
                .approvalMessage(HARNESS_APPROVAL_MESSAGE)
                .includePipelineExecutionHistory(true)
                .isAutoRejectEnabled(false)
                .approvers(ApproversDTO.builder().build())
                .autoApproval(
                    AutoApprovalDTO.builder()
                        .scheduledDeadline(
                            ScheduledDeadlineDTO.builder().time("2023-05-05 04:24 am").timeZone("Asia/Kolkata").build())
                        .build())
                .build();
        harnessApprovalInstance.setId(INSTANCE_ID);
        harnessApprovalInstance.setType(ApprovalType.HARNESS_APPROVAL);
        approvalInstance = harnessApprovalInstance;
        break;
      default:
        return null;
    }
    approvalInstance.setDeadline(DEADLINE);
    approvalInstance.setStatus(ApprovalStatus.WAITING);
    approvalInstance.setCreatedAt(CREATED_AT);
    approvalInstance.setErrorMessage(ERROR_MESSAGE);
    approvalInstance.setLastModifiedAt(UPDATED_AT);
    approvalInstance.setAmbiance(Ambiance.newBuilder()
                                     .putSetupAbstractions("accountId", ACCOUNT_ID)
                                     .putSetupAbstractions("orgIdentifier", ORG_IDENTIFIER)
                                     .putSetupAbstractions("projectIdentifier", PROJ_IDENTIFIER)
                                     .build());
    return approvalInstance;
  }

  private ServiceNowConnectorDTO buildServiceNowConnectorDTO() {
    return ServiceNowConnectorDTO.builder()
        .auth(ServiceNowAuthenticationDTO.builder()
                  .authType(ServiceNowAuthType.USER_PASSWORD)
                  .credentials(ServiceNowUserNamePasswordDTO.builder().build())
                  .build())
        .serviceNowUrl(SERVICENOW_URL)
        .build();
  }

  private JiraConnectorDTO buildJiraConnectorDTO() {
    return JiraConnectorDTO.builder()
        .auth(JiraAuthenticationDTO.builder()
                  .authType(JiraAuthType.USER_PASSWORD)
                  .credentials(JiraUserNamePasswordDTO.builder().build())
                  .build())
        .jiraUrl(JIRA_URL)
        .build();
  }
}
