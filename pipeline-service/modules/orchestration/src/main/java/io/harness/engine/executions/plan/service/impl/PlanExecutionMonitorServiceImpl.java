/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan.service.impl;

import static io.harness.pms.execution.utils.StatusUtils.ACTIVE_STATUSES_WITH_QUEUED;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.service.PlanExecutionMonitorService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.metrics.service.api.MetricService;
import io.harness.monitoring.PlanExecutionCountWithAccountAndTriggerTypeResult;
import io.harness.monitoring.PlanExecutionCountWithAccountResult;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PlanExecutionMonitorServiceImpl implements PlanExecutionMonitorService {
  public static final String PLAN_EXECUTION = "_plan_execution";
  private final PlanExecutionService planExecutionService;
  private final MetricService metricService;
  private final Cache<String, Integer> metricsCache;
  protected static final String METRIC_KEY = "pipeline_executions_gauge";
  protected static final String METRIC_KEY_TRIGGER_TYPE = "pipeline_executions_gauge_per_trigger_type";
  protected static final Set<TriggerType> triggerTypes = Set.of(TriggerType.MANUAL, TriggerType.ARTIFACT,
      TriggerType.MANIFEST, TriggerType.SCHEDULER_CRON, TriggerType.WEBHOOK, TriggerType.WEBHOOK_CUSTOM);

  @Inject
  public PlanExecutionMonitorServiceImpl(PlanExecutionService planExecutionService, MetricService metricService,
      @Named("pmsMetricsCache") Cache<String, Integer> metricsCache) {
    this.planExecutionService = planExecutionService;
    this.metricService = metricService;
    this.metricsCache = metricsCache;
  }

  @Override
  public void registerActiveExecutionsMetrics() {
    boolean alreadyMetricPublished = !metricsCache.putIfAbsent(METRIC_KEY, 1);
    boolean alreadyMetricPublishedPerTriggerType = !metricsCache.putIfAbsent(METRIC_KEY_TRIGGER_TYPE, 1);
    if (!alreadyMetricPublished) {
      populateCountPerAccountIdAndStatus();
    }
    if (!alreadyMetricPublishedPerTriggerType) {
      populateCountPerAccountIdAndTriggerType();
    }
  }

  private void populateCountPerAccountIdAndTriggerType() {
    var accountAndTriggerTypeResultList =
        planExecutionService.aggregateActiveExecutionsCountPerAccountWithTriggerType();
    Set<TriggerType> foundTriggerTypes = new HashSet<>();
    Map<String, String> metricContextMap = new HashMap<>();
    Set<TriggerType> notFoundTriggerTypes;

    for (var accountResult : accountAndTriggerTypeResultList) {
      foundTriggerTypes.clear();
      metricContextMap.put(PmsEventMonitoringConstants.ACCOUNT_ID, accountResult.accountId());
      for (var triggerTypeCounts : accountResult.triggerTypeCounts()) {
        if (EmptyPredicate.isNotEmpty(triggerTypeCounts.triggerType())) {
          foundTriggerTypes.add(TriggerType.valueOf(triggerTypeCounts.triggerType()));
          metricContextMap.put(PmsEventMonitoringConstants.TRIGGER_TYPE, triggerTypeCounts.triggerType());
          populateMetric(triggerTypeCounts.count(), metricContextMap, METRIC_KEY_TRIGGER_TYPE);
        }
      }
      notFoundTriggerTypes = Sets.difference(triggerTypes, foundTriggerTypes);
      notFoundTriggerTypes.forEach(notFoundTriggerType -> {
        metricContextMap.put(PmsEventMonitoringConstants.TRIGGER_TYPE, notFoundTriggerType.toString());
        populateMetric(0, metricContextMap, METRIC_KEY_TRIGGER_TYPE);
      });
    }
    populateCountForMissingKeysInCurrentExecutionStatsPerTriggerType(accountAndTriggerTypeResultList);
  }

  private void populateCountPerAccountIdAndStatus() {
    var accountResultList = planExecutionService.aggregateActiveExecutionsCountPerAccount();
    Set<Status> foundStatuses = new HashSet<>();
    Map<String, String> metricContextMap = new HashMap<>();
    Set<Status> notFoundStatuses;

    for (var accountResult : accountResultList) {
      foundStatuses.clear();
      metricContextMap.put(PmsEventMonitoringConstants.ACCOUNT_ID, accountResult.accountId());
      for (var statusCounts : accountResult.statusCounts()) {
        foundStatuses.add(Status.valueOf(statusCounts.status()));
        metricContextMap.put(PmsEventMonitoringConstants.STATUS, statusCounts.status());
        populateMetric(statusCounts.count(), metricContextMap, METRIC_KEY);
      }
      notFoundStatuses = Sets.difference(ACTIVE_STATUSES_WITH_QUEUED, foundStatuses);
      notFoundStatuses.forEach(notFoundStatus -> {
        metricContextMap.put(PmsEventMonitoringConstants.STATUS, notFoundStatus.toString());
        populateMetric(0, metricContextMap, METRIC_KEY);
      });
    }
    populateCountForMissingKeysInCurrentExecutionStats(accountResultList);
  }

  private void populateCountForMissingKeysInCurrentExecutionStats(
      List<PlanExecutionCountWithAccountResult> accountResultList) {
    Set<String> currentAccountIds = new HashSet<>();
    for (PlanExecutionCountWithAccountResult accountResult : accountResultList) {
      currentAccountIds.add(accountResult.accountId());
    }
    populateZeroCount(currentAccountIds);
  }

  private void populateCountForMissingKeysInCurrentExecutionStatsPerTriggerType(
      List<PlanExecutionCountWithAccountAndTriggerTypeResult> accountResultList) {
    Set<String> currentAccountIds = new HashSet<>();
    for (PlanExecutionCountWithAccountAndTriggerTypeResult accountResult : accountResultList) {
      currentAccountIds.add(accountResult.accountId());
    }
    populateZeroCountForMissingAccountsPerTriggerType(currentAccountIds);
  }

  private void populateZeroCountForMissingAccountsPerTriggerType(Set<String> currentKeys) {
    try {
      Set<String> cachedKeys = new HashSet<>(planExecutionService.findAllAccountIdsWithExecutionsFromAnalytics());
      Set<String> zeroCountKeys = Sets.difference(cachedKeys, currentKeys);

      // if no executions are found for an account that was already cached, we need to mark the count for all status as
      // 0 to keep a consistent state
      Map<String, String> metricContextMap = new HashMap<>();
      for (String key : zeroCountKeys) {
        metricContextMap.put(PmsEventMonitoringConstants.ACCOUNT_ID, key);
        for (TriggerType triggerType : triggerTypes) {
          metricContextMap.put(PmsEventMonitoringConstants.TRIGGER_TYPE, triggerType.toString());
          populateMetric(0, metricContextMap, METRIC_KEY_TRIGGER_TYPE);
        }
      }
    } catch (Exception e) {
      log.error("Unable to populate zero count for metric {}", METRIC_KEY);
    }
  }

  private void populateMetric(Integer metricValue, Map<String, String> metricContextMap, String metricKey) {
    try (PmsMetricContextGuard ignore = new PmsMetricContextGuard(metricContextMap)) {
      metricService.recordMetric(metricKey, metricValue);
    }
  }

  private void populateZeroCount(Set<String> currentKeys) {
    try {
      Set<String> cachedKeys = new HashSet<>(planExecutionService.findAllAccountIdsWithExecutionsFromAnalytics());
      Set<String> zeroCountKeys = Sets.difference(cachedKeys, currentKeys);

      // if no executions are found for an account that was already cached, we need to mark the count for all status as
      // 0 to keep a consistent state
      Map<String, String> metricContextMap = new HashMap<>();
      for (String key : zeroCountKeys) {
        metricContextMap.put(PmsEventMonitoringConstants.ACCOUNT_ID, key);
        for (Status status : ACTIVE_STATUSES_WITH_QUEUED) {
          metricContextMap.put(PmsEventMonitoringConstants.STATUS, status.toString());
          populateMetric(0, metricContextMap, METRIC_KEY);
        }
      }
    } catch (Exception e) {
      log.error("Unable to populate zero count for metric {}", METRIC_KEY);
    }
  }
}
