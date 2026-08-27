/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.monitoring;

import static io.harness.pms.events.PmsMonitoringMetricsConstants.BACKLOG_COUNT_METRIC;
import static io.harness.pms.events.PmsMonitoringMetricsConstants.MAX_LAG_MS_METRIC;
import static io.harness.pms.events.PmsMonitoringMetricsConstants.PIPELINE_DELETE_CLEANUP_CACHE_GATE_KEY;
import static io.harness.pms.events.PmsMonitoringMetricsConstants.STALE_COUNT_METRIC;
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
import io.harness.category.element.UnitTests;
import io.harness.metrics.service.api.MetricService;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.pms.pipelinedelete.beans.entity.PipelineDeleteProcessorIteratorEntity;
import io.harness.rule.Owner;

import java.time.Duration;
import javax.cache.Cache;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineDeleteCleanupMonitorServiceImplTest extends CategoryTest {
  @Mock private MongoTemplate mongoTemplate;
  @Mock private SecondaryMongoTemplateHolder secondaryMongoTemplateHolder;
  @Mock private MetricService metricService;
  @Mock private Cache<String, Integer> metricsCache;

  private PipelineDeleteCleanupMonitorServiceImpl monitorService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    when(secondaryMongoTemplateHolder.getSecondaryMongoTemplate()).thenReturn(mongoTemplate);
    monitorService =
        new PipelineDeleteCleanupMonitorServiceImpl(secondaryMongoTemplateHolder, metricService, metricsCache);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testRegisterCleanupLagMetricsSkipsWhenAlreadyPublished() {
    when(metricsCache.putIfAbsent(PIPELINE_DELETE_CLEANUP_CACHE_GATE_KEY, 1)).thenReturn(false);

    monitorService.registerCleanupLagMetrics();

    verify(mongoTemplate, never()).count(any(Query.class), eq(PipelineDeleteProcessorIteratorEntity.class));
    verify(metricService, never()).recordMetric(anyString(), anyDouble());
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testPublishCleanupLagMetricsEmptyBacklog() {
    when(mongoTemplate.count(any(Query.class), eq(PipelineDeleteProcessorIteratorEntity.class))).thenReturn(0L);

    monitorService.publishCleanupLagMetrics();

    verify(metricService).recordMetric(BACKLOG_COUNT_METRIC, 0);
    verify(metricService).recordMetric(MAX_LAG_MS_METRIC, 0);
    verify(metricService).recordMetric(STALE_COUNT_METRIC, 0);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testPublishCleanupLagMetricsWithStaleEntries() {
    long now = System.currentTimeMillis();
    long createdAt = now - Duration.ofHours(2).toMillis();
    PipelineDeleteProcessorIteratorEntity oldest = PipelineDeleteProcessorIteratorEntity.builder()
                                                       .accountIdentifier("acc")
                                                       .orgIdentifier("org")
                                                       .projectIdentifier("proj")
                                                       .pipelineIdentifier("pipe")
                                                       .createdAt(createdAt)
                                                       .nextIteration(0L)
                                                       .build();

    when(mongoTemplate.count(any(Query.class), eq(PipelineDeleteProcessorIteratorEntity.class)))
        .thenReturn(3L) // backlog
        .thenReturn(2L); // stale
    when(mongoTemplate.findOne(any(Query.class), eq(PipelineDeleteProcessorIteratorEntity.class))).thenReturn(oldest);
    when(mongoTemplate.find(any(Query.class), eq(PipelineDeleteProcessorIteratorEntity.class)))
        .thenReturn(java.util.List.of(oldest));

    monitorService.publishCleanupLagMetrics();

    ArgumentCaptor<Double> lagCaptor = ArgumentCaptor.forClass(Double.class);
    verify(metricService).recordMetric(eq(BACKLOG_COUNT_METRIC), eq(3.0));
    verify(metricService).recordMetric(eq(MAX_LAG_MS_METRIC), lagCaptor.capture());
    verify(metricService).recordMetric(eq(STALE_COUNT_METRIC), eq(2.0));
    assertThat(lagCaptor.getValue()).isGreaterThan(Duration.ofHours(1).toMillis());
    verify(mongoTemplate, times(1)).find(any(Query.class), eq(PipelineDeleteProcessorIteratorEntity.class));
  }
}
