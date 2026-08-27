/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.GROUP_IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.accesscontrol.PlatformPermissions.EDIT_USERGROUP_METADATA_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USERGROUP_NOTIFICATIONS_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USERGROUP_SSO_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USERGROUP_USERS_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_USERGROUP_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USERGROUP;
import static io.harness.ng.core.utils.NGUtils.verifyValuesNotChanged;
import static io.harness.ng.core.utils.UserGroupMapper.toDTO;
import static io.harness.utils.PageUtils.getNGPageResponse;
import static io.harness.utils.PageUtils.getPage;
import static io.harness.utils.PageUtils.getPageRequest;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.commons.exceptions.AccessDeniedErrorDTO;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.beans.SortOrder;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.engine.governance.GovernanceMetadataErrorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.OPAPolicyEvaluationException;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.accesscontrol.scopes.ScopeNameDTO;
import io.harness.ng.accesscontrol.usergroup.UserGroupPermissionUtils;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.OidcLinkGroupRequest;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupDTOInternal;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.entities.UserGroup.UserGroupKeys;
import io.harness.ng.core.user.remote.dto.UserFilter;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.usergroups.filter.UserGroupFilterType;
import io.harness.ng.core.utils.UserGroupMapper;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import software.wings.beans.sso.LdapLinkGroupRequest;
import software.wings.beans.sso.SSOType;
import software.wings.beans.sso.SamlLinkGroupRequest;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
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
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import retrofit2.http.Body;

@OwnedBy(PL)
@Api("user-groups")
@Path("user-groups")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error"),
          @ApiResponse(code = 403, response = AccessDeniedErrorDTO.class, message = "Unauthorized")
    })
@Tag(name = "User Group", description = "This contains APIs related to User Group as defined in Harness")
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
@NextGenManagerAuth
@Slf4j
public class UserGroupResource {
  private final UserGroupService userGroupService;
  private final DefaultUserGroupService defaultUserGroupService;
  private final AccessControlClient accessControlClient;
  private final ScopeInfoService scopeInfoService;
  private final UserGroupPermissionUtils userGroupPermissionUtils;

  @POST
  @ApiOperation(value = "Create a User Group", nickname = "postUserGroup")
  @Operation(operationId = "postUserGroup", summary = "Create User Group",
      description = "Create a User Group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the successfully created User Group")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<UserGroupDTO>
  create(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(description = "User Group entity to be created", required = true) @NotNull
      @Valid UserGroupDTO userGroupDTO, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(USERGROUP, null), userGroupPermissionUtils.getUserGroupCreatePermission(accountIdentifier));
    validateScopes(scopeInfo, userGroupDTO);
    UserGroupDTO savedDTO;
    try {
      savedDTO = userGroupService.createWithEvaluation(scopeInfo, userGroupDTO);
    } catch (OPAPolicyEvaluationException ex) {
      GovernanceMetadata governanceMetadata = ex.getMetadata() instanceof GovernanceMetadataErrorDTO
          ? ((GovernanceMetadataErrorDTO) ex.getMetadata()).getGovernanceMetadata()
          : null;
      return ResponseDTO.newResponse(UserGroupDTO.builder().governanceMetadata(governanceMetadata).build());
    }
    return ResponseDTO.newResponse(Long.toString(savedDTO.getVersion()), savedDTO);
  }

  @PUT
  @ApiOperation(value = "Update a User Group", nickname = "putUserGroup")
  @Operation(operationId = "putUserGroup", description = "Update a User Group in an account/org/project",
      summary = "Update User Group",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the successfully updated User Group")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<UserGroupDTO>
  update(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(description = "User Group entity with the updates", required = true)
      @NotNull UserGroupDTO userGroupDTO, @Context ScopeInfo scopeInfo) {
    userGroupPermissionUtils.hasAnyAccessOrThrow(scopeInfo, Resource.of(USERGROUP, userGroupDTO.getIdentifier()),
        List.of(EDIT_USERGROUP_METADATA_PERMISSION, MANAGE_USERGROUP_USERS_PERMISSION, MANAGE_USERGROUP_SSO_PERMISSION,
            MANAGE_USERGROUP_NOTIFICATIONS_PERMISSION));
    validateScopes(scopeInfo, userGroupDTO);
    UserGroupDTO savedDTO;
    try {
      savedDTO = userGroupService.updateWithEvaluation(scopeInfo, userGroupDTO);
    } catch (OPAPolicyEvaluationException ex) {
      GovernanceMetadata governanceMetadata = ex.getMetadata() instanceof GovernanceMetadataErrorDTO
          ? ((GovernanceMetadataErrorDTO) ex.getMetadata()).getGovernanceMetadata()
          : null;
      return ResponseDTO.newResponse(UserGroupDTO.builder().governanceMetadata(governanceMetadata).build());
    }
    return ResponseDTO.newResponse(Long.toString(savedDTO.getVersion()), savedDTO);
  }

  @PUT
  @Path("/copy")
  @ApiOperation(value = "Copy a User Group to several scopes", nickname = "copyUserGroup")
  @Operation(operationId = "copyUserGroup", summary = "Copy User Group",
      description = "Copy a User Group in an account/org/project",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns whether the copy was successful") })
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  @Deprecated
  public ResponseDTO<Boolean>
  copy(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = GROUP_IDENTIFIER_KEY, required = true) @QueryParam(
          NGCommonEntityConstants.GROUP_IDENTIFIER_KEY) String userGroupIdentifier,
      @RequestBody(description = "List of scopes", required = true) List<ScopeDTO> scopes) {
    throw new InvalidRequestException("This feature is no longer available. You can now directly assign role "
        + "assignments at project/organization to user groups linked in the account.");
  }

  @GET
  @Path("{identifier}")
  @ApiOperation(value = "Get a User Group", nickname = "getUserGroup")
  @Operation(operationId = "getUserGroup", summary = "Get User Group",
      description = "Get a User Group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the successfully fetched User Group")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<UserGroupDTO>
  get(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Identifier of the user group", required = true) @NotEmpty
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY) String identifier, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(USERGROUP, identifier), VIEW_USERGROUP_PERMISSION);
    Optional<UserGroup> userGroupOptional = userGroupService.get(scopeInfo, identifier);
    if (userGroupOptional.isPresent()) {
      return ResponseDTO.newResponse(
          Long.toString(userGroupOptional.get().getVersion()), toDTO(scopeInfo, userGroupOptional.get()));
    } else {
      throw new NotFoundException(
          String.format("User Group with identifier [%s] is not found in the given scope", identifier));
    }
  }

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete a User Group", nickname = "deleteUserGroup")
  @Operation(operationId = "deleteUserGroup", description = "Delete User Group",
      summary = "Delete a User Group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the successfully deleted User Group")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<UserGroupDTO> delete(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty
                                          @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Identifier of the user group", required = true) @NotEmpty
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY) String identifier, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(USERGROUP, identifier),
        userGroupPermissionUtils.getUserGroupManagePermissionForDeletion(accountIdentifier));
    if (userGroupService.isExternallyManaged(scopeInfo, identifier)) {
      log.warn("Deleting an externally managed user group- {} from account- {} org- {} project- {}", identifier,
          scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier());
    }
    UserGroup userGroup = userGroupService.delete(scopeInfo, identifier);
    return ResponseDTO.newResponse(Long.toString(userGroup.getVersion()), toDTO(scopeInfo, userGroup));
  }

  @GET
  @ApiOperation(value = "Get User Group List", nickname = "getUserGroupList")
  @Operation(operationId = "getUserGroupList", description = "List User Groups",
      summary = "List the User Groups in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the paginated list of the User Groups.")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<UserGroupDTO>>
  list(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Search filter which matches by user group name/identifier") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @QueryParam("filterType") @DefaultValue("EXCLUDE_INHERITED_GROUPS") UserGroupFilterType filterType,
      @QueryParam("ssoGroupId") Set<String> linkedSsoGroupIds, @BeanParam PageRequest pageRequest,
      @Context ScopeInfo scopeInfo) {
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order = SortOrder.Builder.aSortOrder().withField("lastModifiedAt", SortOrder.OrderType.DESC).build();
      SortOrder secondOrder = SortOrder.Builder.aSortOrder().withField("identifier", SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order, secondOrder));
    }

    Pageable pageable = getPageRequest(pageRequest);
    Page<UserGroup> userGroups = userGroupService.list(scopeInfo, searchTerm, filterType, linkedSsoGroupIds, pageable);
    Set<String> uniqueIds = userGroups.stream().map(x -> x.getParentUniqueId()).collect(Collectors.toSet());
    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeInfoService.getScopeInfo(accountIdentifier, uniqueIds);

    List<UserGroupDTO> userGroupDTOList =
        userGroups.stream()
            .map(x -> UserGroupMapper.toDTO(scopeInfoMap.get(x.getParentUniqueId()).orElseThrow(), x))
            .collect(Collectors.toList());

    Page<UserGroupDTO> page = new PageImpl<>(userGroupDTOList, userGroups.getPageable(), userGroups.getTotalElements());
    return ResponseDTO.newResponse(getNGPageResponse(page));
  }

  @GET
  @Path("{identifier}/scopes")
  @ApiOperation(value = "Get Inheriting Child Scope List", nickname = "getInheritingChildScopeList")
  @Operation(operationId = "getInheritingChildScopeList", summary = "Get Inheriting Child Scopes",
      description = "List the Child Scopes inheriting this User Group",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of the child scopes inheriting this User Group.")
      })
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  public ResponseDTO<List<ScopeNameDTO>>
  getInheritingChildScopeList(@Parameter(description = "Identifier of the user group",
                                  required = true) @NotNull @PathParam("identifier") String userGroupIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    // ScopeInfoFilter is unable to resolve scope correctly if we have a projectIdentifier but not orgIdentifier
    if (isNotEmpty(projectIdentifier) && isEmpty(orgIdentifier)) {
      orgIdentifier = DEFAULT_ORG_IDENTIFIER;
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, userGroupIdentifier), VIEW_USERGROUP_PERMISSION);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    List<ScopeNameDTO> inheritingScopeNames =
        userGroupService.getInheritingChildScopeList(scopeInfo, userGroupIdentifier);
    return ResponseDTO.newResponse(inheritingScopeNames);
  }

  @POST
  @Path("{identifier}/users")
  @ApiOperation(value = "List users in a user group", nickname = "getUsersInUserGroup")
  @Operation(operationId = "getUserListInUserGroup", summary = "List users in User Group",
      description = "List the users in a User Group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the paginated list of the users in a User Group.")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<UserMetadataDTO>>
  getUsersInUserGroup(@Parameter(description = "Identifier of the user group", required = true) @NotNull @PathParam(
                          "identifier") String userGroupIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @Valid @BeanParam PageRequest pageRequest,
      @RequestBody(description = "Filter users based on multiple parameters") UserFilter userFilter,
      @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(USERGROUP, userGroupIdentifier), VIEW_USERGROUP_PERMISSION);
    return ResponseDTO.newResponse(
        userGroupService.listUsersInUserGroup(scopeInfo, userGroupIdentifier, userFilter, pageRequest));
  }

  @POST
  @Path("batch")
  @ApiOperation(value = "Get Batch User Group List", nickname = "getBatchUserGroupList")
  @Operation(operationId = "getBatchUsersGroupList", summary = "List User Groups by filter",
      description = "List the User Groups selected by a filter in an account/org/project. This api supports maximum of "
          + "10K User Group in response.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of the user groups selected by a filter in a User Group.")
      })
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  public ResponseDTO<List<UserGroupDTO>>
  list(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @RequestBody(
          description = "User Group Filter", required = true) @Body @NotNull UserGroupFilterDTO userGroupFilterDTO) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(userGroupFilterDTO.getAccountIdentifier(),
        userGroupFilterDTO.getOrgIdentifier(), userGroupFilterDTO.getProjectIdentifier());
    List<UserGroup> userGroups = userGroupService.list(scopeInfo, userGroupFilterDTO);
    Set<String> uniqueIds = userGroups.stream().map(UserGroup::getParentUniqueId).collect(Collectors.toSet());
    Map<String, Optional<ScopeInfo>> scopeInfoMap =
        scopeInfoService.getScopeInfo(userGroupFilterDTO.getAccountIdentifier(), uniqueIds);
    if (!accessControlClient.hasAccess(
            ResourceScope.of(userGroupFilterDTO.getAccountIdentifier(), userGroupFilterDTO.getOrgIdentifier(),
                userGroupFilterDTO.getProjectIdentifier()),
            Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION)) {
      userGroups = userGroupService.getPermittedUserGroups(userGroups);
    }
    List<UserGroupDTO> userGroupDTOs =
        userGroups.stream()
            .map(x -> UserGroupMapper.toDTO(scopeInfoMap.get(x.getParentUniqueId()).orElseThrow(), x))
            .collect(Collectors.toList());
    return ResponseDTO.newResponse(userGroupDTOs);
  }

  @POST
  @Path("filter")
  @ApiOperation(value = "Get filtered User Groups", nickname = "getFilteredUserGroupsList")
  @Operation(operationId = "getFilteredUserGroupsList", summary = "Get filtered User Groups",
      description = "List the User Groups selected by a filter in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of the user groups selected by a filter in a User Group.")
      })
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<UserGroupDTO>>
  list(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @RequestBody(description = "User Group Filter", required = true) @Body
      @NotNull UserGroupFilterDTO userGroupFilterDTO, @BeanParam PageRequest pageRequest) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(userGroupFilterDTO.getAccountIdentifier(),
        userGroupFilterDTO.getOrgIdentifier(), userGroupFilterDTO.getProjectIdentifier());
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(UserGroupKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }

    if (accessControlClient.hasAccess(
            ResourceScope.of(userGroupFilterDTO.getAccountIdentifier(), userGroupFilterDTO.getOrgIdentifier(),
                userGroupFilterDTO.getProjectIdentifier()),
            Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION)) {
      Page<UserGroup> userGroups = userGroupService.list(scopeInfo, userGroupFilterDTO, getPageRequest(pageRequest));
      if (userGroups.isEmpty()) {
        // No user groups were selected
        Page<UserGroupDTO> userGroupDTOS = new PageImpl<>(new ArrayList<>());
        return ResponseDTO.newResponse(getNGPageResponse(userGroupDTOS));
      }
      Set<String> uniqueIds = userGroups.stream().map(UserGroup::getParentUniqueId).collect(Collectors.toSet());
      Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeInfoService.getScopeInfo(accountIdentifier, uniqueIds);
      Page<UserGroupDTO> userGroupDTOs =
          userGroups.map(x -> UserGroupMapper.toDTO(scopeInfoMap.get(x.getParentUniqueId()).orElseThrow(), x));
      return ResponseDTO.newResponse(getNGPageResponse(userGroupDTOs));
    }
    Pageable pageable = Pageable.ofSize(50000); // keeping the default max supported value
    Page<UserGroup> pagedUserGroups = userGroupService.list(scopeInfo, userGroupFilterDTO, pageable);
    List<UserGroup> permittedUserGroups = userGroupService.getPermittedUserGroups(pagedUserGroups.getContent());
    Set<String> uniqueIds = permittedUserGroups.stream().map(UserGroup::getParentUniqueId).collect(Collectors.toSet());
    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeInfoService.getScopeInfo(accountIdentifier, uniqueIds);
    List<UserGroupDTO> userGroupDTOs =
        permittedUserGroups.stream()
            .map(x -> UserGroupMapper.toDTO(scopeInfoMap.get(x.getParentUniqueId()).orElseThrow(), x))
            .collect(Collectors.toList());
    return ResponseDTO.newResponse(
        getNGPageResponse(getPage(userGroupDTOs, pageRequest.getPageIndex(), pageRequest.getPageSize())));
  }

  @POST
  @Path("filter/internal")
  @Hidden
  @InternalApi
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<UserGroupDTOInternal>> listInternal(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @RequestBody(description = "User Group Filter", required = true) @Body
      @NotNull UserGroupFilterDTO userGroupFilterDTO, @BeanParam PageRequest pageRequest) {
    ResponseDTO<PageResponse<UserGroupDTO>> userGroupDtoResponse =
        list(accountIdentifier, userGroupFilterDTO, pageRequest);

    List<UserGroupDTOInternal> userGroupDTOInternals =
        userGroupDtoResponse.getData()
            .getContent()
            .stream()
            .map(UserGroupMapper::getUserGroupDTOInternalFromUserGroupDTO)
            .toList();

    return ResponseDTO.newResponse(
        getNGPageResponse(getPage(userGroupDTOInternals, pageRequest.getPageIndex(), pageRequest.getPageSize())));
  }

  @POST
  @Path("default/internal")
  @Hidden
  @InternalApi
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Create default user group at scope", nickname = "createDefaultUserGroupInternal", hidden = true)
  public ResponseDTO<UserGroupDTO>
  createDefaultUserGroupInternal(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
                                     NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    UserGroup userGroup = defaultUserGroupService.create(scopeInfo, List.of());
    return ResponseDTO.newResponse(userGroup != null ? toDTO(scopeInfo, userGroup) : null);
  }

  @GET
  @Path("{identifier}/member/{userIdentifier}")
  @ApiOperation(value = "Check if the user is part of the user group", nickname = "checkMember")
  @Operation(operationId = "getMember", summary = "Check user membership",
      description = "Check if the user is part of the user group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Return true/false based on whether the user is part of the user group")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<Boolean>
  checkMember(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
                  NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Identifier of the user group", required = true) @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Parameter(description = "Identifier of the user", required = true) @PathParam("userIdentifier")
      String userIdentifier, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(USERGROUP, identifier), VIEW_USERGROUP_PERMISSION);
    boolean isMember = userGroupService.checkMember(scopeInfo, identifier, userIdentifier);
    return ResponseDTO.newResponse(isMember);
  }

  @PUT
  @Path("{identifier}/member/{userIdentifier}")
  @ApiOperation(value = "Add a user to the user group", nickname = "addMember")
  @Operation(operationId = "putMember", summary = "Add user to User Group",
      description = "Add a user to the user group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the updated user group after user addition")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<UserGroupDTO>
  addMember(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
                NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Identifier of the user group", required = true) @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Parameter(description = "Identifier of the user", required = true) @PathParam("userIdentifier")
      String userIdentifier, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(USERGROUP, identifier),
        userGroupPermissionUtils.getUserGroupManagePermissionForMembership(scopeInfo.getAccountIdentifier()));
    checkExternallyManaged(scopeInfo, identifier);
    UserGroupDTO savedDTO;
    try {
      savedDTO = userGroupService.addMemberWithEvaluation(scopeInfo, identifier, userIdentifier);
    } catch (OPAPolicyEvaluationException ex) {
      GovernanceMetadata governanceMetadata = ex.getMetadata() instanceof GovernanceMetadataErrorDTO
          ? ((GovernanceMetadataErrorDTO) ex.getMetadata()).getGovernanceMetadata()
          : null;
      return ResponseDTO.newResponse(UserGroupDTO.builder().governanceMetadata(governanceMetadata).build());
    }
    return ResponseDTO.newResponse(Long.toString(savedDTO.getVersion()), savedDTO);
  }

  @DELETE
  @Path("{identifier}/member/{userIdentifier}")
  @ApiOperation(value = "Remove a user from the user group", nickname = "removeMember")
  @Operation(operationId = "deleteMember", summary = "Remove user from User Group",
      description = "Remove a user from the user group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the updated user group after user removal")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<UserGroupDTO>
  removeMember(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Identifier of the user group", required = true) @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Parameter(description = "Identifier of the user", required = true) @PathParam("userIdentifier")
      String userIdentifier, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(USERGROUP, identifier),
        userGroupPermissionUtils.getUserGroupManagePermissionForMembership(scopeInfo.getAccountIdentifier()));
    checkExternallyManaged(scopeInfo, identifier);
    UserGroupDTO savedDTO;
    try {
      savedDTO = userGroupService.removeMemberWithEvaluation(scopeInfo, identifier, userIdentifier);
    } catch (OPAPolicyEvaluationException ex) {
      GovernanceMetadata governanceMetadata = ex.getMetadata() instanceof GovernanceMetadataErrorDTO
          ? ((GovernanceMetadataErrorDTO) ex.getMetadata()).getGovernanceMetadata()
          : null;
      return ResponseDTO.newResponse(UserGroupDTO.builder().governanceMetadata(governanceMetadata).build());
    }
    return ResponseDTO.newResponse(Long.toString(savedDTO.getVersion()), savedDTO);
  }

  private static void validateScopes(ScopeInfo scopeInfo, UserGroupDTO userGroupDTO) {
    verifyValuesNotChanged(
        Lists.newArrayList(Pair.of(scopeInfo.getAccountIdentifier(), userGroupDTO.getAccountIdentifier()),
            Pair.of(scopeInfo.getOrgIdentifier(), userGroupDTO.getOrgIdentifier()),
            Pair.of(scopeInfo.getProjectIdentifier(), userGroupDTO.getProjectIdentifier())),
        true);
  }

  @PUT
  @Path("{userGroupId}/unlink")
  @ApiOperation(value = "API to unlink the harness user group from SSO group", nickname = "unlinkSsoGroup")
  @Operation(operationId = "unlinkUserGroupfromSSO",
      summary = "Unlink SSO Group from the User Group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the updated User Group after unlinking SSO Group")
      })
  @Timed
  @ResponseMetered
  public RestResponse<UserGroup>
  unlinkSsoGroup(@Parameter(description = "Identifier of the user group", required = true) @PathParam(
                     "userGroupId") String userGroupId,
      @Parameter(description = "Retain currently synced members of the user group") @QueryParam(
          "retainMembers") boolean retainMembers,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(USERGROUP, userGroupId),
        userGroupPermissionUtils.getUserGroupManagePermissionForSSO(accountIdentifier));
    checkExternallyManaged(scopeInfo, userGroupId);
    return new RestResponse<>(userGroupService.unlinkSsoGroup(scopeInfo, userGroupId, retainMembers));
  }

  @PUT
  @Path("{userGroupId}/link/saml/{samlId}")
  @ApiOperation(value = "Link to SAML group", nickname = "linkToSamlGroup")
  @Operation(operationId = "linkUserGroupToSAML",
      summary = "Link SAML Group to the User Group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the updated User Group after linking SAML Group")
      })
  @Timed
  @ResponseMetered
  public RestResponse<UserGroup>
  linkToSamlGroup(@Parameter(description = "Identifier of the user group", required = true) @PathParam(
                      "userGroupId") String userGroupId,
      @Parameter(description = "Saml Group entity identifier", required = true) @PathParam("samlId") String samlId,
      @RequestBody(
          description = "Saml Link Group Request", required = true) @NotNull @Valid SamlLinkGroupRequest groupRequest,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(USERGROUP, userGroupId),
        userGroupPermissionUtils.getUserGroupManagePermissionForSSO(accountIdentifier));
    checkExternallyManaged(scopeInfo, userGroupId);
    return new RestResponse<>(userGroupService.linkToSsoGroup(scopeInfo, userGroupId, SSOType.SAML, samlId,
        groupRequest.getSamlGroupName(), groupRequest.getSamlGroupName()));
  }

  @PUT
  @Path("{userGroupId}/link/oidc/{providerId}")
  @ApiOperation(value = "Link to OIDC group", nickname = "linkToOidcGroup")
  @Operation(operationId = "linkUserGroupToOIDC",
      summary = "Link OIDC Group to the User Group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the updated User Group after linking OIDC Group")
      })
  @Timed
  @ResponseMetered
  public RestResponse<UserGroup>
  linkToOidcGroup(@Parameter(description = "Identifier of the user group", required = true) @PathParam(
                      "userGroupId") String userGroupId,
      @Parameter(description = "OIDC Group entity identifier", required = true) @PathParam(
          "providerId") String providerId,
      @RequestBody(
          description = "OIDC Link Group Request", required = true) @NotNull @Valid OidcLinkGroupRequest groupRequest,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(USERGROUP, userGroupId),
        userGroupPermissionUtils.getUserGroupManagePermissionForSSO(accountIdentifier));
    checkExternallyManaged(scopeInfo, userGroupId);
    return new RestResponse<>(
        userGroupService.linkToOidcSsoGroup(scopeInfo, userGroupId, providerId, groupRequest.getOidcGroupName()));
  }

  @PUT
  @Path("{userGroupId}/link/ldap/{ldapId}")
  @ApiOperation(value = "Link to an LDAP group", nickname = "linkToLdapGroup")
  @Operation(operationId = "linkUserGroupToLDAP",
      summary = "Link LDAP Group to the User Group to an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the updated User Group after linking LDAP Group")
      })
  @Timed
  @ResponseMetered
  @FeatureRestrictionCheck(FeatureRestrictionName.LDAP_SUPPORT)
  public RestResponse<UserGroup>
  linkToLdapGroup(@Parameter(description = "Identifier of the user group", required = true) @PathParam(
                      "userGroupId") String userGroupId,
      @Parameter(description = "LDAP entity identifier", required = true) @PathParam("ldapId") String ldapId,
      @RequestBody(
          description = "LDAP Link Group Request", required = true) @NotNull @Valid LdapLinkGroupRequest groupRequest,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(USERGROUP, userGroupId),
        userGroupPermissionUtils.getUserGroupManagePermissionForSSO(accountIdentifier));
    checkExternallyManaged(scopeInfo, userGroupId);
    return new RestResponse<>(userGroupService.linkToSsoGroup(
        scopeInfo, userGroupId, SSOType.LDAP, ldapId, groupRequest.getLdapGroupDN(), groupRequest.getLdapGroupName()));
  }

  @GET
  @Path("sso/{identifier}")
  @Hidden
  @ApiOperation(value = "Get User Groups List linked to SSO", nickname = "getSsoLinkedUserGroups")
  @Operation(operationId = "getSsoLinkedUserGroups", description = "List User Groups linked to sso id",
      summary = "List the User Groups at any account/org/project scope linked to an sso id",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of the User Groups linked to sso id.")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<List<UserGroupDTO>>
  getSsoLinkedUserGroups(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = "Identifier of the SSO setting", required = true) @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String ssoIdentifier) {
    List<UserGroup> userGroups = userGroupService.getUserGroupsBySsoId(accountIdentifier, ssoIdentifier);
    Set<String> uniqueIds = userGroups.stream().map(UserGroup::getParentUniqueId).collect(Collectors.toSet());
    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeInfoService.getScopeInfo(accountIdentifier, uniqueIds);
    List<UserGroupDTO> userGroupDTOS =
        userGroups.stream()
            .map(x -> UserGroupMapper.toDTO(scopeInfoMap.get(x.getParentUniqueId()).orElseThrow(), x))
            .collect(Collectors.toList());
    return ResponseDTO.newResponse(userGroupDTOS);
  }

  private void checkExternallyManaged(ScopeInfo scopeInfo, String identifier) {
    if (userGroupService.isExternallyManaged(scopeInfo, identifier)) {
      throw new InvalidRequestException("This API call is not supported for externally managed group" + identifier);
    }
  }
}