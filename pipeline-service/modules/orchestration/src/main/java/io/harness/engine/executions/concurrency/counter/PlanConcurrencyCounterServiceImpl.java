/*
 * Copyright 2025 Harness Inc. All rights reserved.
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBatch;
import org.redisson.api.RFuture;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/**
 * Thin Redisson wrapper for the plan-level per-project concurrency counters. Mirrors
 * {@code StepConcurrencyCounterServiceImpl}: reads clamp to zero, zero-delta writes short-circuit to
 * a read, and every call uses the async API with a bounded per-call wait so a Redis blip never
 * stalls the status-transition thread. Reuses the shared {@code cacheRedissonClient} pool.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PlanConcurrencyCounterServiceImpl implements PlanConcurrencyCounterService {
  private static final long PER_CALL_TIMEOUT_MS = 400L;
  private static final int SCAN_BATCH_SIZE = 1000;

  // Atomic "reserve one slot" — check and both increments run server-side in one round-trip, so
  // concurrent drainers can't admit against the same headroom and over-shoot the cap.
  //
  //   KEYS[1] = project counter key      KEYS[2] = account counter key
  //   ARGV[1] = projectCap (< 0 => no cap)
  //   ARGV[2] = accountCap (< 0 => no cap)
  //   ARGV[3] = hasProject ("1" if the project leg participates, else "0")
  //
  // Returns 1 if reserved (both +1), 0 if either scope is at cap (nothing mutated). Counts clamp to
  // >= 0 before comparing; both legs are checked before either increments, so no partial reserve.
  private static final String RESERVE_SLOT_SCRIPT = "local accountCap = tonumber(ARGV[2]) "
      + "local accountCount = tonumber(redis.call('get', KEYS[2]) or '0') "
      + "if accountCount < 0 then accountCount = 0 end "
      + "if accountCap >= 0 and accountCount >= accountCap then return 0 end "
      + "local hasProject = tonumber(ARGV[3]) "
      + "if hasProject == 1 then "
      + "  local projectCap = tonumber(ARGV[1]) "
      + "  local projectCount = tonumber(redis.call('get', KEYS[1]) or '0') "
      + "  if projectCount < 0 then projectCount = 0 end "
      + "  if projectCap >= 0 and projectCount >= projectCap then return 0 end "
      + "end "
      + "redis.call('incrby', KEYS[2], 1) "
      + "if hasProject == 1 then redis.call('incrby', KEYS[1], 1) end "
      + "return 1";

  private final RedissonClient redissonClient;

  @Inject
  public PlanConcurrencyCounterServiceImpl(@Named("cacheRedissonClient") RedissonClient redissonClient) {
    this.redissonClient = redissonClient;
  }

  @Override
  public long getAccountCount(String accountId) {
    return clampNonNegative(awaitRead(atomicLong(PlanConcurrencyCounterKey.forAccount(accountId)).getAsync()));
  }

  @Override
  public long getProjectCount(String accountId, String parentUniqueId) {
    return clampNonNegative(
        awaitRead(atomicLong(PlanConcurrencyCounterKey.forProject(accountId, parentUniqueId)).getAsync()));
  }

  @Override
  public long incrementAccount(String accountId, long delta) {
    if (delta == 0) {
      return getAccountCount(accountId);
    }
    return clampNonNegativeOnWrite(atomicLong(PlanConcurrencyCounterKey.forAccount(accountId)), delta);
  }

  @Override
  public long incrementProject(String accountId, String parentUniqueId, long delta) {
    if (delta == 0) {
      return getProjectCount(accountId, parentUniqueId);
    }
    return clampNonNegativeOnWrite(atomicLong(PlanConcurrencyCounterKey.forProject(accountId, parentUniqueId)), delta);
  }

  @Override
  public boolean tryReserveSlot(String accountId, String parentUniqueId, long projectCap, long accountCap) {
    boolean hasProject = parentUniqueId != null && !parentUniqueId.isEmpty();
    // Both legs are always passed as KEYS; the script skips KEYS[1] when hasProject == 0.
    String projectKey = hasProject ? PlanConcurrencyCounterKey.forProject(accountId, parentUniqueId)
                                   : PlanConcurrencyCounterKey.forProject(accountId, "");
    String accountKey = PlanConcurrencyCounterKey.forAccount(accountId);
    try {
      // StringCodec so these writes read back identically through getAtomicLong.
      Long result = redissonClient.getScript(StringCodec.INSTANCE)
                        .eval(RScript.Mode.READ_WRITE, RESERVE_SLOT_SCRIPT, RScript.ReturnType.INTEGER,
                            List.of(projectKey, accountKey), Long.toString(projectCap), Long.toString(accountCap),
                            hasProject ? "1" : "0");
      return result != null && result == 1L;
    } catch (Exception ex) {
      // Fail-closed: a Redis blip must not admit past the cap. The caller requeues and the next
      // drain cycle retries once Redis recovers.
      log.warn("[PLAN_CONCURRENCY] tryReserveSlot failing closed for account={} parentUniqueId={}", accountId,
          parentUniqueId, ex);
      return false;
    }
  }

  /**
   * Applies {@code delta} and pins the stored value at zero if it would go negative (drift from a
   * missed +1 or an over-large bulk decrement) so later increments start from 0. See
   * {@code StepConcurrencyCounterServiceImpl} for the race-safety note.
   */
  private long clampNonNegativeOnWrite(RAtomicLong atomicLong, long delta) {
    long after = awaitWrite(atomicLong.addAndGetAsync(delta));
    if (after < 0) {
      long compensation = -after;
      log.warn("[PLAN_CONCURRENCY] counter went negative (after={}, delta={}); compensating +{} to clamp at zero",
          after, delta, compensation);
      awaitWrite(atomicLong.addAndGetAsync(compensation));
      return 0L;
    }
    return after;
  }

  @Override
  public void setAccountCount(String accountId, long value) {
    atomicLong(PlanConcurrencyCounterKey.forAccount(accountId)).set(value);
  }

  @Override
  public void setProjectCount(String accountId, String parentUniqueId, long value) {
    atomicLong(PlanConcurrencyCounterKey.forProject(accountId, parentUniqueId)).set(value);
  }

  @Override
  public Map<String, Long> getAllAccountCounts() {
    return scanCounts(PlanConcurrencyCounterKey.accountKeyPattern(), PlanConcurrencyCounterKey::accountIdFromKey);
  }

  @Override
  public Map<String, Long> getAllProjectCounts() {
    return scanCounts(PlanConcurrencyCounterKey.projectKeyPattern(), PlanConcurrencyCounterKey::projectScopeFromKey);
  }

  @Override
  public Map<String, Long> getProjectCountsForAccount(String accountId) {
    // Scoped SCAN glob so Redis returns only this account's project keys; keyed by parentUniqueId.
    return scanCounts(PlanConcurrencyCounterKey.projectKeyPatternForAccount(accountId),
        key -> PlanConcurrencyCounterKey.parentUniqueIdFromProjectKey(accountId, key));
  }

  private Map<String, Long> scanCounts(String pattern, java.util.function.Function<String, String> keyToId) {
    Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(pattern, SCAN_BATCH_SIZE);
    Iterator<String> keyIterator = keys.iterator();
    if (!keyIterator.hasNext()) {
      return Map.of();
    }
    RBatch batch = redissonClient.createBatch();
    Map<String, RFuture<Long>> futuresById = new LinkedHashMap<>();
    while (keyIterator.hasNext()) {
      String key = keyIterator.next();
      futuresById.put(keyToId.apply(key), batch.getAtomicLong(key).getAsync());
    }
    batch.execute();

    Map<String, Long> result = new LinkedHashMap<>();
    futuresById.forEach((id, future) -> result.put(id, clampNonNegative(awaitRead(future))));
    return result;
  }

  @Override
  public void setAccountCounts(Map<String, Long> accountIdToValue) {
    if (accountIdToValue.isEmpty()) {
      return;
    }
    RBatch batch = redissonClient.createBatch();
    accountIdToValue.forEach(
        (accountId, value) -> batch.getAtomicLong(PlanConcurrencyCounterKey.forAccount(accountId)).setAsync(value));
    batch.execute();
  }

  @Override
  public void setProjectCounts(Map<String, Long> projectScopeToValue) {
    if (projectScopeToValue.isEmpty()) {
      return;
    }
    RBatch batch = redissonClient.createBatch();
    // Keys are already the projectScope portion; rebuild the full key with the project prefix.
    projectScopeToValue.forEach((scope, value) -> batch.getAtomicLong(projectKeyFromScope(scope)).setAsync(value));
    batch.execute();
  }

  private static String projectKeyFromScope(String scope) {
    // scope is "<accountId>/<parentUniqueId>"; rebuild the full key with the project prefix.
    String[] parts = scope.split("/", -1);
    String accountId = parts.length > 0 ? parts[0] : "";
    String parentUniqueId = parts.length > 1 ? parts[1] : "";
    return PlanConcurrencyCounterKey.forProject(accountId, parentUniqueId);
  }

  private RAtomicLong atomicLong(String key) {
    return redissonClient.getAtomicLong(key);
  }

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

  private static long awaitRead(RFuture<Long> future) {
    try {
      return future.toCompletableFuture().get(PER_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (TimeoutException | ExecutionException | CompletionException ex) {
      log.warn("[PLAN_CONCURRENCY] counter read timed out or failed; returning 0 (fail-open)", ex);
      return 0L;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.warn("[PLAN_CONCURRENCY] counter read interrupted; returning 0 (fail-open)", ex);
      return 0L;
    }
  }

  private static long clampNonNegative(long value) {
    return Math.max(0L, value);
  }
}
