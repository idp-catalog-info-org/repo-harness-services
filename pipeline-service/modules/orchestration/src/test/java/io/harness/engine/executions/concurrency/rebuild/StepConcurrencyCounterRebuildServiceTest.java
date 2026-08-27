/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.rebuild;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StepConcurrencyCounterRebuildServiceTest extends CategoryTest {
  private StepConcurrencyCounterRebuildJob rebuildJob;
  private ScheduledExecutorService executorService;
  private StepConcurrencyCounterRebuildService service;

  @Before
  public void setUp() throws Exception {
    rebuildJob = mock(StepConcurrencyCounterRebuildJob.class);
    executorService = mock(ScheduledExecutorService.class);
    service = new StepConcurrencyCounterRebuildService();
    FieldUtils.writeField(service, "rebuildJob", rebuildJob, true);
    FieldUtils.writeField(service, "executorService", executorService, true);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void startSchedulesJobEveryTwentyFourHours() throws Exception {
    service.start();

    verify(executorService).scheduleWithFixedDelay(eq(rebuildJob), eq(0L), eq(24L), eq(TimeUnit.HOURS));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void stopCancelsFutureAndShutsDownExecutor() throws Exception {
    ScheduledFuture<?> jobFuture = mock(ScheduledFuture.class);
    doReturn(jobFuture).when(executorService).scheduleWithFixedDelay(any(), eq(0L), eq(24L), eq(TimeUnit.HOURS));

    service.start();
    service.stop();

    verify(jobFuture).cancel(false);
    verify(executorService).shutdownNow();
  }
}
