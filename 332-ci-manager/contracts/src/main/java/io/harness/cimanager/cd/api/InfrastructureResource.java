/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.cd.api;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.cd.api.beans.GitCreateRequestDTO;
import io.harness.beans.cd.api.beans.GitFindRequestDTO;
import io.harness.beans.cd.api.beans.GitUpdateRequestDTO;
import io.harness.beans.cd.api.beans.InfrastructureRequestDTO;
import io.harness.beans.cd.api.beans.InfrastructureResponse;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
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

@OwnedBy(HarnessTeam.CI)
@Api("/v0/infrastructure")
@Path("/v0/infrastructure")
@Produces({"application/json"})
@Consumes({"application/json"})
@Tag(name = "Infrastructure Resource", description = "Contains APIs related to infrastructure.")
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
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public interface InfrastructureResource {
  int MAX_LIMIT = 1000;

  @POST
  @ApiOperation(value = "Create a Infrastructure", nickname = "createInfrastructure")
  @Operation(operationId = "createInfrastructure", summary = "Create a Infrastructure",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Infrastructure")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<InfrastructureResponse>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Infrastructure to be created",
          content =
          {
            @Content(examples = @ExampleObject(name = "Create", summary = "Sample Infrastructure create payload",
                         description = "Sample Infrastructure payload"))
          }) @Valid InfrastructureRequestDTO infrastructureRequestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch, Git Repository to be created",
          hidden = true) @BeanParam GitCreateRequestDTO gitDetails);

  @GET
  @Path("{infraIdentifier}")
  @ApiOperation(value = "Gets a Infrastructure by identifier", nickname = "getInfrastructure")
  @Operation(operationId = "getInfrastructure", summary = "Gets a Infrastructure by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "The saved Infrastructure")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<InfrastructureResponse>
  get(@Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @PathParam(
          "infraIdentifier") @ResourceIdentifier String infraIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENVIRONMENT_KEY, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String envIdentifier,
      @Parameter(description = "This contains details of Git Entity like Git Branch info",
          hidden = true) @BeanParam GitFindRequestDTO gitDetails);

  @PUT
  @ApiOperation(value = "Update a infrastructure by identifier", nickname = "updateInfrastructure")
  @Operation(operationId = "updateInfrastructure", summary = "Update a Infrastructure by identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the updated Infrastructure") })
  @Timed
  @ResponseMetered
  ResponseDTO<InfrastructureResponse>
  update(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Infrastructure to be updated",
          content =
          {
            @Content(examples = @ExampleObject(name = "Create", summary = "Sample Infrastructure update payload",
                         description = "Sample Infrastructure payload"))
          }) @Valid InfrastructureRequestDTO infrastructureRequestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch information to be updated")
      @BeanParam GitUpdateRequestDTO gitDetails);

  @DELETE
  @Path("{infraIdentifier}")
  @ApiOperation(value = "Delete a infrastructure by identifier", nickname = "deleteInfrastructure")
  @Operation(operationId = "deleteInfrastructure", summary = "Delete a Infrastructure by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns true if the Infrastructure is deleted")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<Boolean>
  delete(@Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @PathParam(
             "infraIdentifier") @ResourceIdentifier String infraIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String envIdentifier);

  @GET
  @ApiOperation(value = "Gets Infrastructure list", nickname = "getInfrastructureList")
  @Operation(operationId = "getInfrastructureList", summary = "Gets Infrastructure list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Infrastructures for a Environment")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<PageResponse<InfrastructureResponse>>
  listInfrastructures(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String envIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(
          description =
              "Specifies the sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort,
      @Parameter(description = "Specify true if all accessible Environments are to be included") @QueryParam(
          "includeChildrenScope") @DefaultValue("false") boolean includeChildrenScope,
      @Parameter(description = "Specify true if infrastructures with runtime access to be fetched") @QueryParam(
          "runtimeAccess") @DefaultValue("false") boolean access);
}
