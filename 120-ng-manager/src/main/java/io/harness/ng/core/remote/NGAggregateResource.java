/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_USERGROUP_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_USER_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.ACCOUNT;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USER;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USERGROUP;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.SortOrder;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.api.AggregateAccountResourceService;
import io.harness.ng.core.api.AggregateUserGroupService;
import io.harness.ng.core.dto.AccountResourcesDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupAggregateDTO;
import io.harness.ng.core.dto.UserGroupAggregateFilter;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.usergroups.filter.UserGroupFilterType;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.http.Body;

@OwnedBy(PL)
@Api("aggregate")
@Path("aggregate")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Slf4j
@NextGenManagerAuth
public class NGAggregateResource {
  private final AggregateUserGroupService aggregateUserGroupService;
  private final AccessControlClient accessControlClient;
  private final AggregateAccountResourceService aggregateAccountResourceService;

  @GET
  @Path("acl/usergroups")
  @ApiOperation(value = "Get Aggregated User Group list", nickname = "getUserGroupAggregateList")
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<UserGroupAggregateDTO>> list(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @BeanParam PageRequest pageRequest,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @QueryParam("filterType") @DefaultValue("EXCLUDE_INHERITED_GROUPS") UserGroupFilterType filterType,
      @QueryParam("userSize") @DefaultValue("6") @Max(20) int userSize, @Context ScopeInfo scopeInfo) {
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(ProjectKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    return ResponseDTO.newResponse(
        aggregateUserGroupService.listAggregateUserGroups(scopeInfo, pageRequest, searchTerm, userSize, filterType));
  }

  @POST
  @Path("acl/usergroups")
  @ApiOperation(value = "Get Aggregated Filtered User Group list", nickname = "getFilteredUserGroupAggregateList")
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<UserGroupAggregateDTO>> getFilteredUserGroups(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @BeanParam PageRequest pageRequest, @QueryParam("userSize") @DefaultValue("6") @Max(20) int userSize,
      @RequestBody(description = "User Group Filter", required = true) @Body
      @NotNull UserGroupFilterDTO userGroupFilterDTO, @Context ScopeInfo scopeInfo) {
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(ProjectKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    return ResponseDTO.newResponse(aggregateUserGroupService.listAggregateUserGroupsByFilter(
        scopeInfo, pageRequest, userSize, userGroupFilterDTO));
  }

  @POST
  @Path("acl/user/{userId}/usergroups")
  @Hidden
  @ApiOperation(value = "Get User Groups by User Id", nickname = "getUserGroupAggregateListByUser")
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<UserGroupAggregateDTO>> list(@NotNull @PathParam("userId") String userIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @BeanParam PageRequest pageRequest,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @QueryParam("userSize") @DefaultValue("6") @Max(20) int userCount,
      @Body UserGroupAggregateFilter userGroupAggregateFilter, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USER, userIdentifier), VIEW_USER_PERMISSION);
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(ProjectKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    return ResponseDTO.newResponse(aggregateUserGroupService.listAggregateUserGroupsForUser(
        scopeInfo, pageRequest, userGroupAggregateFilter.getScopeFilter(), userIdentifier, searchTerm, userCount));
  }

  @GET
  @Path("acl/usergroups/{identifier}")
  @ApiOperation(value = "Get Aggregated User Group", nickname = "getUserGroupAggregate")
  @Timed
  @ResponseMetered
  public ResponseDTO<UserGroupAggregateDTO> list(
      @NotNull @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_KEY + " for the scope of role assignments") @QueryParam(
          "roleAssignmentScopeOrgIdentifier") String roleAssignmentScopeOrgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_KEY + " for the scope of role assignments") @QueryParam(
          "roleAssignmentScopeProjectIdentifier") String roleAssignmentScopeProjectIdentifier,
      @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(USERGROUP, identifier), VIEW_USERGROUP_PERMISSION);
    ScopeDTO roleAssignmentScope =
        validateAndSetRoleAssignmentScope(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
            scopeInfo.getProjectIdentifier(), roleAssignmentScopeOrgIdentifier, roleAssignmentScopeProjectIdentifier);
    return ResponseDTO.newResponse(
        aggregateUserGroupService.getAggregatedUserGroup(scopeInfo, identifier, roleAssignmentScope));
  }

  @GET
  @Path("/account-resources")
  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  @ApiOperation(value = "Gets count of account resources", nickname = "getAccountResourcesCount")
  @Timed
  @ResponseMetered
  public ResponseDTO<AccountResourcesDTO> getAccountResourcesCount(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Context ScopeInfo scopeInfo) {
    return ResponseDTO.newResponse(aggregateAccountResourceService.getAccountResourcesDTO(accountIdentifier));
  }

  private ScopeDTO validateAndSetRoleAssignmentScope(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String roleAssignmentScopeOrgIdentifier, String roleAssignmentScopeProjectIdentifier) {
    if (!isBlank(orgIdentifier)) {
      if (roleAssignmentScopeOrgIdentifier == null) {
        // Backwards compatible
        roleAssignmentScopeOrgIdentifier = orgIdentifier;
      } else if (!roleAssignmentScopeOrgIdentifier.equals(orgIdentifier)) {
        log.info("roleAssignmentScopeOrgIdentifier {} is not equal to orgIdentifier {}",
            roleAssignmentScopeOrgIdentifier, orgIdentifier);
        throw new InvalidRequestException("Invalid role assignment scope provided as roleAssignmentScopeOrgIdentifier "
            + "is not equal to orgIdentifier.");
      }
    }
    if (!isBlank(projectIdentifier)) {
      if (roleAssignmentScopeProjectIdentifier == null) {
        // Backwards compatible
        roleAssignmentScopeProjectIdentifier = projectIdentifier;
      } else if (!roleAssignmentScopeProjectIdentifier.equals(projectIdentifier)) {
        log.info("roleAssignmentScopeProjectIdentifier {} is not equal to projectIdentifier {}",
            roleAssignmentScopeProjectIdentifier, projectIdentifier);
        throw new InvalidRequestException("Invalid role assignment scope provided as "
            + "roleAssignmentScopeProjectIdentifier is not equal to projectIdentifier.");
      }
    }
    return ScopeDTO.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(roleAssignmentScopeOrgIdentifier)
        .projectIdentifier(roleAssignmentScopeProjectIdentifier)
        .build();
  }
}
