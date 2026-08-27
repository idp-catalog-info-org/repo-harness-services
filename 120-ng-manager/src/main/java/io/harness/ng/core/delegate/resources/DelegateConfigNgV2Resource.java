/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.delegate.resources;

import static io.harness.delegate.utils.RbacConstants.DELEGATE_CONFIG_RESOURCE_TYPE;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_CONFIG_VIEW_PERMISSION;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.delegate.beans.DelegateProfileDetailsNg;
import io.harness.delegate.filter.DelegateProfileFilterPropertiesDTO;
import io.harness.ng.core.api.DelegateProfileManagerNgService;
import io.harness.ng.core.delegate.client.DelegateNgManagerCgManagerClient;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.rest.RestResponse;

import software.wings.security.annotations.AuthRule;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

@Path("/v2")
@Api("/v2")
@Produces("application/json")
@AuthRule(permissionType = LOGGED_IN)
@OwnedBy(HarnessTeam.DEL)
@Hidden
@Tag(name = "Delegate Configuration Resource",
    description = "Contains APIs related to Delegate Configuration management")
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
@Deprecated
public class DelegateConfigNgV2Resource {
  private final DelegateProfileManagerNgService delegateProfileManagerNgService;
  private final AccessControlClient accessControlClient;
  private final DelegateNgManagerCgManagerClient delegateNgManagerCgManagerClient;

  @Inject
  public DelegateConfigNgV2Resource(DelegateProfileManagerNgService delegateProfileManagerNgService,
      AccessControlClient accessControlClient, DelegateNgManagerCgManagerClient delegateNgManagerCgManagerClient) {
    this.delegateProfileManagerNgService = delegateProfileManagerNgService;
    this.accessControlClient = accessControlClient;
    this.delegateNgManagerCgManagerClient = delegateNgManagerCgManagerClient;
  }

  @GET
  @ApiOperation(value = "Lists the Delegate Configurations", nickname = "listDelegateConfigsNgV2")
  @Timed
  @Path("/accounts/{accountId}/delegate-configs")
  @ExceptionMetered
  @ResponseMetered
  @Hidden
  @Operation(operationId = "getDelegateConfigurationsForAccountV2",
      summary = "Lists Delegate Configuration for specified account, org and project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "A list of Delegate Configurations for the account, org and project")
      })
  public RestResponse<PageResponse<DelegateProfileDetailsNg>>
  list(@BeanParam PageRequest<DelegateProfileDetailsNg> pageRequest,
      @Parameter(description = "Account id") @PathParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_CONFIG_RESOURCE_TYPE, null), DELEGATE_CONFIG_VIEW_PERMISSION);

    return new RestResponse<>(delegateProfileManagerNgService.list(accountId, pageRequest, orgId, projectId));
  }

  @POST
  @ApiOperation(value = "Lists the Delegate configs with filter", nickname = "listDelegateConfigsNgV2WithFilter")
  @Timed
  @Path("/accounts/{accountId}/delegate-configs/listV2")
  @ExceptionMetered
  @ResponseMetered
  @Hidden
  @Operation(operationId = "getDelegateConfigurationsWithFiltering",
      summary = "Lists Delegate Configuration for specified account, org and project and filter applied",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "A list of Delegate Configurations for the account, org and project and filter applied")
      })
  public RestResponse<PageResponse<DelegateProfileDetailsNg>>
  listV2(@Parameter(description = "Account id") @PathParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId,
      @Parameter(description = "Filter identifier") @QueryParam(
          NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      @Parameter(description = "Search term") @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @RequestBody(
          description =
              "Delegate Configuration filter properties: name, identifier, description, approvalRequired, list of selectors ")
      @Body DelegateProfileFilterPropertiesDTO delegateProfileFilterPropertiesDTO,
      @BeanParam PageRequest<DelegateProfileDetailsNg> pageRequest) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_CONFIG_RESOURCE_TYPE, null), DELEGATE_CONFIG_VIEW_PERMISSION);

    return new RestResponse<>(delegateProfileManagerNgService.listV2(
        accountId, orgId, projectId, filterIdentifier, searchTerm, delegateProfileFilterPropertiesDTO, pageRequest));
  }
}
