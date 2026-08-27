/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.spec.server.pipeline.v1.model.ApprovalInstanceResponseBody;
import io.harness.spec.server.pipeline.v1.model.ApprovalInstanceResponseBody.StatusEnum;
import io.harness.spec.server.pipeline.v1.model.ApprovalInstanceResponseBody.TypeEnum;
import io.harness.spec.server.pipeline.v1.model.ApproverInputDTO;
import io.harness.spec.server.pipeline.v1.model.HarnessApprovalActivityRequestBody;
import io.harness.spec.server.pipeline.v1.model.HarnessApprovalActivityRequestBody.ActionEnum;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.harness.beans.ApproverInput;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class ApprovalsAPIUtils {
  public HarnessApprovalActivityRequestDTO toHarnessApprovalActivityRequestDTO(
      HarnessApprovalActivityRequestBody harnessApprovalActivityRequestBody) {
    if (isNull(harnessApprovalActivityRequestBody)) {
      return null;
    }
    return HarnessApprovalActivityRequestDTO.builder()
        .action(toHarnessApprovalAction(harnessApprovalActivityRequestBody.getAction()))
        .comments(harnessApprovalActivityRequestBody.getComments())
        .approverInputs(toApproverInputs(harnessApprovalActivityRequestBody.getApproverInputs()))
        .build();
  }

  public ApprovalInstanceResponseBody toApprovalInstanceResponseBody(
      ApprovalInstanceResponseDTO approvalInstanceResponseDTO) {
    if (isNull(approvalInstanceResponseDTO)) {
      return null;
    }

    ApprovalInstanceResponseBody approvalInstanceResponseBody = new ApprovalInstanceResponseBody();
    approvalInstanceResponseBody.setId(approvalInstanceResponseDTO.getId());
    approvalInstanceResponseBody.setType(toTypeEnum(approvalInstanceResponseDTO.getType()));
    approvalInstanceResponseBody.setStatus(toStatusEnum(approvalInstanceResponseDTO.getStatus()));
    approvalInstanceResponseBody.setDeadline(approvalInstanceResponseDTO.getDeadline());
    approvalInstanceResponseBody.setDetails(approvalInstanceResponseDTO.getDetails());
    approvalInstanceResponseBody.setCreated(approvalInstanceResponseDTO.getCreatedAt());
    approvalInstanceResponseBody.setUpdated(approvalInstanceResponseDTO.getLastModifiedAt());
    approvalInstanceResponseBody.setErrorMessage(approvalInstanceResponseDTO.getErrorMessage());

    return approvalInstanceResponseBody;
  }

  public List<ApproverInput> toApproverInputs(List<ApproverInputDTO> approverInputDTOList) {
    if (approverInputDTOList == null) {
      return null;
    }
    return approverInputDTOList.stream().map(ApprovalsAPIUtils::toApproverInput).collect(Collectors.toList());
  }

  public ApproverInput toApproverInput(ApproverInputDTO approverInputDTO) {
    return ApproverInput.builder().name(approverInputDTO.getName()).value(approverInputDTO.getValue()).build();
  }

  public HarnessApprovalAction toHarnessApprovalAction(ActionEnum actionEnum) {
    if (isNull(actionEnum)) {
      throw new InvalidRequestException(
          String.format("action in request body should be one of %s", Arrays.toString(ActionEnum.values())));
    }
    switch (actionEnum) {
      case APPROVE:
        return HarnessApprovalAction.APPROVE;
      case REJECT:
        return HarnessApprovalAction.REJECT;
      default:
        return null;
    }
  }

  public TypeEnum toTypeEnum(ApprovalType approvalType) {
    switch (approvalType) {
      case HARNESS_APPROVAL:
        return TypeEnum.HARNESSAPPROVAL;
      case JIRA_APPROVAL:
        return TypeEnum.JIRAAPPROVAL;
      case SERVICENOW_APPROVAL:
        return TypeEnum.SERVICENOWAPPROVAL;
      case CUSTOM_APPROVAL:
        return TypeEnum.CUSTOMAPPROVAL;
      default:
        return null;
    }
  }

  public StatusEnum toStatusEnum(ApprovalStatus approvalStatus) {
    switch (approvalStatus) {
      case WAITING:
        return StatusEnum.WAITING;
      case APPROVED:
        return StatusEnum.APPROVED;
      case REJECTED:
        return StatusEnum.REJECTED;
      case FAILED:
        return StatusEnum.FAILED;
      case ABORTED:
        return StatusEnum.ABORTED;
      case EXPIRED:
        return StatusEnum.EXPIRED;
      default:
        return null;
    }
  }
}
