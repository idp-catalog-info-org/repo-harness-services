/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.gitx.GitXWebhhookRbacPermissionsConstants.GITX_WEBHOOKS_RESOURCE_TYPE;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.gitsync.common.metrics.GitXApiMetrics;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.RepoSyncStatus;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.RepoSyncStatusDTO;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.RepoSyncStatusListResponseDTO;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.UntrackedFilePathDTO;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.UntrackedFilePathsPageDTO;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookHealthService;
import io.harness.gitx.GitXWebhhookRbacPermissionsConstants;
import io.harness.metrics.service.api.MetricService;
import io.harness.spec.server.ng.v1.model.GitXWebhookStatusPerRepoDTO;
import io.harness.spec.server.ng.v1.model.ListGitXWebhookStatusResponseDTO;
import io.harness.spec.server.ng.v1.model.ScopeInfo;
import io.harness.spec.server.ng.v1.model.UntrackedFilePath;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bridges the OpenAPI-facing {@code listWebhookStatusPerRepo} contract to {@link GitXWebhookHealthService}.
 *
 * <p>Right now, only the account-scope variant is wired (this PR). Org and project scope variants will be
 * added in a follow-up: this helper is the single seam that those future {@code OrgGitXWebhooksApiImpl} /
 * {@code ProjectGitXWebhooksApiImpl} methods will plug into, mirroring how {@link GitXWebhooksApiHelper}
 * already serves the CRUD endpoints across all three scopes.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GitXWebhookHealthApiHelper {
  private static final String OP_LIST_WEBHOOK_STATUS_PER_REPO = "listWebhookStatusPerRepo";
  private static final String OP_LIST_UNTRACKED_FILE_PATHS = "listUntrackedFilePaths";

  private final GitXWebhookHealthService gitXWebhookHealthService;
  private final AccessControlClient accessControlClient;
  private final MetricService metricService;

  public RepoSyncStatusListResponseDTO listWebhookStatusPerRepo(
      String harnessAccount, String repoName, String entityType, int page, int limit) {
    return GitXApiMetrics.executeWithMetrics(harnessAccount, OP_LIST_WEBHOOK_STATUS_PER_REPO, metricService, () -> {
      checkForGitXWebhookViewPermission(harnessAccount);
      return gitXWebhookHealthService.getRepoSyncStatus(harnessAccount, repoName, entityType, page, limit);
    });
  }

  public UntrackedFilePathsPageDTO listUntrackedFilePaths(
      String harnessAccount, String repoName, String entityType, int page, int limit) {
    return GitXApiMetrics.executeWithMetrics(harnessAccount, OP_LIST_UNTRACKED_FILE_PATHS, metricService, () -> {
      checkForGitXWebhookViewPermission(harnessAccount);
      return gitXWebhookHealthService.listUntrackedFilePaths(harnessAccount, repoName, entityType, page, limit);
    });
  }

  static List<UntrackedFilePath> toApiUntrackedFilePaths(UntrackedFilePathsPageDTO page) {
    if (page == null || page.getFilePaths() == null) {
      return Collections.emptyList();
    }
    List<UntrackedFilePath> result = new ArrayList<>(page.getFilePaths().size());
    for (UntrackedFilePathDTO entry : page.getFilePaths()) {
      UntrackedFilePath ufp = new UntrackedFilePath();
      ufp.setFilePath(entry.getFilePath());
      ufp.setScope(toScopeInfo(entry.getScope()));
      result.add(ufp);
    }
    return result;
  }

  private void checkForGitXWebhookViewPermission(String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(GITX_WEBHOOKS_RESOURCE_TYPE, null), GitXWebhhookRbacPermissionsConstants.GitXWebhhook_VIEW);
  }

  static ListGitXWebhookStatusResponseDTO mapToApiResponse(RepoSyncStatusListResponseDTO serviceResponse) {
    ListGitXWebhookStatusResponseDTO response = new ListGitXWebhookStatusResponseDTO();
    if (serviceResponse == null) {
      response.setGitxWebhookStatusPerRepoList(Collections.emptyList());
      return response;
    }

    List<RepoSyncStatusDTO> repos =
        serviceResponse.getRepositories() == null ? Collections.emptyList() : serviceResponse.getRepositories();

    List<GitXWebhookStatusPerRepoDTO> perRepo = new ArrayList<>(repos.size());
    for (RepoSyncStatusDTO repo : repos) {
      perRepo.add(toPerRepoDto(repo));
    }

    perRepo.sort(java.util.Comparator.comparing(
        GitXWebhookStatusPerRepoDTO::getRepoName, java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

    response.setGitxWebhookStatusPerRepoList(perRepo);
    return response;
  }

  private static GitXWebhookStatusPerRepoDTO toPerRepoDto(RepoSyncStatusDTO repo) {
    GitXWebhookStatusPerRepoDTO dto = new GitXWebhookStatusPerRepoDTO();
    dto.setRepoName(repo.getRepoName());
    dto.setTotalEntities(toIntSafely(repo.getTotalEntities()));
    // Out-of-sync count derives from trackedEntities (not the untracked file list) so it stays
    // accurate for NOT_CONFIGURED, where the detailed untracked list is exposed via the separate
    // /v1/gitx-health/repo endpoint rather than inline here.
    dto.setOutOfSyncEntities(toIntSafely(Math.max(0L, repo.getTotalEntities() - repo.getTrackedEntities())));
    dto.setSyncStatus(GitXWebhookStatusPerRepoDTO.SyncStatusEnum.valueOf(repo.getStatus().name()));
    // last_sync_time is only meaningful for ACTIVE/PARTIAL — for NOT_CONFIGURED the source is always null.
    if (repo.getStatus() != RepoSyncStatus.NOT_CONFIGURED) {
      dto.setLastSyncTime(repo.getLastSyncTime());
    }
    return dto;
  }

  private static ScopeInfo toScopeInfo(Scope scope) {
    if (scope == null) {
      return null;
    }
    ScopeInfo info = new ScopeInfo();
    info.setAccountIdentifier(scope.getAccountIdentifier());
    info.setOrgIdentifier(scope.getOrgIdentifier());
    info.setProjectIdentifier(scope.getProjectIdentifier());
    return info;
  }

  private static int toIntSafely(long value) {
    if (value > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    if (value < Integer.MIN_VALUE) {
      return Integer.MIN_VALUE;
    }
    return (int) value;
  }
}
