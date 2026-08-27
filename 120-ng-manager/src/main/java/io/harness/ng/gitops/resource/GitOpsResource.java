/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.resource;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitops.models.Agent;
import io.harness.gitops.models.AgentExpressionRequest;
import io.harness.gitops.models.AgentExpressionResponse;
import io.harness.gitops.models.ApplicationSetList;
import io.harness.gitops.models.ApplicationSetQuery;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.ng.gitops.service.GitOpsExpressionService;
import io.harness.security.annotations.GitOpsServiceAuth;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
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
import java.io.IOException;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@NextGenManagerAuth
@Api("/gitops")
@Path("/gitops")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "GitOps", description = "APIs related to GitOps resources")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
@OwnedBy(GITOPS)
@Slf4j
public class GitOpsResource {
  @Inject private GitopsResourceClient gitopsResourceClient;
  @Inject private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject private AccessControlClient accessControlClient;
  @Inject private ScopeInfoService scopeInfoService;
  @Inject private GitOpsExpressionService gitOpsExpressionService;

  /**
   * Lists application sets based on the provided query criteria.
   *
   * @param query Query parameters to filter application sets
   * @return List of application sets matching the query
   */
  @POST
  @Path("applicationsets")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Lists application sets", nickname = "listApplicationSets")
  @Operation(operationId = "listApplicationSets", summary = "List application sets based on provided criteria",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "List of application sets") })
  @Hidden
  public ResponseDTO<ApplicationSetList>
  listApplicationSets(@Valid ApplicationSetQuery query) {
    try {
      Preconditions.checkArgument(query != null, "Query cannot be null");
      Preconditions.checkArgument(
          EmptyPredicate.isNotEmpty(query.getAccountIdentifier()), "Account ID cannot be empty");
      orgAndProjectValidationHelper.validateOrgAndProject(
          query.getAccountIdentifier(), query.getOrgIdentifier(), query.getProjectIdentifier());

      // Call GitOps service to list application sets
      Response<ApplicationSetList> response = gitopsResourceClient.listApplicationSets(query).execute();
      if (response.isSuccessful() && response.body() != null) {
        return ResponseDTO.newResponse(response.body());
      } else {
        throw new InvalidRequestException("Failed to list application sets due to an internal error");
      }
    } catch (IOException e) {
      throw new InvalidRequestException("Failed to list application sets due to an internal error", e);
    }
  }

  /**
   * Lists GitOps agents based on the provided query parameters.
   *
   * @param accountIdentifier Account identifier
   * @param orgIdentifier Organization identifier (optional)
   * @param projectIdentifier Project identifier (optional)
   * @param page Page number (0-indexed)
   * @param size Number of items per page
   * @param searchTerm Search term to filter agents (optional)
   * @return List of GitOps agents
   */
  @GET
  @Path("agents")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Lists GitOps agents", nickname = "listGitOpsAgents")
  @Operation(operationId = "listGitOpsAgents", summary = "List GitOps agents based on provided criteria",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "List of GitOps agents") })
  @Hidden
  public ResponseDTO<PageResponse<Agent>>
  listGitOpsAgents(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGResourceFilterConstants.SIZE_KEY) @DefaultValue("10") int size,
      @Parameter(description = "Search term to filter agents") @QueryParam("searchTerm") String searchTerm) {
    try {
      Preconditions.checkArgument(EmptyPredicate.isNotEmpty(accountIdentifier), "Account ID cannot be empty");
      if (orgIdentifier != null) {
        orgAndProjectValidationHelper.validateOrgAndProject(accountIdentifier, orgIdentifier, projectIdentifier);
      }

      // Call GitOps service to list agents
      Response<PageResponse<Agent>> response =
          gitopsResourceClient.listAgents(accountIdentifier, orgIdentifier, projectIdentifier, page, size, searchTerm)
              .execute();
      if (response.isSuccessful() && response.body() != null) {
        return ResponseDTO.newResponse(response.body());
      } else {
        throw new InvalidRequestException("Failed to list GitOps agents due to an internal error");
      }
    } catch (IOException e) {
      throw new InvalidRequestException("Failed to list GitOps agents due to an internal error", e);
    }
  }

  @POST
  @Path("expressions/render")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Render app expressions", nickname = "renderAppExpressions", hidden = true)
  @Operation(operationId = "renderExpressions", summary = "Render GitOps app manifest expressions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the rendered expressions or encryption details")
      })
  @Hidden
  @GitOpsServiceAuth
  public ResponseDTO<AgentExpressionResponse>
  renderAppExpressions(@Valid AgentExpressionRequest request) {
    return ResponseDTO.newResponse(gitOpsExpressionService.getExpression(request));
  }
}