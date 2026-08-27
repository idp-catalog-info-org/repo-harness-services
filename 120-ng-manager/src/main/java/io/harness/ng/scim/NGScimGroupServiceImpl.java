/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scim;

import static io.harness.NGConstants.CREATED;
import static io.harness.NGConstants.DISPLAY_NAME;
import static io.harness.NGConstants.LAST_MODIFIED;
import static io.harness.NGConstants.LOCATION;
import static io.harness.NGConstants.RESOURCE_TYPE;
import static io.harness.NGConstants.VERSION;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.PL_NEW_SCIM_STANDARDS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.GROUP;
import static io.harness.ng.core.dto.UserGroupDTO.UserGroupDTOBuilder;
import static io.harness.ng.core.user.entities.UserGroup.UserGroupKeys;
import static io.harness.ng.core.utils.UserGroupMapper.toDTO;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupUpdateRequest;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.entities.UserMetadata;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.scim.Member;
import io.harness.scim.PatchOperation;
import io.harness.scim.PatchRequest;
import io.harness.scim.ScimGroup;
import io.harness.scim.ScimListResponse;
import io.harness.scim.ScimMultiValuedObject;
import io.harness.scim.service.ScimGroupService;
import io.harness.serializer.JsonUtils;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeUtils;
import io.harness.utils.UuidAndIdentifierUtils;

import com.google.inject.Inject;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class NGScimGroupServiceImpl implements ScimGroupService {
  @Inject private UserGroupService userGroupService;
  @Inject private NgUserService ngUserService;
  @Inject private ScopeInfoService scopeInfoService;

  private final PmsFeatureFlagHelper ngFeatureFlagHelperService;

  private static final String EXC_MSG_GROUP_DOESNT_EXIST = "Group does not exist";
  private static final Integer MAX_RESULT_COUNT = 20;
  private static final String REPLACE_OKTA = "replace";
  private static final String REPLACE = "Replace";
  private static final String ADD = "Add";
  private static final String ADD_OKTA = "add";
  private static final String REMOVE = "Remove";
  private static final String REMOVE_OKTA = "remove";
  private static final int USER_GROUP_IDENTIFIER_MAX_LENGTH = 128;
  private static final int USER_GROUP_IDENTIFIER_HASH_LENGTH = 8;
  private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  @Override
  public ScimListResponse<ScimGroup> searchGroup(String filter, String accountId, Integer count, Integer startIndex) {
    startIndex = startIndex == null ? 0 : startIndex;
    Integer tempStartIndex = startIndex == 0 ? 0 : startIndex - 1;
    count = count == null ? MAX_RESULT_COUNT : count;
    ScimListResponse<ScimGroup> searchGroupResponse = new ScimListResponse<>();
    log.info("NGSCIM: Searching groups in account {} with filter: {}", accountId, filter);
    String searchQuery = null;

    if (StringUtils.isNotEmpty(filter)) {
      try {
        filter = URLDecoder.decode(filter, "UTF-8");
        String[] split = filter.split(" eq ");
        String operand = split[1];
        searchQuery = operand.substring(1, operand.length() - 1);
        log.info("NGSCIM: Search query is {}, for accountId {}", searchQuery, accountId);
      } catch (Exception ex) {
        log.error("NGSCIM: Failed to process for account {} group search query: {} ", accountId, filter, ex);
      }
    }
    List<ScimGroup> groupList = new ArrayList<>();

    try {
      groupList = searchUserGroupByGroupName(accountId, searchQuery, count, tempStartIndex);
      groupList.forEach(searchGroupResponse::resource);
    } catch (WingsException ex) {
      log.info("NGSCIM: Search in account {} for group , query: {}", accountId, searchQuery, ex);
    }

    searchGroupResponse.startIndex(startIndex);
    searchGroupResponse.itemsPerPage(count);
    searchGroupResponse.totalResults(groupList.size());
    log.info("NGSCIM: Search Group Response is {}, for accountId {}",
        searchGroupResponse.getResources().stream().map(ScimGroup::getDisplayName).collect(Collectors.toList()),
        accountId);
    return searchGroupResponse;
  }

  private List<ScimGroup> searchUserGroupByGroupName(
      String accountId, String searchQuery, Integer count, Integer startIndex) {
    List<UserGroup> userGroupList;
    List<ScimGroup> scimGroupList = new ArrayList<>();

    if (StringUtils.isNotEmpty(searchQuery)) {
      userGroupList = userGroupService.list(Criteria.where(UserGroupKeys.accountIdentifier)
                                                .is(accountId)
                                                .and(UserGroupKeys.parentUniqueId)
                                                .is(accountId)
                                                .and(UserGroupKeys.name)
                                                .is(searchQuery)
                                                .and(UserGroupKeys.externallyManaged)
                                                .is(Boolean.TRUE),
          startIndex, count);

    } else {
      userGroupList = userGroupService.list(Criteria.where(UserGroupKeys.accountIdentifier)
                                                .is(accountId)
                                                .and(UserGroupKeys.parentUniqueId)
                                                .is(accountId)
                                                .and(UserGroupKeys.externallyManaged)
                                                .is(Boolean.TRUE),
          startIndex, count);
    }
    if (isNotEmpty(userGroupList)) {
      for (UserGroup userGroup : userGroupList) {
        scimGroupList.add(buildGroupResponse(userGroup, accountId));
      }
    }
    return scimGroupList;
  }

  private ScimGroup buildGroupResponse(UserGroup userGroup, String accountId) {
    ScimGroup scimGroup = new ScimGroup();
    if (userGroup != null) {
      scimGroup.setId(userGroup.getIdentifier());
      scimGroup.setDisplayName(userGroup.getName());
      List<Member> memberList = new ArrayList<>();
      ScopeInfo scopeInfo =
          scopeInfoService.getScopeInfo(userGroup.getAccountIdentifier(), Set.of(userGroup.getParentUniqueId()))
              .get(userGroup.getParentUniqueId())
              .orElseThrow();
      try (Stream<UserMetadata> stream = userGroupService.getUsersInUserGroup(scopeInfo, userGroup.getIdentifier())) {
        Iterator<UserMetadata> iterator = stream.iterator();
        while (null != iterator && iterator.hasNext()) {
          UserMetadata member = iterator.next();
          Member memberTemp = new Member();
          memberTemp.setValue(member.getUserId());
          memberTemp.setDisplay(member.getEmail());
          memberTemp.setRef(URI.create(""));
          memberList.add(memberTemp);
        }
      }

      if (ngFeatureFlagHelperService.isEnabled(accountId, PL_NEW_SCIM_STANDARDS)) {
        Map<String, String> metaMap = new HashMap<String, String>() {
          {
            put(RESOURCE_TYPE, "Group");
            put(CREATED, simpleDateFormat.format(new Date(userGroup.getCreatedAt())));
            put(LAST_MODIFIED, simpleDateFormat.format(new Date(userGroup.getLastModifiedAt())));
            put(VERSION, "");
            put(LOCATION, "");
          }
        };
        scimGroup.setMeta(JsonUtils.asTree(metaMap));
      }
      scimGroup.setMembers(memberList);
    }
    return scimGroup;
  }

  @Override
  public Response updateGroup(String groupId, String accountId, ScimGroup scimGroup) {
    log.info("NGSCIM: Update group call with accountId: {}, groupIdentifier {}, group resource:{}", accountId, groupId,
        scimGroup);
    List<UserGroup> existingUserGroupList;
    existingUserGroupList = userGroupService.list(Criteria.where(UserGroupKeys.identifier)
                                                      .is(groupId)
                                                      .and(UserGroupKeys.accountIdentifier)
                                                      .is(accountId)
                                                      .and(UserGroupKeys.parentUniqueId)
                                                      .is(accountId)
                                                      .and(UserGroupKeys.externallyManaged)
                                                      .is(true),
        null, null);

    if (!isNotEmpty(existingUserGroupList)) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    // ScopeInfo is always account for scim groups
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).scopeType(ScopeLevel.ACCOUNT).build();
    for (UserGroup existingUserGroup : existingUserGroupList) {
      UserGroupDTO userGroupDTO = toDTO(scopeInfo, existingUserGroup);
      userGroupDTO.setName(scimGroup.getDisplayName());
      userGroupDTO.setUsers(fetchMembersOfUserGroup(scimGroup));
      userGroupService.update(scopeInfo, userGroupDTO);
    }
    log.info("NGSCIM: Member userIds provided by the SCIM Provider are {}, for account {}",
        fetchMembersOfUserGroup(scimGroup), accountId);
    log.info("NGSCIM: Update group call successful accountId {}, groupId  {}, group resource: {}", accountId, groupId,
        scimGroup);
    return Response.status(Response.Status.OK).entity(scimGroup).build();
  }

  @Override
  public void deleteGroup(String groupId, String accountId) {
    List<UserGroup> userGroupList;
    userGroupList = userGroupService.list(Criteria.where(UserGroupKeys.identifier)
                                              .is(groupId)
                                              .and(UserGroupKeys.accountIdentifier)
                                              .is(accountId)
                                              .and(UserGroupKeys.parentUniqueId)
                                              .is(accountId)
                                              .and(UserGroupKeys.externallyManaged)
                                              .is(true),
        null, null);

    if (!isNotEmpty(userGroupList)) {
      throw new UnauthorizedException(EXC_MSG_GROUP_DOESNT_EXIST, GROUP);
    }
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).scopeType(ScopeLevel.ACCOUNT).build();
    for (UserGroup userGroupToBeDeleted : userGroupList) {
      userGroupService.delete(scopeInfo, userGroupToBeDeleted.getIdentifier());
      log.info("NGSCIM: Deleted from account {}, group {} and scope {}", accountId,
          userGroupToBeDeleted.getIdentifier(), ScopeUtils.toString(scopeInfo));
    }
  }

  private String processReplaceOperationOnGroup(String groupId, String accountId, PatchOperation patchOperation) {
    if (!DISPLAY_NAME.equals(patchOperation.getPath())) {
      log.error("NGSCIM: Expected replace operation only on the displayName. Received it on path: {}, for accountId: "
              + "{}, for GroupId {}",
          patchOperation.getPath(), accountId, groupId);
      // no operation needed. Pass
    } else {
      try {
        return patchOperation.getValue(String.class);
      } catch (Exception ex) {
        log.error("NGSCIM: Failed to process the operation: {}, for accountId: {}, for GroupId {}",
            patchOperation.toString(), accountId, groupId, ex);
      }
    }
    throw new InvalidRequestException("Failed to update group name");
  }

  private String processOktaReplaceOperationOnGroup(String groupId, String accountId, PatchOperation patchOperation) {
    try {
      if (patchOperation.getValue(ScimMultiValuedObject.class) != null) {
        return patchOperation.getValue(ScimMultiValuedObject.class).getDisplayName();
      }
    } catch (Exception ex) {
      log.error("NGSCIM: Failed to process the REPLACE_OKTA operation: {}, for accountId: {}, for GroupId {}",
          patchOperation.toString(), accountId, groupId, ex);
      throw new InvalidRequestException("Failed to update group name");
    }
    return null;
  }

  private void processMemberReplaceOperation(String groupId, String accountId, List<UserGroup> existingUserGroupList,
      ScopeInfo scopeInfo, List<String> userIdsToRemove, Set<String> userIdsFromOperation, List<String> userIdsToAdd) {
    log.info("NGSCIM: Processing member replace operation for groupId: {}, accountId: {}", groupId, accountId);

    Set<String> existingUserIds = new HashSet<>();
    for (UserGroup userGroup : existingUserGroupList) {
      try (Stream<UserMetadata> stream = userGroupService.getUsersInUserGroup(scopeInfo, userGroup.getIdentifier())) {
        Iterator<UserMetadata> iterator = stream.iterator();
        while (iterator != null && iterator.hasNext()) {
          UserMetadata member = iterator.next();
          existingUserIds.add(member.getUserId());
        }
      }
    }

    Set<String> newUserIds = new HashSet<>(userIdsFromOperation);

    // Users to remove: exist currently but not in new set
    Set<String> toRemove = new HashSet<>(existingUserIds);
    toRemove.removeAll(newUserIds);
    userIdsToRemove.addAll(toRemove);

    // Users to add: in new set but don't exist currently
    Set<String> toAdd = new HashSet<>(newUserIds);
    toAdd.removeAll(existingUserIds);
    userIdsToAdd.addAll(toAdd);

    log.info("NGSCIM: Member replace operation - removing {} existing members, adding {} new members for groupId: {}",
        toRemove.size(), toAdd.size(), groupId);
  }

  @Override
  public Response updateGroup(String groupId, String accountId, PatchRequest patchRequest) {
    String operation = isNotEmpty(patchRequest.getOperations()) ? patchRequest.getOperations().toString() : null;
    String schemas = isNotEmpty(patchRequest.getSchemas()) ? patchRequest.getSchemas().toString() : null;
    log.info("NGSCIM: Updating Group: Patch Request Logging\nOperations {}\n, Schemas {}\n,External Id {}\n, Meta {}, "
            + "for accountId {}",
        operation, schemas, patchRequest.getExternalId(), patchRequest.getMeta(), accountId);
    List<UserGroup> existingUserGroupList;
    existingUserGroupList = userGroupService.list(Criteria.where(UserGroupKeys.identifier)
                                                      .is(groupId)
                                                      .and(UserGroupKeys.accountIdentifier)
                                                      .is(accountId)
                                                      .and(UserGroupKeys.parentUniqueId)
                                                      .is(accountId)
                                                      .and(UserGroupKeys.externallyManaged)
                                                      .is(true),
        null, null);

    if (!isNotEmpty(existingUserGroupList)) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    String newGroupName = null;
    List<String> userIdsToAdd = new ArrayList<>();
    List<String> userIdsToRemove = new ArrayList<>();

    // For all SCIM managed groups scope is always account
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(accountId).scopeType(ScopeLevel.ACCOUNT).uniqueId(accountId).build();

    for (PatchOperation patchOperation : patchRequest.getOperations()) {
      Set<String> userIdsFromOperation = getUserIdsFromOperation(patchOperation, accountId, groupId);
      switch (patchOperation.getOpType()) {
        case REPLACE: {
          if ("members".equals(patchOperation.getPath())) {
            processMemberReplaceOperation(groupId, accountId, existingUserGroupList, scopeInfo, userIdsToRemove,
                userIdsFromOperation, userIdsToAdd);
          } else {
            newGroupName = processReplaceOperationOnGroup(groupId, accountId, patchOperation);
          }
          break;
        }
        case REPLACE_OKTA: {
          if ("members".equals(patchOperation.getPath())) {
            processMemberReplaceOperation(groupId, accountId, existingUserGroupList, scopeInfo, userIdsToRemove,
                userIdsFromOperation, userIdsToAdd);
          } else {
            newGroupName = processOktaReplaceOperationOnGroup(groupId, accountId, patchOperation);
          }
          break;
        }
        case ADD:
        case ADD_OKTA: {
          userIdsToAdd.addAll(userIdsFromOperation);
          break;
        }
        case REMOVE:
        case REMOVE_OKTA: {
          userIdsToRemove.addAll(userIdsFromOperation);
          break;
        }
        default: {
          log.error("NGSCIM: Received unexpected PATCH operation: {}, for account id {}", patchOperation, accountId);
          break;
        }
      }
    }

    for (UserGroup userGroup : existingUserGroupList) {
      UserGroupDTO finalUserGroupDTO = toDTO(scopeInfo, userGroup);
      boolean updateGroup = false;

      if (StringUtils.isNotEmpty(newGroupName) || isNotEmpty(userIdsToAdd) || isNotEmpty(userIdsToRemove)) {
        updateGroup = true;
      }

      if (updateGroup) {
        UserGroupUpdateRequest userGroupUpdateRequest = UserGroupUpdateRequest.builder()
                                                            .identifier(finalUserGroupDTO.getIdentifier())
                                                            .name(newGroupName)
                                                            .accountIdentifier(accountId)
                                                            .orgIdentifier(finalUserGroupDTO.getOrgIdentifier())
                                                            .projectIdentifier(finalUserGroupDTO.getProjectIdentifier())
                                                            .usersToAdd(userIdsToAdd)
                                                            .usersToRemove(userIdsToRemove)
                                                            .build();
        userGroupService.update(scopeInfo, userGroupUpdateRequest);
      }
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  private Set<String> getUserIdsFromOperation(PatchOperation patchOperation, String accountId, String groupId) {
    if (patchOperation.getPath() != null && patchOperation.getPath().contains("members[")) {
      try {
        Set<String> userIds = new HashSet<>();
        String filter = URLDecoder.decode(patchOperation.getPath(), "UTF-8");
        String[] split = filter.split(" eq ");
        String operand = split[1];
        userIds.add(operand.substring(1, operand.length() - 2));
        return userIds;
      } catch (Exception ex) {
        log.error("NGSCIM: Not able to decode path. Received it in path: {}, for accountId: {}, for GroupId {}",
            patchOperation.getPath(), accountId, groupId, ex);
      }
    }

    if (!"members".equals(patchOperation.getPath())) {
      log.warn(
          "NGSCIM: Expect operation only on the members. Received it in path: {}, for accountId: {}, for GroupId {}",
          patchOperation.getPath(), accountId, groupId);
      return Collections.emptySet();
    }

    try {
      List<? extends ScimMultiValuedObject> operations = patchOperation.getValues(ScimMultiValuedObject.class);
      if (!isEmpty(operations)) {
        return operations.stream().map(operation -> (String) operation.getValue()).collect(Collectors.toSet());
      }
      log.error("NGSCIM: Operations received is null. Skipping remove operation processing for groupId: {}", groupId);
    } catch (Exception ex) {
      log.error("NGSCIM: Failed to process the operation: {}, for accountId: {}, for GroupId {}", patchOperation,
          accountId, groupId, ex);
    }

    return Collections.emptySet();
  }

  @Override
  public ScimGroup getGroup(String groupId, String accountId) {
    List<UserGroup> userGroupList;
    userGroupList = userGroupService.list(Criteria.where(UserGroupKeys.identifier)
                                              .is(groupId)
                                              .and(UserGroupKeys.accountIdentifier)
                                              .is(accountId)
                                              .and(UserGroupKeys.parentUniqueId)
                                              .is(accountId),
        null, null);

    if (!isNotEmpty(userGroupList)) {
      log.info("NGSCIM: UserGroup with id {} is not found in account {}", groupId, accountId);
      throw new UnauthorizedException(EXC_MSG_GROUP_DOESNT_EXIST, GROUP);
    }
    ScimGroup scimGroup = buildGroupResponse(userGroupList.get(0), accountId);
    log.info("NGSCIM: Response for accountId {} to get group {} with call: {}", accountId, groupId, scimGroup);
    return scimGroup;
  }

  @Override
  public ScimGroup createGroup(ScimGroup groupQuery, String accountId) {
    log.info("NGSCIM: Creating group in account {} where name {} with call: {}", accountId, groupQuery.getDisplayName(),
        groupQuery);
    String legacyUserGroupIdentifier = isNotEmpty(groupQuery.getDisplayName())
        ? UuidAndIdentifierUtils.generateHarnessUIFormatIdentifier(groupQuery.getDisplayName())
        : groupQuery.getDisplayName();
    String userGroupIdentifier = generateUserGroupIdentifier(groupQuery.getDisplayName(), legacyUserGroupIdentifier);
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).scopeType(ScopeLevel.ACCOUNT).build();

    // check if user group already exists with new identifier.
    Optional<UserGroup> userGroupOptional = userGroupService.get(scopeInfo, userGroupIdentifier);
    // Backward compatibility for groups created before hash suffix rollout.
    if (!userGroupOptional.isPresent() && isNotEmpty(legacyUserGroupIdentifier)
        && !StringUtils.equals(userGroupIdentifier, legacyUserGroupIdentifier)) {
      userGroupOptional = userGroupService.get(scopeInfo, legacyUserGroupIdentifier);
    }

    if (userGroupOptional.isPresent()) {
      UserGroup existingUserGroup = userGroupOptional.get();
      UserGroupDTO existingUserGroupDTO = toDTO(scopeInfo, existingUserGroup);

      UserGroup updatedUserGroup = existingUserGroup;
      if (!existingUserGroup.isExternallyManaged()) {
        existingUserGroupDTO.setUsers(fetchMembersOfUserGroup(groupQuery));
        existingUserGroupDTO.setExternallyManaged(true);
        updatedUserGroup = userGroupService.update(scopeInfo, existingUserGroupDTO);
      }
      return buildGroupResponse(updatedUserGroup, accountId);
    }

    UserGroupDTOBuilder userGroupDTOBuilder = UserGroupDTO.builder()
                                                  .name(groupQuery.getDisplayName())
                                                  .users(fetchMembersOfUserGroup(groupQuery))
                                                  .accountIdentifier(accountId)
                                                  .parentUniqueId(accountId)
                                                  .identifier(userGroupIdentifier)
                                                  .externallyManaged(true);
    UserGroup userGroupCreated = null;
    log.info("NGSCIM: User ids received for account {} from SCIM provider are {}", accountId,
        fetchMembersOfUserGroup(groupQuery));
    if (StringUtils.isNotEmpty(groupQuery.getHarnessScopes())) {
      String[] scopes = groupQuery.getHarnessScopes().split(",");
      log.info("NGSCIM: Harness Scopes of SCIM group are {}, for accountId {}", scopes, accountId);
      for (String scimScope : scopes) {
        String[] identifiers = scimScope.split(":");
        if (identifiers.length == 2) {
          userGroupDTOBuilder.orgIdentifier(identifiers[1]);
        }
        if (identifiers.length == 3) {
          userGroupDTOBuilder.orgIdentifier(identifiers[1]).projectIdentifier(identifiers[2]);
        }
        if (identifiers.length > 3) {
          log.info("NGSCIM: Skipping group creation unidentified scope");
          continue;
        }
        userGroupCreated = userGroupService.createForSCIM(scopeInfo, userGroupDTOBuilder.build());
      }
    } else {
      userGroupCreated = userGroupService.createForSCIM(scopeInfo, userGroupDTOBuilder.build());
    }

    ScimGroup scimGroup = buildGroupResponse(userGroupCreated, accountId);
    log.info("NGSCIM: Response for accountId {} to create group {} with call: {}", accountId,
        scimGroup.getDisplayName(), scimGroup);
    return scimGroup;
  }

  private String generateUserGroupIdentifier(String displayName, String normalizedIdentifier) {
    if (isEmpty(displayName) || isEmpty(normalizedIdentifier)) {
      return normalizedIdentifier;
    }
    String trimmedDisplayName = displayName.trim();
    if (trimmedDisplayName.isEmpty() || !Character.isDigit(trimmedDisplayName.charAt(0))) {
      return normalizedIdentifier;
    }
    if (StringUtils.equals(displayName, normalizedIdentifier)) {
      return normalizedIdentifier;
    }
    String shortHash = shortSha256Hash(displayName);
    int maxPrefixLength = USER_GROUP_IDENTIFIER_MAX_LENGTH - USER_GROUP_IDENTIFIER_HASH_LENGTH - 1;
    String normalizedPrefix = normalizedIdentifier.length() > maxPrefixLength
        ? normalizedIdentifier.substring(0, maxPrefixLength)
        : normalizedIdentifier;
    return normalizedPrefix + "_" + shortHash;
  }

  private String shortSha256Hash(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String hash = HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
      return hash.substring(0, USER_GROUP_IDENTIFIER_HASH_LENGTH);
    } catch (NoSuchAlgorithmException ex) {
      throw new InvalidRequestException("Unable to generate deterministic identifier hash", ex);
    }
  }

  private List<String> fetchMembersOfUserGroup(ScimGroup scimGroup) {
    List<String> newMemberIds = new ArrayList<>();
    if (isNotEmpty(scimGroup.getMembers())) {
      scimGroup.getMembers().forEach(member -> {
        if (!newMemberIds.contains(member.getValue())) {
          Optional<UserInfo> userInfoOptional = ngUserService.getUserById(member.getValue());
          if (userInfoOptional.isPresent()) {
            newMemberIds.add(member.getValue());
          } else {
            log.info("NGSCIM: No user exists with the id {}", member.getValue());
          }
        }
      });
    }
    return newMemberIds;
  }
}