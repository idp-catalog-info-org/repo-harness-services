/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.delegate.resources;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateGroupListing;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.rest.RestResponse;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Api("delegate-token-ng")
@Path("/delegate-token-ng")
@Produces("application/json")
@Consumes({"application/json"})
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@Tag(name = "Delegate Token Resource", description = "Contains APIs related to Delegate Token management")
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

public class DelegateTokenNgResource {
  @POST
  @Timed
  @ExceptionMetered
  @ResponseMetered
  @ApiOperation(value = "Creates Delegate Token", nickname = "createDelegateToken")
  @Operation(operationId = "createDelegateToken", summary = "Creates Delegate Token.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully created delegate token", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = DelegateTokenDetails.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = DelegateTokenDetails.class))
        })
      })
  public RestResponse<DelegateTokenDetails>
  createDelegateToken(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Token name") @QueryParam("tokenName") @NotNull String tokenName,
      @Parameter(
          description = "Epoch time in milliseconds after which the token will be marked as revoked. There can be a "
              + "delay of upto one hour from the epoch value provided and actual revoking of the token.")
      @QueryParam("revokeAfter") Long revokeAfter) {
    return new RestResponse<>(DelegateTokenDetails.builder().build());
  }

  @PUT
  @Timed
  @ExceptionMetered
  @ResponseMetered
  @ApiOperation(value = "Revokes Delegate Token", nickname = "revokeCgDelegateToken")
  @Operation(operationId = "revokeCgDelegateToken", summary = "Revokes Delegate Token.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully revoked delegate token", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = DelegateTokenDetails.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = DelegateTokenDetails.class))
        })
      })
  public RestResponse<DelegateTokenDetails>
  revokeDelegateToken(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Token name") @QueryParam("tokenName") @NotNull String tokenName) {
    return new RestResponse<>(DelegateTokenDetails.builder().build());
  }

  @DELETE
  @Timed
  @ResponseMetered
  @ExceptionMetered
  @ApiOperation(value = "Deletes a revoked Delegate Token", nickname = "deleteNgDelegateToken")
  @Operation(operationId = "deleteNgDelegateToken", summary = "Deletes a revoked Delegate Token.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "204", description = "Successfully deleted delegate token, no content returned")
      })
  public Response
  deleteToken(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                  NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Token name") @QueryParam("tokenName") @NotNull String tokenName) {
    return Response.noContent().build();
  }

  @GET
  @Timed
  @ExceptionMetered
  @ResponseMetered
  @ApiOperation(value = "Get Delegate Tokens", nickname = "getCgDelegateTokens")
  @Operation(operationId = "getCgDelegateTokens",
      summary = "Retrieves Delegate Tokens by Account, Organization, Project and status.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully retrieved list of delegate tokens", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = DelegateTokenDetails.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = DelegateTokenDetails.class))
        })
      })
  public RestResponse<List<DelegateTokenDetails>>
  getDelegateTokens(@Parameter(description = "Name of Delegate Token (ACTIVE or REVOKED).") @QueryParam(
                        "name") String delegateTokenName,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Status of Delegate Token (ACTIVE or REVOKED). "
              + "If left empty both active and revoked tokens will be retrieved") @QueryParam("status")
      DelegateTokenStatus status) {
    return new RestResponse<>(List.of(DelegateTokenDetails.builder().build()));
  }

  @GET
  @Path("/delegate-groups")
  @Timed
  @ExceptionMetered
  @ResponseMetered
  @ApiOperation(value = "Get Delegate Groups", nickname = "getDelegateGroupsUsingToken")
  @Operation(operationId = "getDelegateGroupsUsingToken",
      summary = "Lists delegate groups that are using the specified delegate token.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Successfully retrieved list of delegate groups using the specified token", content = {
              @Content(mediaType = "application/json", schema = @Schema(implementation = DelegateGroupListing.class))
              , @Content(mediaType = "application/yaml", schema = @Schema(implementation = DelegateGroupListing.class))
            })
      })
  public RestResponse<DelegateGroupListing>
  list(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Token name") @QueryParam("delegateTokenName") String delegateTokenName) {
    return new RestResponse<>(DelegateGroupListing.builder().build());
  }
}