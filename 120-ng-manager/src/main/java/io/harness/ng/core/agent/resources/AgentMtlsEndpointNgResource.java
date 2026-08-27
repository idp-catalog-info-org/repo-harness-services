/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.agent.resources;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.agent.beans.AgentMtlsEndpointDetails;
import io.harness.agent.beans.AgentMtlsEndpointRequest;
import io.harness.agent.utils.AgentMtlsApiConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.rest.RestResponse;

import software.wings.security.annotations.Scope;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import io.dropwizard.jersey.PATCH;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

@Api(AgentMtlsApiConstants.API_ROOT)
@Path(AgentMtlsApiConstants.API_ROOT)
@Produces("application/json")
@Consumes("application/json")
@Scope(DELEGATE)
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@Tag(name = "Agent mTLS Endpoint Management", description = "Contains APIs related to Agent mTLS Endpoint management.")
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
public class AgentMtlsEndpointNgResource {
  @POST
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  @ResponseMetered
  //  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  @ApiOperation(nickname = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_CREATE_NAME,
      value = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_CREATE_DESC)
  @Operation(operationId = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_CREATE_NAME,
      summary = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_CREATE_DESC,
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully created mTLS endpoint", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = AgentMtlsEndpointDetails.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = AgentMtlsEndpointDetails.class))
        })
      })
  public RestResponse<AgentMtlsEndpointDetails>
  createEndpointForAccount(
      @Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE)
      @ApiParam(required = true, value = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier,
      @RequestBody(required = true, description = AgentMtlsApiConstants.API_PARAM_CREATE_REQUEST_DESC) @ApiParam(
          required = true, value = AgentMtlsApiConstants.API_PARAM_CREATE_REQUEST_DESC)
      @NotNull AgentMtlsEndpointRequest endpointRequest) {
    return new RestResponse<>(AgentMtlsEndpointDetails.builder().build());
  }

  @PUT
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  @ResponseMetered
  //  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  @ApiOperation(nickname = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_UPDATE_NAME,
      value = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_UPDATE_DESC)
  @Operation(operationId = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_UPDATE_NAME,
      summary = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_UPDATE_DESC,
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully updated mTLS endpoint", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = AgentMtlsEndpointDetails.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = AgentMtlsEndpointDetails.class))
        })
      })
  public RestResponse<AgentMtlsEndpointDetails>
  updateEndpointForAccount(
      @Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE)
      @ApiParam(required = true, value = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier,
      @RequestBody(required = true, description = AgentMtlsApiConstants.API_PARAM_UPDATE_REQUEST_DESC) @ApiParam(
          required = true, value = AgentMtlsApiConstants.API_PARAM_UPDATE_REQUEST_DESC)
      @NotNull AgentMtlsEndpointRequest endpointRequest) {
    return new RestResponse<>(AgentMtlsEndpointDetails.builder().build());
  }

  @PATCH
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  @ResponseMetered
  //  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  @ApiOperation(nickname = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_PATCH_NAME,
      value = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_PATCH_DESC)
  @Operation(operationId = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_PATCH_NAME,
      summary = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_PATCH_DESC,
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully patched mTLS endpoint", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = AgentMtlsEndpointDetails.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = AgentMtlsEndpointDetails.class))
        })
      })
  public RestResponse<AgentMtlsEndpointDetails>
  patchEndpointForAccount(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE)
                          @ApiParam(required = true, value = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                              NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier,
      @RequestBody(required = true, description = AgentMtlsApiConstants.API_PARAM_PATCH_REQUEST_DESC) @ApiParam(
          required = true,
          value = AgentMtlsApiConstants.API_PARAM_PATCH_REQUEST_DESC) @NotNull AgentMtlsEndpointRequest patchRequest) {
    return new RestResponse<>(AgentMtlsEndpointDetails.builder().build());
  }

  @DELETE
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  @ResponseMetered
  //  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  @ApiOperation(nickname = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_DELETE_NAME,
      value = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_DELETE_DESC)
  @Operation(operationId = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_DELETE_NAME,
      summary = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_DELETE_DESC,
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully deleted mTLS endpoint", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = Boolean.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = Boolean.class))
        })
      })
  public RestResponse<Boolean>
  deleteEndpointForAccount(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE)
      @ApiParam(required = true, value = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier) {
    return new RestResponse<>(true);
  }

  @GET
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  @ResponseMetered
  //  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  @ApiOperation(nickname = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_GET_NAME,
      value = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_GET_DESC)
  @Operation(operationId = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_GET_NAME,
      summary = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_GET_DESC,
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully retrieved mTLS endpoint details", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = AgentMtlsEndpointDetails.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = AgentMtlsEndpointDetails.class))
        })
      })
  public RestResponse<AgentMtlsEndpointDetails>
  getEndpointForAccount(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE)
      @ApiParam(required = true, value = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier) {
    return new RestResponse<>(AgentMtlsEndpointDetails.builder().build());
  }

  /**
   * Checks whether the provided domain prefix is available.
   *
   * @param accountIdentifier required to be compliant with new internal OpenAPI specifications.
   * @param domainPrefix The domain prefix to check.
   * @return True if and only if there is no existing mTLS endpoint that uses the provided domain prefix.
   */
  @GET
  @Path(AgentMtlsApiConstants.API_PATH_CHECK_AVAILABILITY)
  @Timed
  @ExceptionMetered
  @ResponseMetered
  //  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  @ApiOperation(nickname = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_CHECK_AVAILABILITY_NAME,
      value = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_CHECK_AVAILABILITY_DESC)
  @Operation(operationId = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_CHECK_AVAILABILITY_NAME,
      summary = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_CHECK_AVAILABILITY_DESC,
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully checked domain prefix availability", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = Boolean.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = Boolean.class))
        })
      })
  public RestResponse<Boolean>
  isDomainPrefixAvailable(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE)
                          @ApiParam(required = true, value = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                              NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier,
      @Parameter(required = true, description = AgentMtlsApiConstants.API_PARAM_DOMAIN_PREFIX_DESC)
      @ApiParam(required = true, value = AgentMtlsApiConstants.API_PARAM_DOMAIN_PREFIX_DESC) @QueryParam(
          AgentMtlsApiConstants.API_PARAM_DOMAIN_PREFIX_NAME) @NotNull String domainPrefix) {
    return new RestResponse<>(true);
  }
}
