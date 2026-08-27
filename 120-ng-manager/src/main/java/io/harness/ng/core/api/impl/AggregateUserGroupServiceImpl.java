/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.resourcegroups.api.ResourceGroupDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentAggregateResponseDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.ScopeFilterType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.api.AggregateUserGroupService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.ScopeSelector;
import io.harness.ng.core.dto.UserGroupAggregateDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.role.dto.RoleAssignmentMetadataDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.core.usergroups.filter.UserGroupFilterType;
import io.harness.ng.core.utils.UserGroupMapper;
import io.harness.utils.PageUtils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.data.domain.Page;

@OwnedBy(PL)
@Singleton
@ValidateOnExecution
public class AggregateUserGroupServiceImpl implements AggregateUserGroupService {
  private final UserGroupService userGroupService;
  private final AccessControlAdminClient accessControlAdminClient;
  private final NgUserService ngUserService;
  private final ScopeInfoService scopeInfoService;

  @Inject
  public AggregateUserGroupServiceImpl(UserGroupService userGroupService,
      AccessControlAdminClient accessControlAdminClient, NgUserService ngUserService,
      ScopeInfoService scopeInfoService) {
    this.userGroupService = userGroupService;
    this.accessControlAdminClient = accessControlAdminClient;
    this.ngUserService = ngUserService;
    this.scopeInfoService = scopeInfoService;
  }

  @Override
  public PageResponse<UserGroupAggregateDTO> listAggregateUserGroups(
      ScopeInfo scopeInfo, PageRequest pageRequest, String searchTerm, int userSize, UserGroupFilterType filterType) {
    Page<UserGroup> userGroupPageResponse =
        userGroupService.list(scopeInfo, searchTerm, filterType, null, getPageRequest(pageRequest));

    return getUserGroupAggregateDTOPageResponse(scopeInfo, userSize, userGroupPageResponse);
  }

  @Override
  public PageResponse<UserGroupAggregateDTO> listAggregateUserGroupsByFilter(
      ScopeInfo scopeInfo, PageRequest pageRequest, int userSize, UserGroupFilterDTO userGroupFilterDTO) {
    Page<UserGroup> userGroupPageResponse =
        userGroupService.listByViewPermission(userGroupFilterDTO, getPageRequest(pageRequest));

    return getUserGroupAggregateDTOPageResponse(scopeInfo, userSize, userGroupPageResponse);
  }

  private PageResponse<UserGroupAggregateDTO> getUserGroupAggregateDTOPageResponse(
      ScopeInfo scopeInfo, int userSize, Page<UserGroup> userGroupPageResponse) {
    List<String> userIdentifiers = getUsersInUserGroup(userGroupPageResponse, userSize);
    Map<String, UserMetadataDTO> userMetadataMap =
        ngUserService.getUserMetadata(userIdentifiers)
            .stream()
            .collect(Collectors.toMap(UserMetadataDTO::getUuid, Function.identity()));
    Set<String> parentUniqueIds =
        userGroupPageResponse.getContent().stream().map(UserGroup::getParentUniqueId).collect(Collectors.toSet());
    Map<String, Optional<ScopeInfo>> scopeInfoMap =
        scopeInfoService.getScopeInfo(scopeInfo.getAccountIdentifier(), parentUniqueIds);

    Set<PrincipalDTO> principalDTOSet =
        userGroupPageResponse.getContent()
            .stream()
            .map(userGroup -> {
              ScopeInfo ugScopeInfo = scopeInfoMap.get(userGroup.getParentUniqueId()).orElseThrow();
              return PrincipalDTO.builder()
                  .identifier(userGroup.getIdentifier())
                  .type(USER_GROUP)
                  .scopeLevel(ScopeLevel
                                  .of(ugScopeInfo.getAccountIdentifier(), ugScopeInfo.getOrgIdentifier(),
                                      ugScopeInfo.getProjectIdentifier())
                                  .toString()
                                  .toLowerCase())
                  .build();
            })
            .collect(Collectors.toSet());
    RoleAssignmentFilterDTO roleAssignmentFilterDTO =
        RoleAssignmentFilterDTO.builder().principalFilter(principalDTOSet).build();
    Map<ImmutablePair<String, String>, List<RoleAssignmentMetadataDTO>> userGroupRoleAssignmentsMap =
        getPrincipalRoleAssignmentMap(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
            scopeInfo.getProjectIdentifier(), roleAssignmentFilterDTO);

    return PageUtils.getNGPageResponse(userGroupPageResponse.map(userGroup -> {
      List<UserMetadataDTO> users = getLastNElementsReversed(userGroup.getUsers(), userSize)
                                        .stream()
                                        .map(userMetadataMap::get)
                                        .filter(Objects::nonNull)
                                        .collect(toList());
      ScopeInfo ugScopeInfo = scopeInfoMap.get(userGroup.getParentUniqueId()).orElseThrow();
      return UserGroupAggregateDTO.builder()
          .userGroupDTO(UserGroupMapper.toDTO(ugScopeInfo, userGroup))
          .roleAssignmentsMetadataDTO(userGroupRoleAssignmentsMap.get(new ImmutablePair<>(userGroup.getIdentifier(),
              ScopeLevel
                  .of(ugScopeInfo.getAccountIdentifier(), ugScopeInfo.getOrgIdentifier(),
                      ugScopeInfo.getProjectIdentifier())
                  .toString()
                  .toLowerCase())))
          .users(users)
          .lastModifiedAt(userGroup.getLastModifiedAt())
          .build();
    }));
  }

  @Override
  public PageResponse<UserGroupAggregateDTO> listAggregateUserGroupsForUser(ScopeInfo scopeInfo,
      PageRequest pageRequest, List<ScopeSelector> scopeFilter, String userIdentifier, String searchTerm,
      int userCount) {
    if (isEmpty(scopeFilter)) {
      scopeFilter.add(ScopeSelector.builder()
                          .accountIdentifier(scopeInfo.getAccountIdentifier())
                          .orgIdentifier(scopeInfo.getOrgIdentifier())
                          .projectIdentifier(scopeInfo.getProjectIdentifier())
                          .filter(ScopeFilterType.EXCLUDING_CHILD_SCOPES)
                          .build());
    }

    Page<UserGroup> userGroupPageResponse =
        userGroupService.list(scopeFilter, userIdentifier, searchTerm, getPageRequest(pageRequest));

    List<String> userIdentifiers = getUsersInUserGroup(userGroupPageResponse, userCount);
    Map<String, UserMetadataDTO> userMetadataMap =
        ngUserService.getUserMetadata(userIdentifiers)
            .stream()
            .collect(Collectors.toMap(UserMetadataDTO::getUuid, Function.identity()));

    return PageUtils.getNGPageResponse(userGroupPageResponse.map(userGroup -> {
      List<UserMetadataDTO> users = getLastNElementsReversed(userGroup.getUsers(), userCount)
                                        .stream()
                                        .map(userMetadataMap::get)
                                        .filter(Objects::nonNull)
                                        .collect(toList());
      Map<String, Optional<ScopeInfo>> scopeInfoMap =
          scopeInfoService.getScopeInfo(userGroup.getAccountIdentifier(), Set.of(userGroup.getParentUniqueId()));
      ScopeInfo ugScopeInfo = scopeInfoMap.get(userGroup.getParentUniqueId()).orElseThrow();
      return UserGroupAggregateDTO.builder()
          .userGroupDTO(UserGroupMapper.toDTO(ugScopeInfo, userGroup))
          .users(users)
          .lastModifiedAt(userGroup.getLastModifiedAt())
          .build();
    }));
  }

  public static <T> List<T> getLastNElementsReversed(List<T> list, int n) {
    if (n < 0) {
      n = list.size();
    }
    List<T> result = list.subList(Math.max(list.size() - n, 0), list.size());
    return Lists.reverse(result);
  }

  @Override
  public UserGroupAggregateDTO getAggregatedUserGroup(
      ScopeInfo scopeInfo, String userGroupIdentifier, ScopeDTO roleAssignmentScope) {
    Optional<UserGroup> userGroupOpt = userGroupService.get(scopeInfo, userGroupIdentifier);
    if (!userGroupOpt.isPresent()) {
      throw new InvalidRequestException(
          String.format("User Group is not available %s:%s:%s:%s", scopeInfo.getAccountIdentifier(),
              scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), userGroupIdentifier));
    }
    PrincipalDTO principalDTO = PrincipalDTO.builder()
                                    .identifier(userGroupIdentifier)
                                    .type(USER_GROUP)
                                    .scopeLevel(ScopeLevel
                                                    .of(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
                                                        scopeInfo.getProjectIdentifier())
                                                    .toString()
                                                    .toLowerCase())
                                    .build();
    RoleAssignmentFilterDTO roleAssignmentFilterDTO =
        RoleAssignmentFilterDTO.builder().principalFilter(Collections.singleton(principalDTO)).build();
    Map<ImmutablePair<String, String>, List<RoleAssignmentMetadataDTO>> userGroupRoleAssignmentsMap =
        getPrincipalRoleAssignmentMap(roleAssignmentScope.getAccountIdentifier(),
            roleAssignmentScope.getOrgIdentifier(), roleAssignmentScope.getProjectIdentifier(),
            roleAssignmentFilterDTO);

    List<UserMetadataDTO> users = isEmpty(userGroupOpt.get().getUsers())
        ? Collections.emptyList()
        : ngUserService.getUserMetadata(userGroupOpt.get().getUsers());

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeInfoService.getScopeInfo(
        userGroupOpt.get().getAccountIdentifier(), Set.of(userGroupOpt.get().getParentUniqueId()));
    ScopeInfo ugScopeInfo = scopeInfoMap.get(userGroupOpt.get().getParentUniqueId()).orElseThrow();
    return UserGroupAggregateDTO.builder()
        .userGroupDTO(UserGroupMapper.toDTO(ugScopeInfo, userGroupOpt.get()))
        .roleAssignmentsMetadataDTO(userGroupRoleAssignmentsMap.get(new ImmutablePair<>(userGroupIdentifier,
            ScopeLevel
                .of(ugScopeInfo.getAccountIdentifier(), ugScopeInfo.getOrgIdentifier(),
                    ugScopeInfo.getProjectIdentifier())
                .toString()
                .toLowerCase())))
        .users(users)
        .lastModifiedAt(userGroupOpt.get().getLastModifiedAt())
        .build();
  }

  private Map<ImmutablePair<String, String>, List<RoleAssignmentMetadataDTO>> getPrincipalRoleAssignmentMap(
      String accountIdentifier, String orgIdentifier, String projectIdentifier,
      RoleAssignmentFilterDTO roleAssignmentFilterDTO) {
    RoleAssignmentAggregateResponseDTO roleAssignmentAggregateResponseDTO =
        getResponse(accessControlAdminClient.getAggregatedFilteredRoleAssignments(
            accountIdentifier, orgIdentifier, projectIdentifier, roleAssignmentFilterDTO));

    Map<String, RoleResponseDTO> roleMap = new HashMap<>();
    for (RoleResponseDTO roleResponse : roleAssignmentAggregateResponseDTO.getRoles()) {
      Set<RoleDTO.ScopeLevel> allowedScopeLevels = roleResponse.getRole().getAllowedScopeLevels();
      if (allowedScopeLevels != null && !allowedScopeLevels.isEmpty()) {
        for (RoleDTO.ScopeLevel scopeLevel : allowedScopeLevels) {
          roleMap.put(roleKey(roleResponse.getRole().getIdentifier(), scopeLevel.toString()), roleResponse);
        }
      } else {
        roleMap.put(roleKey(roleResponse.getRole().getIdentifier(), null), roleResponse);
      }
    }

    Map<String, ResourceGroupDTO> resourceGroupMap =
        roleAssignmentAggregateResponseDTO.getResourceGroups().stream().collect(
            toMap(ResourceGroupDTO::getIdentifier, Function.identity()));

    return roleAssignmentAggregateResponseDTO.getRoleAssignments()
        .stream()
        .filter(roleAssignmentDTO
            -> roleMap.containsKey(roleKeyFromAssignment(roleAssignmentDTO))
                && resourceGroupMap.containsKey(roleAssignmentDTO.getResourceGroupIdentifier()))
        .collect(Collectors.groupingBy(roleAssignment
            -> new ImmutablePair<>(roleAssignment.getPrincipal().getIdentifier(),
                roleAssignment.getPrincipal().getScopeLevel() == null
                    ? ScopeLevel.of(accountIdentifier, orgIdentifier, projectIdentifier).toString().toLowerCase()
                    : roleAssignment.getPrincipal().getScopeLevel()),
            // pair of scope level and identifier
            Collectors.mapping(roleAssignment -> {
              String key = roleKeyFromAssignment(roleAssignment);
              return RoleAssignmentMetadataDTO.builder()
                  .identifier(roleAssignment.getIdentifier())
                  .roleIdentifier(roleAssignment.getRoleIdentifier())
                  .roleScopeLevel(roleAssignment.getRoleReference() == null
                          ? null
                          : roleAssignment.getRoleReference().getScopeLevel())
                  .resourceGroupIdentifier(roleAssignment.getResourceGroupIdentifier())
                  .roleName(roleMap.get(key).getRole().getName())
                  .resourceGroupName(resourceGroupMap.get(roleAssignment.getResourceGroupIdentifier()).getName())
                  .managedRole(roleMap.get(key).isHarnessManaged())
                  .managedRoleAssignment(roleAssignment.isManaged())
                  .build();
            }, toList())));
  }

  private static String roleKey(String identifier, String scopeLevel) {
    return identifier + "|" + scopeLevel;
  }

  private static String roleKeyFromAssignment(RoleAssignmentDTO roleAssignment) {
    String scopeLevel =
        roleAssignment.getRoleReference() == null ? null : roleAssignment.getRoleReference().getScopeLevel();
    return roleKey(roleAssignment.getRoleIdentifier(), scopeLevel);
  }

  private List<String> getUsersInUserGroup(Page<UserGroup> userGroupPageResponse, int userCount) {
    return userGroupPageResponse.stream()
        .map(ug -> getLastNElementsReversed(ug.getUsers(), userCount))
        .flatMap(List::stream)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
  }
}
