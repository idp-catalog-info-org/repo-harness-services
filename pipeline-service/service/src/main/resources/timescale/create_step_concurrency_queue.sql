-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- step_concurrency_queue backs the tier-2 (cross-plan) FIFO dequeue path of step-level concurrency.
-- One row per leaf node currently in QUEUED_STEP_LIMIT_REACHED. Rows are inserted on queue-in and
-- deleted on dequeue. Sync with `nodeExecutions.status` is guaranteed by the write ordering in the
-- application layer (Postgres INSERT first, Mongo status flip second, self-healing dequeue).
CREATE TABLE IF NOT EXISTS step_concurrency_queue (
    node_execution_id VARCHAR(64) PRIMARY KEY,
    plan_execution_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- FIFO scan on the tier-2 dequeue path (LIMIT 100 ORDER BY created_at ASC).
CREATE INDEX IF NOT EXISTS idx_step_concurrency_queue_created_at
    ON step_concurrency_queue(created_at);

-- Lookup by account for backfill / debugging.
CREATE INDEX IF NOT EXISTS idx_step_concurrency_queue_account_id
    ON step_concurrency_queue(account_id);
