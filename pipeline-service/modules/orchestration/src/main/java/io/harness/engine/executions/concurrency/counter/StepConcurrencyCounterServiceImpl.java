/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.counter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBatch;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;

/**
 * Thin Redisson wrapper for the step-concurrency counters. Reads clamp to zero; zero-delta writes
 * short-circuit to a read.
 *
 * <p>Reuses the shared {@code cacheRedissonClient} pool (no new Redisson client, no new connection
 * pool). To keep the shared client's per-command timeout + retry defaults from stalling the
 * orchestration status-transition thread on a Redis blip, every call goes through the Redisson
 * async API with a bounded per-call wait. If the future doesn't complete in time we bail — the
 * Redisson background retry continues on the shared pool but the calling thread is released.
 * Drift left behind is reconciled by the daily rebuild job in the follow-up PR.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class StepConcurrencyCounterServiceImpl implements StepConcurrencyCounterService {
  private static final long PER_CALL_TIMEOUT_MS = 200L;
  private static final int SCAN_BATCH_SIZE = 1000;

  private final RedissonClient redissonClient;

  @Inject
  public StepConcurrencyCounterServiceImpl(@Named("cacheRedissonClient") RedissonClient redissonClient) {
    this.redissonClient = redissonClient;
  }

  @Override
  public long getClusterCount() {
    return clampNonNegative(awaitRead(atomicLong(StepConcurrencyCounterKey.forCluster()).getAsync()));
  }

  @Override
  public long getAccountCount(String accountId) {
    return clampNonNegative(awaitRead(atomicLong(StepConcurrencyCounterKey.forAccount(accountId)).getAsync()));
  }

  @Override
  public long incrementCluster(long delta) {
    if (delta == 0) {
      return getClusterCount();
    }
    return clampNonNegativeOnWrite(atomicLong(StepConcurrencyCounterKey.forCluster()), delta);
  }

  @Override
  public long incrementAccount(String accountId, long delta) {
    if (delta == 0) {
      return getAccountCount(accountId);
    }
    return clampNonNegativeOnWrite(atomicLong(StepConcurrencyCounterKey.forAccount(accountId)), delta);
  }

  /**
   * Applies {@code delta} to the counter and enforces the invariant "counter never persists
   * below zero." If the resulting value is negative (drift from a missed +1, or an over-large
   * bulk decrement), a compensating positive delta brings it back to zero atomically. Reads
   * already clamp on the way out; this pins the persisted value too so subsequent increments
   * start from 0 rather than from a stale negative.
   *
   * <p>Race note: a concurrent +1 landing between the addAndGet and the compensating write is
   * safe — the concurrent op sees whatever the counter is at that moment, and our compensating
   * write only adds enough to reach zero from the negative floor we observed. Worst case: a
   * transient over-count of one until the daily rebuild reconciles.
   */
  private long clampNonNegativeOnWrite(RAtomicLong atomicLong, long delta) {
    long after = awaitWrite(atomicLong.addAndGetAsync(delta));
    if (after < 0) {
      long compensation = -after;
      log.warn("[STEP_CONCURRENCY] counter went negative (after={}, delta={}); compensating +{} to clamp at zero",
          after, delta, compensation);
      awaitWrite(atomicLong.addAndGetAsync(compensation));
      return 0L;
    }
    return after;
  }

  @Override
  public void setClusterCount(long value) {
    atomicLong(StepConcurrencyCounterKey.forCluster()).set(value);
  }

  @Override
  public void setAccountCount(String accountId, long value) {
    atomicLong(StepConcurrencyCounterKey.forAccount(accountId)).set(value);
  }

  @Override
  public Map<String, Long> getAllAccountCounts() {
    Iterable<String> keys =
        redissonClient.getKeys().getKeysByPattern(StepConcurrencyCounterKey.accountKeyPattern(), SCAN_BATCH_SIZE);
    Iterator<String> keyIterator = keys.iterator();
    if (!keyIterator.hasNext()) {
      return Map.of();
    }

    RBatch batch = redissonClient.createBatch();
    Map<String, RFuture<Long>> futuresByAccountId = new LinkedHashMap<>();
    while (keyIterator.hasNext()) {
      String key = keyIterator.next();
      futuresByAccountId.put(StepConcurrencyCounterKey.accountIdFromKey(key), batch.getAtomicLong(key).getAsync());
    }
    batch.execute();

    Map<String, Long> result = new LinkedHashMap<>();
    futuresByAccountId.forEach((accountId, future) -> result.put(accountId, clampNonNegative(awaitRead(future))));
    return result;
  }

  @Override
  public void setAccountCounts(Map<String, Long> accountIdToValue) {
    if (accountIdToValue.isEmpty()) {
      return;
    }
    RBatch batch = redissonClient.createBatch();
    accountIdToValue.forEach(
        (accountId, value) -> batch.getAtomicLong(StepConcurrencyCounterKey.forAccount(accountId)).setAsync(value));
    batch.execute();
  }

  private RAtomicLong atomicLong(String key) {
    return redissonClient.getAtomicLong(key);
  }

  // Bounded-wait wrapper for mutation calls. On timeout / failure surface a RuntimeException so
  // the hook's exception-swallow path takes over. Redisson's internal retry keeps running in the
  // background regardless.
  private static long awaitWrite(RFuture<Long> future) {
    try {
      return future.toCompletableFuture().get(PER_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (TimeoutException | ExecutionException | CompletionException ex) {
      throw new RuntimeException(ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(ex);
    }
  }

  // Bounded-wait wrapper for reads. On failure fall back to 0 so the gate fails open (matches the
  // clamp-non-negative contract — a stale zero is safer than a stale surge).
  private static long awaitRead(RFuture<Long> future) {
    try {
      return future.toCompletableFuture().get(PER_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (TimeoutException | ExecutionException | CompletionException ex) {
      log.warn("[STEP_CONCURRENCY] counter read timed out or failed; returning 0 (fail-open)", ex);
      return 0L;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.warn("[STEP_CONCURRENCY] counter read interrupted; returning 0 (fail-open)", ex);
      return 0L;
    }
  }

  private static long clampNonNegative(long value) {
    return Math.max(0L, value);
  }
}
