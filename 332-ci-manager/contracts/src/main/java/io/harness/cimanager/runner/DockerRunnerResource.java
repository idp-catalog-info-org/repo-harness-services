/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.runner;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.accesscontrol.commons.exceptions.AccessDeniedErrorDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.rest.RestResponse;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Api("/docker-runner")
@Path("/docker-runner")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Docker Runner Command", description = "This contains APIs for getting Docker Runner Command")
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
@OwnedBy(CI)
public interface DockerRunnerResource {
  @GET
  @ApiOperation(value = "get docker-runner command", nickname = "dockerRunnerCommand")
  @io.swagger.v3.oas.annotations.Operation(operationId = "dockerRunnerCommand", summary = "get docker-runner command",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "command") })
  RestResponse<String>
  get(@QueryParam("accountId") @NotEmpty String accountId, @QueryParam("os") String os, @QueryParam("arch") String arch)
      throws Exception;

  @GET
  @Path("/trustLevel")
  @ApiOperation(value = "get account trust level", nickname = "accountTrustLevel")
  @io.swagger.v3.oas.annotations.Operation(operationId = "accountTrustLevel", summary = "get account trust level",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "command") })
  RestResponse<Integer>
  getTrustLevel(@QueryParam("accountId") @NotEmpty String accountId) throws Exception;
}
