/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.common.dtos.GithubSCMDTO;
import io.harness.gitsync.common.dtos.GithubSCMResponseDTO;
import io.harness.gitsync.common.dtos.GitlabSCMDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerDTO;
import io.harness.gitsync.common.mappers.UserSourceCodeManagerMapper;
import io.harness.gitsync.common.service.UserSourceCodeManagerService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.UserSourceCodeManagersApiResponse;

import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class UserSourceCodeManagerApiImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "accountId";
  private static final String USER_IDENTIFIER = "userId";

  @Mock private UserSourceCodeManagerService userSourceCodeManagerService;
  @Mock private UserSourceCodeManagerMapper userSourceCodeManagerMapper;
  @Mock private ScopeInfoService scopeInfoService;

  private UserSourceCodeManagerApiImpl userSourceCodeManagerApiImpl;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    when(userSourceCodeManagerMapper.toResponseDTO(any(UserSourceCodeManagerDTO.class))).thenAnswer(invocation -> {
      UserSourceCodeManagerDTO dto = invocation.getArgument(0);
      return GithubSCMResponseDTO.builder().userName(dto.getUserName()).build();
    });
    userSourceCodeManagerApiImpl = new UserSourceCodeManagerApiImpl(
        userSourceCodeManagerService, Map.of(SCMType.GITHUB, userSourceCodeManagerMapper), scopeInfoService);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetUserSourceCodeManagers() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .scopeType(ScopeLevel.ACCOUNT)
                              .uniqueId("uniqueId")
                              .build();
    when(scopeInfoService.getScopeInfo(ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfo);
    when(userSourceCodeManagerService.getSourceCodeManagers(
             eq(scopeInfo), eq(USER_IDENTIFIER), eq(SCMType.GITHUB), eq(null), eq(true)))
        .thenReturn(List.of(GithubSCMDTO.builder().userName("git-user").build()));

    Response response =
        userSourceCodeManagerApiImpl.listAccountSourceCodeManagers(USER_IDENTIFIER, ACCOUNT_IDENTIFIER, null, "GITHUB");

    assertThat(response.getStatus()).isEqualTo(200);
    UserSourceCodeManagersApiResponse entity = (UserSourceCodeManagersApiResponse) response.getEntity();
    assertThat(entity.getData().getUserSourceCodeManagerResponseList()).hasSize(1);
    assertThat(entity.getData().getUserSourceCodeManagerResponseList().get(0).getUserName()).isEqualTo("git-user");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetUserSourceCodeManagersInvalidScmType() {
    assertThatThrownBy(()
                           -> userSourceCodeManagerApiImpl.listAccountSourceCodeManagers(
                               USER_IDENTIFIER, ACCOUNT_IDENTIFIER, null, "UNKNOWN"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid SCM type [UNKNOWN]");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetUserSourceCodeManagersSkipsUnmappedScmType() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .scopeType(ScopeLevel.ACCOUNT)
                              .uniqueId("uniqueId")
                              .build();
    when(scopeInfoService.getScopeInfo(ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfo);
    when(userSourceCodeManagerService.getSourceCodeManagers(
             eq(scopeInfo), eq(USER_IDENTIFIER), eq(null), eq(null), eq(true)))
        .thenReturn(List.of(GithubSCMDTO.builder().userName("git-user").build(),
            GitlabSCMDTO.builder().userName("gitlab-user").build()));

    Response response =
        userSourceCodeManagerApiImpl.listAccountSourceCodeManagers(USER_IDENTIFIER, ACCOUNT_IDENTIFIER, null, null);

    assertThat(response.getStatus()).isEqualTo(200);
    UserSourceCodeManagersApiResponse entity = (UserSourceCodeManagersApiResponse) response.getEntity();
    assertThat(entity.getData().getUserSourceCodeManagerResponseList()).hasSize(1);
    assertThat(entity.getData().getUserSourceCodeManagerResponseList().get(0).getUserName()).isEqualTo("git-user");
  }
}
