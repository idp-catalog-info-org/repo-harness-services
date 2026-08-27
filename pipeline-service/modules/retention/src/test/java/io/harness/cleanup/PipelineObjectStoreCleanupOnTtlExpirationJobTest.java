/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cleanup;

import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
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
import io.harness.dataretention.PipelineRetentionService;
import io.harness.dataretention.config.DataRetentionConfig;
import io.harness.dataretention.entity.beans.ExecutionRetentionCleanupResult;
import io.harness.dataretention.service.ExecutionRetentionMetadataService;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.rule.Owner;
import io.harness.search.service.PipelineSearchService;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineObjectStoreCleanupOnTtlExpirationJobTest extends CategoryTest {
  @Mock private PersistentLocker persistentLocker;
  @Mock private PipelineRetentionService pipelineRetentionService;
  @Mock private ExecutionRetentionMetadataService executionRetentionMetadataService;
  @Mock private ExecutionRetentionService executionRetentionService;
  @Mock private ObjectStoreClient objectStoreClient;
  @Mock private PipelineSearchService pipelineSearchService;
  @Mock private DataRetentionConfig dataRetentionConfig;
  @Mock private AcquiredLock acquiredLock;

  @InjectMocks private PipelineObjectStoreCleanupOnTtlExpirationJob cleanupJob;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    when(dataRetentionConfig.getCleanUpIntervalMinutes()).thenReturn(360); // 6 hours
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(pipelineSearchService.deleteCompletedExecutions()).thenReturn("taskId");
    when(pipelineRetentionService.getAllWithRetentionSettingsEnabledFromSecondary()).thenReturn(Stream.empty());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testRun_WithNullAccountIds() {
    // Test data: account list contains null values
    List<String> accountIdsWithNull = Arrays.asList("account1", null, "account2", null, "account3");
    when(executionRetentionMetadataService.getAllUniqueAccountIdsWithoutRetentionSetting(any()))
        .thenReturn(accountIdsWithNull);
    when(pipelineSearchService.deleteExpiredExecutionsForDefaultRetentionPeriod(anyList(), anyInt()))
        .thenReturn("taskId123");
    when(executionRetentionService.deleteExpiredTTLExecutionsWithResult(anyString(), anyInt(), any(), any()))
        .thenReturn(ExecutionRetentionCleanupResult.builder()
                        .cleanedCount(10)
                        .cleanedPlanExecutionIds(Collections.emptyList())
                        .build());

    // Execute the public run method
    cleanupJob.run();

    // Capture the arguments passed to deleteExpiredExecutionsForDefaultRetentionPeriod
    ArgumentCaptor<List<String>> accountListCaptor = ArgumentCaptor.forClass(List.class);
    verify(pipelineSearchService, times(1))
        .deleteExpiredExecutionsForDefaultRetentionPeriod(accountListCaptor.capture(), anyInt());

    // Verify that null account IDs were filtered out in the batches
    List<String> capturedAccounts = accountListCaptor.getValue();
    assertThat(capturedAccounts).doesNotContainNull();

    // Verify that only 3 non-null accounts were processed
    verify(executionRetentionService, times(3))
        .deleteExpiredTTLExecutionsWithResult(anyString(), anyInt(), any(), any());

    // Verify that null was never passed to deleteExpiredTTLExecutionsWithResult
    verify(executionRetentionService, never()).deleteExpiredTTLExecutionsWithResult(eq(null), anyInt(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testRun_WithAllNullAccountIds() {
    // Test data: all account IDs are null
    List<String> allNullAccounts = Arrays.asList(null, null, null);
    when(executionRetentionMetadataService.getAllUniqueAccountIdsWithoutRetentionSetting(any()))
        .thenReturn(allNullAccounts);
    when(pipelineSearchService.deleteExpiredExecutionsForDefaultRetentionPeriod(anyList(), anyInt()))
        .thenReturn("taskId123");

    // Execute the public run method
    cleanupJob.run();

    // Verify that deleteExpiredTTLExecutionsWithResult was never called since all accounts were null
    verify(executionRetentionService, never())
        .deleteExpiredTTLExecutionsWithResult(anyString(), anyInt(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testRun_WithNoNullAccountIds() {
    // Test data: no null account IDs
    List<String> validAccounts = Arrays.asList("account1", "account2", "account3");
    when(executionRetentionMetadataService.getAllUniqueAccountIdsWithoutRetentionSetting(any()))
        .thenReturn(validAccounts);
    when(pipelineSearchService.deleteExpiredExecutionsForDefaultRetentionPeriod(anyList(), anyInt()))
        .thenReturn("taskId123");
    when(executionRetentionService.deleteExpiredTTLExecutionsWithResult(anyString(), anyInt(), any(), any()))
        .thenReturn(ExecutionRetentionCleanupResult.builder()
                        .cleanedCount(10)
                        .cleanedPlanExecutionIds(Collections.emptyList())
                        .build());

    // Execute the public run method
    cleanupJob.run();

    // Capture the arguments passed to deleteExpiredExecutionsForDefaultRetentionPeriod
    ArgumentCaptor<List<String>> accountListCaptor = ArgumentCaptor.forClass(List.class);
    verify(pipelineSearchService, times(1))
        .deleteExpiredExecutionsForDefaultRetentionPeriod(accountListCaptor.capture(), anyInt());

    // Verify that all accounts are present
    List<String> capturedAccounts = accountListCaptor.getValue();
    assertThat(capturedAccounts).containsExactlyInAnyOrder("account1", "account2", "account3");

    // Verify that all 3 accounts were processed
    verify(executionRetentionService, times(3))
        .deleteExpiredTTLExecutionsWithResult(anyString(), anyInt(), any(), any());
  }
}
