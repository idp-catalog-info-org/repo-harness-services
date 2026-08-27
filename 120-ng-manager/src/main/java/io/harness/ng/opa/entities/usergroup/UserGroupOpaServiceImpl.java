/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.opa.entities.usergroup;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.opaclient.model.OpaConstants.OPA_EVALUATION_TYPE_USER_GROUP;
import static io.harness.opaclient.model.OpaConstants.OPA_STATUS_ERROR;
import static io.harness.opaclient.model.OpaConstants.OPA_STATUS_WARNING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.api.opa.UserGroupOpaService;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.entities.UserMembership;
import io.harness.ng.core.user.entities.UserMembership.UserMembershipKeys;
import io.harness.ng.core.utils.UserGroupMapper;
import io.harness.opa.OpaEvaluationContext;
import io.harness.opa.OpaService;
import io.harness.opaclient.OpaUtils;
import io.harness.repositories.user.custom.UserMembershipRepositoryCustom;
import io.harness.utils.NGFeatureFlagHelperService;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Slf4j
@Singleton
public class UserGroupOpaServiceImpl implements UserGroupOpaService {
  private final OpaService opaService;
  private final UserMembershipRepositoryCustom userMembershipRepository;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @Inject
  public UserGroupOpaServiceImpl(OpaService opaService, UserMembershipRepositoryCustom userMembershipRepository,
      NGFeatureFlagHelperService ngFeatureFlagHelperService) {
    this.opaService = opaService;
    this.userMembershipRepository = userMembershipRepository;
    this.ngFeatureFlagHelperService = ngFeatureFlagHelperService;
  }

  @Override
  public GovernanceMetadata evaluateWithOpa(ScopeInfo scopeInfo, UserGroupDTO userGroupDTO, String action) {
    return evaluateWithOpa(scopeInfo, userGroupDTO, action, Optional.empty());
  }

  @Override
  public GovernanceMetadata evaluateWithOpa(
      ScopeInfo scopeInfo, UserGroupDTO userGroupDTO, String action, Optional<UserGroup> existingUserGroup) {
    return evaluateWithOpa(scopeInfo, userGroupDTO, action, existingUserGroup, null);
  }

  private GovernanceMetadata evaluateWithOpa(ScopeInfo scopeInfo, UserGroupDTO userGroupDTO, String action,
      Optional<UserGroup> existingUserGroup, Map<String, List<UserMembership>> prefetchedUserMemberships) {
    if (!isOpaForUserGroupsEnabled(scopeInfo)) {
      return null;
    }
    if (existingUserGroup.isPresent() && shouldSkipOpaEvaluation(existingUserGroup.get())) {
      return null;
    }
    addScopeFieldsOnDto(scopeInfo, userGroupDTO);
    Set<String> providedUsers =
        userGroupDTO.getUsers() != null ? new HashSet<>(userGroupDTO.getUsers()) : new HashSet<>();
    Set<String> changedUsers = new HashSet<>();
    if (existingUserGroup.isPresent()) {
      Set<String> alreadyExistingUsers = existingUserGroup.get().getUsers() != null
          ? new HashSet<>(existingUserGroup.get().getUsers())
          : new HashSet<>();
      Set<String> addedUsers = new HashSet<>(providedUsers);
      addedUsers.removeAll(alreadyExistingUsers);
      Set<String> removedUsers = new HashSet<>(alreadyExistingUsers);
      removedUsers.removeAll(providedUsers);
      changedUsers.addAll(addedUsers);
      changedUsers.addAll(removedUsers);
    } else {
      changedUsers.addAll(providedUsers);
    }
    Map<String, List<UserMembership>> userMemberships = prefetchedUserMemberships == null
        ? fetchUserMemberships(scopeInfo.getAccountIdentifier(), changedUsers)
        : getUserMembershipsForChangedUsers(prefetchedUserMemberships, changedUsers);
    return evaluatePoliciesWithEntity(scopeInfo, userGroupDTO, action, userGroupDTO.getIdentifier(), userMemberships);
  }

  private GovernanceMetadata evaluatePoliciesWithEntity(ScopeInfo scopeInfo, UserGroupDTO userGroupDTO, String action,
      String identifier, Map<String, List<UserMembership>> userMemberships) {
    OpaEvaluationContext context;
    try {
      String expandedYaml = getUserGroupYaml(userGroupDTO, userMemberships);
      context = createEvaluationContext(expandedYaml, OPA_EVALUATION_TYPE_USER_GROUP);
      return opaService.evaluate(context, scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
          scopeInfo.getProjectIdentifier(), identifier, action, OPA_EVALUATION_TYPE_USER_GROUP);
    } catch (IOException e) {
      return GovernanceMetadata.newBuilder()
          .setDeny(true)
          .setMessage(String.format("Could not create OPA context: [%s]", e.getMessage()))
          .build();
    }
  }

  @Override
  public GovernanceMetadata findFirstOpaFailureForUserAddition(
      ScopeInfo scopeInfo, List<UserGroup> userGroups, Set<String> userIdsToAdd, String action) {
    if (!isOpaForUserGroupsEnabled(scopeInfo)) {
      return null;
    }
    GovernanceMetadata firstWarning = null;
    Map<String, List<UserMembership>> userMemberships = hasUserGroupsToEvaluate(userGroups)
        ? fetchUserMemberships(scopeInfo.getAccountIdentifier(), userIdsToAdd)
        : null;
    for (UserGroup userGroup : userGroups) {
      if (shouldSkipOpaEvaluation(userGroup)) {
        continue;
      }
      UserGroupDTO postAdditionDTO = UserGroupMapper.toDTO(scopeInfo, userGroup);
      Set<String> updatedUsers =
          postAdditionDTO.getUsers() != null ? new HashSet<>(postAdditionDTO.getUsers()) : new HashSet<>();
      updatedUsers.addAll(userIdsToAdd);
      postAdditionDTO.setUsers(new ArrayList<>(updatedUsers));
      GovernanceMetadata metadata =
          evaluateWithOpa(scopeInfo, postAdditionDTO, action, Optional.of(userGroup), userMemberships);
      if (metadata == null) {
        continue;
      }
      if (OPA_STATUS_ERROR.equals(metadata.getStatus())) {
        return metadata;
      }
      if (OPA_STATUS_WARNING.equals(metadata.getStatus()) && firstWarning == null) {
        firstWarning = metadata;
      }
    }
    return firstWarning;
  }

  @Override
  public GovernanceMetadata findFirstOpaFailureForUserGroupMembershipUpdate(
      ScopeInfo scopeInfo, List<UserGroup> groupsToAdd, List<UserGroup> groupsToRemove, String userId, String action) {
    if (!isOpaForUserGroupsEnabled(scopeInfo)) {
      return null;
    }
    Map<String, List<UserMembership>> userMemberships =
        hasUserGroupsToEvaluate(groupsToAdd) || hasUserGroupsToEvaluate(groupsToRemove)
        ? fetchUserMemberships(scopeInfo.getAccountIdentifier(), Set.of(userId))
        : null;
    GovernanceMetadata additionResult = checkGroupAddition(scopeInfo, groupsToAdd, userId, action, userMemberships);
    if (additionResult != null && OPA_STATUS_ERROR.equals(additionResult.getStatus())) {
      return additionResult;
    }
    GovernanceMetadata removalResult = checkGroupRemoval(scopeInfo, groupsToRemove, userId, action, userMemberships);
    if (removalResult != null && OPA_STATUS_ERROR.equals(removalResult.getStatus())) {
      return removalResult;
    }
    if (additionResult != null) {
      return additionResult;
    }
    return removalResult;
  }

  // groupsToAdd and groupsToRemove are always disjoint — a group is either gaining or losing the user, never both.
  // This is safe to evaluate separately because no single group's post-state depends on the other set.
  private GovernanceMetadata checkGroupAddition(ScopeInfo scopeInfo, List<UserGroup> groups, String userId,
      String action, Map<String, List<UserMembership>> userMemberships) {
    GovernanceMetadata firstWarning = null;
    for (UserGroup userGroup : groups) {
      if (shouldSkipOpaEvaluation(userGroup)) {
        continue;
      }
      UserGroupDTO postStateDTO = UserGroupMapper.toDTO(scopeInfo, userGroup);
      List<String> updatedUsers =
          new ArrayList<>(postStateDTO.getUsers() != null ? postStateDTO.getUsers() : Collections.emptyList());
      if (!updatedUsers.contains(userId)) {
        updatedUsers.add(userId);
      }
      postStateDTO.setUsers(updatedUsers);
      GovernanceMetadata metadata =
          evaluateWithOpa(scopeInfo, postStateDTO, action, Optional.of(userGroup), userMemberships);
      if (metadata == null) {
        continue;
      }
      if (OPA_STATUS_ERROR.equals(metadata.getStatus())) {
        return metadata;
      }
      if (OPA_STATUS_WARNING.equals(metadata.getStatus()) && firstWarning == null) {
        firstWarning = metadata;
      }
    }
    return firstWarning;
  }

  private GovernanceMetadata checkGroupRemoval(ScopeInfo scopeInfo, List<UserGroup> groups, String userId,
      String action, Map<String, List<UserMembership>> userMemberships) {
    GovernanceMetadata firstWarning = null;
    for (UserGroup userGroup : groups) {
      if (shouldSkipOpaEvaluation(userGroup)) {
        continue;
      }
      UserGroupDTO postStateDTO = UserGroupMapper.toDTO(scopeInfo, userGroup);
      List<String> updatedUsers =
          new ArrayList<>(postStateDTO.getUsers() != null ? postStateDTO.getUsers() : Collections.emptyList());
      updatedUsers.remove(userId);
      postStateDTO.setUsers(updatedUsers);
      GovernanceMetadata metadata =
          evaluateWithOpa(scopeInfo, postStateDTO, action, Optional.of(userGroup), userMemberships);
      if (metadata == null) {
        continue;
      }
      if (OPA_STATUS_ERROR.equals(metadata.getStatus())) {
        return metadata;
      }
      if (OPA_STATUS_WARNING.equals(metadata.getStatus()) && firstWarning == null) {
        firstWarning = metadata;
      }
    }
    return firstWarning;
  }

  private boolean hasUserGroupsToEvaluate(List<UserGroup> userGroups) {
    return userGroups.stream().anyMatch(userGroup -> !shouldSkipOpaEvaluation(userGroup));
  }

  private void addScopeFieldsOnDto(ScopeInfo scopeInfo, UserGroupDTO userGroupDTO) {
    userGroupDTO.setAccountIdentifier(scopeInfo.getAccountIdentifier());
    userGroupDTO.setOrgIdentifier(scopeInfo.getOrgIdentifier());
    userGroupDTO.setProjectIdentifier(scopeInfo.getProjectIdentifier());
  }

  public boolean isOpaForUserGroupsEnabled(ScopeInfo scopeInfo) {
    return ngFeatureFlagHelperService.isEnabled(
        scopeInfo.getAccountIdentifier(), FeatureName.PL_ENABLE_OPA_FOR_USER_GROUPS);
  }

  private boolean shouldSkipOpaEvaluation(UserGroup userGroup) {
    return userGroup.isHarnessManaged() || userGroup.isExternallyManaged();
  }

  private UserGroupOpaEvaluationContext createEvaluationContext(String userGroupYaml, String key) throws IOException {
    return UserGroupOpaEvaluationContext.builder()
        .userGroup(OpaUtils.extractObjectFromYamlString(userGroupYaml, key))
        .build();
  }

  private Map<String, List<UserMembership>> fetchUserMemberships(String accountIdentifier, Set<String> userIds) {
    if (isEmpty(userIds)) {
      return Collections.emptyMap();
    }
    Criteria criteria = Criteria.where(UserMembershipKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(UserMembershipKeys.userId)
                            .in(userIds);
    List<UserMembership> memberships = userMembershipRepository.findAllWithCriteria(criteria);
    Map<String, List<UserMembership>> membershipsByUser = new HashMap<>();
    for (UserMembership membership : memberships) {
      membershipsByUser.computeIfAbsent(membership.getUserId(), k -> new ArrayList<>()).add(membership);
    }
    return membershipsByUser;
  }

  private Map<String, List<UserMembership>> getUserMembershipsForChangedUsers(
      Map<String, List<UserMembership>> userMemberships, Set<String> changedUsers) {
    if (isEmpty(changedUsers)) {
      return Collections.emptyMap();
    }
    Map<String, List<UserMembership>> changedUserMemberships = new HashMap<>();
    for (String userId : changedUsers) {
      List<UserMembership> memberships = userMemberships.get(userId);
      if (memberships != null) {
        changedUserMemberships.put(userId, memberships);
      }
    }
    return changedUserMemberships;
  }

  private String getUserGroupYaml(UserGroupDTO userGroupDTO, Map<String, List<UserMembership>> userMemberships)
      throws IOException {
    String userGroupYaml = null;
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()
                                                     .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                                     .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                                     .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID));
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    try {
      Map<String, Object> userGroupMap = objectMapper.convertValue(userGroupDTO, Map.class);
      userGroupMap.put("userMemberships", userMemberships);
      userGroupYaml = objectMapper.writeValueAsString(userGroupMap);
    } catch (Exception e) {
      throw new IOException("Failed while converting to user-group yaml", e);
    }
    return userGroupYaml;
  }
}
