-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- Create the orchestration graph cache table (PostgreSQL equivalent of SpringCacheEntity)
-- This table stores OrchestrationGraph objects binary blobs
CREATE TABLE IF NOT EXISTS orchestration_graph_cache (
    -- Primary key: cache_key (stores both planExecutionId and planExecutionId/nodeExecutionId)
    cache_key VARCHAR(50) PRIMARY KEY,
    context_value BIGINT NOT NULL DEFAULT 0,

    -- Account identification
    account_identifier VARCHAR(50) NOT NULL,

    -- Core graph data as BYTEA (Kryo-serialized binary data)
    graph_data BYTEA NOT NULL,

    -- Audit and lifecycle fields
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    entity_updated_at BIGINT NOT NULL DEFAULT 0,
    valid_until TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 1
);

-- Index on created_at for time-based queries
CREATE INDEX IF NOT EXISTS idx_orch_cache_created_at
    ON orchestration_graph_cache(created_at);