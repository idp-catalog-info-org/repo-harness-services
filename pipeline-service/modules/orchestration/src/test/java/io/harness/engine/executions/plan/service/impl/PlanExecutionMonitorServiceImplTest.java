/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan.service.impl;

import static io.harness.engine.executions.plan.service.impl.PlanExecutionMonitorServiceImpl.METRIC_KEY;
import static io.harness.engine.executions.plan.service.impl.PlanExecutionMonitorServiceImpl.METRIC_KEY_TRIGGER_TYPE;
import static io.harness.engine.executions.plan.service.impl.PlanExecutionMonitorServiceImpl.triggerTypes;
import static io.harness.pms.execution.utils.StatusUtils.ACTIVE_STATUSES_WITH_QUEUED;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionMonitorService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.metrics.service.api.MetricService;
import io.harness.monitoring.PlanExecutionCountWithAccountAndTriggerTypeResult;
import io.harness.monitoring.PlanExecutionCountWithAccountResult;
import io.harness.monitoring.PlanExecutionCountWithAccountResult.StatusCount;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.cache.Cache;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionMonitorServiceImplTest extends CategoryTest {
  @Mock PlanExecutionService planExecutionService;
  @Mock MetricService metricService;

  @Mock Cache<String, Integer> metricsCache;
  PlanExecutionMonitorService planExecutionMonitorService;

  @Before
  public void beforeTest() throws Exception {
    try (var ignore = MockitoAnnotations.openMocks(this)) {
      planExecutionMonitorService =
          new PlanExecutionMonitorServiceImpl(planExecutionService, metricService, metricsCache);
    }
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testRegisterQueuedExecutionMetricsWithZeroValues() {
    doReturn(true).when(metricsCache).putIfAbsent(any(), any());

    List<PlanExecutionCountWithAccountResult> result = new LinkedList<>();
    result.add(PlanExecutionCountWithAccountResult.builder()
                   .accountId("ABC")
                   .statusCounts(List.of(StatusCount.builder().status("RUNNING").count(2).build(),
                       StatusCount.builder().status("QUEUED").count(1).build()))
                   .build());

    doReturn(result).when(planExecutionService).aggregateActiveExecutionsCountPerAccount();

    planExecutionMonitorService.registerActiveExecutionsMetrics();
    verify(metricService).recordMetric(METRIC_KEY, 2);
    verify(metricService).recordMetric(METRIC_KEY, 1);

    Set<Status> foundStatuses = new HashSet<>() {
      {
        add(Status.RUNNING);
        add(Status.QUEUED);
      }
    };

    Set<Status> notFoundStatuses = Sets.difference(ACTIVE_STATUSES_WITH_QUEUED, foundStatuses);
    verify(metricService, times(notFoundStatuses.size())).recordMetric(METRIC_KEY, 0);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testRegisterQueuedExecutionMetrics() {
    doReturn(true).when(metricsCache).putIfAbsent(any(), any());

    List<PlanExecutionCountWithAccountResult> result = new LinkedList<>();
    result.add(PlanExecutionCountWithAccountResult.builder()
                   .accountId("ABC")
                   .statusCounts(List.of(StatusCount.builder().status("RUNNING").count(2).build()))
                   .build());
    result.add(PlanExecutionCountWithAccountResult.builder()
                   .accountId("DEF")
                   .statusCounts(List.of(StatusCount.builder().status("QUEUED").count(1).build()))
                   .build());

    doReturn(result).when(planExecutionService).aggregateActiveExecutionsCountPerAccount();

    planExecutionMonitorService.registerActiveExecutionsMetrics();
    verify(metricService).recordMetric(METRIC_KEY, 2);
    verify(metricService).recordMetric(METRIC_KEY, 1);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testRegisterExecutionMetricsWithZeroValuesPerTriggerType() {
    doReturn(true).when(metricsCache).putIfAbsent(any(), any());

    List<PlanExecutionCountWithAccountAndTriggerTypeResult> result = new LinkedList<>();
    result.add(
        PlanExecutionCountWithAccountAndTriggerTypeResult.builder()
            .accountId("ABC")
            .triggerTypeCounts(List.of(PlanExecutionCountWithAccountAndTriggerTypeResult.TriggerTypeCount.builder()
                                           .triggerType("ARTIFACT")
                                           .count(2)
                                           .build(),
                PlanExecutionCountWithAccountAndTriggerTypeResult.TriggerTypeCount.builder()
                    .triggerType("MANIFEST")
                    .count(1)
                    .build()))
            .build());

    doReturn(result).when(planExecutionService).aggregateActiveExecutionsCountPerAccountWithTriggerType();

    planExecutionMonitorService.registerActiveExecutionsMetrics();
    verify(metricService).recordMetric(METRIC_KEY_TRIGGER_TYPE, 2);
    verify(metricService).recordMetric(METRIC_KEY_TRIGGER_TYPE, 1);

    Set<TriggerType> foundTriggerTypes = new HashSet<>() {
      {
        add(TriggerType.ARTIFACT);
        add(TriggerType.MANIFEST);
      }
    };

    Set<TriggerType> notFoundTriggerTypes = Sets.difference(triggerTypes, foundTriggerTypes);
    verify(metricService, times(notFoundTriggerTypes.size())).recordMetric(METRIC_KEY_TRIGGER_TYPE, 0);
  }
}
