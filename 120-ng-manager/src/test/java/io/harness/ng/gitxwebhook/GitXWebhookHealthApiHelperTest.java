/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import static io.harness.gitx.GitXWebhhookRbacPermissionsConstants.GITX_WEBHOOKS_RESOURCE_TYPE;
import static io.harness.rule.OwnerRule.ADITHYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.RepoSyncStatus;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.RepoSyncStatusDTO;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.RepoSyncStatusListResponseDTO;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookHealthService;
import io.harness.gitx.GitXWebhhookRbacPermissionsConstants;
import io.harness.metrics.service.api.MetricService;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.ListGitXWebhookStatusResponseDTO;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitXWebhookHealthApiHelperTest extends CategoryTest {
  private GitXWebhookHealthApiHelper helper;
  @Mock private GitXWebhookHealthService gitXWebhookHealthService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private MetricService metricService;

  private static final String ACCOUNT = "acct";
  private static final String REPO = "harness-core";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    // GitXWebhookHealthApiHelper has @AllArgsConstructor(access = PACKAGE) so we reflect to construct it.
    Constructor<GitXWebhookHealthApiHelper> ctor = GitXWebhookHealthApiHelper.class.getDeclaredConstructor(
        GitXWebhookHealthService.class, AccessControlClient.class, MetricService.class);
    ctor.setAccessible(true);
    helper = ctor.newInstance(gitXWebhookHealthService, accessControlClient, metricService);
  }

  private static RepoSyncStatusListResponseDTO emptyServiceResponse() {
    return RepoSyncStatusListResponseDTO.builder().totalEntities(0).repositories(Collections.emptyList()).build();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void invokesAccessControlCheckBeforeService() {
    when(gitXWebhookHealthService.getRepoSyncStatus(
             eq(ACCOUNT), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(emptyServiceResponse());

    helper.listWebhookStatusPerRepo(ACCOUNT, REPO, null, 0, 20);

    // Account-scope only — health endpoint isn't org/project scoped today.
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(argThat(rs
                                   -> rs.getAccountIdentifier().equals(ACCOUNT) && rs.getOrgIdentifier() == null
                                       && rs.getProjectIdentifier() == null),
            argThat(r -> GITX_WEBHOOKS_RESOURCE_TYPE.equals(r.getResourceType())),
            eq(GitXWebhhookRbacPermissionsConstants.GitXWebhhook_VIEW));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void rbacDeniedDoesNotInvokeService() {
    org.mockito.Mockito.doThrow(new RuntimeException("forbidden"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any());

    try {
      helper.listWebhookStatusPerRepo(ACCOUNT, null, null, 0, 20);
    } catch (RuntimeException expected) {
      // expected
    }

    verify(gitXWebhookHealthService, never())
        .getRepoSyncStatus(
            any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void mapsServiceResponseAndComputesCounters() {
    RepoSyncStatusDTO active = RepoSyncStatusDTO.builder()
                                   .repoName("repoActive")
                                   .repoURL("https://x/repoActive")
                                   .status(RepoSyncStatus.ACTIVE)
                                   .totalEntities(10)
                                   .trackedEntities(10)
                                   .untrackedFilesByScope(new TreeMap<>())
                                   .build();
    RepoSyncStatusDTO partial = RepoSyncStatusDTO.builder()
                                    .repoName("repoPartial")
                                    .repoURL("https://x/repoPartial")
                                    .status(RepoSyncStatus.PARTIAL)
                                    .totalEntities(5)
                                    .trackedEntities(2)
                                    .untrackedFilesByScope(new TreeMap<>())
                                    .build();
    RepoSyncStatusDTO notConfigured = RepoSyncStatusDTO.builder()
                                          .repoName("repoNotConfigured")
                                          .repoURL("https://x/repoNotConfigured")
                                          .status(RepoSyncStatus.NOT_CONFIGURED)
                                          .totalEntities(7)
                                          .trackedEntities(0)
                                          .untrackedFilesByScope(new TreeMap<>())
                                          .build();
    RepoSyncStatusListResponseDTO svcResp = RepoSyncStatusListResponseDTO.builder()
                                                .totalEntities(22)
                                                .repositories(java.util.Arrays.asList(active, partial, notConfigured))
                                                .build();
    when(gitXWebhookHealthService.getRepoSyncStatus(eq(ACCOUNT), eq(null), eq(null), eq(0), eq(20)))
        .thenReturn(svcResp);

    ListGitXWebhookStatusResponseDTO resp =
        GitXWebhookHealthApiHelper.mapToApiResponse(helper.listWebhookStatusPerRepo(ACCOUNT, null, null, 0, 20));

    assertThat(resp.getGitxWebhookStatusPerRepoList()).hasSize(3);

    // Per-repo out-of-sync count = totalEntities - trackedEntities. Repos arrive sorted alphabetically:
    // repoActive, repoNotConfigured, repoPartial. ACTIVE is fully covered → 0; NOT_CONFIGURED has no
    // webhook → all 7 entities are out of sync; PARTIAL has 3 of 5 entities out of sync.
    assertThat(resp.getGitxWebhookStatusPerRepoList().get(0).getOutOfSyncEntities()).isZero();
    assertThat(resp.getGitxWebhookStatusPerRepoList().get(1).getOutOfSyncEntities()).isEqualTo(7);
    assertThat(resp.getGitxWebhookStatusPerRepoList().get(2).getOutOfSyncEntities()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void perRepoListIsSortedAlphabeticallyCaseInsensitive() {
    // Service hands repos back in non-alphabetical order with mixed casing — the helper must sort
    // them before returning so the UI ordering is stable.
    RepoSyncStatusDTO repoZ = RepoSyncStatusDTO.builder()
                                  .repoName("zebra-config")
                                  .status(RepoSyncStatus.ACTIVE)
                                  .totalEntities(1)
                                  .trackedEntities(1)
                                  .untrackedFilesByScope(new TreeMap<>())
                                  .build();
    RepoSyncStatusDTO repoA = RepoSyncStatusDTO.builder()
                                  .repoName("Apple-pipelines")
                                  .status(RepoSyncStatus.ACTIVE)
                                  .totalEntities(1)
                                  .trackedEntities(1)
                                  .untrackedFilesByScope(new TreeMap<>())
                                  .build();
    RepoSyncStatusDTO repoM = RepoSyncStatusDTO.builder()
                                  .repoName("mango")
                                  .status(RepoSyncStatus.ACTIVE)
                                  .totalEntities(1)
                                  .trackedEntities(1)
                                  .untrackedFilesByScope(new TreeMap<>())
                                  .build();
    RepoSyncStatusListResponseDTO svcResp = RepoSyncStatusListResponseDTO.builder()
                                                .totalEntities(3)
                                                .repositories(java.util.Arrays.asList(repoZ, repoM, repoA))
                                                .build();
    when(gitXWebhookHealthService.getRepoSyncStatus(eq(ACCOUNT), eq(null), eq(null), eq(0), eq(20)))
        .thenReturn(svcResp);

    ListGitXWebhookStatusResponseDTO resp =
        GitXWebhookHealthApiHelper.mapToApiResponse(helper.listWebhookStatusPerRepo(ACCOUNT, null, null, 0, 20));

    // Case-insensitive: "Apple-pipelines" sorts before "mango" sorts before "zebra-config".
    assertThat(resp.getGitxWebhookStatusPerRepoList())
        .extracting(io.harness.spec.server.ng.v1.model.GitXWebhookStatusPerRepoDTO::getRepoName)
        .containsExactly("Apple-pipelines", "mango", "zebra-config");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void entityTypeIsForwardedToService() {
    when(gitXWebhookHealthService.getRepoSyncStatus(eq(ACCOUNT), eq(REPO), eq("PIPELINES"), eq(0), eq(20)))
        .thenReturn(emptyServiceResponse());

    helper.listWebhookStatusPerRepo(ACCOUNT, REPO, "PIPELINES", 0, 20);

    verify(gitXWebhookHealthService).getRepoSyncStatus(eq(ACCOUNT), eq(REPO), eq("PIPELINES"), eq(0), eq(20));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void nullServiceResponseProducesEmptyApiResponse() {
    when(gitXWebhookHealthService.getRepoSyncStatus(
             eq(ACCOUNT), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(null);

    ListGitXWebhookStatusResponseDTO resp =
        GitXWebhookHealthApiHelper.mapToApiResponse(helper.listWebhookStatusPerRepo(ACCOUNT, null, null, 0, 20));

    assertThat(resp.getGitxWebhookStatusPerRepoList()).isEmpty();
  }
}
