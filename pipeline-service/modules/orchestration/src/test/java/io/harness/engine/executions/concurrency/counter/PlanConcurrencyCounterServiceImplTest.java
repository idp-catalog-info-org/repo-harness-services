/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBatch;
import org.redisson.api.RFuture;
import org.redisson.api.RKeys;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;

public class PlanConcurrencyCounterServiceImplTest extends CategoryTest {
  @Mock private RedissonClient redissonClient;
  @Mock private RKeys rKeys;
  @Mock private RAtomicLong atomicLong;
  @Mock private RFuture<Long> future;

  private PlanConcurrencyCounterServiceImpl service;

  private static final String ACCOUNT_ID = "acc123";
  private static final String PARENT_UNIQUE_ID = "proj-uuid-456";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new PlanConcurrencyCounterServiceImpl(redissonClient);
    when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetAccountCount_Success() {
    when(atomicLong.getAsync()).thenReturn(future);
    when(future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(5L));

    long count = service.getAccountCount(ACCOUNT_ID);

    assertThat(count).isEqualTo(5L);
    verify(redissonClient).getAtomicLong(PlanConcurrencyCounterKey.forAccount(ACCOUNT_ID));
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetAccountCount_ReturnsZeroOnTimeout() {
    when(atomicLong.getAsync()).thenReturn(future);
    CompletableFuture<Long> timeoutFuture = new CompletableFuture<>();
    when(future.toCompletableFuture()).thenReturn(timeoutFuture);

    long count = service.getAccountCount(ACCOUNT_ID);

    // Should fail-open to 0 on timeout
    assertThat(count).isEqualTo(0L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetAccountCount_ClampsNegativeToZero() {
    when(atomicLong.getAsync()).thenReturn(future);
    when(future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(-3L));

    long count = service.getAccountCount(ACCOUNT_ID);

    assertThat(count).isEqualTo(0L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetProjectCount_Success() {
    when(atomicLong.getAsync()).thenReturn(future);
    when(future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(3L));

    long count = service.getProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID);

    assertThat(count).isEqualTo(3L);
    verify(redissonClient).getAtomicLong(PlanConcurrencyCounterKey.forProject(ACCOUNT_ID, PARENT_UNIQUE_ID));
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testIncrementAccount_PositiveDelta() {
    when(atomicLong.addAndGetAsync(2L)).thenReturn(future);
    when(future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(7L));

    long result = service.incrementAccount(ACCOUNT_ID, 2L);

    assertThat(result).isEqualTo(7L);
    verify(atomicLong).addAndGetAsync(2L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testIncrementAccount_NegativeDelta() {
    when(atomicLong.addAndGetAsync(-1L)).thenReturn(future);
    when(future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(4L));

    long result = service.incrementAccount(ACCOUNT_ID, -1L);

    assertThat(result).isEqualTo(4L);
    verify(atomicLong).addAndGetAsync(-1L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testIncrementAccount_ZeroDelta_ShortCircuitsToRead() {
    when(atomicLong.getAsync()).thenReturn(future);
    when(future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(5L));

    long result = service.incrementAccount(ACCOUNT_ID, 0L);

    assertThat(result).isEqualTo(5L);
    verify(atomicLong).getAsync();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testIncrementAccount_ClampsNegativeResult() {
    // First call returns negative value
    RFuture<Long> addFuture = mock(RFuture.class);
    when(atomicLong.addAndGetAsync(-10L)).thenReturn(addFuture);
    when(addFuture.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(-3L));

    // Compensation call to bring it back to zero
    RFuture<Long> compensateFuture = mock(RFuture.class);
    when(atomicLong.addAndGetAsync(3L)).thenReturn(compensateFuture);
    when(compensateFuture.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(0L));

    long result = service.incrementAccount(ACCOUNT_ID, -10L);

    assertThat(result).isEqualTo(0L);
    verify(atomicLong).addAndGetAsync(-10L);
    verify(atomicLong).addAndGetAsync(3L); // compensation
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testIncrementProject_Success() {
    when(atomicLong.addAndGetAsync(1L)).thenReturn(future);
    when(future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(4L));

    long result = service.incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, 1L);

    assertThat(result).isEqualTo(4L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTryReserveSlot_ReservedWhenScriptReturnsOne() {
    RScript script = mock(RScript.class);
    when(redissonClient.getScript(any(Codec.class))).thenReturn(script);
    when(script.eval(
             eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), anyList(), any(), any(), any()))
        .thenReturn(1L);

    boolean reserved = service.tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID, 5L, 100L);

    assertThat(reserved).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTryReserveSlot_DeniedWhenScriptReturnsZero() {
    RScript script = mock(RScript.class);
    when(redissonClient.getScript(any(Codec.class))).thenReturn(script);
    when(script.eval(
             eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), anyList(), any(), any(), any()))
        .thenReturn(0L);

    boolean reserved = service.tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID, 5L, 100L);

    assertThat(reserved).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTryReserveSlot_FailsClosedOnScriptException() {
    RScript script = mock(RScript.class);
    when(redissonClient.getScript(any(Codec.class))).thenReturn(script);
    when(script.eval(
             eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), anyList(), any(), any(), any()))
        .thenThrow(new RuntimeException("Redis down"));

    boolean reserved = service.tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID, 5L, 100L);

    // Fail-closed: a Redis blip must never admit past the cap.
    assertThat(reserved).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTryReserveSlot_PassesProjectAndAccountKeysAndArgs() {
    RScript script = mock(RScript.class);
    when(redissonClient.getScript(any(Codec.class))).thenReturn(script);
    when(script.eval(
             eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), anyList(), any(), any(), any()))
        .thenReturn(1L);

    service.tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID, 5L, 100L);

    ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Object> argsCaptor = ArgumentCaptor.forClass(Object.class);
    verify(script).eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), keysCaptor.capture(),
        argsCaptor.capture(), argsCaptor.capture(), argsCaptor.capture());

    List<Object> keys = keysCaptor.getValue();
    assertThat(keys).containsExactly(PlanConcurrencyCounterKey.forProject(ACCOUNT_ID, PARENT_UNIQUE_ID),
        PlanConcurrencyCounterKey.forAccount(ACCOUNT_ID));
    // projectCap, accountCap, hasProject
    assertThat(argsCaptor.getAllValues()).containsExactly("5", "100", "1");
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTryReserveSlot_AccountOnlyWhenNoParentUniqueId() {
    RScript script = mock(RScript.class);
    when(redissonClient.getScript(any(Codec.class))).thenReturn(script);
    when(script.eval(
             eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), anyList(), any(), any(), any()))
        .thenReturn(1L);

    boolean reserved = service.tryReserveSlot(ACCOUNT_ID, null, -1L, 100L);

    assertThat(reserved).isTrue();
    ArgumentCaptor<Object> argsCaptor = ArgumentCaptor.forClass(Object.class);
    verify(script).eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), anyList(),
        argsCaptor.capture(), argsCaptor.capture(), argsCaptor.capture());
    // hasProject flag must be "0" so the script only touches the account leg.
    assertThat(argsCaptor.getAllValues().get(2)).isEqualTo("0");
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testSetAccountCount() {
    service.setAccountCount(ACCOUNT_ID, 10L);

    verify(atomicLong).set(10L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testSetProjectCount() {
    service.setProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID, 8L);

    verify(atomicLong).set(8L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetAllAccountCounts_EmptyResult() {
    when(redissonClient.getKeys()).thenReturn(rKeys);
    Iterable<String> emptyIterable = () -> new Iterator<String>() {
      @Override
      public boolean hasNext() {
        return false;
      }
      @Override
      public String next() {
        return null;
      }
    };
    when(rKeys.getKeysByPattern(eq(PlanConcurrencyCounterKey.accountKeyPattern()), eq(1000))).thenReturn(emptyIterable);

    Map<String, Long> result = service.getAllAccountCounts();

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetAllAccountCounts_WithKeys() {
    when(redissonClient.getKeys()).thenReturn(rKeys);

    String key1 = PlanConcurrencyCounterKey.forAccount("acc1");
    String key2 = PlanConcurrencyCounterKey.forAccount("acc2");
    Iterable<String> keys = Arrays.asList(key1, key2);

    when(rKeys.getKeysByPattern(eq(PlanConcurrencyCounterKey.accountKeyPattern()), eq(1000))).thenReturn(keys);

    RBatch batch = mock(RBatch.class);
    when(redissonClient.createBatch()).thenReturn(batch);

    RAtomicLong batchAtomicLong = mock(RAtomicLong.class);
    when(batch.getAtomicLong(anyString())).thenReturn(batchAtomicLong);

    RFuture<Long> future1 = mock(RFuture.class);
    RFuture<Long> future2 = mock(RFuture.class);
    when(batchAtomicLong.getAsync()).thenReturn(future1).thenReturn(future2);

    when(future1.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(5L));
    when(future2.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(3L));

    Map<String, Long> result = service.getAllAccountCounts();

    assertThat(result).hasSize(2);
    assertThat(result.get("acc1")).isEqualTo(5L);
    assertThat(result.get("acc2")).isEqualTo(3L);
    verify(batch).execute();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testSetAccountCounts_EmptyMap() {
    service.setAccountCounts(Map.of());

    // Should do nothing, no batch created
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testSetAccountCounts_WithValues() {
    RBatch batch = mock(RBatch.class);
    when(redissonClient.createBatch()).thenReturn(batch);

    RAtomicLong batchAtomicLong = mock(RAtomicLong.class);
    when(batch.getAtomicLong(anyString())).thenReturn(batchAtomicLong);

    Map<String, Long> values = Map.of("acc1", 10L, "acc2", 20L);
    service.setAccountCounts(values);

    verify(batch).execute();
    verify(batchAtomicLong).setAsync(10L);
    verify(batchAtomicLong).setAsync(20L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testSetProjectCounts_WithValues() {
    RBatch batch = mock(RBatch.class);
    when(redissonClient.createBatch()).thenReturn(batch);

    RAtomicLong batchAtomicLong = mock(RAtomicLong.class);
    when(batch.getAtomicLong(anyString())).thenReturn(batchAtomicLong);

    String scope1 = "acc1/proj-uuid-1";
    String scope2 = "acc2/proj-uuid-2";
    Map<String, Long> values = Map.of(scope1, 5L, scope2, 8L);

    service.setProjectCounts(values);

    verify(batch).execute();
    verify(batchAtomicLong).setAsync(5L);
    verify(batchAtomicLong).setAsync(8L);
  }
}
