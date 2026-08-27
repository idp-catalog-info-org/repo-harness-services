/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.opaclient.model.OpaConstants.OPA_STATUS_ERROR;
import static io.harness.opaclient.model.OpaConstants.OPA_STATUS_WARNING;
import static io.harness.rule.OwnerRule.ABHISHEK_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.engine.governance.GovernanceMetadataErrorDTO;
import io.harness.exception.OPAPolicyEvaluationException;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.accesscontrol.usergroup.UserGroupPermissionUtils;
import io.harness.ng.core.Status;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupRequestV2DTO;
import io.harness.ng.core.dto.UserGroupResponseV2DTO;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PL)
public class UserGroupResourceV2Test extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "account";
  private static final ScopeInfo SCOPE_INFO = ScopeInfo.builder()
                                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                  .uniqueId(ACCOUNT_IDENTIFIER)
                                                  .scopeType(ScopeLevel.ACCOUNT)
                                                  .build();

  @Mock private UserGroupService userGroupService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private UserGroupPermissionUtils userGroupPermissionUtils;
  @InjectMocks private UserGroupResourceV2 userGroupResourceV2;

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenCreateAndOpaPolicyReturnsErrorThenEvaluateMappedUserIdsWithoutCreatingUserGroup() {
    String email = "user@harness.io";
    String userId = "userId";
    UserGroupRequestV2DTO request = UserGroupRequestV2DTO.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .identifier("errorGroup")
                                        .users(List.of(email))
                                        .build();
    GovernanceMetadata error = GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_ERROR).setDeny(true).build();
    ArgumentCaptor<UserGroupDTO> userGroupCaptor = ArgumentCaptor.forClass(UserGroupDTO.class);
    when(userGroupService.getUserIds(List.of(email))).thenReturn(List.of(userId));
    when(userGroupService.createWithEvaluation(eq(SCOPE_INFO), userGroupCaptor.capture()))
        .thenThrow(new OPAPolicyEvaluationException("Error: Failed to save the User Group due to Policy enforcement",
            GovernanceMetadataErrorDTO.builder().governanceMetadata(error).build()));

    ResponseDTO<UserGroupResponseV2DTO> response =
        userGroupResourceV2.create(ACCOUNT_IDENTIFIER, null, null, request, SCOPE_INFO);

    assertThat(userGroupCaptor.getValue().getUsers()).containsExactly(userId);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(response.getData().getGovernanceMetadata()).isEqualTo(error);
    verify(userGroupService).createWithEvaluation(eq(SCOPE_INFO), any(UserGroupDTO.class));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenUpdateAndOpaPolicyReturnsWarningThenUpdateUserGroupAndReturnGovernanceMetadata() {
    String email = "user@harness.io";
    String userId = "userId";
    String userGroupIdentifier = "warningGroup";
    UserGroupRequestV2DTO request = UserGroupRequestV2DTO.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .identifier(userGroupIdentifier)
                                        .users(List.of(email))
                                        .build();
    GovernanceMetadata warning = GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_WARNING).setDeny(false).build();
    when(userGroupService.getUserIds(List.of(email))).thenReturn(List.of(userId));
    when(userGroupService.updateWithEvaluation(eq(SCOPE_INFO), any(UserGroupDTO.class)))
        .thenReturn(UserGroupDTO.builder()
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .identifier(userGroupIdentifier)
                        .users(List.of(userId))
                        .governanceMetadata(warning)
                        .version(1L)
                        .build());
    when(userGroupService.getUserMetaData(List.of(userId))).thenReturn(List.of());

    ResponseDTO<UserGroupResponseV2DTO> response =
        userGroupResourceV2.update(ACCOUNT_IDENTIFIER, null, null, request, SCOPE_INFO);

    assertThat(response.getData().getIdentifier()).isEqualTo(userGroupIdentifier);
    assertThat(response.getData().getGovernanceMetadata()).isEqualTo(warning);
    verify(userGroupService).updateWithEvaluation(eq(SCOPE_INFO), any(UserGroupDTO.class));
  }
}
