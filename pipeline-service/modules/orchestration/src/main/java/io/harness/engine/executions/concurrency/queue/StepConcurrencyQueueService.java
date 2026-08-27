/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.queue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;

/**
 * Postgres-backed FIFO work queue for the tier-2 dequeue path of step-level concurrency.
 * See TechSpec "The queue store" for schema and semantics.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public interface StepConcurrencyQueueService {
  /**
   * Insert a queued-waiting node into the queue. Idempotent — retrying the same {@code
   * nodeExecutionId} is a no-op via {@code ON CONFLICT DO NOTHING}. Called before the Mongo
   * status flip to {@code QUEUED_STEP_LIMIT_REACHED} (write ordering: Postgres first).
   */
  void insert(StepConcurrencyQueueEntry entry);

  /**
   * Atomically delete a row by {@code nodeExecutionId}. Returns true if a row was actually
   * removed (this pod owns the claim), false if another pod already claimed it. Also used as a
   * compensating write when the Mongo status flip after queue-in fails, and inline on tier-1
   * dequeue to keep the queue in sync when a same-plan wake-up steals a queued node.
   */
  boolean deleteByNodeExecutionId(String nodeExecutionId);

  /**
   * Fetch up to {@code limit} candidates in FIFO order (oldest {@code createdAt} first). Plain
   * SELECT — no {@code FOR UPDATE} — because the walker holds no Postgres lock while it reads
   * Redis counters between candidates. Race resolution happens via {@link #deleteByNodeExecutionId}.
   */
  List<StepConcurrencyQueueEntry> fetchBatch(int limit);
}
