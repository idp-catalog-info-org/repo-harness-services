/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.counter;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RAtomicLongAsync;
import org.redisson.api.RBatch;
import org.redisson.api.RFuture;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;

public class StepConcurrencyCounterServiceImplTest extends CategoryTest {
  private RedissonClient redissonClient;
  private RAtomicLong atomicLong;
  private StepConcurrencyCounterServiceImpl service;

  @Before
  public void setUp() {
    redissonClient = mock(RedissonClient.class);
    atomicLong = mock(RAtomicLong.class);
    when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);
    service = new StepConcurrencyCounterServiceImpl(redissonClient);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void clampsNegativeReadsToZero() {
    RFuture<Long> negative = completed(-5L);
    when(atomicLong.getAsync()).thenReturn(negative);
    assertThat(service.getClusterCount()).isEqualTo(0L);
    assertThat(service.getAccountCount("acc")).isEqualTo(0L);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void passesThroughPositiveReads() {
    RFuture<Long> positive = completed(42L);
    when(atomicLong.getAsync()).thenReturn(positive);
    assertThat(service.getClusterCount()).isEqualTo(42L);
    assertThat(service.getAccountCount("acc")).isEqualTo(42L);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void zeroDeltaSkipsWriteAndReturnsCurrent() {
    RFuture<Long> current = completed(7L);
    when(atomicLong.getAsync()).thenReturn(current);
    long result = service.incrementCluster(0);
    assertThat(result).isEqualTo(7L);
    verify(atomicLong, org.mockito.Mockito.never()).addAndGetAsync(org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void nonZeroDeltaCallsAddAndGet() {
    RFuture<Long> result = completed(10L);
    when(atomicLong.addAndGetAsync(3L)).thenReturn(result);
    long actual = service.incrementAccount("acc", 3);
    assertThat(actual).isEqualTo(10L);
    verify(atomicLong).addAndGetAsync(3L);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void negativeResultTriggersCompensationBackToZero() {
    // Simulate the abort-flow scenario: bulk -3 lands but only +1 slot was occupied.
    // First addAndGet returns -2; the impl must fire a compensating +2 and report 0.
    RFuture<Long> firstResult = completed(-2L);
    RFuture<Long> compensationResult = completed(0L);
    when(atomicLong.addAndGetAsync(-3L)).thenReturn(firstResult);
    when(atomicLong.addAndGetAsync(2L)).thenReturn(compensationResult);
    long actual = service.incrementCluster(-3L);
    assertThat(actual).isEqualTo(0L);
    verify(atomicLong).addAndGetAsync(-3L);
    verify(atomicLong).addAndGetAsync(2L);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void positiveResultSkipsCompensation() {
    RFuture<Long> result = completed(5L);
    when(atomicLong.addAndGetAsync(2L)).thenReturn(result);
    long actual = service.incrementAccount("acc", 2);
    assertThat(actual).isEqualTo(5L);
    verify(atomicLong).addAndGetAsync(2L);
    verify(atomicLong, org.mockito.Mockito.never()).addAndGetAsync(org.mockito.Mockito.longThat(v -> v != 2L));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getAllAccountCountsReadsEveryScannedKeyViaBatch() {
    RKeys keys = mock(RKeys.class);
    when(redissonClient.getKeys()).thenReturn(keys);
    when(keys.getKeysByPattern(StepConcurrencyCounterKey.accountKeyPattern(), 1000))
        .thenReturn(
            List.of(StepConcurrencyCounterKey.forAccount("acc1"), StepConcurrencyCounterKey.forAccount("acc2")));

    RBatch batch = mock(RBatch.class);
    when(redissonClient.createBatch()).thenReturn(batch);
    RAtomicLongAsync acc1Async = mock(RAtomicLongAsync.class);
    RAtomicLongAsync acc2Async = mock(RAtomicLongAsync.class);
    when(batch.getAtomicLong(StepConcurrencyCounterKey.forAccount("acc1"))).thenReturn(acc1Async);
    when(batch.getAtomicLong(StepConcurrencyCounterKey.forAccount("acc2"))).thenReturn(acc2Async);
    RFuture<Long> acc1Result = completed(3L);
    RFuture<Long> acc2Result = completed(-1L);
    when(acc1Async.getAsync()).thenReturn(acc1Result);
    when(acc2Async.getAsync()).thenReturn(acc2Result);

    Map<String, Long> result = service.getAllAccountCounts();

    assertThat(result).containsEntry("acc1", 3L).containsEntry("acc2", 0L);
    verify(batch).execute();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getAllAccountCountsReturnsEmptyMapWithoutBatchWhenNoKeysScanned() {
    RKeys keys = mock(RKeys.class);
    when(redissonClient.getKeys()).thenReturn(keys);
    when(keys.getKeysByPattern(anyString(), anyInt())).thenReturn(List.of());

    Map<String, Long> result = service.getAllAccountCounts();

    assertThat(result).isEmpty();
    verify(redissonClient, org.mockito.Mockito.never()).createBatch();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void setAccountCountsWritesEveryEntryViaBatch() {
    RBatch batch = mock(RBatch.class);
    when(redissonClient.createBatch()).thenReturn(batch);
    RAtomicLongAsync acc1Async = mock(RAtomicLongAsync.class);
    when(batch.getAtomicLong(StepConcurrencyCounterKey.forAccount("acc1"))).thenReturn(acc1Async);

    service.setAccountCounts(Map.of("acc1", 5L));

    verify(acc1Async).setAsync(5L);
    verify(batch).execute();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void setAccountCountsSkipsBatchForEmptyMap() {
    service.setAccountCounts(Map.of());

    verify(redissonClient, org.mockito.Mockito.never()).createBatch();
  }

  @SuppressWarnings("unchecked")
  private static RFuture<Long> completed(long value) {
    RFuture<Long> future = mock(RFuture.class);
    doReturn(CompletableFuture.completedFuture(value)).when(future).toCompletableFuture();
    return future;
  }
}
