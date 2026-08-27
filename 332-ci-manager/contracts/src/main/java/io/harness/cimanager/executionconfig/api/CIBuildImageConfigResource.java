/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ci.pipeline.executions.beans;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.commons.exceptions.AccessDeniedErrorDTO;
import io.harness.annotations.dev.HarnessTeam;
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
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@OwnedBy(HarnessTeam.CI)
@Api("admin/build-image-config")
@Path("admin/build-image-config")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "CI Build Image Config", description = "This contains APIs for Build Image Config")
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

public interface CIBuildImageConfigResource {
  @POST
  @Path("/")
  @ApiOperation(value = "Update Build Image Config", nickname = "updateBuildImageConfig")
  @io.swagger.v3.oas.annotations.
  Operation(operationId = "updateBuildImageConfig", summary = "Update Build Image Config for CI builds",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "True or False") })
  @Hidden
  RestResponse<Boolean>
  updateBuildImageConfig(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @DefaultValue("global") String accountIdentifier,
      @RequestBody(
          required = true, description = "Build Image Config") @NotNull @Valid BuildImageConfigDTO buildImageConfigDTO);

  @GET
  @Path("/")
  @ApiOperation(value = "Get Build Image Config", nickname = "getBuildImageConfig")
  @io.swagger.v3.oas.annotations.
  Operation(operationId = "getBuildImageConfig", summary = "Get Build Image Config for CI builds",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Build Image Config") })
  @Hidden
  RestResponse<BuildImageConfigDTO>
  getBuildImageConfig(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @DefaultValue("global") String accountIdentifier);

  @DELETE
  @Path("/")
  @ApiOperation(value = "Delete Build Image Config", nickname = "deleteBuildImageConfig")
  @io.swagger.v3.oas.annotations.Operation(operationId = "deleteBuildImageConfig",
      summary = "Delete Build Image Config for an account to revert to default",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "True or False") })
  @Hidden
  RestResponse<Boolean>
  deleteBuildImageConfig(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);
}
