/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cleanup;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cleanup.config.OrchestrationGraphCacheCleanupConfig;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.maintenance.MaintenanceController;
import io.harness.rule.Owner;
import io.harness.service.PostgreSQLGraphStoreService;

import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class OrchestrationGraphCacheCleanupJobTest extends OrchestrationVisualizationTestBase {
  @Mock private PersistentLocker persistentLocker;
  @Mock private OrchestrationGraphCacheCleanupConfig config;
  @Mock private PostgreSQLGraphStoreService postgreSQLGraphStoreService;
  @Mock private AcquiredLock<?> acquiredLock;

  @InjectMocks private OrchestrationGraphCacheCleanupJob cleanupJob;

  private static final int DEFAULT_BATCH_SIZE = 5000;
  private static final int DEFAULT_CLEANUP_INTERVAL_MINUTES = 60;
  private static final int DEFAULT_MAX_JOB_DURATION_MINUTES = 55;

  @Before
  public void setup() {
    MaintenanceController.forceMaintenance(false);

    when(config.isCleanUpEnabled()).thenReturn(true);
    when(config.getBatchSize()).thenReturn(DEFAULT_BATCH_SIZE);
    when(config.getCleanUpIntervalMinutes()).thenReturn(DEFAULT_CLEANUP_INTERVAL_MINUTES);
    when(config.getMaxJobDurationMinutes()).thenReturn(DEFAULT_MAX_JOB_DURATION_MINUTES);
  }

  @After
  public void tearDown() {
    MaintenanceController.forceMaintenance(false);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunWhenCleanUpDisabled() {
    when(config.isCleanUpEnabled()).thenReturn(false);

    cleanupJob.run();

    verify(persistentLocker, never()).waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class));
    verify(postgreSQLGraphStoreService, never()).deleteExpiredGraphs(anyInt());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunWhenInMaintenanceMode() {
    MaintenanceController.forceMaintenance(true);

    cleanupJob.run();

    verify(persistentLocker, never()).waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class));
    verify(postgreSQLGraphStoreService, never()).deleteExpiredGraphs(anyInt());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunWhenLockNotAcquired() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(null);

    cleanupJob.run();

    verify(persistentLocker)
        .waitToAcquireLockOptional(eq("OrchestrationGraphCacheCleanupJob"), any(Duration.class), any(Duration.class));
    verify(postgreSQLGraphStoreService, never()).deleteExpiredGraphs(anyInt());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunSuccessfulCleanupSingleBatch() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(postgreSQLGraphStoreService.deleteExpiredGraphs(DEFAULT_BATCH_SIZE)).thenReturn(100);

    cleanupJob.run();

    verify(persistentLocker)
        .waitToAcquireLockOptional(eq("OrchestrationGraphCacheCleanupJob"), any(Duration.class), any(Duration.class));
    verify(postgreSQLGraphStoreService, times(1)).deleteExpiredGraphs(DEFAULT_BATCH_SIZE);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunSuccessfulCleanupMultipleBatches() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(postgreSQLGraphStoreService.deleteExpiredGraphs(DEFAULT_BATCH_SIZE))
        .thenReturn(DEFAULT_BATCH_SIZE)
        .thenReturn(DEFAULT_BATCH_SIZE)
        .thenReturn(1000);

    cleanupJob.run();

    verify(postgreSQLGraphStoreService, times(3)).deleteExpiredGraphs(DEFAULT_BATCH_SIZE);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunNoExpiredRecords() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(postgreSQLGraphStoreService.deleteExpiredGraphs(DEFAULT_BATCH_SIZE)).thenReturn(0);

    cleanupJob.run();

    verify(postgreSQLGraphStoreService, times(1)).deleteExpiredGraphs(DEFAULT_BATCH_SIZE);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunWithExceptionDuringCleanup() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(postgreSQLGraphStoreService.deleteExpiredGraphs(DEFAULT_BATCH_SIZE))
        .thenThrow(new RuntimeException("Database error"));

    cleanupJob.run();

    verify(postgreSQLGraphStoreService).deleteExpiredGraphs(DEFAULT_BATCH_SIZE);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunWithExceptionDuringLockAcquisition() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenThrow(new RuntimeException("Lock service unavailable"));

    cleanupJob.run();

    verify(postgreSQLGraphStoreService, never()).deleteExpiredGraphs(anyInt());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testLockTimeoutMatchesMaxJobDuration() {
    int customMaxJobDuration = 30;
    when(config.getMaxJobDurationMinutes()).thenReturn(customMaxJobDuration);
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(postgreSQLGraphStoreService.deleteExpiredGraphs(anyInt())).thenReturn(0);

    cleanupJob.run();

    verify(persistentLocker)
        .waitToAcquireLockOptional(
            eq("OrchestrationGraphCacheCleanupJob"), eq(Duration.ofMinutes(customMaxJobDuration)), any(Duration.class));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testBatchSizeFromConfig() {
    int customBatchSize = 10000;
    when(config.getBatchSize()).thenReturn(customBatchSize);
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(postgreSQLGraphStoreService.deleteExpiredGraphs(customBatchSize)).thenReturn(0);

    cleanupJob.run();

    verify(postgreSQLGraphStoreService).deleteExpiredGraphs(customBatchSize);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCleanupStopsWhenMaintenanceModeEnabledDuringExecution() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(postgreSQLGraphStoreService.deleteExpiredGraphs(DEFAULT_BATCH_SIZE)).thenAnswer(invocation -> {
      MaintenanceController.forceMaintenance(true);
      return DEFAULT_BATCH_SIZE;
    });

    cleanupJob.run();

    verify(postgreSQLGraphStoreService, times(1)).deleteExpiredGraphs(DEFAULT_BATCH_SIZE);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCleanupContinuesUntilLessThanBatchSizeDeleted() {
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(postgreSQLGraphStoreService.deleteExpiredGraphs(DEFAULT_BATCH_SIZE))
        .thenReturn(DEFAULT_BATCH_SIZE)
        .thenReturn(DEFAULT_BATCH_SIZE)
        .thenReturn(DEFAULT_BATCH_SIZE)
        .thenReturn(DEFAULT_BATCH_SIZE)
        .thenReturn(2500);

    cleanupJob.run();

    verify(postgreSQLGraphStoreService, times(5)).deleteExpiredGraphs(DEFAULT_BATCH_SIZE);
  }
}
