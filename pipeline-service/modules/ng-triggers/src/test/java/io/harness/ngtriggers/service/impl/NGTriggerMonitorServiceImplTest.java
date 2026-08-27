/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service.impl;

import static io.harness.pms.events.PmsMonitoringMetricsConstants.ORPHAN_SCAN_CACHE_GATE_KEY;
import static io.harness.pms.events.PmsMonitoringMetricsConstants.ORPHAN_TRIGGERS_COUNT_METRIC;
import static io.harness.pms.events.PmsMonitoringMetricsConstants.REASON_PARENT_UNIQUE_ID_MISMATCH;
import static io.harness.pms.events.PmsMonitoringMetricsConstants.REASON_PROJECT_MISSING;
import static io.harness.rule.OwnerRule.ANKUR_PATEL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.metrics.service.api.MetricService;
import io.harness.ngtriggers.instrumentation.OrphanScanGroupResult;
import io.harness.ngtriggers.instrumentation.OrphanScanGroupResult.OrphanScanGroupId;
import io.harness.ngtriggers.instrumentation.OrphanScanGroupResult.OrphanScanGroupResultBuilder;
import io.harness.repositories.custom.NGTriggerRepositoryCustom;
import io.harness.rule.Owner;
import io.harness.utils.ScopeResolutionHelper;

import java.util.List;
import java.util.Set;
import javax.cache.Cache;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGTriggerMonitorServiceImplTest extends CategoryTest {
  @Mock private NGTriggerRepositoryCustom ngTriggerRepositoryCustom;
  @Mock private MetricService metricService;
  @Mock private Cache<String, Integer> metricsCache;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;

  private NGTriggerMonitorServiceImpl monitorService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    monitorService =
        new NGTriggerMonitorServiceImpl(ngTriggerRepositoryCustom, metricService, metricsCache, scopeResolutionHelper);
  }

  private static OrphanScanGroupResultBuilder groupBuilder(
      String accountId, String orgId, String projectId, String parentUniqueId) {
    return OrphanScanGroupResult.builder().id(OrphanScanGroupId.builder()
                                                  .accountId(accountId)
                                                  .orgIdentifier(orgId)
                                                  .projectIdentifier(projectId)
                                                  .parentUniqueId(parentUniqueId)
                                                  .build());
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testClassifyOrphanHealthy() {
    assertThat(NGTriggerMonitorServiceImpl.classifyOrphan("uid-1", "uid-1")).isNull();
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testClassifyOrphanParentUniqueIdMismatch() {
    assertThat(NGTriggerMonitorServiceImpl.classifyOrphan("old-uid", "new-uid"))
        .isEqualTo(REASON_PARENT_UNIQUE_ID_MISMATCH);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testClassifyOrphanProjectMissing() {
    assertThat(NGTriggerMonitorServiceImpl.classifyOrphan("old-uid", null)).isEqualTo(REASON_PROJECT_MISSING);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testRegisterOrphanTriggerMetricsSkipsWhenAlreadyPublished() {
    when(metricsCache.putIfAbsent(ORPHAN_SCAN_CACHE_GATE_KEY, 1)).thenReturn(false);

    monitorService.registerOrphanTriggerMetrics();

    verify(ngTriggerRepositoryCustom, never()).aggregateForOrphanScan();
    verify(metricService, never()).recordMetric(anyString(), anyDouble());
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testScanAndPublishOrphanMetricsDetectsMismatchAndHealthy() {
    when(metricsCache.putIfAbsent(ORPHAN_SCAN_CACHE_GATE_KEY, 1)).thenReturn(true);

    OrphanScanGroupResult orphan = groupBuilder("acc", "org", "proj", "old-uid")
                                       .count(1L)
                                       .sampleIdentifier("trig-orphan")
                                       .sampleTargetIdentifier("pipe")
                                       .sampleCreatedAt(System.currentTimeMillis() - 60_000L)
                                       .build();
    OrphanScanGroupResult healthy = groupBuilder("acc", "org", "proj", "live-uid")
                                        .count(1L)
                                        .sampleIdentifier("trig-ok")
                                        .sampleTargetIdentifier("pipe")
                                        .sampleCreatedAt(System.currentTimeMillis())
                                        .build();

    when(ngTriggerRepositoryCustom.aggregateForOrphanScan()).thenReturn(List.of(orphan, healthy));
    when(scopeResolutionHelper.getScopeInfoListForProjects(eq("acc"), eq("org"), eq(Set.of("proj"))))
        .thenReturn(List.of(ScopeInfo.builder()
                                .accountIdentifier("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .scopeType(ScopeLevel.PROJECT)
                                .uniqueId("live-uid")
                                .build()));

    monitorService.scanAndPublishOrphanMetrics();

    verify(metricService, times(1)).recordMetric(ORPHAN_TRIGGERS_COUNT_METRIC, 1);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testScanAndPublishOrphanMetricsDetectsProjectMissing() {
    OrphanScanGroupResult orphan = groupBuilder("acc", "org", "missing-proj", "old-uid")
                                       .count(1L)
                                       .sampleIdentifier("trig-orphan")
                                       .sampleTargetIdentifier("pipe")
                                       .sampleCreatedAt(System.currentTimeMillis() - 60_000L)
                                       .build();
    OrphanScanGroupResult healthySibling = groupBuilder("acc", "org", "live-proj", "live-uid")
                                               .count(1L)
                                               .sampleIdentifier("trig-ok")
                                               .sampleTargetIdentifier("pipe")
                                               .sampleCreatedAt(System.currentTimeMillis())
                                               .build();

    when(ngTriggerRepositoryCustom.aggregateForOrphanScan()).thenReturn(List.of(orphan, healthySibling));
    when(scopeResolutionHelper.getScopeInfoListForProjects(
             eq("acc"), eq("org"), eq(Set.of("missing-proj", "live-proj"))))
        .thenReturn(List.of(ScopeInfo.builder()
                                .accountIdentifier("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("live-proj")
                                .scopeType(ScopeLevel.PROJECT)
                                .uniqueId("live-uid")
                                .build()));

    monitorService.scanAndPublishOrphanMetrics();

    verify(metricService, times(1)).recordMetric(ORPHAN_TRIGGERS_COUNT_METRIC, 1);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testScanAndPublishOrphanMetricsSkipsWhenScopeResolutionReturnsEmpty() {
    OrphanScanGroupResult group = groupBuilder("acc", "org", "proj", "old-uid")
                                      .count(1L)
                                      .sampleIdentifier("trig-1")
                                      .sampleTargetIdentifier("pipe")
                                      .sampleCreatedAt(System.currentTimeMillis())
                                      .build();

    when(ngTriggerRepositoryCustom.aggregateForOrphanScan()).thenReturn(List.of(group));
    when(scopeResolutionHelper.getScopeInfoListForProjects(eq("acc"), eq("org"), eq(Set.of("proj"))))
        .thenReturn(List.of());

    monitorService.scanAndPublishOrphanMetrics();

    verify(metricService, never()).recordMetric(eq(ORPHAN_TRIGGERS_COUNT_METRIC), eq(1.0));
    verify(metricService, times(1)).recordMetric(ORPHAN_TRIGGERS_COUNT_METRIC, 0);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testScanAndPublishOrphanMetricsSkipsClassificationWhenScopeResolutionFails() {
    OrphanScanGroupResult group = groupBuilder("acc", "org", "proj", "old-uid")
                                      .count(1L)
                                      .sampleIdentifier("trig-1")
                                      .sampleTargetIdentifier("pipe")
                                      .sampleCreatedAt(System.currentTimeMillis())
                                      .build();

    when(ngTriggerRepositoryCustom.aggregateForOrphanScan()).thenReturn(List.of(group));
    when(scopeResolutionHelper.getScopeInfoListForProjects(eq("acc"), eq("org"), eq(Set.of("proj"))))
        .thenThrow(new RuntimeException("scope service unavailable"));

    monitorService.scanAndPublishOrphanMetrics();

    // failure to resolve scope must never be treated as an orphan; account is only zero-filled.
    verify(metricService, never()).recordMetric(eq(ORPHAN_TRIGGERS_COUNT_METRIC), eq(1.0));
    verify(metricService, times(1)).recordMetric(ORPHAN_TRIGGERS_COUNT_METRIC, 0);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testScanAndPublishOrphanMetricsZeroFillsAccountsWithNoOrphans() {
    OrphanScanGroupResult healthy = groupBuilder("acc", "org", "proj", "live-uid")
                                        .count(1L)
                                        .sampleIdentifier("trig-ok")
                                        .sampleTargetIdentifier("pipe")
                                        .sampleCreatedAt(System.currentTimeMillis())
                                        .build();

    when(ngTriggerRepositoryCustom.aggregateForOrphanScan()).thenReturn(List.of(healthy));
    when(scopeResolutionHelper.getScopeInfoListForProjects(eq("acc"), eq("org"), eq(Set.of("proj"))))
        .thenReturn(List.of(ScopeInfo.builder()
                                .accountIdentifier("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .scopeType(ScopeLevel.PROJECT)
                                .uniqueId("live-uid")
                                .build()));

    monitorService.scanAndPublishOrphanMetrics();

    ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
    verify(metricService, times(1)).recordMetric(eq(ORPHAN_TRIGGERS_COUNT_METRIC), valueCaptor.capture());
    assertThat(valueCaptor.getValue()).isEqualTo(0.0);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testScanAndPublishOrphanMetricsIgnoresSoftDeletedWhenParentStillMatches() {
    // Soft-deleted under a healthy project is normal purge lag — not an orphan for project delete/recreate.
    OrphanScanGroupResult softDeletedHealthyParent = groupBuilder("acc", "org", "proj", "live-uid")
                                                         .count(1L)
                                                         .sampleIdentifier("trig-soft-deleted")
                                                         .sampleTargetIdentifier("pipe")
                                                         .sampleDeleted(Boolean.TRUE)
                                                         .sampleCreatedAt(System.currentTimeMillis() - 60_000L)
                                                         .build();

    when(ngTriggerRepositoryCustom.aggregateForOrphanScan()).thenReturn(List.of(softDeletedHealthyParent));
    when(scopeResolutionHelper.getScopeInfoListForProjects(eq("acc"), eq("org"), eq(Set.of("proj"))))
        .thenReturn(List.of(ScopeInfo.builder()
                                .accountIdentifier("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .scopeType(ScopeLevel.PROJECT)
                                .uniqueId("live-uid")
                                .build()));

    monitorService.scanAndPublishOrphanMetrics();

    verify(metricService, times(1)).recordMetric(ORPHAN_TRIGGERS_COUNT_METRIC, 0);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testScanAndPublishOrphanMetricsDetectsSoftDeletedAfterProjectRecreate() {
    // Soft-deleted leftover after project delete/recreate: parentUniqueId points at old project instance.
    OrphanScanGroupResult softDeletedOrphan = groupBuilder("acc", "org", "proj", "old-uid")
                                                  .count(1L)
                                                  .sampleIdentifier("trig-soft-deleted")
                                                  .sampleTargetIdentifier("pipe")
                                                  .sampleDeleted(Boolean.TRUE)
                                                  .sampleCreatedAt(System.currentTimeMillis() - 60_000L)
                                                  .build();

    when(ngTriggerRepositoryCustom.aggregateForOrphanScan()).thenReturn(List.of(softDeletedOrphan));
    when(scopeResolutionHelper.getScopeInfoListForProjects(eq("acc"), eq("org"), eq(Set.of("proj"))))
        .thenReturn(List.of(ScopeInfo.builder()
                                .accountIdentifier("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .scopeType(ScopeLevel.PROJECT)
                                .uniqueId("live-uid")
                                .build()));

    monitorService.scanAndPublishOrphanMetrics();

    verify(metricService, times(1)).recordMetric(ORPHAN_TRIGGERS_COUNT_METRIC, 1);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testScanAndPublishOrphanMetricsDetectsSoftDeletedWhenProjectMissing() {
    OrphanScanGroupResult softDeletedOrphan = groupBuilder("acc", "org", "proj", "old-uid")
                                                  .count(1L)
                                                  .sampleIdentifier("trig-soft-deleted")
                                                  .sampleTargetIdentifier("pipe")
                                                  .sampleDeleted(Boolean.TRUE)
                                                  .sampleCreatedAt(System.currentTimeMillis())
                                                  .build();

    OrphanScanGroupResult liveSibling = groupBuilder("acc", "org", "live-proj", "live-uid")
                                            .count(1L)
                                            .sampleIdentifier("trig-ok")
                                            .sampleTargetIdentifier("pipe")
                                            .sampleCreatedAt(System.currentTimeMillis())
                                            .build();

    when(ngTriggerRepositoryCustom.aggregateForOrphanScan()).thenReturn(List.of(softDeletedOrphan, liveSibling));
    when(scopeResolutionHelper.getScopeInfoListForProjects(eq("acc"), eq("org"), eq(Set.of("proj", "live-proj"))))
        .thenReturn(List.of(ScopeInfo.builder()
                                .accountIdentifier("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("live-proj")
                                .scopeType(ScopeLevel.PROJECT)
                                .uniqueId("live-uid")
                                .build()));

    monitorService.scanAndPublishOrphanMetrics();

    verify(metricService, times(1)).recordMetric(ORPHAN_TRIGGERS_COUNT_METRIC, 1);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testScanAndPublishOrphanMetricsMultiplesOrphanCountByGroupSize() {
    // A single aggregated group can represent many identical leftover triggers; the metric must reflect the
    // group's count, not the number of group rows.
    OrphanScanGroupResult orphanGroup = groupBuilder("acc", "org", "proj", "old-uid")
                                            .count(7L)
                                            .sampleIdentifier("trig-orphan")
                                            .sampleTargetIdentifier("pipe")
                                            .sampleCreatedAt(System.currentTimeMillis())
                                            .build();

    when(ngTriggerRepositoryCustom.aggregateForOrphanScan()).thenReturn(List.of(orphanGroup));
    when(scopeResolutionHelper.getScopeInfoListForProjects(eq("acc"), eq("org"), eq(Set.of("proj"))))
        .thenReturn(List.of(ScopeInfo.builder()
                                .accountIdentifier("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .scopeType(ScopeLevel.PROJECT)
                                .uniqueId("live-uid")
                                .build()));

    monitorService.scanAndPublishOrphanMetrics();

    verify(metricService, times(1)).recordMetric(ORPHAN_TRIGGERS_COUNT_METRIC, 7);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testScanAndPublishOrphanMetricsResolvesScopeOnceEvenWithMultipleGroupsForSameAccountOrg() {
    // Regression test for the aggregation fix: multiple groups sharing the same (accountId, orgIdentifier) must
    // resolve live project uniqueIds exactly once, not once per group.
    OrphanScanGroupResult groupOne = groupBuilder("acc", "org", "proj1", "old-uid-1")
                                         .count(1L)
                                         .sampleIdentifier("trig-1")
                                         .sampleTargetIdentifier("pipe")
                                         .sampleCreatedAt(System.currentTimeMillis())
                                         .build();
    OrphanScanGroupResult groupTwo = groupBuilder("acc", "org", "proj2", "old-uid-2")
                                         .count(1L)
                                         .sampleIdentifier("trig-2")
                                         .sampleTargetIdentifier("pipe")
                                         .sampleCreatedAt(System.currentTimeMillis())
                                         .build();
    OrphanScanGroupResult groupThree = groupBuilder("acc", "org", "proj3", "old-uid-3")
                                           .count(1L)
                                           .sampleIdentifier("trig-3")
                                           .sampleTargetIdentifier("pipe")
                                           .sampleCreatedAt(System.currentTimeMillis())
                                           .build();

    when(ngTriggerRepositoryCustom.aggregateForOrphanScan()).thenReturn(List.of(groupOne, groupTwo, groupThree));
    when(scopeResolutionHelper.getScopeInfoListForProjects(eq("acc"), eq("org"), eq(Set.of("proj1", "proj2", "proj3"))))
        .thenReturn(List.of(ScopeInfo.builder()
                                .accountIdentifier("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj1")
                                .scopeType(ScopeLevel.PROJECT)
                                .uniqueId("old-uid-1")
                                .build()));

    monitorService.scanAndPublishOrphanMetrics();

    verify(scopeResolutionHelper, times(1)).getScopeInfoListForProjects(eq("acc"), eq("org"), any());
    verify(ngTriggerRepositoryCustom, times(1)).aggregateForOrphanScan();
    verify(metricService, times(1)).recordMetric(ORPHAN_TRIGGERS_COUNT_METRIC, 2);
  }
}
