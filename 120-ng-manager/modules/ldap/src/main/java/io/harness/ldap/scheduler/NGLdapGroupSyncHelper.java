/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.scheduler;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.common.beans.UserSource.LDAP;
import static io.harness.ng.core.utils.UserGroupMapper.toDTO;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.ds.remote.DSLdapUserResponse;
import io.harness.ds.remote.DirectoryServiceResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.CreateUserDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.invites.InviteType;
import io.harness.ng.core.invites.api.InviteService;
import io.harness.ng.core.invites.entities.Invite;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserMembershipUpdateSource;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.user.remote.UserClient;
import io.harness.user.remote.UserFilterNG;

import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapUserResponse;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.SetUtils;

@OwnedBy(PL)
@Slf4j
public class NGLdapGroupSyncHelper {
  @Inject private NgUserService ngUserService;
  @Inject private InviteService inviteService;
  @Inject private UserClient userClient;
  @Inject private UserGroupService userGroupService;
  @Inject private ScopeInfoService scopeInfoService;
  @Inject private DirectoryServiceResourceClient directoryServiceResourceClient;
  @Inject private FeatureFlagService featureFlagService;

  public void reconcileAllUserGroups(
      Map<UserGroup, LdapGroupResponse> userGroupLdapGroupResponseMap, String ssoId, String accountId) {
    List<UserGroup> failedUserGroups = new ArrayList<>();
    Set<String> uniqueIds =
        userGroupLdapGroupResponseMap.keySet().stream().map(UserGroup::getParentUniqueId).collect(Collectors.toSet());
    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeInfoService.getScopeInfo(accountId, uniqueIds);
    for (Map.Entry<UserGroup, LdapGroupResponse> responseEntry : userGroupLdapGroupResponseMap.entrySet()) {
      UserGroup userGroup = responseEntry.getKey();
      ScopeInfo scopeInfo = scopeInfoMap.get(userGroup.getParentUniqueId()).orElseThrow();
      reconcileUserGroupWithLdapGroup(scopeInfo, userGroup, responseEntry.getValue(), ssoId, failedUserGroups);
    }
    log.info("NGLDAP: LDAP Sync for all linked groups in account {}, where the total count of groups are: {}, and has "
            + "failed for: {} groups",
        accountId, userGroupLdapGroupResponseMap.size(), failedUserGroups.size());
  }

  private void reconcileUserGroupWithLdapGroup(ScopeInfo scopeInfo, UserGroup userGroup, LdapGroupResponse ldapGroup,
      String ssoId, List<UserGroup> failedUserGroups) {
    log.info("NGLDAP: Starting sync for user group {}, in account {}, with corresponding ldap group dn {}. The number "
            + "of users returned for LdapGroup is: {}",
        userGroup.getIdentifier(), userGroup.getAccountIdentifier(), ldapGroup.getDn(), ldapGroup.getTotalMembers());
    try {
      syncUserGroupMetadata(scopeInfo, userGroup, ldapGroup);
      Set<String> ldapUserEmails = ldapGroup.getUsers()
                                       .stream()
                                       .map(LdapUserResponse::getEmail)
                                       .filter(Objects::nonNull)
                                       .collect(Collectors.toSet());

      log.info("NGLDAP: Users email list received from LDAP on group dn {}, linked to user group {} are {}",
          ldapGroup.getDn(), userGroup.getIdentifier(), getStringBuilderForEmails(ldapUserEmails).toString());
      List<UserInfo> usersInfo = new ArrayList<>();

      // can cause issue, check if our APIs support querying ~1K list of users
      if (isNotEmpty(userGroup.getUsers())) {
        log.info("NGLDAP: Get list of CG users for user group: {}, which has user count: {} in account: {}",
            userGroup.getIdentifier(), userGroup.getUsers().size(), userGroup.getAccountIdentifier());
        usersInfo.addAll(ngUserService.listCurrentGenUsers(
            userGroup.getAccountIdentifier(), UserFilterNG.builder().userIds(userGroup.getUsers()).build()));
      }

      Map<String, UserInfo> emailToUserInfoMap = new HashMap<>();
      Set<String> userGroupUserEmails = new HashSet<>();

      for (UserInfo info : usersInfo) {
        if (isNotEmpty(info.getEmail())) {
          emailToUserInfoMap.put(info.getEmail().toLowerCase(), info);
          userGroupUserEmails.add(info.getEmail().toLowerCase());
        }
      }

      log.info("NGLDAP: Users email after getting user records from CG for user group {}, has received count: {} and "
              + "are having emails as {}",
          userGroup.getIdentifier(), userGroupUserEmails.size(),
          getStringBuilderForEmails(userGroupUserEmails).toString());

      Set<String> usersToRemove = SetUtils.difference(userGroupUserEmails, ldapUserEmails);
      Set<String> usersToAdd = SetUtils.difference(ldapUserEmails, userGroupUserEmails);

      log.info("NGLDAP: Count of users getting added to user group- {} : {}. Emails: {}", userGroup.getIdentifier(),
          usersToAdd.size(), usersToAdd);
      log.info("NGLDAP: Count of users getting deleted from user group- {} : {}. Emails: {}", userGroup.getIdentifier(),
          usersToRemove.size(), usersToRemove);

      for (LdapUserResponse userResponse : ldapGroup.getUsers()) {
        try {
          if (usersToAdd.contains(userResponse.getEmail())) {
            // add to userGroup
            addMemberToGroup(scopeInfo, userGroup, userResponse);
          } else {
            // update user name
            updateUserInGroup(userGroup, userResponse, scopeInfo);
          }
        } catch (InvalidRequestException exception) {
          log.debug("NGLDAP: Skipping : Add/update user with ldap externalUserId {}, email: {} to User group: {} in "
                  + "account: {}, organization: {}, project: {} failed with exception {}",
              userResponse.getUserId(), userResponse.getEmail(), userGroup.getIdentifier(),
              scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(),
              exception.getMessage());
        } catch (Exception exception) {
          log.warn("NGLDAP: Skipping : Add/update user with ldap externalUserId {}, email: {} to User group: {} in "
                  + "account: {}, organization: {}, project: {} failed with exception {}",
              userResponse.getUserId(), userResponse.getEmail(), userGroup.getIdentifier(),
              scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(),
              exception.getMessage());
        }
      }

      if (isNotEmpty(usersToRemove)) {
        removeUserFromGroup(scopeInfo, userGroup, emailToUserInfoMap, usersToRemove);
      }
    } catch (Exception exc) {
      log.error("NGLDAP: Sync Error while updating user Group or its users {}: in account {} : ",
          userGroup.getIdentifier(), userGroup.getAccountIdentifier(), exc);
      failedUserGroups.add(userGroup);
    }
  }

  private void updateUserInGroup(UserGroup userGroup, LdapUserResponse userResponse, ScopeInfo scopeInfo) {
    if (isNotEmpty(userResponse.getEmail())) {
      if (featureFlagService.isEnabled(FeatureName.PL_ENABLE_DS_LDAP_SYNC, userGroup.getAccountIdentifier())) {
        NGRestUtils.getResponse(directoryServiceResourceClient.createUsersInDS(scopeInfo.getAccountIdentifier(),
            scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), userResponse));
      }
      log.debug("NGLDAP: updating user {}, in group: {} for account {} and externalUserId {}", userResponse.getEmail(),
          userGroup.getIdentifier(), userGroup.getAccountIdentifier(), userResponse.getUserId());
      CGRestUtils.getResponse(userClient.updateUser(
          UserInfo.builder().name(userResponse.getName()).email(userResponse.getEmail()).build()));
    }
  }

  private void removeUserFromGroup(
      ScopeInfo scopeInfo, UserGroup userGroup, Map<String, UserInfo> emailToUserInfoMap, Set<String> usersToRemove) {
    for (String emailStr : usersToRemove) {
      String userId = emailToUserInfoMap.containsKey(emailStr) ? emailToUserInfoMap.get(emailStr).getUuid() : null;
      if (isNotEmpty(userId)) {
        log.info("NGLDAP: removing user {}, from group: {} for account {}", userId, userGroup.getIdentifier(),
            userGroup.getAccountIdentifier());
        try {
          userGroupService.removeMember(scopeInfo, userGroup.getIdentifier(), userId);
        } catch (Exception exception) {
          log.error("NGLDAP: Skipping : Remove user with harness userId {}, email: {} to User group: {} in account: "
                  + "{}, organization: {}, project: {} failed",
              userId, emailStr, userGroup, scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
              scopeInfo.getProjectIdentifier(), exception);
        }
      }
    }
  }

  private void addMemberToGroup(ScopeInfo scopeInfo, UserGroup userGroup, LdapUserResponse userResponse) {
    if (isNotEmpty(userResponse.getEmail())) {
      Optional<UserInfo> userInfoCg = ngUserService.getUserInfoByEmailFromCG(userResponse.getEmail());
      if (userInfoCg.isEmpty() || !checkUserPartOfAccount(userGroup.getAccountIdentifier(), userInfoCg)) {
        if (featureFlagService.isEnabled(FeatureName.PL_ENABLE_DS_LDAP_SYNC, scopeInfo.getAccountIdentifier())) {
          createUserInDS(scopeInfo, userResponse);
        } else {
          inviteUserToAccount(userResponse, userGroup.getAccountIdentifier());
        }
      }

      Optional<UserMetadataDTO> userInfoNg = ngUserService.getUserByEmail(userResponse.getEmail(), false);

      if (userInfoCg.isPresent() && userInfoNg.isEmpty()) {
        log.info(
            "NGLDAP: User {} with externalUserId {}, not present in NG. Adding to NG at user group {}, in account: {}.",
            userInfoCg.get().getUuid(), userResponse.getUserId(), userGroup.getIdentifier(),
            userGroup.getAccountIdentifier());
        // ScopeInfo being passed in reconcileUserGroupWithLdapGroup is for user group
        userInfoNg = addUserToScopeAndReturnMetadataDTO(userResponse, userInfoCg.get().getUuid(),
            scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(),
            scopeInfo);
      }

      boolean checkUserPartOfAccountInNg = checkUserPartOfAccountInNg(scopeInfo, userInfoNg.get());

      if (userInfoNg.isPresent() && !checkUserPartOfAccountInNg) {
        log.info("NGLDAP: User {} with externalUserId {}, present in NG but not in this account {}. Adding to NG for "
                + "the user group {}.",
            userInfoCg.get().getUuid(), userResponse.getUserId(), userGroup.getAccountIdentifier(),
            userGroup.getIdentifier());
        userInfoNg = addUserToScopeAndReturnMetadataDTO(userResponse, userInfoNg.get().getUuid(),
            scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(),
            scopeInfo);
      }

      if (userInfoNg.isEmpty() || !checkUserPartOfAccountInNg(scopeInfo, userInfoNg.get())) {
        log.warn("NGLDAP: Invite user with ldap externalUserId {}, or adding user to scope- account: {}, organization: "
                + "{}, project: {} failed",
            userResponse.getUserId(), scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
            scopeInfo.getProjectIdentifier());
        // throw here to be caught above and added to 'failedUserGroups' count
        throw new IllegalStateException("NGLDAP: Illegal state value of user to be added as member to user group");
      }

      log.info("NGLDAP: adding new user {} having email: {} to group: {} in account {} and externalUserId {}",
          userInfoNg.get().getUuid(), userInfoNg.get().getEmail(), userGroup.getIdentifier(),
          userGroup.getAccountIdentifier(), userResponse.getUserId());
      userGroupService.addMember(scopeInfo, userGroup.getIdentifier(), userInfoNg.get().getUuid());
    }
  }
  private void createUserInDS(ScopeInfo scopeInfo, LdapUserResponse ldapUserResponse) {
    try {
      DSLdapUserResponse dsLdapUserResponse =
          NGRestUtils.getResponse(directoryServiceResourceClient.createUsersInDS(scopeInfo.getAccountIdentifier(),
              scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), ldapUserResponse));
      CreateUserDTO createUserDTO = CreateUserDTO.builder()
                                        .accountIdentifier(scopeInfo.getAccountIdentifier())
                                        .userId(dsLdapUserResponse.getUserId())
                                        .externalId(dsLdapUserResponse.getUserId())
                                        .name(dsLdapUserResponse.getName())
                                        .email(dsLdapUserResponse.getEmail())
                                        .build();
      boolean isCreated = ngUserService.createUserForDS(createUserDTO);
      if (!isCreated) {
        log.warn("NGLDAP : Failed to create user {} in directory service", ldapUserResponse.getEmail());
      }
    } catch (Exception e) {
      log.warn("NGLDAP : Failed to create user {} in directory service", ldapUserResponse.getEmail());
    }
  }
  private boolean checkUserPartOfAccount(String accountId, Optional<UserInfo> userInfoOptional) {
    if (userInfoOptional.isPresent() && isNotEmpty(userInfoOptional.get().getAccounts())) {
      return userInfoOptional.get().getAccounts().stream().anyMatch(account -> accountId.equals(account.getUuid()));
    }
    return false;
  }

  private boolean checkUserPartOfAccountInNg(ScopeInfo scopeInfo, UserMetadataDTO userOptional) {
    return ngUserService.isUserAtScope(userOptional.getUuid(), scopeInfo);
  }

  private Optional<UserMetadataDTO> addUserToScopeAndReturnMetadataDTO(LdapUserResponse userResponse, final String uuid,
      final String accountId, final String orgId, final String projectId, ScopeInfo scopeInfo) {
    log.info("NGLDAP: adding user {} with externalUserId {}, to scope- account: {}, organization: {}, project: {}",
        uuid, userResponse.getUserId(), accountId, orgId, projectId);
    ngUserService.addUserToScope(uuid, Scope.of(accountId, orgId, projectId), emptyList(), emptyList(),
        UserMembershipUpdateSource.SYSTEM, scopeInfo);
    ngUserService.updateNGUserToCGWithSource(uuid, Scope.builder().accountIdentifier(accountId).build(), LDAP);
    return ngUserService.getUserByEmail(userResponse.getEmail(), false);
  }

  private void inviteUserToAccount(LdapUserResponse ldapUserResponse, String accountId) {
    Invite invite = Invite.builder()
                        .accountIdentifier(accountId)
                        .approved(true)
                        .email(ldapUserResponse.getEmail())
                        .name(ldapUserResponse.getName())
                        .inviteType(InviteType.ADMIN_INITIATED_INVITE)
                        .build();
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).scopeType(ScopeLevel.ACCOUNT).build();
    invite.setRoleBindings(emptyList());
    log.info("NGLDAP: creating user invite for account {} and user Invite {} and externalUserId {}", accountId,
        invite.getEmail(), ldapUserResponse.getUserId());
    inviteService.create(scopeInfo, invite, false, true);
  }

  private void syncUserGroupMetadata(ScopeInfo scopeInfo, UserGroup userGroup, LdapGroupResponse groupResponse) {
    UserGroupDTO userGroupDTO = toDTO(scopeInfo, userGroup);
    if (null != userGroupDTO.getSsoGroupName() && null != groupResponse.getName()
        && groupResponse.getName().equals(userGroupDTO.getSsoGroupName())) {
      return;
    }
    userGroupDTO.setSsoGroupName(groupResponse.getName());
    log.info("NGLDAP: Updating user group {} in account {} with name: {}", userGroup.getIdentifier(),
        userGroup.getAccountIdentifier(), groupResponse.getName());
    userGroupService.update(scopeInfo, userGroupDTO);
  }

  private StringBuilder getStringBuilderForEmails(Set<String> emails) {
    StringBuilder sb = new StringBuilder();
    if (isNotEmpty(emails)) {
      emails.forEach(id -> sb.append(id).append(" "));
    }
    return sb;
  }
}
