/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.filter.FilterConstants.QUEUED_PIPELINE_FILTER;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineBulkAbortRequestDTO;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineBulkAbortResponseDTO;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineFilterDTO;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineListResponse;
import io.harness.pms.plan.execution.service.QueuedPipelineService;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Api("pipelines/queue-management")
@Path("pipelines/queue-management")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(
    name = "Queued Pipeline Executions", description = "APIs for observing queued pipeline executions at account level")
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
@PipelineServiceAuth
@Slf4j
public class QueuedPipelineResource {
  @Inject private final QueuedPipelineService queuedPipelineService;

  @POST
  @Path("/queued-pipelines")
  @ApiOperation(value = "List queued and/or running pipeline executions", nickname = "listQueuedPipelines")
  @Operation(operationId = "listQueuedPipelines",
      description =
          "Lists queued and/or running pipeline executions at account level. Queued rows carry a global queue "
          + "position; running rows do not. Use the 'mode' parameter to switch between QUEUED, RUNNING and BOTH.",
      summary = "List Queued/Running Pipelines",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline executions with metadata")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<QueuedPipelineListResponse>
  listQueuedPipelines(@Parameter(description = "Account Identifier", required = true) @NotNull @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = "Page index (0-based)") @QueryParam(NGCommonEntityConstants.PAGE) @DefaultValue(
          "0") int page,
      @Parameter(description = "Page size (max 100)") @QueryParam(NGCommonEntityConstants.SIZE) @DefaultValue(
          "20") int size,
      @Parameter(description = "Free-text search on pipeline name or identifier") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "Identifier of a saved filter of type '" + QUEUED_PIPELINE_FILTER
              + "' to apply. Cannot be used together with the request body filter properties.")
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      QueuedPipelineFilterDTO filterDTO) {
    return ResponseDTO.newResponse(
        queuedPipelineService.listQueuedPipelines(accountId, filterIdentifier, filterDTO, searchTerm, page, size));
  }

  @POST
  @Path("/bulk-abort")
  @ApiOperation(value = "Bulk abort queued pipeline executions", nickname = "bulkAbortQueuedPipelines")
  @Operation(operationId = "bulkAbortQueuedPipelines",
      description = "Aborts multiple queued pipeline executions at account level",
      summary = "Bulk Abort Queued Pipelines",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns per-execution abort results")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<QueuedPipelineBulkAbortResponseDTO>
  bulkAbortQueuedPipelines(@Parameter(description = "Account Identifier", required = true) @NotNull @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull QueuedPipelineBulkAbortRequestDTO request) {
    return ResponseDTO.newResponse(
        queuedPipelineService.bulkAbortQueuedPipelines(accountId, request.getPlanExecutionIds()));
  }
}
