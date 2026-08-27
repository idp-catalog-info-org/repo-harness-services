/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cimanager.traceableagenttoken.api;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.commons.exceptions.AccessDeniedErrorDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.rest.RestResponse;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@OwnedBy(CI)
@Api("admin/traceable/agent-tokens")
@Path("admin/traceable/agent-tokens")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "CI Agent Tokens", description = "APIs for managing per-account Traceable egress agent tokens")
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
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Unauthorized",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = AccessDeniedErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = AccessDeniedErrorDTO.class))
    })
public interface TraceableAgentTokenAdminResource {
  @POST
  @Path("/")
  @ApiOperation(value = "Create or update agent token for an account", nickname = "createOrUpdateTraceableAgentToken")
  @io.swagger.v3.oas.annotations.Operation(operationId = "createOrUpdateTraceableAgentToken",
      summary = "Create or update Traceable agent token for an account",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "True or False") })
  @Hidden
  RestResponse<Boolean>
  createOrUpdateTraceableAgentToken(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(
          required = true, description = "Agent token request") @NotNull @Valid TraceableAgentTokenRequestDTO request);

  @GET
  @Path("/")
  @ApiOperation(value = "Get agent token metadata for an account", nickname = "getTraceableAgentToken")
  @io.swagger.v3.oas.annotations.
  Operation(operationId = "getTraceableAgentToken", summary = "Get Traceable agent token metadata for an account",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Agent token metadata") })
  @Hidden
  RestResponse<TraceableAgentTokenResponseDTO>
  getTraceableAgentToken(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);

  @DELETE
  @Path("/")
  @ApiOperation(value = "Delete agent token for an account", nickname = "deleteTraceableAgentToken")
  @io.swagger.v3.oas.annotations.
  Operation(operationId = "deleteTraceableAgentToken", summary = "Delete Traceable agent token for an account",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "True if deleted") })
  @Hidden
  RestResponse<Boolean>
  deleteTraceableAgentToken(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);
}
