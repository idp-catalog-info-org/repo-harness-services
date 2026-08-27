/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.planqueue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;

/**
 * Postgres-backed FIFO work queue for queue-based plan creation. Replaces the external hsqs queue
 * service (behind the {@code useDbQueueForPlanCreation} toggle) and backs the per-project
 * concurrency skip-ahead drain.
 *
 * <p>The queue is only a lightweight FIFO index; the durable state of a queued pipeline execution
 * lives in Mongo (the {@code PlanExecution} in {@code QUEUED_PLAN_CREATION} plus the heavy
 * {@code PlanCreationQueueRequest} doc). Sync between this table and {@code planExecutions.status}
 * is guaranteed by the write ordering (Postgres INSERT first, Mongo status flip second) and the
 * self-healing drain (a stale row is discarded on a Mongo predicate miss).
 */
@OwnedBy(HarnessTeam.PIPELINE)
public interface PlanCreationDbQueueService {
  /**
   * Insert a queued pipeline execution into the queue. Idempotent — retrying the same
   * {@code planExecutionId} is a no-op via {@code ON CONFLICT DO NOTHING}.
   */
  void insert(PlanCreationDbQueueEntry entry);

  /**
   * Atomically delete a row by {@code planExecutionId}. Returns true if a row was actually removed
   * (this pod owns the claim), false if another pod already claimed it (or Postgres blipped — the
   * drain's Mongo predicate check is the backstop for that ambiguity).
   */
  boolean deleteByPlanExecutionId(String planExecutionId);

  /**
   * Fetch up to {@code limit} candidates in FIFO order (oldest {@code createdAt} first). Plain
   * SELECT — no {@code FOR UPDATE} — because the walker holds no Postgres lock while it evaluates
   * concurrency headroom between candidates. Race resolution happens via
   * {@link #deleteByPlanExecutionId}.
   */
  List<PlanCreationDbQueueEntry> fetchBatch(int limit);
}
