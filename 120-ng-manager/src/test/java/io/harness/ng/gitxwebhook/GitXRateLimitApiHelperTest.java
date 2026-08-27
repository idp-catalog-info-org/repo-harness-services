/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import static io.harness.rule.OwnerRule.ADITHYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.gitxwebhooks.dtos.GitXRateLimitEntryDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXRateLimitListPageDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXRateLimitTimelineDTO;
import io.harness.gitsync.gitxwebhooks.service.gitxratelimit.GitXRateLimitService;
import io.harness.metrics.service.api.MetricService;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.GitXRateLimitProvider;
import io.harness.spec.server.ng.v1.model.GitXRateLimitWindow;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitXRateLimitApiHelperTest extends CategoryTest {
  private GitXRateLimitApiHelper helper;
  @Mock private GitXRateLimitService gitXRateLimitService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private MetricService metricService;

  private static final String ACCOUNT = "acct";
  private static final String CONNECTOR = "connector-1";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    Constructor<GitXRateLimitApiHelper> ctor = GitXRateLimitApiHelper.class.getDeclaredConstructor(
        GitXRateLimitService.class, AccessControlClient.class, MetricService.class);
    ctor.setAccessible(true);
    helper = ctor.newInstance(gitXRateLimitService, accessControlClient, metricService);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void listRateLimits_invokesAccessControlCheck() {
    when(gitXRateLimitService.listRateLimits(eq(ACCOUNT), any(), any(), anyInt(), anyInt()))
        .thenReturn(GitXRateLimitListPageDTO.builder().entries(Collections.emptyList()).totalElements(0).build());

    helper.listRateLimits(ACCOUNT, CONNECTOR, GitXRateLimitProvider.GITHUB, 0, 20);

    verify(accessControlClient).checkForAccessOrThrow(any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void listRateLimits_mapsEntriesAndTotal() {
    when(gitXRateLimitService.listRateLimits(eq(ACCOUNT), any(), any(), anyInt(), anyInt()))
        .thenReturn(GitXRateLimitListPageDTO.builder()
                        .entries(Arrays.asList(GitXRateLimitEntryDTO.builder()
                                                   .connectorId(CONNECTOR)
                                                   .provider("github")
                                                   .bucket("core")
                                                   .currentConsumption(500L)
                                                   .limit(5000L)
                                                   .remaining(4500L)
                                                   .resetsAtMs(1_700_000_060_000L)
                                                   .tooManyRequestsInWindow(0L)
                                                   .lastUpdatedAtMs(1_700_000_010_000L)
                                                   .build()))
                        .totalElements(1L)
                        .build());

    GitXRateLimitApiHelper.ListResult result = helper.listRateLimits(ACCOUNT, CONNECTOR, null, 0, 20);
    assertThat(result.getTotalElements()).isEqualTo(1L);
    assertThat(result.getBody().getRateLimits()).hasSize(1);
    assertThat(result.getBody().getRateLimits().get(0).getProvider()).isEqualTo(GitXRateLimitProvider.GITHUB);
    assertThat(result.getBody().getRateLimits().get(0).getBucket()).isEqualTo("core");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void listRateLimits_ordersDataBearingConnectorsFirstThenAlphabetically() {
    GitXRateLimitEntryDTO liveZebra = liveEntry("zebra-connector");
    GitXRateLimitEntryDTO placeholderAlpha = placeholderEntry("alpha-connector");
    GitXRateLimitEntryDTO liveApple = liveEntry("Apple-connector");
    GitXRateLimitEntryDTO placeholderBravo = placeholderEntry("bravo-connector");
    when(gitXRateLimitService.listRateLimits(eq(ACCOUNT), any(), any(), anyInt(), anyInt()))
        .thenReturn(GitXRateLimitListPageDTO.builder()
                        .entries(Arrays.asList(liveZebra, placeholderAlpha, liveApple, placeholderBravo))
                        .totalElements(4L)
                        .build());

    GitXRateLimitApiHelper.ListResult result = helper.listRateLimits(ACCOUNT, null, null, 0, 20);

    assertThat(result.getBody().getRateLimits())
        .extracting(io.harness.spec.server.ng.v1.model.GitXRateLimitEntry::getConnectorId)
        .containsExactly("Apple-connector", "zebra-connector", "alpha-connector", "bravo-connector");
  }

  private static GitXRateLimitEntryDTO liveEntry(String connectorId) {
    return GitXRateLimitEntryDTO.builder()
        .connectorId(connectorId)
        .provider("github")
        .bucket("core")
        .currentConsumption(500L)
        .limit(5000L)
        .remaining(4500L)
        .resetsAtMs(1_700_000_060_000L)
        .tooManyRequestsInWindow(0L)
        .lastUpdatedAtMs(1_700_000_010_000L)
        .build();
  }

  private static GitXRateLimitEntryDTO placeholderEntry(String connectorId) {
    return GitXRateLimitEntryDTO.builder().connectorId(connectorId).provider("github").build();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void getTimeline_delegatesToServiceAndMapsPoints() {
    GitXRateLimitTimelineDTO.Series s = GitXRateLimitTimelineDTO.Series.builder()
                                            .bucket("core")
                                            .peakPercentConsumed(Arrays.asList(10.0, null, 30.0))
                                            .build();
    when(gitXRateLimitService.getTimeline(eq(ACCOUNT), eq(CONNECTOR), eq(4L), eq(3600L), any()))
        .thenReturn(GitXRateLimitTimelineDTO.builder()
                        .connectorId(CONNECTOR)
                        .provider("github")
                        .resolutionSeconds(4L)
                        .startMs(1_700_000_000_000L)
                        .endMs(1_700_003_600_000L)
                        .series(Arrays.asList(s))
                        .build());

    io.harness.spec.server.ng.v1.model.GitXRateLimitTimeSeriesResponseDTO out =
        helper.getTimeline(ACCOUNT, CONNECTOR, GitXRateLimitWindow.ONE_HOUR, null);
    assertThat(out.getConnectorId()).isEqualTo(CONNECTOR);
    assertThat(out.getProvider()).isEqualTo(GitXRateLimitProvider.GITHUB);
    assertThat(out.getSeries()).hasSize(1);
    assertThat(out.getSeries().get(0).getPoints()).hasSize(3);
    assertThat(out.getSeries().get(0).getPoints().get(1).getPercentConsumed()).isNull();
    assertThat(out.getSeries().get(0).getPoints().get(2).getPercentConsumed()).isEqualTo(30.0);
    // timestamp_ms = startMs + slotMs*i (4s = 4000ms)
    assertThat(out.getSeries().get(0).getPoints().get(1).getTimestampMs()).isEqualTo(1_700_000_004_000L);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void listRateLimits_success_emits2xxCounterAndDurationHistogram() {
    when(gitXRateLimitService.listRateLimits(eq(ACCOUNT), any(), any(), anyInt(), anyInt()))
        .thenReturn(GitXRateLimitListPageDTO.builder().entries(Collections.emptyList()).totalElements(0).build());

    helper.listRateLimits(ACCOUNT, null, null, 0, 20);

    verify(metricService).incCounter(eq("gitx_api_requests_total"));
    verify(metricService).recordMetric(eq("gitx_api_process_time"), org.mockito.ArgumentMatchers.anyDouble());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void listRateLimits_wingsExceptionMappedTo4xx_countsRequestButSkipsDuration() {
    when(gitXRateLimitService.listRateLimits(eq(ACCOUNT), any(), any(), anyInt(), anyInt()))
        .thenThrow(new InvalidRequestException("feature flag off"));

    Assertions.assertThatThrownBy(() -> helper.listRateLimits(ACCOUNT, null, null, 0, 20))
        .isInstanceOf(InvalidRequestException.class);

    // Counter recorded even on failure (statusClass label = "4xx"); duration recorded only on 2xx.
    verify(metricService).incCounter(eq("gitx_api_requests_total"));
    verify(metricService, never()).recordMetric(eq("gitx_api_process_time"), org.mockito.ArgumentMatchers.anyDouble());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void getTimeline_nonWingsExceptionMappedTo5xx_countsRequestButSkipsDuration() {
    when(gitXRateLimitService.getTimeline(
             eq(ACCOUNT), eq(CONNECTOR), eq(4L), eq(3600L), org.mockito.ArgumentMatchers.<String>any()))
        .thenThrow(new RuntimeException("mongo timeout"));

    Assertions.assertThatThrownBy(() -> helper.getTimeline(ACCOUNT, CONNECTOR, GitXRateLimitWindow.ONE_HOUR, null))
        .isInstanceOf(RuntimeException.class);

    verify(metricService).incCounter(eq("gitx_api_requests_total"));
    verify(metricService, never()).recordMetric(eq("gitx_api_process_time"), org.mockito.ArgumentMatchers.anyDouble());
  }
}
