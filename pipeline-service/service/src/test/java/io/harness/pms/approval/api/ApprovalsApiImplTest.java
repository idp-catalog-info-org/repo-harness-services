/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.pms.approval.ApprovalResourceService;
import io.harness.pms.contracts.plan.RetryExecutionInfo;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.ApprovalInstanceResponseBody;
import io.harness.spec.server.pipeline.v1.model.ApproverInputDTO;
import io.harness.spec.server.pipeline.v1.model.HarnessApprovalActivityRequestBody;
import io.harness.spec.server.pipeline.v1.model.HarnessApprovalActivityRequestBody.ActionEnum;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.telemetry.helpers.ApprovalApiInstrumentationHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PIPELINE)
public class ApprovalsApiImplTest extends PipelineServiceTestBase {
  @Mock ApprovalResourceService approvalResourceService;
  @Mock PMSExecutionService pmsExecutionService;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Mock ApprovalApiInstrumentationHelper instrumentationHelper;
  @InjectMocks ApprovalsApiImpl approvalsApiImpl;
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";
  private static final String EXECUTION_IDENTIFIER = "exeId";
  private static final String CALLBACK_IDENTIFIER = "callbackId";
  private static final String NODE_IDENTIFIER = "nodeId";
  private static final String RESOURCE_IDENTIFIER = "id";
  private static final String ERROR_MESSAGE = "errorMessage";
  private static final String APPROVAL_STATUS = "WAITING";
  private static final String APPROVAL_TYPE = "HarnessApproval";
  private static final Long CREATED_AT = 1L;
  private static final Long UPDATED_AT = 2L;
  private static final Long DEADLINE = 3L;

  @Before
  public void setup() {
    when(pmsExecutionService.getPipelineExecutionSummaryEntity(ACCOUNT_ID, EXECUTION_IDENTIFIER, false))
        .thenReturn(null);
    when(planExecutionMetadataService.getWithFieldsIncludedFromSecondary(any(), any(), any()))
        .thenReturn(PlanExecutionMetadata.builder()
                        .retryExecutionInfo(RetryExecutionInfo.newBuilder().setIsRetry(false).build())
                        .build());
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetApprovalInstancesByExecutionIdNegativeCases() {
    assertThatThrownBy(()
                           -> approvalsApiImpl.getApprovalInstancesByExecutionId(ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               EXECUTION_IDENTIFIER, ACCOUNT_ID, "InvalidStatus", null, null, null))
        .isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(()
                           -> approvalsApiImpl.getApprovalInstancesByExecutionId(ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               EXECUTION_IDENTIFIER, ACCOUNT_ID, null, "InvalidType", null, null))
        .isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(()
                           -> approvalsApiImpl.getApprovalInstancesByExecutionId(ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               EXECUTION_IDENTIFIER, ACCOUNT_ID, "InvalidStatus", "InvalidType", null, null))
        .isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(()
                           -> approvalsApiImpl.getApprovalInstancesByExecutionId(ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               EXECUTION_IDENTIFIER, ACCOUNT_ID, "InvalidStatus", "InvalidType", null, null))
        .isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(()
                           -> approvalsApiImpl.getApprovalInstancesByExecutionId(ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               EXECUTION_IDENTIFIER, null, "InvalidStatus", "InvalidType", null, null))
        .isInstanceOf(InvalidRequestException.class);

    when(approvalResourceService.getApprovalInstancesByExecutionId(EXECUTION_IDENTIFIER, null, null, null, null, false))
        .thenReturn(Collections.emptyList());

    assertThatCode(()
                       -> approvalsApiImpl.getApprovalInstancesByExecutionId(
                           ORG_IDENTIFIER, PROJ_IDENTIFIER, EXECUTION_IDENTIFIER, ACCOUNT_ID, null, null, null, null))
        .doesNotThrowAnyException();

    when(planExecutionMetadataService.getWithFieldsIncludedFromSecondary(any(), any(), any()))
        .thenThrow(new EntityNotFoundException("summary doesn't exist"));

    assertThatThrownBy(()
                           -> approvalsApiImpl.getApprovalInstancesByExecutionId(ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               EXECUTION_IDENTIFIER, ACCOUNT_ID, null, null, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("execution_id param value provided doesn't belong to Account: %s, Org: %s, Project: "
                + "%s or the pipeline has been deleted",
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER));

    verify(planExecutionMetadataService, times(2)).getWithFieldsIncludedFromSecondary(any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddHarnessApprovalActivityByPipelineExecutionIdNegativeCases() {
    HarnessApprovalActivityRequestBody harnessApprovalActivityRequestBody = new HarnessApprovalActivityRequestBody();
    assertThatThrownBy(
        ()
            -> approvalsApiImpl.addHarnessApprovalActivityByPipelineExecutionId(ORG_IDENTIFIER, PROJ_IDENTIFIER,
                EXECUTION_IDENTIFIER, harnessApprovalActivityRequestBody, ACCOUNT_ID, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("action in request body should be one of [APPROVE, REJECT]");

    harnessApprovalActivityRequestBody.setAction(ActionEnum.APPROVE);
    assertThatCode(()
                       -> approvalsApiImpl.addHarnessApprovalActivityByPipelineExecutionId(ORG_IDENTIFIER,
                           PROJ_IDENTIFIER, EXECUTION_IDENTIFIER, harnessApprovalActivityRequestBody, ACCOUNT_ID, null))
        .doesNotThrowAnyException();

    harnessApprovalActivityRequestBody.setApproverInputs(new ArrayList<>());
    assertThatCode(()
                       -> approvalsApiImpl.addHarnessApprovalActivityByPipelineExecutionId(ORG_IDENTIFIER,
                           PROJ_IDENTIFIER, EXECUTION_IDENTIFIER, harnessApprovalActivityRequestBody, ACCOUNT_ID, null))
        .doesNotThrowAnyException();

    ApproverInputDTO approverInputDTO = new ApproverInputDTO();
    approverInputDTO.setName("example");
    approverInputDTO.setValue("example");
    harnessApprovalActivityRequestBody.getApproverInputs().add(approverInputDTO);
    assertThatCode(()
                       -> approvalsApiImpl.addHarnessApprovalActivityByPipelineExecutionId(ORG_IDENTIFIER,
                           PROJ_IDENTIFIER, EXECUTION_IDENTIFIER, harnessApprovalActivityRequestBody, ACCOUNT_ID, null))
        .doesNotThrowAnyException();

    when(pmsExecutionService.getPipelineExecutionSummaryEntity(ACCOUNT_ID, EXECUTION_IDENTIFIER, false))
        .thenThrow(new EntityNotFoundException("summary doesn't exist"));

    assertThatThrownBy(()
                           -> approvalsApiImpl.addHarnessApprovalActivityByPipelineExecutionId(
                               ORG_IDENTIFIER, PROJ_IDENTIFIER, EXECUTION_IDENTIFIER, null, ACCOUNT_ID, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("execution_id param value provided doesn't belong to Account: %s, Org: %s, Project: "
                + "%s or the pipeline has been deleted",
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER));

    verify(instrumentationHelper)
        .sendApprovalApiEvent(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, EXECUTION_IDENTIFIER,
            ApprovalApiInstrumentationHelper.FAILURE, ApprovalApiInstrumentationHelper.EXECUTION_ID_NOT_FOUND);
    verify(pmsExecutionService, times(5)).getPipelineExecutionSummaryEntity(ACCOUNT_ID, EXECUTION_IDENTIFIER, false);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddHarnessApprovalActivityByPipelineExecutionIdNegativeCasesWithCallbackId() {
    HarnessApprovalActivityRequestBody harnessApprovalActivityRequestBody = new HarnessApprovalActivityRequestBody();
    assertThatThrownBy(
        ()
            -> approvalsApiImpl.addHarnessApprovalActivityByPipelineExecutionId(ORG_IDENTIFIER, PROJ_IDENTIFIER,
                EXECUTION_IDENTIFIER, harnessApprovalActivityRequestBody, ACCOUNT_ID, CALLBACK_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("action in request body should be one of [APPROVE, REJECT]");

    harnessApprovalActivityRequestBody.setAction(ActionEnum.APPROVE);
    assertThatCode(
        ()
            -> approvalsApiImpl.addHarnessApprovalActivityByPipelineExecutionId(ORG_IDENTIFIER, PROJ_IDENTIFIER,
                EXECUTION_IDENTIFIER, harnessApprovalActivityRequestBody, ACCOUNT_ID, CALLBACK_IDENTIFIER))
        .doesNotThrowAnyException();

    harnessApprovalActivityRequestBody.setApproverInputs(new ArrayList<>());
    assertThatCode(
        ()
            -> approvalsApiImpl.addHarnessApprovalActivityByPipelineExecutionId(ORG_IDENTIFIER, PROJ_IDENTIFIER,
                EXECUTION_IDENTIFIER, harnessApprovalActivityRequestBody, ACCOUNT_ID, CALLBACK_IDENTIFIER))
        .doesNotThrowAnyException();

    ApproverInputDTO approverInputDTO = new ApproverInputDTO();
    approverInputDTO.setName("example");
    approverInputDTO.setValue("example");
    harnessApprovalActivityRequestBody.getApproverInputs().add(approverInputDTO);
    assertThatCode(
        ()
            -> approvalsApiImpl.addHarnessApprovalActivityByPipelineExecutionId(ORG_IDENTIFIER, PROJ_IDENTIFIER,
                EXECUTION_IDENTIFIER, harnessApprovalActivityRequestBody, ACCOUNT_ID, CALLBACK_IDENTIFIER))
        .doesNotThrowAnyException();

    when(pmsExecutionService.getPipelineExecutionSummaryEntity(ACCOUNT_ID, EXECUTION_IDENTIFIER, false))
        .thenThrow(new EntityNotFoundException("summary doesn't exist"));

    assertThatThrownBy(()
                           -> approvalsApiImpl.addHarnessApprovalActivityByPipelineExecutionId(ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, EXECUTION_IDENTIFIER, null, ACCOUNT_ID, CALLBACK_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("execution_id param value provided doesn't belong to Account: %s, Org: %s, Project: "
                + "%s or the pipeline has been deleted",
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER));

    verify(instrumentationHelper)
        .sendApprovalApiEvent(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, EXECUTION_IDENTIFIER,
            ApprovalApiInstrumentationHelper.FAILURE, ApprovalApiInstrumentationHelper.EXECUTION_ID_NOT_FOUND);
    verify(pmsExecutionService, times(5)).getPipelineExecutionSummaryEntity(ACCOUNT_ID, EXECUTION_IDENTIFIER, false);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetApprovalInstancesByExecutionIdWhenValidatingExecutionIdResultsInUnexpectedError() {
    when(planExecutionMetadataService.getWithFieldsIncludedFromSecondary(any(), any(), any()))
        .thenThrow(new InvalidRequestException("random exception"));

    assertThatThrownBy(()
                           -> approvalsApiImpl.getApprovalInstancesByExecutionId(ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               EXECUTION_IDENTIFIER, ACCOUNT_ID, null, null, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("An unexpected error occurred while validating execution_id param");
    verify(planExecutionMetadataService, times(1)).getWithFieldsIncludedFromSecondary(any(), any(), any());
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetApprovalInstancesByExecutionId() {
    List<ApprovalInstanceResponseDTO> approvalInstances = new ArrayList<>();
    approvalInstances.add(buildApprovalInstance(ApprovalType.HARNESS_APPROVAL, ApprovalStatus.WAITING));
    approvalInstances.add(buildApprovalInstance(ApprovalType.SERVICENOW_APPROVAL, ApprovalStatus.WAITING));
    approvalInstances.add(buildApprovalInstance(ApprovalType.JIRA_APPROVAL, ApprovalStatus.WAITING));
    approvalInstances.add(buildApprovalInstance(ApprovalType.CUSTOM_APPROVAL, ApprovalStatus.WAITING));

    when(approvalResourceService.getApprovalInstancesByExecutionId(
             EXECUTION_IDENTIFIER, ApprovalStatus.WAITING, ApprovalType.HARNESS_APPROVAL, NODE_IDENTIFIER, null, false))
        .thenReturn(approvalInstances);

    Response response = approvalsApiImpl.getApprovalInstancesByExecutionId(ORG_IDENTIFIER, PROJ_IDENTIFIER,
        EXECUTION_IDENTIFIER, ACCOUNT_ID, APPROVAL_STATUS, APPROVAL_TYPE, NODE_IDENTIFIER, null);
    List<ApprovalInstanceResponseBody> approvalInstanceResponseBodyList =
        (List<ApprovalInstanceResponseBody>) response.getEntity();
    assertThat(approvalInstanceResponseBodyList.size()).isEqualTo(4);
    ApprovalInstanceResponseBody approvalInstanceResponseBody = approvalInstanceResponseBodyList.get(0);

    assertThat(approvalInstanceResponseBody.getId()).isEqualTo(RESOURCE_IDENTIFIER);
    assertThat(approvalInstanceResponseBody.getStatus()).isEqualTo(ApprovalInstanceResponseBody.StatusEnum.WAITING);
    assertThat(approvalInstanceResponseBody.getDeadline()).isEqualTo(DEADLINE);
    assertThat(approvalInstanceResponseBody.getDetails()).isEqualTo(null);
    assertThat(approvalInstanceResponseBody.getCreated()).isEqualTo(CREATED_AT);
    assertThat(approvalInstanceResponseBody.getUpdated()).isEqualTo(UPDATED_AT);
    assertThat(approvalInstanceResponseBody.getErrorMessage()).isEqualTo(ERROR_MESSAGE);
    assertThat(approvalInstanceResponseBody.getType()).isNotNull();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetApprovalInstancesByExecutionIdWithCallbackId() {
    List<ApprovalInstanceResponseDTO> approvalInstances = new ArrayList<>();
    approvalInstances.add(buildApprovalInstance(ApprovalType.HARNESS_APPROVAL, ApprovalStatus.WAITING));

    when(approvalResourceService.getApprovalInstancesByExecutionId(EXECUTION_IDENTIFIER, ApprovalStatus.WAITING,
             ApprovalType.HARNESS_APPROVAL, NODE_IDENTIFIER, CALLBACK_IDENTIFIER, false))
        .thenReturn(approvalInstances);

    Response response = approvalsApiImpl.getApprovalInstancesByExecutionId(ORG_IDENTIFIER, PROJ_IDENTIFIER,
        EXECUTION_IDENTIFIER, ACCOUNT_ID, APPROVAL_STATUS, APPROVAL_TYPE, NODE_IDENTIFIER, CALLBACK_IDENTIFIER);
    List<ApprovalInstanceResponseBody> approvalInstanceResponseBodyList =
        (List<ApprovalInstanceResponseBody>) response.getEntity();
    assertThat(approvalInstanceResponseBodyList.size()).isEqualTo(1);
    ApprovalInstanceResponseBody approvalInstanceResponseBody = approvalInstanceResponseBodyList.get(0);

    assertThat(approvalInstanceResponseBody.getId()).isEqualTo(RESOURCE_IDENTIFIER);
    assertThat(approvalInstanceResponseBody.getStatus()).isEqualTo(ApprovalInstanceResponseBody.StatusEnum.WAITING);
    assertThat(approvalInstanceResponseBody.getDeadline()).isEqualTo(DEADLINE);
    assertThat(approvalInstanceResponseBody.getDetails()).isEqualTo(null);
    assertThat(approvalInstanceResponseBody.getCreated()).isEqualTo(CREATED_AT);
    assertThat(approvalInstanceResponseBody.getUpdated()).isEqualTo(UPDATED_AT);
    assertThat(approvalInstanceResponseBody.getErrorMessage()).isEqualTo(ERROR_MESSAGE);
    assertThat(approvalInstanceResponseBody.getType()).isNotNull();
  }

  public ApprovalInstanceResponseDTO buildApprovalInstance(ApprovalType approvalType, ApprovalStatus approvalStatus) {
    return ApprovalInstanceResponseDTO.builder()
        .id(RESOURCE_IDENTIFIER)
        .type(approvalType)
        .status(approvalStatus)
        .createdAt(CREATED_AT)
        .lastModifiedAt(UPDATED_AT)
        .errorMessage(ERROR_MESSAGE)
        .deadline(DEADLINE)
        .build();
  }
}
