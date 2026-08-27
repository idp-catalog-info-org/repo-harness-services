/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.resource;

import static io.harness.rule.OwnerRule.MANISH;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitops.models.Agent;
import io.harness.gitops.models.ApplicationSetList;
import io.harness.gitops.models.ApplicationSetQuery;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.GITOPS)
@RunWith(MockitoJUnitRunner.class)
public class GitOpsResourceTest extends CategoryTest {
  @Mock private GitopsResourceClient gitopsResourceClient;
  @Mock private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock private Call<ApplicationSetList> applicationSetListCall;
  @Mock private Call<PageResponse<Agent>> agentPageResponseCall;

  @InjectMocks private GitOpsResource gitOpsResource;

  private static final String ACCOUNT_ID = "account1";
  private static final String ORG_ID = "org1";
  private static final String PROJECT_ID = "proj1";

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testListApplicationSets_Success() throws IOException {
    // Setup
    ApplicationSetQuery query = ApplicationSetQuery.builder()
                                    .accountIdentifier(ACCOUNT_ID)
                                    .orgIdentifier(ORG_ID)
                                    .projectIdentifier(PROJECT_ID)
                                    .build();

    ApplicationSetList expectedList = ApplicationSetList.builder().build();
    Response<ApplicationSetList> response = Response.success(expectedList);

    when(gitopsResourceClient.listApplicationSets(query)).thenReturn(applicationSetListCall);
    when(applicationSetListCall.execute()).thenReturn(response);

    // Execute
    ResponseDTO<ApplicationSetList> result = gitOpsResource.listApplicationSets(query);

    // Verify
    assertNotNull(result);
    assertEquals(expectedList, result.getData());
    verify(orgAndProjectValidationHelper).validateOrgAndProject(ACCOUNT_ID, ORG_ID, PROJECT_ID);
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testListApplicationSets_ApiError() throws IOException {
    // Setup
    ApplicationSetQuery query = ApplicationSetQuery.builder()
                                    .accountIdentifier(ACCOUNT_ID)
                                    .orgIdentifier(ORG_ID)
                                    .projectIdentifier(PROJECT_ID)
                                    .build();

    when(gitopsResourceClient.listApplicationSets(query)).thenReturn(applicationSetListCall);
    when(applicationSetListCall.execute()).thenThrow(new IOException());

    // Execute & Verify
    assertThrows(InvalidRequestException.class, () -> gitOpsResource.listApplicationSets(query));
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testListGitOpsAgents_Success() throws IOException {
    // Setup
    int page = 0;
    int size = 10;

    PageResponse<Agent> expectedResponse = PageResponse.<Agent>builder()
                                               .totalPages(0)
                                               .totalItems(0)
                                               .pageItemCount(0)
                                               .pageSize(10)
                                               .content(Collections.emptyList())
                                               .pageIndex(0)
                                               .empty(true)
                                               .build();

    Response<PageResponse<Agent>> response = Response.success(expectedResponse);

    when(gitopsResourceClient.listAgents(ACCOUNT_ID, ORG_ID, PROJECT_ID, page, size, null))
        .thenReturn(agentPageResponseCall);
    when(agentPageResponseCall.execute()).thenReturn(response);

    // Execute
    ResponseDTO<PageResponse<Agent>> result =
        gitOpsResource.listGitOpsAgents(ACCOUNT_ID, ORG_ID, PROJECT_ID, page, size, null);

    // Verify
    assertNotNull(result);
    assertEquals(expectedResponse, result.getData());
    verify(orgAndProjectValidationHelper).validateOrgAndProject(ACCOUNT_ID, ORG_ID, PROJECT_ID);
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testListGitOpsAgents_NoOrgId() throws IOException {
    // Setup
    int page = 0;
    int size = 10;

    PageResponse<Agent> expectedResponse = PageResponse.<Agent>builder()
                                               .totalPages(0)
                                               .totalItems(0)
                                               .pageItemCount(0)
                                               .pageSize(10)
                                               .content(Collections.emptyList())
                                               .pageIndex(0)
                                               .empty(true)
                                               .pageToken("")
                                               .build();
    Response<PageResponse<Agent>> response = Response.success(expectedResponse);

    when(gitopsResourceClient.listAgents(ACCOUNT_ID, null, null, page, size, null)).thenReturn(agentPageResponseCall);
    when(agentPageResponseCall.execute()).thenReturn(response);

    // Execute
    ResponseDTO<PageResponse<Agent>> result = gitOpsResource.listGitOpsAgents(ACCOUNT_ID, null, null, page, size, null);

    // Verify
    assertNotNull(result);
    assertEquals(expectedResponse, result.getData());
    verify(orgAndProjectValidationHelper, never()).validateOrgAndProject(any(), any(), any());
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testListGitOpsAgents_ApiError() throws IOException {
    // Setup
    when(gitopsResourceClient.listAgents(eq(ACCOUNT_ID), any(), any(), anyInt(), anyInt(), any()))
        .thenReturn(agentPageResponseCall);
    when(agentPageResponseCall.execute()).thenThrow(new IOException());

    // Execute & Verify
    assertThrows(
        InvalidRequestException.class, () -> gitOpsResource.listGitOpsAgents(ACCOUNT_ID, null, null, 0, 10, null));
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testListGitOpsAgents_WithSearchTerm() throws IOException {
    // Setup
    int page = 0;
    int size = 10;
    String searchTerm = "test-agent";

    PageResponse<Agent> expectedResponse = PageResponse.<Agent>builder()
                                               .totalPages(0)
                                               .totalItems(0)
                                               .pageItemCount(0)
                                               .pageSize(10)
                                               .content(Collections.emptyList())
                                               .pageIndex(0)
                                               .empty(true)
                                               .build();

    Response<PageResponse<Agent>> response = Response.success(expectedResponse);

    when(gitopsResourceClient.listAgents(ACCOUNT_ID, ORG_ID, PROJECT_ID, page, size, searchTerm))
        .thenReturn(agentPageResponseCall);
    when(agentPageResponseCall.execute()).thenReturn(response);

    // Execute
    ResponseDTO<PageResponse<Agent>> result =
        gitOpsResource.listGitOpsAgents(ACCOUNT_ID, ORG_ID, PROJECT_ID, page, size, searchTerm);

    // Verify
    assertNotNull(result);
    assertEquals(expectedResponse, result.getData());
    verify(orgAndProjectValidationHelper).validateOrgAndProject(ACCOUNT_ID, ORG_ID, PROJECT_ID);
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testListGitOpsAgents_NullAccountId() {
    // Execute & Verify
    assertThrows(IllegalArgumentException.class, () -> gitOpsResource.listGitOpsAgents(null, null, null, 0, 10, null));
  }
}
