/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node.service.impl;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.service.NodeExecutionMonitorService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.metrics.service.api.MetricService;
import io.harness.monitoring.ExecutionCountWithAccountResult;
import io.harness.monitoring.ExecutionCountWithModuleAndStepTypeResult;
import io.harness.monitoring.ExecutionStatistics;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;

import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class NodeExecutionMonitorServiceImpl implements NodeExecutionMonitorService {
  private static final String NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_METRIC_NAME = "node_execution_active_count";
  private static final String NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_PER_MODULE_AND_STEP_TYPE_METRIC_NAME =
      "node_execution_active_count_per_module_and_stepType";
  private static final String MODULE_STEP_TYPE =
      PmsEventMonitoringConstants.MODULE + "_" + PmsEventMonitoringConstants.STEP_TYPE;
  public static final String NODE_EXECUTION = "_node_execution";
  private final NodeExecutionService nodeExecutionService;
  private final PlanExecutionService planExecutionService;
  private final MetricService metricService;
  private final Cache<String, Integer> metricsCache;
  private final LoadingCache<String, Set<String>>
      metricsLoadingCache; // Cache to avoid retaining count in prometheus for a key in case count is 0 now
  @Inject
  public NodeExecutionMonitorServiceImpl(NodeExecutionService nodeExecutionService,
      PlanExecutionService planExecutionService, MetricService metricService,
      @Named("pmsMetricsCache") Cache<String, Integer> metricsCache,
      @Named("pmsMetricsLoadingCache") LoadingCache<String, Set<String>> metricsLoadingCache) {
    this.nodeExecutionService = nodeExecutionService;
    this.planExecutionService = planExecutionService;
    this.metricService = metricService;
    this.metricsCache = metricsCache;
    this.metricsLoadingCache = metricsLoadingCache;
  }

  @Override
  public void registerActiveExecutionMetrics() {
    boolean alreadyMetricPublished = !metricsCache.putIfAbsent(NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_METRIC_NAME, 1);
    if (alreadyMetricPublished) {
      return;
    }

    ExecutionStatistics executionStatistics = nodeExecutionService.aggregateRunningNodeExecutionsCount();
    if (executionStatistics == null) {
      return;
    }

    for (ExecutionCountWithAccountResult accountResult : executionStatistics.getAccountStats()) {
      Map<String, String> metricContextMap =
          ImmutableMap.<String, String>builder()
              .put(PmsEventMonitoringConstants.ACCOUNT_ID, accountResult.getAccountId())
              .build();
      populateMetric(metricContextMap, NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_METRIC_NAME, accountResult.getCount());
    }

    for (ExecutionCountWithModuleAndStepTypeResult moduleAndStepTypeResult :
        executionStatistics.getModuleAndStepTypeStats()) {
      Map<String, String> metricContextMap =
          ImmutableMap.<String, String>builder()
              .put(PmsEventMonitoringConstants.MODULE, moduleAndStepTypeResult.getModule())
              .put(PmsEventMonitoringConstants.STEP_TYPE, moduleAndStepTypeResult.getType())
              .build();
      populateMetric(metricContextMap, NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_PER_MODULE_AND_STEP_TYPE_METRIC_NAME,
          moduleAndStepTypeResult.getCount());
    }

    populateCountForMissingKeysInCurrentExecutionStats(executionStatistics);
  }

  private void populateCountForMissingKeysInCurrentExecutionStats(ExecutionStatistics executionStatistics) {
    Set<String> currentAccountIds = new HashSet<>();
    for (ExecutionCountWithAccountResult accountResult : executionStatistics.getAccountStats()) {
      currentAccountIds.add(accountResult.getAccountId());
    }
    populateZeroCount(
        currentAccountIds, PmsEventMonitoringConstants.ACCOUNT_ID, NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_METRIC_NAME);

    Set<String> currentModulesWithStepTypes = new HashSet<>();
    for (ExecutionCountWithModuleAndStepTypeResult moduleAndStepTypeResult :
        executionStatistics.getModuleAndStepTypeStats()) {
      currentModulesWithStepTypes.add(moduleAndStepTypeResult.getModule() + "_" + moduleAndStepTypeResult.getType());
    }
    populateZeroCount(currentModulesWithStepTypes, MODULE_STEP_TYPE,
        NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_PER_MODULE_AND_STEP_TYPE_METRIC_NAME);
  }

  private void populateZeroCount(
      Set<String> currentKeys, String metricKey, String nodeExecutionActiveExecutionCountMetricName) {
    try {
      Set<String> cachedKeys = getCachedKeys(metricKey);
      Set<String> zeroCountKeys = Sets.difference(cachedKeys, currentKeys);
      for (String key : zeroCountKeys) {
        populateMetric(getMetricContextMap(key, nodeExecutionActiveExecutionCountMetricName),
            nodeExecutionActiveExecutionCountMetricName, 0);
      }
      if (metricKey.equals(MODULE_STEP_TYPE)) {
        cachedKeys.addAll(currentKeys);
        metricsLoadingCache.put(MODULE_STEP_TYPE + NODE_EXECUTION, cachedKeys);
      }
    } catch (Exception e) {
      log.error("Unable to populate zero count for metric {}", nodeExecutionActiveExecutionCountMetricName);
    }
  }

  private Set<String> getCachedKeys(String metricKey) throws ExecutionException {
    if (metricKey.equals(PmsEventMonitoringConstants.ACCOUNT_ID)) {
      return new HashSet<>(planExecutionService.findAllAccountIdsWithExecutionsFromAnalytics());
    } else {
      return metricsLoadingCache.get(metricKey + NODE_EXECUTION);
    }
  }

  private Map<String, String> getMetricContextMap(String key, String nodeExecutionActiveExecutionCountMetricName) {
    if (NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_METRIC_NAME.equals(nodeExecutionActiveExecutionCountMetricName)) {
      return ImmutableMap.<String, String>builder().put(PmsEventMonitoringConstants.ACCOUNT_ID, key).build();
    } else if (NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_PER_MODULE_AND_STEP_TYPE_METRIC_NAME.equals(
                   nodeExecutionActiveExecutionCountMetricName)) {
      String[] keys = key.split("_", 2);
      return ImmutableMap.<String, String>builder()
          .put(PmsEventMonitoringConstants.MODULE, keys[0])
          .put(PmsEventMonitoringConstants.STEP_TYPE, keys[1])
          .build();
    }
    return new HashMap<>();
  }

  private void populateMetric(Map<String, String> metricContextMap, String metricName, Integer metricValue) {
    try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
      metricService.recordMetric(metricName, metricValue);
    }
  }
}
