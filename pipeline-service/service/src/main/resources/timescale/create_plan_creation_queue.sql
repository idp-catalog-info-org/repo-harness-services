-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- plan_creation_queue backs the Postgres FIFO replacement for the hsqs-based queue-based plan
-- creation flow (PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION) and the per-project concurrency skip-ahead
-- drain (PIPE_PER_PROJECT_CONCURRENCY_OVERRIDES). One row per pipeline execution currently held in
-- QUEUED_PLAN_CREATION. Rows are inserted on queue-in and deleted on dequeue. Sync with
-- planExecutions.status is guaranteed by the application-layer write ordering (Postgres INSERT
-- first, Mongo status flip second, self-healing dequeue on predicate miss).
CREATE TABLE IF NOT EXISTS plan_creation_queue (
    plan_execution_id VARCHAR(64) PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL,
    -- parent_unique_id = the project's stable DB uniqueId; this is the project IDENTITY used to key
    -- the per-project concurrency counter, and it survives project-move-across-orgs.
    parent_unique_id VARCHAR(64),
    -- org_id / project_id are an enqueue-time SNAPSHOT, used only as input to the per-project cap
    -- lookup (the NG-settings API is keyed by org/project) and for debugging. They are NOT the
    -- project identity and may be stale for a row that outlives an org move — the counter never
    -- uses them.
    org_id VARCHAR(64),
    project_id VARCHAR(64),
    priority_type VARCHAR(16),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- FIFO scan on the drain path (ORDER BY created_at ASC LIMIT n) — the only query pattern; required.
CREATE INDEX IF NOT EXISTS idx_plan_creation_queue_created_at
    ON plan_creation_queue(created_at);

-- Lookup by account for backfill / ops / debugging.
CREATE INDEX IF NOT EXISTS idx_plan_creation_queue_account_id
    ON plan_creation_queue(account_id);

-- NOTE: no index on (org_id, project_id) or parent_unique_id — the drain is global FIFO and no
-- query filters by project. Add (account_id, parent_unique_id) only if a per-project fetch/count
-- query is introduced later.
