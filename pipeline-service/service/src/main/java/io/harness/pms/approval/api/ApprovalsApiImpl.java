/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.api;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.util.Objects.isNull;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.approval.ApprovalResourceService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.spec.server.pipeline.v1.ApprovalsApi;
import io.harness.spec.server.pipeline.v1.model.ApprovalInstanceResponseBody;
import io.harness.spec.server.pipeline.v1.model.HarnessApprovalActivityRequestBody;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.telemetry.helpers.ApprovalApiInstrumentationHelper;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class ApprovalsApiImpl implements ApprovalsApi {
  private final ApprovalResourceService approvalResourceService;
  private final PMSExecutionService pmsExecutionService;
  @Inject ApprovalApiInstrumentationHelper instrumentationHelper;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response addHarnessApprovalActivityByPipelineExecutionId(@OrgIdentifier String org,
      @ProjectIdentifier String project, String executionId, @Valid HarnessApprovalActivityRequestBody body,
      @AccountIdentifier String harnessAccount, String callbackId) {
    if (isNull(harnessAccount)) {
      // harnessAccount is required to passed when using bearer token for rbac check
      throw new InvalidRequestException("harnessAccount param value is required");
    }
    try {
      pmsExecutionService.getPipelineExecutionSummaryEntity(harnessAccount, executionId, false);
    } catch (EntityNotFoundException ex) {
      log.warn("Invalid execution id provided", ex);
      instrumentationHelper.sendApprovalApiEvent(harnessAccount, org, project, executionId,
          ApprovalApiInstrumentationHelper.FAILURE, ApprovalApiInstrumentationHelper.EXECUTION_ID_NOT_FOUND);
      throw new InvalidRequestException(String.format("execution_id param value provided doesn't belong to Account: "
              + "%s, Org: %s, Project: %s or the pipeline has been deleted",
          harnessAccount, org, project));
    } catch (Exception ex) {
      log.warn("An error occurred validating execution_id param", ex);
      throw new InvalidRequestException("An unexpected error occurred while validating execution_id param");
    }

    ApprovalInstanceResponseDTO approvalInstanceResponseDTO =
        approvalResourceService.addHarnessApprovalActivityByPlanExecutionId(harnessAccount, org, project, executionId,
            ApprovalsAPIUtils.toHarnessApprovalActivityRequestDTO(body), callbackId);

    instrumentationHelper.sendApprovalApiEvent(
        harnessAccount, org, project, executionId, ApprovalApiInstrumentationHelper.SUCCESS, null);
    return Response.ok().entity(ApprovalsAPIUtils.toApprovalInstanceResponseBody(approvalInstanceResponseDTO)).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getApprovalInstancesByExecutionId(@OrgIdentifier String org, @ProjectIdentifier String project,
      String executionId, @AccountIdentifier String harnessAccount, String approvalStatus, String approvalType,
      String nodeExecutionId, String callbackId) {
    if (isNull(harnessAccount)) {
      // harnessAccount is required to passed when using bearer token for rbac check
      throw new InvalidRequestException("harnessAccount param value is required");
    }
    log.info(String.format("Retrieving approval instances for execution-id %s in project %s, org %s, account %s",
        executionId, project, org, harnessAccount));
    ApprovalType approvalTypeEnum = null;
    ApprovalStatus approvalStatusEnum = null;
    if (!isNull(approvalType)) {
      approvalTypeEnum = ApprovalType.fromName(approvalType);
      if (isNull(approvalTypeEnum)) {
        throw new InvalidRequestException(
            String.format("approval_type param value should be one of %s", Arrays.toString(ApprovalType.values())));
      }
    }

    if (!isNull(approvalStatus)) {
      try {
        approvalStatusEnum = ApprovalStatus.valueOf(approvalStatus);
      } catch (IllegalArgumentException ex) {
        log.warn(ExceptionUtils.getMessage(ex));
        throw new InvalidRequestException(
            String.format("approval_status param value should be one of %s", Arrays.toString(ApprovalStatus.values())));
      }
    }

    PlanExecutionMetadata planExecutionMetadata;
    try {
      planExecutionMetadata = planExecutionMetadataService.getWithFieldsIncludedFromSecondary(
          harnessAccount, executionId, Set.of(PlanExecutionMetadataKeys.retryExecutionInfo));
    } catch (EntityNotFoundException ex) {
      log.warn("Invalid execution id provided", ex);
      throw new InvalidRequestException(String.format("execution_id param value provided doesn't belong to Account: "
              + "%s, Org: %s, Project: %s or the pipeline has been deleted",
          harnessAccount, org, project));
    } catch (Exception ex) {
      log.warn("An error occurred validating execution_id param", ex);
      throw new InvalidRequestException("An unexpected error occurred while validating execution_id param");
    }

    boolean isRetry = false;
    if (planExecutionMetadata.getRetryExecutionInfo() != null) {
      isRetry = planExecutionMetadata.getRetryExecutionInfo().getIsRetry();
    }

    List<ApprovalInstanceResponseDTO> approvalInstances = approvalResourceService.getApprovalInstancesByExecutionId(
        executionId, approvalStatusEnum, approvalTypeEnum, nodeExecutionId, callbackId, isRetry);

    List<ApprovalInstanceResponseBody> approvalInstanceResponseBodyList =
        approvalInstances.stream().map(ApprovalsAPIUtils::toApprovalInstanceResponseBody).collect(Collectors.toList());

    return Response.ok().entity(approvalInstanceResponseBodyList).build();
  }
}
