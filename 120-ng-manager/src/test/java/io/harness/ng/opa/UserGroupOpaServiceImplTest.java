/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.opa;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.opaclient.model.OpaConstants.OPA_EVALUATION_ACTION_SAVE;
import static io.harness.opaclient.model.OpaConstants.OPA_EVALUATION_TYPE_USER_GROUP;
import static io.harness.opaclient.model.OpaConstants.OPA_STATUS_ERROR;
import static io.harness.opaclient.model.OpaConstants.OPA_STATUS_WARNING;
import static io.harness.rule.OwnerRule.ABHISHEK_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.entities.UserMembership;
import io.harness.ng.opa.entities.usergroup.UserGroupOpaEvaluationContext;
import io.harness.ng.opa.entities.usergroup.UserGroupOpaServiceImpl;
import io.harness.opa.OpaEvaluationContext;
import io.harness.opa.OpaService;
import io.harness.repositories.user.custom.UserMembershipRepositoryCustom;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public class UserGroupOpaServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "account";
  private static final String ORG_IDENTIFIER = "org";
  private static final String PROJECT_IDENTIFIER = "project";
  private static final String USER_GROUP_IDENTIFIER = "userGroup";
  private static final String OTHER_USER_GROUP_IDENTIFIER = "otherUserGroup";
  private static final ScopeInfo SCOPE_INFO = ScopeInfo.builder()
                                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJECT_IDENTIFIER)
                                                  .uniqueId(PROJECT_IDENTIFIER)
                                                  .scopeType(ScopeLevel.PROJECT)
                                                  .build();

  @Mock private OpaService opaService;
  @Mock private UserMembershipRepositoryCustom userMembershipRepository;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @InjectMocks private UserGroupOpaServiceImpl userGroupOpaService;

  @Before
  public void setup() {
    initMocks(this);
    when(ngFeatureFlagHelperService.isEnabled(ACCOUNT_IDENTIFIER, FeatureName.PL_ENABLE_OPA_FOR_USER_GROUPS))
        .thenReturn(true);
  }

  private UserGroup buildUserGroup(
      String identifier, List<String> users, boolean harnessManaged, boolean externallyManaged) {
    return UserGroup.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .parentUniqueId(PROJECT_IDENTIFIER)
        .identifier(identifier)
        .harnessManaged(harnessManaged)
        .externallyManaged(externallyManaged)
        .users(users)
        .build();
  }

  private void mockOpaEvaluate(GovernanceMetadata result) {
    when(opaService.evaluate(any(OpaEvaluationContext.class), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER),
             eq(PROJECT_IDENTIFIER), any(String.class), eq(OPA_EVALUATION_ACTION_SAVE),
             eq(OPA_EVALUATION_TYPE_USER_GROUP)))
        .thenReturn(result);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenEvaluateWithOpaAndFeatureFlagIsDisabledThenReturnNullWithoutEvaluatingPolicies() {
    UserGroupDTO userGroupDTO = UserGroupDTO.builder().identifier(USER_GROUP_IDENTIFIER).build();
    when(ngFeatureFlagHelperService.isEnabled(ACCOUNT_IDENTIFIER, FeatureName.PL_ENABLE_OPA_FOR_USER_GROUPS))
        .thenReturn(false);

    GovernanceMetadata result =
        userGroupOpaService.evaluateWithOpa(SCOPE_INFO, userGroupDTO, OPA_EVALUATION_ACTION_SAVE);

    assertThat(result).isNull();
    verifyNoInteractions(opaService);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenEvaluateWithOpaAndNoExistingGroupThenEvaluatePoliciesWithAllProvidedUsersAsChanged() {
    List<String> providedUsers = List.of("firstUser", "secondUser");
    UserGroupDTO userGroupDTO = UserGroupDTO.builder().identifier(USER_GROUP_IDENTIFIER).users(providedUsers).build();
    GovernanceMetadata expected = GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_WARNING).setDeny(false).build();
    mockOpaEvaluate(expected);

    GovernanceMetadata result =
        userGroupOpaService.evaluateWithOpa(SCOPE_INFO, userGroupDTO, OPA_EVALUATION_ACTION_SAVE);

    assertThat(result).isEqualTo(expected);
    verify(userMembershipRepository).findAllWithCriteria(any(Criteria.class));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenEvaluateWithOpaAndExistingUsersChangeThenEvaluatePoliciesWithAddedAndRemovedUsers() {
    String retainedUser = "retainedUser";
    String addedUser = "addedUser";
    String removedUser = "removedUser";
    UserGroup existingUserGroup =
        buildUserGroup(USER_GROUP_IDENTIFIER, List.of(retainedUser, removedUser), false, false);
    UserGroupDTO updatedUserGroup =
        UserGroupDTO.builder().identifier(USER_GROUP_IDENTIFIER).users(List.of(retainedUser, addedUser)).build();
    GovernanceMetadata expected = GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_WARNING).setDeny(false).build();
    ArgumentCaptor<OpaEvaluationContext> contextCaptor = ArgumentCaptor.forClass(OpaEvaluationContext.class);
    when(
        opaService.evaluate(contextCaptor.capture(), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER),
            eq(USER_GROUP_IDENTIFIER), eq(OPA_EVALUATION_ACTION_SAVE), eq(OPA_EVALUATION_TYPE_USER_GROUP)))
        .thenReturn(expected);

    GovernanceMetadata result = userGroupOpaService.evaluateWithOpa(
        SCOPE_INFO, updatedUserGroup, OPA_EVALUATION_ACTION_SAVE, Optional.of(existingUserGroup));

    assertThat(result).isEqualTo(expected);
    verify(userMembershipRepository).findAllWithCriteria(any(Criteria.class));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenEvaluateWithOpaAndExistingGroupIsHarnessManagedThenReturnNullWithoutEvaluatingPolicies() {
    UserGroup existingUserGroup = buildUserGroup(USER_GROUP_IDENTIFIER, List.of("user1"), true, false);
    UserGroupDTO requestDto =
        UserGroupDTO.builder().identifier(USER_GROUP_IDENTIFIER).users(List.of("user1", "user2")).build();

    GovernanceMetadata result = userGroupOpaService.evaluateWithOpa(
        SCOPE_INFO, requestDto, OPA_EVALUATION_ACTION_SAVE, Optional.of(existingUserGroup));

    assertThat(result).isNull();
    verifyNoInteractions(opaService);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenEvaluateWithOpaAndExistingGroupIsExternallyManagedThenReturnNullWithoutEvaluatingPolicies() {
    UserGroup existingUserGroup = buildUserGroup(USER_GROUP_IDENTIFIER, List.of("user1"), false, true);
    UserGroupDTO requestDto =
        UserGroupDTO.builder().identifier(USER_GROUP_IDENTIFIER).users(List.of("user1", "user2")).build();

    GovernanceMetadata result = userGroupOpaService.evaluateWithOpa(
        SCOPE_INFO, requestDto, OPA_EVALUATION_ACTION_SAVE, Optional.of(existingUserGroup));

    assertThat(result).isNull();
    verifyNoInteractions(opaService);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenEvaluateWithOpaAndChangedUsersAreEmptyThenSkipMembershipFetchAndEvaluatePolicies() {
    UserGroupDTO userGroupDTO = UserGroupDTO.builder().identifier(USER_GROUP_IDENTIFIER).build();
    GovernanceMetadata expected = GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_WARNING).setDeny(false).build();
    mockOpaEvaluate(expected);

    GovernanceMetadata result =
        userGroupOpaService.evaluateWithOpa(SCOPE_INFO, userGroupDTO, OPA_EVALUATION_ACTION_SAVE);

    assertThat(result).isEqualTo(expected);
    verify(userMembershipRepository, never()).findAllWithCriteria(any(Criteria.class));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void whenEvaluateWithOpaAndChangedUsersExistThenIncludeGroupedMembershipsInOpaContext() {
    String userId = "userId";
    UserGroupDTO userGroupDTO = UserGroupDTO.builder().identifier(USER_GROUP_IDENTIFIER).users(List.of(userId)).build();
    UserMembership firstMembership =
        UserMembership.builder().accountIdentifier(ACCOUNT_IDENTIFIER).userId(userId).parentUniqueId("scope1").build();
    UserMembership secondMembership =
        UserMembership.builder().accountIdentifier(ACCOUNT_IDENTIFIER).userId(userId).parentUniqueId("scope2").build();
    GovernanceMetadata expected = GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_WARNING).setDeny(false).build();
    ArgumentCaptor<OpaEvaluationContext> contextCaptor = ArgumentCaptor.forClass(OpaEvaluationContext.class);
    when(userMembershipRepository.findAllWithCriteria(any(Criteria.class)))
        .thenReturn(List.of(firstMembership, secondMembership));
    when(
        opaService.evaluate(contextCaptor.capture(), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER),
            eq(USER_GROUP_IDENTIFIER), eq(OPA_EVALUATION_ACTION_SAVE), eq(OPA_EVALUATION_TYPE_USER_GROUP)))
        .thenReturn(expected);

    GovernanceMetadata result =
        userGroupOpaService.evaluateWithOpa(SCOPE_INFO, userGroupDTO, OPA_EVALUATION_ACTION_SAVE);

    assertThat(result).isEqualTo(expected);
    UserGroupOpaEvaluationContext context = (UserGroupOpaEvaluationContext) contextCaptor.getValue();
    Map<String, Object> userGroupPayload = (Map<String, Object>) context.getUserGroup();
    Map<String, Object> membershipsByUser = (Map<String, Object>) userGroupPayload.get("userMemberships");
    assertThat(membershipsByUser).containsKey(userId);
    assertThat((List<Object>) membershipsByUser.get(userId)).hasSize(2);
    verify(userMembershipRepository).findAllWithCriteria(any(Criteria.class));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenFindFirstOpaFailureForUserAdditionAndFeatureFlagDisabledThenReturnNull() {
    when(ngFeatureFlagHelperService.isEnabled(ACCOUNT_IDENTIFIER, FeatureName.PL_ENABLE_OPA_FOR_USER_GROUPS))
        .thenReturn(false);
    UserGroup userGroup = buildUserGroup(USER_GROUP_IDENTIFIER, Collections.emptyList(), false, false);

    GovernanceMetadata result = userGroupOpaService.findFirstOpaFailureForUserAddition(
        SCOPE_INFO, List.of(userGroup), Set.of("user1"), OPA_EVALUATION_ACTION_SAVE);

    assertThat(result).isNull();
    verifyNoInteractions(opaService);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenFindFirstOpaFailureForUserAdditionThenSkipsHarnessManagedGroups() {
    UserGroup harnessManagedGroup = buildUserGroup(USER_GROUP_IDENTIFIER, Collections.emptyList(), true, false);

    GovernanceMetadata result = userGroupOpaService.findFirstOpaFailureForUserAddition(
        SCOPE_INFO, List.of(harnessManagedGroup), Set.of("user1"), OPA_EVALUATION_ACTION_SAVE);

    assertThat(result).isNull();
    verifyNoInteractions(opaService);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenFindFirstOpaFailureForUserAdditionAcrossMultipleGroupsThenFetchMembershipsOnce() {
    UserGroup firstGroup = buildUserGroup(USER_GROUP_IDENTIFIER, Collections.emptyList(), false, false);
    UserGroup secondGroup = buildUserGroup(OTHER_USER_GROUP_IDENTIFIER, Collections.emptyList(), false, false);
    GovernanceMetadata warningResult =
        GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_WARNING).setDeny(false).build();
    mockOpaEvaluate(warningResult);

    GovernanceMetadata result = userGroupOpaService.findFirstOpaFailureForUserAddition(
        SCOPE_INFO, List.of(firstGroup, secondGroup), Set.of("user1"), OPA_EVALUATION_ACTION_SAVE);

    assertThat(result).isEqualTo(warningResult);
    verify(userMembershipRepository, times(1)).findAllWithCriteria(any(Criteria.class));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void whenFindFirstOpaFailureForUserAdditionThenPassMembershipsOnlyForUsersChangedInEachGroup() {
    String userId = "user1";
    UserGroup groupWithExistingUser = buildUserGroup(USER_GROUP_IDENTIFIER, List.of(userId), false, false);
    UserGroup groupWithAddedUser = buildUserGroup(OTHER_USER_GROUP_IDENTIFIER, Collections.emptyList(), false, false);
    UserMembership membership =
        UserMembership.builder().accountIdentifier(ACCOUNT_IDENTIFIER).userId(userId).parentUniqueId("scope1").build();
    GovernanceMetadata warningResult =
        GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_WARNING).setDeny(false).build();
    ArgumentCaptor<OpaEvaluationContext> contextCaptor = ArgumentCaptor.forClass(OpaEvaluationContext.class);
    when(userMembershipRepository.findAllWithCriteria(any(Criteria.class))).thenReturn(List.of(membership));
    when(
        opaService.evaluate(contextCaptor.capture(), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER),
            any(String.class), eq(OPA_EVALUATION_ACTION_SAVE), eq(OPA_EVALUATION_TYPE_USER_GROUP)))
        .thenReturn(warningResult);

    GovernanceMetadata result = userGroupOpaService.findFirstOpaFailureForUserAddition(
        SCOPE_INFO, List.of(groupWithExistingUser, groupWithAddedUser), Set.of(userId), OPA_EVALUATION_ACTION_SAVE);

    assertThat(result).isEqualTo(warningResult);
    List<OpaEvaluationContext> contexts = contextCaptor.getAllValues();
    Map<String, Object> firstGroupPayload =
        (Map<String, Object>) ((UserGroupOpaEvaluationContext) contexts.get(0)).getUserGroup();
    Map<String, Object> secondGroupPayload =
        (Map<String, Object>) ((UserGroupOpaEvaluationContext) contexts.get(1)).getUserGroup();
    assertThat((Map<String, Object>) firstGroupPayload.get("userMemberships")).isEmpty();
    assertThat((Map<String, Object>) secondGroupPayload.get("userMemberships")).containsKey(userId);
    verify(userMembershipRepository, times(1)).findAllWithCriteria(any(Criteria.class));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenFindFirstOpaFailureForUserAdditionAndErrorEncounteredThenReturnErrorImmediately() {
    UserGroup firstGroup = buildUserGroup(USER_GROUP_IDENTIFIER, Collections.emptyList(), false, false);
    UserGroup secondGroup = buildUserGroup(OTHER_USER_GROUP_IDENTIFIER, Collections.emptyList(), false, false);
    GovernanceMetadata errorResult = GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_ERROR).setDeny(true).build();
    when(opaService.evaluate(any(OpaEvaluationContext.class), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER),
             eq(PROJECT_IDENTIFIER), eq(USER_GROUP_IDENTIFIER), eq(OPA_EVALUATION_ACTION_SAVE),
             eq(OPA_EVALUATION_TYPE_USER_GROUP)))
        .thenReturn(errorResult);

    GovernanceMetadata result = userGroupOpaService.findFirstOpaFailureForUserAddition(
        SCOPE_INFO, List.of(firstGroup, secondGroup), Set.of("user1"), OPA_EVALUATION_ACTION_SAVE);

    assertThat(result).isEqualTo(errorResult);
    verify(opaService, never())
        .evaluate(any(OpaEvaluationContext.class), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER),
            eq(OTHER_USER_GROUP_IDENTIFIER), eq(OPA_EVALUATION_ACTION_SAVE), eq(OPA_EVALUATION_TYPE_USER_GROUP));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenFindFirstOpaFailureForUserGroupMembershipUpdateThenEvaluatesAdditionAndRemovalSeparately() {
    UserGroup groupToAddTo = buildUserGroup(USER_GROUP_IDENTIFIER, Collections.emptyList(), false, false);
    UserGroup groupToRemoveFrom = buildUserGroup(OTHER_USER_GROUP_IDENTIFIER, List.of("userId"), false, false);
    GovernanceMetadata additionResult =
        GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_WARNING).setDeny(false).build();
    GovernanceMetadata removalResult =
        GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_WARNING).setDeny(false).build();
    when(opaService.evaluate(any(OpaEvaluationContext.class), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER),
             eq(PROJECT_IDENTIFIER), eq(USER_GROUP_IDENTIFIER), eq(OPA_EVALUATION_ACTION_SAVE),
             eq(OPA_EVALUATION_TYPE_USER_GROUP)))
        .thenReturn(additionResult);
    when(opaService.evaluate(any(OpaEvaluationContext.class), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER),
             eq(PROJECT_IDENTIFIER), eq(OTHER_USER_GROUP_IDENTIFIER), eq(OPA_EVALUATION_ACTION_SAVE),
             eq(OPA_EVALUATION_TYPE_USER_GROUP)))
        .thenReturn(removalResult);

    GovernanceMetadata result = userGroupOpaService.findFirstOpaFailureForUserGroupMembershipUpdate(
        SCOPE_INFO, List.of(groupToAddTo), List.of(groupToRemoveFrom), "userId", OPA_EVALUATION_ACTION_SAVE);

    assertThat(result).isEqualTo(additionResult);
    verify(opaService)
        .evaluate(any(OpaEvaluationContext.class), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER),
            eq(USER_GROUP_IDENTIFIER), eq(OPA_EVALUATION_ACTION_SAVE), eq(OPA_EVALUATION_TYPE_USER_GROUP));
    verify(opaService)
        .evaluate(any(OpaEvaluationContext.class), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER),
            eq(OTHER_USER_GROUP_IDENTIFIER), eq(OPA_EVALUATION_ACTION_SAVE), eq(OPA_EVALUATION_TYPE_USER_GROUP));
    verify(userMembershipRepository, times(1)).findAllWithCriteria(any(Criteria.class));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenFindFirstOpaFailureForUserGroupMembershipUpdateAndRemovalErrorsThenReturnRemovalError() {
    UserGroup groupToAddTo = buildUserGroup(USER_GROUP_IDENTIFIER, Collections.emptyList(), false, false);
    UserGroup groupToRemoveFrom = buildUserGroup(OTHER_USER_GROUP_IDENTIFIER, List.of("userId"), false, false);
    GovernanceMetadata additionResult =
        GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_WARNING).setDeny(false).build();
    GovernanceMetadata removalError = GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_ERROR).setDeny(true).build();
    when(opaService.evaluate(any(OpaEvaluationContext.class), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER),
             eq(PROJECT_IDENTIFIER), eq(USER_GROUP_IDENTIFIER), eq(OPA_EVALUATION_ACTION_SAVE),
             eq(OPA_EVALUATION_TYPE_USER_GROUP)))
        .thenReturn(additionResult);
    when(opaService.evaluate(any(OpaEvaluationContext.class), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER),
             eq(PROJECT_IDENTIFIER), eq(OTHER_USER_GROUP_IDENTIFIER), eq(OPA_EVALUATION_ACTION_SAVE),
             eq(OPA_EVALUATION_TYPE_USER_GROUP)))
        .thenReturn(removalError);

    GovernanceMetadata result = userGroupOpaService.findFirstOpaFailureForUserGroupMembershipUpdate(
        SCOPE_INFO, List.of(groupToAddTo), List.of(groupToRemoveFrom), "userId", OPA_EVALUATION_ACTION_SAVE);

    assertThat(result).isEqualTo(removalError);
  }
}
