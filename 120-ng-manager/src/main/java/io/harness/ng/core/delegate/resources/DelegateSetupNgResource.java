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
import io.harness.delegate.beans.DelegateListResponse;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.delegate.beans.SupportedDelegateVersion;
import io.harness.delegate.beans.VersionOverridesResponse;
import io.harness.delegate.filter.DelegateFilterPropertiesDTO;
import io.harness.delegate.utilities.DelegateDeleteResponse;
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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Range;
import retrofit2.http.Body;

@Api("delegate-setup")
@Path("/delegate-setup")
@Consumes({"application/json"})
@Produces({"application/json"})
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@Tag(name = "Delegate Setup Resource", description = "Contains Delegate Setup APIs")
@ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })

public class DelegateSetupNgResource {
  public DelegateSetupNgResource() {}

  @POST
  @Timed
  @Path("generate-helm-values")
  @ExceptionMetered
  @ResponseMetered
  @ApiOperation(value = "Generate helm values yaml file", nickname = "generateNgHelmValuesYaml")
  @Operation(operationId = "generateNgHelmValuesYaml",
      summary = "Generates helm values yaml file from the data specified in request body (Delegate setup details).",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully generated helm values yaml file")
      })
  public Response
  generateNgHelmValuesYaml(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(
          required = true, description = "Delegate setup details, containing data to populate yaml file values.")
      DelegateSetupDetails delegateSetupDetails,
      @Context ScopeInfo scopeInfo) throws IOException {
    return Response.ok().build();
  }

  @GET
  @Timed
  @Path("delegate-terraform-module-file")
  @ResponseMetered
  @ExceptionMetered
  @ApiOperation(value = "Generate delegate terraform example module file", nickname = "generateTerraformModule")
  @Operation(operationId = "generateTerraformModule",
      summary = "Generates delegate terraform example module file from the account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully generated terraform module file")
      })
  public Response
  generateNgHelmValuesYaml(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Context ScopeInfo scopeInfo)
      throws IOException {
    return Response.ok().build();
  }

  @DELETE
  @Path("delegate/{delegateIdentifier}")
  @Timed
  @ResponseMetered
  @ExceptionMetered
  @ApiOperation(value = "Deletes delegate", nickname = "deleteDelegate")
  @Operation(operationId = "deleteDelegate", summary = "Deletes a Delegate by its identifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully deleted delegate", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = DelegateDeleteResponse.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = DelegateDeleteResponse.class))
        })
      })
  public RestResponse<DelegateDeleteResponse>
  deleteDelegateGroup(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.IDENTIFIER_PARAM_MESSAGE) @PathParam(
          NGCommonEntityConstants.DELEGATE_IDENTIFIER_KEY) @NotEmpty String delegateGroupIdentifier,
      @Context ScopeInfo scopeInfo) {
    return new RestResponse<>(new DelegateDeleteResponse(delegateGroupIdentifier));
  }

  @GET
  @Timed
  @Path("latest-supported-version")
  @ResponseMetered
  @ExceptionMetered
  @ApiOperation(value = "Gets the latest supported delegate version", nickname = "publishedDelegateVersion")
  @Operation(operationId = "publishedDelegateVersion",
      summary = "Gets the latest supported delegate version. The version has YY.MM.XXXXX format. You can use any "
          + "version lower than the returned results(upto 3 months old)",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Successfully retrieved the latest supported delegate version", content = {
              @Content(
                  mediaType = "application/json", schema = @Schema(implementation = SupportedDelegateVersion.class))
              ,
                  @Content(
                      mediaType = "application/yaml", schema = @Schema(implementation = SupportedDelegateVersion.class))
            })
      })
  public RestResponse<SupportedDelegateVersion>
  publishedDelegateVersion(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) throws IOException {
    return new RestResponse<>(SupportedDelegateVersion.builder().build());
  }

  @PUT
  @Path("/override-delegate-tag")
  @ApiOperation(value = "Overrides delegate image tag for account", nickname = "overrideDelegateImageTag")
  @Timed
  @ResponseMetered
  @ExceptionMetered
  @Operation(operationId = "overrideDelegateImageTag", summary = "Overrides delegate image tag for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully overridden delegate image tag", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = String.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = String.class))
        })
      })
  public RestResponse<String>
  setDelegateTagOverride(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(
          description = "if provided, will override the image for all delegates which have all the provided tags")
      @QueryParam("tags") final Set<String> tags,
      @NotEmpty @QueryParam("delegateTag") final String delegateTag,
      @Parameter(description = "If set to true, harness will override your custom tag when new delegate is released")
      @QueryParam("validTillNextRelease") @DefaultValue("false") final Boolean validTillNextRelease,
      @Parameter(description = "days after which harness will override your custom tag") @Range(max = 180)
      @QueryParam("validForDays") @DefaultValue("180") final int validForDays, @Context ScopeInfo scopeInfo) {
    return new RestResponse<>(delegateTag);
  }

  @GET
  @Timed
  @Path("override-delegate-tag")
  @ResponseMetered
  @ExceptionMetered
  @ApiOperation(value = "Lists all delegates overrides in NG", nickname = "listOverrideDelegateImageTag")
  @Operation(operationId = "listOverrideDelegateImageTag",
      summary = "Lists all delegates overrides in NG filtered by provided conditions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully retrieved list of delegate overrides", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = VersionOverridesResponse.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = VersionOverridesResponse.class))
        })
      })
  public RestResponse<VersionOverridesResponse>
  getDelegateTagOverrides(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                              NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Context ScopeInfo scopeInfo) {
    return new RestResponse<>(new VersionOverridesResponse(List.of()));
  }

  @DELETE
  @Path("delete-delegate-override")
  @ApiOperation(value = "Delete delegate image tag override", nickname = "deleteOverrideDelegateImageTag")
  @Timed
  @ResponseMetered
  @ExceptionMetered
  @Operation(operationId = "overrideDelegateImageTag", summary = "Delete delegate image tag override",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully deleted delegate image tag override")
      })
  public Response
  deleteDelegateTagOverride(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.TAGS_KEY) Set<String> tags, @Context ScopeInfo scopeInfo) {
    return Response.ok().build();
  }

  @POST
  @Timed
  @Path("listDelegates")
  @ResponseMetered
  @ExceptionMetered
  @ApiOperation(value = "Lists all delegates in NG", nickname = "listDelegates")
  @Operation(operationId = "listDelegates", summary = "Lists all delegates in NG filtered by provided conditions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully retrieved list of delegates", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = DelegateListResponse.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = DelegateListResponse.class))
        })
      })
  public RestResponse<List<DelegateListResponse>>
  listDelegates(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                    NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(
          description = "Filter delegates based on the scope (query param: all), if true will return delegates of "
              + "underlying orgs/projects and ignore the filters provided as part of the request body")
      @DefaultValue("false") @QueryParam("all") boolean all,
      @Body @RequestBody(description = "Details of the Delegate filter properties to be applied")
      DelegateFilterPropertiesDTO delegateFilterPropertiesDTO, @Context ScopeInfo scopeInfo) throws IOException {
    return new RestResponse<>(List.of());
  }
}
