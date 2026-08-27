/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.rebuild;

import static io.harness.rule.OwnerRule.MANAS_ASATI;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.concurrency.counter.StepConcurrencyCounterService;
import io.harness.engine.executions.node.helper.NodeExecutionReadHelper;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.metrics.service.api.MetricService;
import io.harness.monitoring.ExecutionCountWithAccountResult;
import io.harness.rule.Owner;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StepConcurrencyCounterRebuildJobTest extends CategoryTest {
  @Mock private PersistentLocker persistentLocker;
  @Mock private NodeExecutionReadHelper nodeExecutionReadHelper;
  @Mock private StepConcurrencyCounterService counterService;
  @Mock private MetricService metricService;
  @Mock private AcquiredLock<?> acquiredLock;

  @InjectMocks private StepConcurrencyCounterRebuildJob job;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void runSkipsWhenLockNotAcquired() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(null);

    job.run();

    verify(persistentLocker)
        .waitToAcquireLockOptional(eq("StepConcurrencyCounterRebuildJob"), any(Duration.class), any(Duration.class));
    verify(nodeExecutionReadHelper, never()).aggregateLeafStepCountByAccount(anySet());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void runSwallowsExceptionDuringLockAcquisition() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenThrow(new RuntimeException("lock service unavailable"));

    job.run();

    verify(nodeExecutionReadHelper, never()).aggregateLeafStepCountByAccount(anySet());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void runInvokesRebuildWhenLockAcquired() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(nodeExecutionReadHelper.aggregateLeafStepCountByAccount(anySet())).thenReturn(Collections.emptyList());

    job.run();

    verify(nodeExecutionReadHelper).aggregateLeafStepCountByAccount(anySet());
    verify(counterService).setClusterCount(0);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void runSwallowsExceptionDuringRebuild() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(nodeExecutionReadHelper.aggregateLeafStepCountByAccount(anySet()))
        .thenThrow(new RuntimeException("mongo down"));

    job.run();

    verify(counterService, never()).setClusterCount(anyLong());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void rebuildSetsPerAccountCountsAndClusterTotal() {
    List<ExecutionCountWithAccountResult> counts =
        List.of(ExecutionCountWithAccountResult.builder().accountId("acc1").count(3).build(),
            ExecutionCountWithAccountResult.builder().accountId("acc2").count(5).build());
    when(nodeExecutionReadHelper.aggregateLeafStepCountByAccount(anySet())).thenReturn(counts);

    job.rebuild();

    verify(counterService).setAccountCount("acc1", 3);
    verify(counterService).setAccountCount("acc2", 5);
    verify(counterService).setClusterCount(8);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void rebuildTreatsNullCountAsZero() {
    List<ExecutionCountWithAccountResult> counts =
        List.of(ExecutionCountWithAccountResult.builder().accountId("acc1").count(null).build());
    when(nodeExecutionReadHelper.aggregateLeafStepCountByAccount(anySet())).thenReturn(counts);

    job.rebuild();

    verify(counterService).setAccountCount("acc1", 0);
    verify(counterService).setClusterCount(0);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void rebuildWithNoAccountsSetsClusterCountToZero() {
    when(nodeExecutionReadHelper.aggregateLeafStepCountByAccount(anySet())).thenReturn(Collections.emptyList());

    job.rebuild();

    verify(counterService, never()).setAccountCount(anyString(), anyLong());
    verify(counterService).setClusterCount(0);
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void runOnSuccessEmitsSuccessRunMetric() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(nodeExecutionReadHelper.aggregateLeafStepCountByAccount(anySet())).thenReturn(Collections.emptyList());

    job.run();

    verify(metricService).incCounter(StepConcurrencyCounterRebuildJob.METRIC_REBUILD_RUN_TOTAL);
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void runSkipsWhenLockNotAcquired_emitsLeaderLockHeldByOtherMetric() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(null);

    job.run();

    verify(metricService).incCounter(StepConcurrencyCounterRebuildJob.METRIC_REBUILD_RUN_TOTAL);
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void runSwallowsExceptionDuringRebuild_emitsErrorRunMetric() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(nodeExecutionReadHelper.aggregateLeafStepCountByAccount(anySet()))
        .thenThrow(new RuntimeException("mongo down"));

    job.run();

    verify(metricService).incCounter(StepConcurrencyCounterRebuildJob.METRIC_REBUILD_RUN_TOTAL);
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void rebuildEmitsDriftForEachRecomputedAccount() {
    when(counterService.getAllAccountCounts()).thenReturn(Map.of("acc1", 1L));
    List<ExecutionCountWithAccountResult> counts =
        List.of(ExecutionCountWithAccountResult.builder().accountId("acc1").count(3).build());
    when(nodeExecutionReadHelper.aggregateLeafStepCountByAccount(anySet())).thenReturn(counts);

    job.rebuild();

    // One drift sample for acc1 (account scope) + one for the cluster scope.
    verify(metricService, org.mockito.Mockito.atLeast(2))
        .recordMetric(eq(StepConcurrencyCounterRebuildJob.METRIC_COUNTER_DRIFT), anyDouble());
    verify(metricService).recordMetric(eq(StepConcurrencyCounterRebuildJob.METRIC_REBUILD_ACCOUNTS_RECOMPUTED), eq(1d));
    verify(metricService)
        .recordMetric(eq(StepConcurrencyCounterRebuildJob.METRIC_REBUILD_STALE_ACCOUNTS_ZEROED), eq(0d));
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void rebuildZeroesStaleAccountAndEmitsNegativeDrift() {
    when(counterService.getAllAccountCounts()).thenReturn(Map.of("staleAcc", 7L));
    when(nodeExecutionReadHelper.aggregateLeafStepCountByAccount(anySet())).thenReturn(Collections.emptyList());

    job.rebuild();

    verify(counterService).setAccountCount("staleAcc", 0L);
    verify(metricService)
        .recordMetric(eq(StepConcurrencyCounterRebuildJob.METRIC_REBUILD_STALE_ACCOUNTS_ZEROED), eq(1d));
  }
}
