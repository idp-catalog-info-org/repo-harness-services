/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.rule.OwnerRule.MANISH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.config.ServiceUniqueIdBackfillConfig;
import io.harness.rule.Owner;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceUniqueIdBackfillJobTest extends CategoryTest {
  @Mock private ServiceUniqueIdBackfillTask task;
  @Mock private ScheduledExecutorService executorService;
  @Mock private ScheduledFuture<?> jobFuture;

  private ServiceUniqueIdBackfillJob job;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    job = new ServiceUniqueIdBackfillJob(task);
    // Replace the executor service with mock using reflection
    setPrivateField(job, "executorService", executorService);
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testStart_whenDisabled_shouldNotScheduleTask() throws Exception {
    // Arrange
    ServiceUniqueIdBackfillConfig config = ServiceUniqueIdBackfillConfig.builder()
                                               .disabled(true)
                                               .initialDelayInMinutes(10)
                                               .intervalInMinutes(60)
                                               .batchSize(500)
                                               .sleepBetweenBatchesInMillis(1000)
                                               .maxRetryCount(5)
                                               .build();
    setPrivateField(job, "config", config);

    // Act
    job.start();

    // Assert
    verify(executorService, never()).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testStart_whenEnabled_shouldScheduleTask() throws Exception {
    // Arrange
    ServiceUniqueIdBackfillConfig config = ServiceUniqueIdBackfillConfig.builder()
                                               .disabled(false)
                                               .initialDelayInMinutes(10)
                                               .intervalInMinutes(60)
                                               .batchSize(500)
                                               .sleepBetweenBatchesInMillis(1000)
                                               .maxRetryCount(5)
                                               .build();
    setPrivateField(job, "config", config);
    doReturn(jobFuture).when(executorService).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());

    // Act
    job.start();

    // Assert
    verify(executorService).scheduleWithFixedDelay(eq(task), eq(10L), eq(60L), eq(TimeUnit.MINUTES));
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testStop_withScheduledJob_shouldCancelAndShutdown() throws Exception {
    // Arrange
    ServiceUniqueIdBackfillConfig config = ServiceUniqueIdBackfillConfig.builder()
                                               .disabled(false)
                                               .initialDelayInMinutes(10)
                                               .intervalInMinutes(60)
                                               .batchSize(500)
                                               .sleepBetweenBatchesInMillis(1000)
                                               .maxRetryCount(5)
                                               .build();
    setPrivateField(job, "config", config);
    setPrivateField(job, "jobFuture", jobFuture);

    // Act
    job.stop();

    // Assert
    verify(jobFuture).cancel(false);
    verify(executorService).shutdownNow();
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testStop_withNoScheduledJob_shouldOnlyShutdown() throws Exception {
    // Arrange - jobFuture is null (never started)

    // Act
    job.stop();

    // Assert
    verify(executorService).shutdownNow();
  }

  private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
    Field field = findField(target.getClass(), fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
    try {
      return clazz.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      Class<?> superclass = clazz.getSuperclass();
      if (superclass != null) {
        return findField(superclass, fieldName);
      }
      throw e;
    }
  }
}
