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

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.delegate.beans.DelegateProfileDetailsNg;
import io.harness.ng.core.api.DelegateProfileManagerNgService;
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
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotEmpty;

@Path("/delegate-profiles/ng")
@Api("delegate-profiles/ng")
@Produces("application/json")
@AuthRule(permissionType = LOGGED_IN)
//@Scope(DELEGATE_SCOPE)
// This NG specific, switching to NG access control. AuthRule to be removed also when NG access control is fully
// enabled.
@OwnedBy(HarnessTeam.DEL)
@Hidden
@Tag(name = "Delegate Configuration Management",
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
public class DelegateProfileNgResource {
  private final DelegateProfileManagerNgService delegateProfileManagerNgService;
  private final AccessControlClient accessControlClient;

  @Inject
  public DelegateProfileNgResource(
      DelegateProfileManagerNgService delegateProfileManagerNgService, AccessControlClient accessControlClient) {
    this.delegateProfileManagerNgService = delegateProfileManagerNgService;
    this.accessControlClient = accessControlClient;
  }

  @GET
  @ApiOperation(value = "Lists the Delegate Configurations (profiles)", nickname = "listDelegateProfilesNg")
  @Timed
  @ExceptionMetered
  @ResponseMetered
  @Hidden
  @Operation(operationId = "getDelegateConfigurationsForAccount",
      summary = "Lists Delegate Configuration for specified Account, Organization and Project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "A list of Delegate Configurations for the Account, Organization and Project")
      })
  public RestResponse<PageResponse<DelegateProfileDetailsNg>>
  list(@BeanParam PageRequest<DelegateProfileDetailsNg> pageRequest,
      @Parameter(description = "Account id") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_CONFIG_RESOURCE_TYPE, null), DELEGATE_CONFIG_VIEW_PERMISSION);

    return new RestResponse<>(delegateProfileManagerNgService.list(accountId, pageRequest, orgId, projectId));
  }
}
