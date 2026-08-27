/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.delegate.resources;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.delegate.beans.DelegateGroupDTO;
import io.harness.delegate.beans.DelegateGroupTags;
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
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

@Api("/delegate-group-tags")
@Path("/delegate-group-tags")
@Produces("application/json")
@Consumes({"application/json"})
@OwnedBy(HarnessTeam.DEL)
@Tag(name = "Delegate Group Tags Resource", description = "Contains APIs related to Delegate Group Tags management")
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

public class DelegateGroupTagsResource {
  public DelegateGroupTagsResource() {}

  @GET
  @ApiOperation(value = "List tags attached with Delegate group", nickname = "listTagsForDelegateGroup")
  @Timed
  @Path("{groupIdentifier}")
  @ExceptionMetered
  @ResponseMetered
  @Operation(operationId = "listTagsForDelegateGroup", summary = "Retrieves list of tags attached with Delegate group",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Successfully retrieved delegate group details with tags", content = {
              @Content(mediaType = "application/json", schema = @Schema(implementation = DelegateGroupDTO.class))
              , @Content(mediaType = "application/yaml", schema = @Schema(implementation = DelegateGroupDTO.class))
            })
      })
  public RestResponse<DelegateGroupDTO>
  listTagsForDelegateGroup(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Group Identifier") @PathParam(NGCommonEntityConstants.GROUP_IDENTIFIER_KEY)
      @NotEmpty String groupIdentifier, @Context ScopeInfo scopeInfo) {
    return new RestResponse<>(DelegateGroupDTO.builder().build());
  }

  @POST
  @ApiOperation(value = "Add tags to the Delegate group", nickname = "addTagsToDelegateGroup")
  @Timed
  @Path("{groupIdentifier}")
  @ExceptionMetered
  @ResponseMetered
  @Operation(operationId = "addTagsToDelegateGroup", summary = "Add given list of tags to the Delegate group",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully added tags to delegate group", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = DelegateGroupDTO.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = DelegateGroupDTO.class))
        })
      })
  public RestResponse<DelegateGroupDTO>
  addSelectorsToDelegateGroup(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                  NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Group Identifier") @PathParam(
          NGCommonEntityConstants.GROUP_IDENTIFIER_KEY) @NotEmpty String groupIdentifier,
      @RequestBody(required = true, description = "Set of tags") DelegateGroupTags tags, @Context ScopeInfo scopeInfo) {
    return new RestResponse<>(DelegateGroupDTO.builder().build());
  }

  @PUT
  @ApiOperation(value = "Update tags of the Delegate group", nickname = "updateTagsOfDelegateGroup")
  @Timed
  @Path("{groupIdentifier}")
  @ExceptionMetered
  @ResponseMetered
  @Operation(operationId = "updateTagsOfDelegateGroup",
      summary = "Clears all existing tags with delegate group and attach given set of tags to delegate group.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully updated delegate group tags", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = DelegateGroupDTO.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = DelegateGroupDTO.class))
        })
      })
  public RestResponse<DelegateGroupDTO>
  updateSelectorsForDelegateGroup(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                      NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Group Identifier") @PathParam(
          NGCommonEntityConstants.GROUP_IDENTIFIER_KEY) @NotEmpty String groupIdentifier,
      @RequestBody(required = true, description = "Set of tags") DelegateGroupTags tags, @Context ScopeInfo scopeInfo) {
    return new RestResponse<>(DelegateGroupDTO.builder().build());
  }

  @DELETE
  @ApiOperation(value = "Deletes all tags from the Delegate group", nickname = "deleteTagsFromDelegateGroup")
  @Timed
  @Path("{groupIdentifier}")
  @ExceptionMetered
  @ResponseMetered
  @Operation(operationId = "deleteTagsFromDelegateGroup", summary = "Deletes all tags from the Delegate group",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully deleted all tags from delegate group", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = DelegateGroupDTO.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = DelegateGroupDTO.class))
        })
      })
  public RestResponse<DelegateGroupDTO>
  deleteTagsFromDelegateGroup(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                  NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Group Identifier") @PathParam(NGCommonEntityConstants.GROUP_IDENTIFIER_KEY)
      @NotEmpty String groupIdentifier, @Context ScopeInfo scopeInfo) {
    return new RestResponse<>(DelegateGroupDTO.builder().build());
  }

  @POST
  @ApiOperation(
      value = "List delegate groups that are having mentioned tags.", nickname = "listDelegateGroupsUsingTags")
  @Timed
  @Path("/delegate-groups")
  @ExceptionMetered
  @ResponseMetered
  @Operation(operationId = "listDelegateGroupsUsingTags",
      summary = "List delegate groups that are having mentioned tags.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Successfully retrieved list of delegate groups with specified tags", content = {
              @Content(mediaType = "application/json", schema = @Schema(implementation = DelegateGroupDTO.class))
              , @Content(mediaType = "application/yaml", schema = @Schema(implementation = DelegateGroupDTO.class))
            })
      })
  public RestResponse<List<DelegateGroupDTO>>
  listDelegateGroupsHavingTags(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                   NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(required = true, description = "Set of tags") DelegateGroupTags tags, @Context ScopeInfo scopeInfo) {
    return new RestResponse<>(List.of());
  }
}
