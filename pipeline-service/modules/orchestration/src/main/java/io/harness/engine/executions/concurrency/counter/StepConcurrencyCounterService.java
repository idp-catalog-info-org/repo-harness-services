/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.counter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Map;

/**
 * Reads and mutates the Redis-backed step-concurrency counters. See the "The gate" section of
 * the TechSpec for semantics.
 *
 * <p>Reads clamp negative values to zero — drift can push a counter negative (bulk decrement
 * outrunning individual increments), and a negative value would silently disable the gate.
 *
 * <p>Redis creates keys lazily on first {@code incrementBy}/{@code set}, so no explicit upsert
 * is required.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public interface StepConcurrencyCounterService {
  /** Current cluster-wide count, clamped to [0, Long.MAX_VALUE]. */
  long getClusterCount();

  /** Current per-account count, clamped to [0, Long.MAX_VALUE]. */
  long getAccountCount(String accountId);

  /** Atomically add {@code delta} to the cluster counter. Delta may be negative. */
  long incrementCluster(long delta);

  /** Atomically add {@code delta} to the per-account counter. Delta may be negative. */
  long incrementAccount(String accountId, long delta);

  /** Overwrite the cluster counter (rebuild / backfill paths only). */
  void setClusterCount(long value);

  /** Overwrite the per-account counter (rebuild / backfill paths only). */
  void setAccountCount(String accountId, long value);

  /**
   * Current count for every account with a live counter key in Redis, scanned via the
   * {@code step_concurrency:account:*} pattern. Values are clamped the same as {@link
   * #getAccountCount(String)}. Empty map if no per-account keys exist yet.
   */
  Map<String, Long> getAllAccountCounts();

  /** Overwrite the per-account counter for every entry in {@code accountIdToValue}, in one round trip. */
  void setAccountCounts(Map<String, Long> accountIdToValue);
}
