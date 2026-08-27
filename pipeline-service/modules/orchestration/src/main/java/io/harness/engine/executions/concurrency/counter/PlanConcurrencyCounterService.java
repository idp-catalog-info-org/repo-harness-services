/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.counter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Map;

/**
 * Reads and mutates the Redis-backed plan-level concurrency counters used by the per-project mode.
 * Mirrors {@code StepConcurrencyCounterService} but keyed by account and by project.
 *
 * <p>Reads clamp negatives to zero — drift can push a counter negative (a bulk decrement outrunning
 * increments) and a negative value would silently disable the gate. Redis creates keys lazily, so
 * no explicit upsert is needed.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public interface PlanConcurrencyCounterService {
  /** Current per-account count, clamped to [0, Long.MAX_VALUE]. */
  long getAccountCount(String accountId);

  /**
   * Current per-project count, clamped to [0, Long.MAX_VALUE]. Keyed by the stable project
   * {@code parentUniqueId} so the count follows the project across org moves.
   */
  long getProjectCount(String accountId, String parentUniqueId);

  /** Atomically add {@code delta} to the per-account counter. Delta may be negative. */
  long incrementAccount(String accountId, long delta);

  /** Atomically add {@code delta} to the per-project counter (keyed by {@code parentUniqueId}). */
  long incrementProject(String accountId, String parentUniqueId, long delta);

  /**
   * Atomically reserve one slot: check both counters against their caps and increment both only if
   * both have headroom, in one server-side (Lua) step. Closes the check-then-act race that lets pods
   * over-shoot the cap (PIPE-35674).
   *
   * <p>A null/empty {@code parentUniqueId} uses only the account leg. A cap {@code < 0} means "no
   * cap" (still incremented on success).
   *
   * @return {@code true} if reserved; {@code false} if either scope is at cap.
   */
  boolean tryReserveSlot(String accountId, String parentUniqueId, long projectCap, long accountCap);

  /** Overwrite the per-account counter (rebuild / backfill paths only). */
  void setAccountCount(String accountId, long value);

  /** Overwrite the per-project counter (rebuild / backfill paths only). */
  void setProjectCount(String accountId, String parentUniqueId, long value);

  /** All per-account counters keyed by accountId (rebuild path — reconcile stale keys). */
  Map<String, Long> getAllAccountCounts();

  /**
   * All per-project counters keyed by the {@code accountId/parentUniqueId} scope string (rebuild
   * path). Use {@code PlanConcurrencyCounterKey.projectScope(...)} to build the same key.
   */
  Map<String, Long> getAllProjectCounts();

  /**
   * Per-project counters for a single account, keyed by the project {@code parentUniqueId}. Scans
   * only this account's project keys ({@code plan_concurrency:project:<accountId>/*}) so it does not
   * read every project counter in the cluster.
   */
  Map<String, Long> getProjectCountsForAccount(String accountId);

  /** Batch overwrite per-account counters keyed by accountId (rebuild path). */
  void setAccountCounts(Map<String, Long> accountIdToValue);

  /** Batch overwrite per-project counters keyed by {@code accountId/parentUniqueId} scope (rebuild). */
  void setProjectCounts(Map<String, Long> projectScopeToValue);
}
