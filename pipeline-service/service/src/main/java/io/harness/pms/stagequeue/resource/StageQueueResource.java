/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.stagequeue.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_TRANSACTION_QUEUE_MANAGE_PERMISSION;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_TRANSACTION_QUEUE_RESOURCE_TYPE;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_TRANSACTION_QUEUE_VIEW_PERMISSION;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.stagequeue.beans.StageQueueListResponse;
import io.harness.pms.stagequeue.beans.StageQueueStatus;
import io.harness.pms.stagequeue.beans.UpdatePriorityRequest;
import io.harness.pms.stagequeue.beans.UpdatePriorityResponse;
import io.harness.pms.stagequeue.service.StageQueueService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

/**
 * Customer-facing Stage Queue Visualization & Priority API. Hosted in pipeline-service. Reads
 * are served from pipeline-service's own collections joined with upstream RunnerTransaction state
 * fetched via gRPC; writes are forwarded to the upstream priority RPC.
 */
@OwnedBy(PIPELINE)
@Api("/v2/stages/queue")
@Path("/v2/stages/queue")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@PipelineServiceAuth
@Slf4j
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
public class StageQueueResource {
  @Inject private StageQueueService stageQueueService;
  @Inject private AccessControlClient accessControlClient;

  @GET
  @ApiOperation(value = "List queued and running stages within a scope", nickname = "listStageQueue")
  @Operation(operationId = "listStageQueue",
      summary = "List queued and running stages within an account/org/project scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Paginated list of stage queue rows")
      })
  public ResponseDTO<StageQueueListResponse>
  list(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Filter by queue status") @QueryParam("status") @DefaultValue(
          "ALL") StageQueueStatus status,
      @Parameter(description = "0-based page index") @QueryParam("page") @DefaultValue("0") int page,
      @Parameter(description = "Page size (1-100)") @QueryParam("limit") @DefaultValue("50") int limit) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_TRANSACTION_QUEUE_RESOURCE_TYPE, null), DELEGATE_TRANSACTION_QUEUE_VIEW_PERMISSION);
    Scope scope = Scope.of(accountId, orgId, projectId);
    StageQueueListResponse response = stageQueueService.list(scope, status, page, limit);
    return ResponseDTO.newResponse(response);
  }

  @PUT
  @Path("/priority")
  @ApiOperation(value = "Reprioritize queued stages", nickname = "updateStageQueuePriority")
  @Operation(operationId = "updateStageQueuePriority", summary = "Reprioritize queued stages (max 10 per call)",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Per-stage success/failure breakdown")
      })
  public ResponseDTO<UpdatePriorityResponse>
  updatePriority(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                 @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @RequestBody(required = true,
          description = "Stages to reprioritize and target priority. A maximum of 10 stages may be reprioritized per "
              + "call; requests above this limit are rejected.") @NotNull @Valid UpdatePriorityRequest request) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_TRANSACTION_QUEUE_RESOURCE_TYPE, null), DELEGATE_TRANSACTION_QUEUE_MANAGE_PERMISSION);
    Scope scope = Scope.of(accountId, orgId, projectId);
    UpdatePriorityResponse response =
        stageQueueService.updatePriority(scope, request.getStages(), request.getPriority());
    return ResponseDTO.newResponse(response);
  }
}
