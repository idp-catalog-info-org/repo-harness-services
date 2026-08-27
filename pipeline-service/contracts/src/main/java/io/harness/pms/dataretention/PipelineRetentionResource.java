/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.dataretention;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.rest.RestResponse;
import io.harness.retention.PipelineRetentionPeriodResponseDTO;
import io.harness.retention.PipelineUpdateRetentionPeriodResponseDTO;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Api("pipelines/retention")
@Path("pipelines/retention")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Pipeline data retention",
    description = "This contains APIs related to retention of pipeline execution data")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
public interface PipelineRetentionResource {
  @GET
  @Path("/{accountIdentifier}")
  @ApiOperation(value = "Get retention period for pipeline executions based on accountId",
      nickname = "getRetentionPeriodInMonths")
  @Timed
  @Operation(operationId = "getRetentionPeriodInMonths",
      description = "Returns the retention period for pipeline executions based on accountId",
      summary = "Get retention period for pipeline executions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the retention period for pipeline executions based on accountId")
      })
  RestResponse<Integer>
  getRetentionPeriodInMonths(@NotNull @PathParam("accountIdentifier") String accountId);

  @GET
  @Path("/migration-status")
  @Hidden
  @ApiOperation(value = "Get the retention migration status for an account", nickname = "getRetentionMigrationStatus")
  @Operation(operationId = "getRetentionMigrationStatus",
      description = "Get the retention migration status for an account",
      summary = "Get the retention migration status for an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the retention migration status for an account")
      })
  ResponseDTO<PipelineRetentionPeriodResponseDTO>
  getRetentionMigrationStatus(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier);

  @POST
  @Path("/update-retention")
  @Hidden
  @ApiOperation(value = "Update the retention period for an account", nickname = "updateRetentionPeriod")
  @Operation(operationId = "updateRetentionPeriod", description = "Update the retention period for an account",
      summary = "Update the retention period for an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Update the retention period for an account")
      })
  ResponseDTO<PipelineUpdateRetentionPeriodResponseDTO>
  updateRetentionPeriod(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.DATA_RETENTION_PERIOD, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.DATA_RETENTION_PERIOD_KEY) @NotNull DataRetentionPeriod dataRetentionPeriod);
}
