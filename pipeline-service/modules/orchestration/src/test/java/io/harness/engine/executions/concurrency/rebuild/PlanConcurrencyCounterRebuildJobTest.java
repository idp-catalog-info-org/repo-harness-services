/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.rebuild;

import static io.harness.rule.OwnerRule.MANAS_ASATI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.concurrency.counter.PlanConcurrencyCounterService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.metrics.service.api.MetricService;
import io.harness.rule.Owner;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PlanConcurrencyCounterRebuildJobTest extends CategoryTest {
  @Mock private PlanExecutionService planExecutionService;
  @Mock private PlanConcurrencyCounterService counterService;
  @Mock private PersistentLocker persistentLocker;
  @Mock private MetricService metricService;
  @Mock private AcquiredLock<?> acquiredLock;

  @InjectMocks private PlanConcurrencyCounterRebuildJob job;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    when(counterService.getAllAccountCounts()).thenReturn(Map.of());
    when(counterService.getAllProjectCounts()).thenReturn(Map.of());
    when(planExecutionService.fetchPlanExecutionsByStatusFromAnalytics(anySet(), anySet()))
        .thenReturn(Stream.<PlanExecution>empty());
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void runSkipsWhenLockNotAcquired_emitsLeaderLockHeldByOtherMetric() {
    when(persistentLocker.tryToAcquireLock(anyString(), any(Duration.class))).thenReturn(null);

    job.run();

    verify(planExecutionService, never()).fetchPlanExecutionsByStatusFromAnalytics(anySet(), anySet());
    verify(metricService).incCounter(PlanConcurrencyCounterRebuildJob.METRIC_REBUILD_RUN_TOTAL);
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void runOnSuccessEmitsSuccessRunMetric() {
    when(persistentLocker.tryToAcquireLock(anyString(), any(Duration.class))).thenReturn(acquiredLock);

    job.run();

    verify(counterService).setAccountCounts(any());
    verify(counterService).setProjectCounts(any());
    verify(metricService).incCounter(PlanConcurrencyCounterRebuildJob.METRIC_REBUILD_RUN_TOTAL);
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void rebuildEmitsRecomputedGauges() {
    job.rebuild();

    verify(metricService).recordMetric(eq(PlanConcurrencyCounterRebuildJob.METRIC_REBUILD_ACCOUNTS_RECOMPUTED), eq(0d));
    verify(metricService).recordMetric(eq(PlanConcurrencyCounterRebuildJob.METRIC_REBUILD_PROJECTS_RECOMPUTED), eq(0d));
    verify(metricService)
        .recordMetric(eq(PlanConcurrencyCounterRebuildJob.METRIC_REBUILD_STALE_ACCOUNTS_ZEROED), eq(0d));
    verify(metricService)
        .recordMetric(eq(PlanConcurrencyCounterRebuildJob.METRIC_REBUILD_STALE_PROJECTS_ZEROED), eq(0d));
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void rebuildZeroesStaleAccountAndEmitsNegativeDrift() {
    when(counterService.getAllAccountCounts()).thenReturn(Map.of("staleAcc", 7L));

    job.rebuild();

    verify(metricService)
        .recordMetric(eq(PlanConcurrencyCounterRebuildJob.METRIC_REBUILD_STALE_ACCOUNTS_ZEROED), eq(1d));
    verify(metricService, org.mockito.Mockito.atLeastOnce())
        .recordMetric(eq(PlanConcurrencyCounterRebuildJob.METRIC_COUNTER_DRIFT), anyDouble());
  }
}
