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
import static io.harness.pms.events.PmsMonitoringMetricsConstants.STALE_THRESHOLD;
import static io.harness.pms.events.PmsMonitoringMetricsConstants.TOP_STALE_LOG_LIMIT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.metrics.service.api.MetricService;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.pipelinedelete.beans.entity.PipelineDeleteProcessorIteratorEntity;
import io.harness.pms.pipelinedelete.beans.entity.PipelineDeleteProcessorIteratorEntity.PipelineDeleteProcessorIteratorEntityKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.List;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PipelineDeleteCleanupMonitorServiceImpl implements PipelineDeleteCleanupMonitorService {
  private final MongoTemplate secondaryMongoTemplate;
  private final MetricService metricService;
  private final Cache<String, Integer> metricsCache;

  @Inject
  public PipelineDeleteCleanupMonitorServiceImpl(SecondaryMongoTemplateHolder secondaryMongoTemplateHolder,
      MetricService metricService, @Named("pipelineDeleteCleanupMetricsCache") Cache<String, Integer> metricsCache) {
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
    this.metricService = metricService;
    this.metricsCache = metricsCache;
  }

  @Override
  public void registerCleanupLagMetrics() {
    boolean alreadyMetricPublished = !metricsCache.putIfAbsent(PIPELINE_DELETE_CLEANUP_CACHE_GATE_KEY, 1);
    if (alreadyMetricPublished) {
      return;
    }
    try {
      publishCleanupLagMetrics();
    } catch (Exception ex) {
      log.error("[PIPELINE_DELETE_CLEANUP_LAG] Failed while publishing cleanup lag metrics", ex);
    }
  }

  void publishCleanupLagMetrics() {
    long now = System.currentTimeMillis();
    long staleBefore = now - STALE_THRESHOLD.toMillis();

    long backlogCount = secondaryMongoTemplate.count(new Query(), PipelineDeleteProcessorIteratorEntity.class);
    long staleCount = secondaryMongoTemplate.count(
        new Query(Criteria.where(PipelineDeleteProcessorIteratorEntityKeys.createdAt).lt(staleBefore)),
        PipelineDeleteProcessorIteratorEntity.class);

    long maxLagMs = 0L;
    if (backlogCount > 0) {
      Query oldestQuery =
          new Query().with(Sort.by(Sort.Direction.ASC, PipelineDeleteProcessorIteratorEntityKeys.createdAt)).limit(1);
      oldestQuery.fields().include(PipelineDeleteProcessorIteratorEntityKeys.createdAt);
      PipelineDeleteProcessorIteratorEntity oldest =
          secondaryMongoTemplate.findOne(oldestQuery, PipelineDeleteProcessorIteratorEntity.class);
      if (oldest != null && oldest.getCreatedAt() != null) {
        maxLagMs = Math.max(0L, now - oldest.getCreatedAt());
      }
    }

    recordMetric(BACKLOG_COUNT_METRIC, backlogCount);
    recordMetric(MAX_LAG_MS_METRIC, maxLagMs);
    recordMetric(STALE_COUNT_METRIC, staleCount);

    if (staleCount > 0) {
      logStaleEntries(now, staleBefore, backlogCount, maxLagMs, staleCount);
    }
  }

  private void logStaleEntries(long now, long staleBefore, long backlogCount, long maxLagMs, long staleCount) {
    Query staleQuery = new Query(Criteria.where(PipelineDeleteProcessorIteratorEntityKeys.createdAt).lt(staleBefore))
                           .with(Sort.by(Sort.Direction.ASC, PipelineDeleteProcessorIteratorEntityKeys.createdAt))
                           .limit(TOP_STALE_LOG_LIMIT);
    staleQuery.fields()
        .include(PipelineDeleteProcessorIteratorEntityKeys.accountIdentifier)
        .include(PipelineDeleteProcessorIteratorEntityKeys.orgIdentifier)
        .include(PipelineDeleteProcessorIteratorEntityKeys.projectIdentifier)
        .include(PipelineDeleteProcessorIteratorEntityKeys.pipelineIdentifier)
        .include(PipelineDeleteProcessorIteratorEntityKeys.createdAt)
        .include(PipelineDeleteProcessorIteratorEntityKeys.nextIteration);

    List<PipelineDeleteProcessorIteratorEntity> staleEntries =
        secondaryMongoTemplate.find(staleQuery, PipelineDeleteProcessorIteratorEntity.class);

    log.warn("[PIPELINE_DELETE_CLEANUP_LAG] backlog={} maxLagMs={} staleCount={}", backlogCount, maxLagMs, staleCount);
    for (PipelineDeleteProcessorIteratorEntity entity : staleEntries) {
      long ageMs = entity.getCreatedAt() == null ? -1L : now - entity.getCreatedAt();
      log.warn("[PIPELINE_DELETE_CLEANUP_LAG] accountId={} orgIdentifier={} projectIdentifier={} pipelineIdentifier={} "
              + "ageMs={} nextIteration={}",
          entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier(),
          entity.getPipelineIdentifier(), ageMs, entity.getNextIteration());
    }
  }

  private void recordMetric(String metricName, long value) {
    try (PmsMetricContextGuard ignore = new PmsMetricContextGuard(new HashMap<>())) {
      metricService.recordMetric(metricName, value);
    }
  }
}
