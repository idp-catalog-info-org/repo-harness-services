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
import io.harness.gitsync.common.metrics.GitXApiMetrics;
import io.harness.gitsync.gitxwebhooks.dtos.GitXRateLimitEntryDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXRateLimitListPageDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXRateLimitTimelineDTO;
import io.harness.gitsync.gitxwebhooks.service.gitxratelimit.GitXRateLimitService;
import io.harness.gitx.GitXWebhhookRbacPermissionsConstants;
import io.harness.metrics.service.api.MetricService;
import io.harness.spec.server.ng.v1.model.GitXRateLimitEntry;
import io.harness.spec.server.ng.v1.model.GitXRateLimitPoint;
import io.harness.spec.server.ng.v1.model.GitXRateLimitProvider;
import io.harness.spec.server.ng.v1.model.GitXRateLimitSeries;
import io.harness.spec.server.ng.v1.model.GitXRateLimitTimeSeriesResponseDTO;
import io.harness.spec.server.ng.v1.model.GitXRateLimitWindow;
import io.harness.spec.server.ng.v1.model.ListGitXRateLimitsResponseDTO;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GitXRateLimitApiHelper {
  private static final String OP_LIST_RATE_LIMITS = "listRateLimits";
  private static final String OP_GET_TIMELINE = "getTimeline";

  private final GitXRateLimitService gitXRateLimitService;
  private final AccessControlClient accessControlClient;
  private final MetricService metricService;

  @lombok.Value
  public static class ListResult {
    ListGitXRateLimitsResponseDTO body;
    long totalElements;
  }

  public ListResult listRateLimits(
      String harnessAccount, String connectorId, GitXRateLimitProvider providerFilter, int page, int limit) {
    return GitXApiMetrics.executeWithMetrics(harnessAccount, OP_LIST_RATE_LIMITS, metricService,
        () -> listRateLimitsInternal(harnessAccount, connectorId, providerFilter, page, limit));
  }

  private ListResult listRateLimitsInternal(
      String harnessAccount, String connectorId, GitXRateLimitProvider providerFilter, int page, int limit) {
    checkForGitXWebhookViewPermission(harnessAccount);
    String providerString = providerFilter == null ? null : toDriverName(providerFilter);
    GitXRateLimitListPageDTO servicePage =
        gitXRateLimitService.listRateLimits(harnessAccount, connectorId, providerString, page, limit);
    // Data-bearing connectors first, then alphabetical (case-insensitive) by connectorId. Page-local
    // only: servicePage is already paginated upstream (connector service pages the connector list),
    // so this does not guarantee data-first ordering across the full result set.
    List<GitXRateLimitEntryDTO> sortedEntries = new ArrayList<>(servicePage.getEntries());
    sortedEntries.sort(
        Comparator.comparingInt((GitXRateLimitEntryDTO e) -> hasRateLimitData(e) ? 0 : 1)
            .thenComparing(GitXRateLimitEntryDTO::getConnectorId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
    List<GitXRateLimitEntry> apiEntries = new ArrayList<>(sortedEntries.size());
    for (GitXRateLimitEntryDTO entry : sortedEntries) {
      apiEntries.add(toApiEntry(entry));
    }
    return new ListResult(new ListGitXRateLimitsResponseDTO().rateLimits(apiEntries), servicePage.getTotalElements());
  }

  public GitXRateLimitTimeSeriesResponseDTO getTimeline(
      String harnessAccount, String connectorId, GitXRateLimitWindow window, String bucket) {
    return GitXApiMetrics.executeWithMetrics(harnessAccount, OP_GET_TIMELINE, metricService,
        () -> getTimelineInternal(harnessAccount, connectorId, window, bucket));
  }

  private GitXRateLimitTimeSeriesResponseDTO getTimelineInternal(
      String harnessAccount, String connectorId, GitXRateLimitWindow window, String bucket) {
    checkForGitXWebhookViewPermission(harnessAccount);
    long resolutionSeconds = resolutionSecondsFor(window);
    long windowSeconds = windowSecondsFor(window);
    GitXRateLimitTimelineDTO service =
        gitXRateLimitService.getTimeline(harnessAccount, connectorId, resolutionSeconds, windowSeconds, bucket);
    GitXRateLimitTimeSeriesResponseDTO api = new GitXRateLimitTimeSeriesResponseDTO()
                                                 .connectorId(service.getConnectorId())
                                                 .provider(fromDriverName(service.getProvider()))
                                                 .window(window)
                                                 .resolutionSeconds(service.getResolutionSeconds())
                                                 .startMs(service.getStartMs())
                                                 .endMs(service.getEndMs());
    List<GitXRateLimitSeries> apiSeries = new ArrayList<>();
    if (service.getSeries() != null) {
      for (GitXRateLimitTimelineDTO.Series s : service.getSeries()) {
        GitXRateLimitSeries out = new GitXRateLimitSeries().bucket(s.getBucket());
        List<GitXRateLimitPoint> points = new ArrayList<>(s.getPeakPercentConsumed().size());
        long slotMs = resolutionSeconds * 1000L;
        for (int i = 0; i < s.getPeakPercentConsumed().size(); i++) {
          GitXRateLimitPoint point = new GitXRateLimitPoint()
                                         .timestampMs(service.getStartMs() + slotMs * i)
                                         .percentConsumed(s.getPeakPercentConsumed().get(i));
          points.add(point);
        }
        out.setPoints(points);
        apiSeries.add(out);
      }
    }
    api.setSeries(apiSeries);
    return api;
  }

  // Placeholder entries (connector with no snapshot rows) have null metric fields; live entries
  // always carry lastUpdatedAtMs.
  private static boolean hasRateLimitData(GitXRateLimitEntryDTO entry) {
    return entry.getLastUpdatedAtMs() != null;
  }

  private GitXRateLimitEntry toApiEntry(GitXRateLimitEntryDTO entry) {
    GitXRateLimitEntry out = new GitXRateLimitEntry();
    out.setConnectorId(entry.getConnectorId());
    out.setProvider(fromDriverName(entry.getProvider()));
    out.setBucket(entry.getBucket());
    out.setCurrentConsumption(entry.getCurrentConsumption());
    out.setLimit(entry.getLimit());
    out.setRemaining(entry.getRemaining());
    out.setResetsAt(entry.getResetsAtMs());
    out.setTooManyRequestsInWindow(entry.getTooManyRequestsInWindow());
    out.setLastUpdatedAt(entry.getLastUpdatedAtMs());
    return out;
  }

  // Mongo stores the go-scm driver name (lower-case); the OpenAPI enum is upper-snake-case.
  private static GitXRateLimitProvider fromDriverName(String driverName) {
    if (driverName == null) {
      return null;
    }
    switch (driverName.toLowerCase(Locale.ROOT)) {
      case "github":
        return GitXRateLimitProvider.GITHUB;
      case "gitlab":
        return GitXRateLimitProvider.GITLAB;
      case "bitbucket":
        return GitXRateLimitProvider.BITBUCKET;
      case "stash":
      case "bitbucket_server":
        return GitXRateLimitProvider.BITBUCKET_SERVER;
      case "azure":
      case "azure_repos":
        return GitXRateLimitProvider.AZURE_REPOS;
      default:
        return null;
    }
  }

  private static String toDriverName(GitXRateLimitProvider provider) {
    switch (provider) {
      case GITHUB:
        return "github";
      case GITLAB:
        return "gitlab";
      case BITBUCKET:
        return "bitbucket";
      case BITBUCKET_SERVER:
        return "stash";
      case AZURE_REPOS:
        return "azure";
      default:
        return null;
    }
  }

  // Chosen so a bucket has at most 1000 points for the given window.
  private static long resolutionSecondsFor(GitXRateLimitWindow window) {
    switch (window) {
      case THREE_HOURS:
        return 11L;
      case SIX_HOURS:
        return 22L;
      case TWELVE_HOURS:
        return 44L;
      case TWENTY_FOUR_HOURS:
        return 87L;
      case ONE_HOUR:
      default:
        return 4L;
    }
  }

  private static long windowSecondsFor(GitXRateLimitWindow window) {
    switch (window) {
      case THREE_HOURS:
        return 3L * 3600L;
      case SIX_HOURS:
        return 6L * 3600L;
      case TWELVE_HOURS:
        return 12L * 3600L;
      case TWENTY_FOUR_HOURS:
        return 24L * 3600L;
      case ONE_HOUR:
      default:
        return 3600L;
    }
  }

  private void checkForGitXWebhookViewPermission(String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(GITX_WEBHOOKS_RESOURCE_TYPE, null), GitXWebhhookRbacPermissionsConstants.GitXWebhhook_VIEW);
  }
}
