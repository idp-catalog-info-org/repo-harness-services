-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- Normalized graph_vertex table for efficient delta updates
-- Each row represents a single node in the execution graph
-- The graph structure is derived from parent_id relationships

-- ============================================
-- Table: graph_vertex (individual vertices with adjacency info)
-- ============================================
CREATE TABLE IF NOT EXISTS graph_vertex (
    -- Primary key
    node_execution_id VARCHAR(64) PRIMARY KEY,
    plan_execution_id VARCHAR(64),
    account_identifier VARCHAR(64),

    -- Identity columns (extracted for fast queries)
    plan_node_id VARCHAR(64),
    identifier VARCHAR(256),
    name VARCHAR(512),
    step_type VARCHAR(128),
    node_group VARCHAR(32),  -- StepCategory: STAGE, STEP, STRATEGY, etc. (from Level.group)

    -- Pagination support (critical for matrix/loop stages with many children)
    child_index INT DEFAULT 0,

    -- Status & Timing (frequently queried, avoid JSONB parsing)
    status VARCHAR(32),
    start_ts BIGINT,
    end_ts BIGINT,
    execution_mode VARCHAR(32),

    -- Adjacency relationships
    -- Parent relationship (children derived via: WHERE parent_id = ? ORDER BY child_index)
    parent_id VARCHAR(64),
    -- Sibling relationships for chain execution (sequential steps)
    previous_id VARCHAR(64),  -- Previous sibling in chain
    next_id VARCHAR(64),      -- Next sibling in chain

    -- Structured payloads (keep as JSONB for flexibility)
    step_parameters JSONB,
    step_parameters_version INT DEFAULT 0,
    outcome_documents JSONB DEFAULT '{}'::jsonb,
    step_details JSONB DEFAULT '{}'::jsonb,
    failure_info JSONB,
    node_run_info JSONB,
    skip_info JSONB,
    progress_data JSONB,
    unit_progresses JSONB,

    -- Strategy metadata (for matrix/loops)
    strategy_metadata JSONB,
    base_fqn TEXT,
    current_level JSONB,

    -- Retry handling
    retry_ids TEXT[] DEFAULT '{}',
    retry_node_metadata JSONB,  -- Metadata about retry (startTs, endTs, runSequence, originalPlanExecutionId, executedBy)

    -- Execution details
    executable_responses JSONB,
    interrupt_histories JSONB,

    -- Other fields
    execution_input_configured BOOLEAN DEFAULT FALSE,
    log_base_key VARCHAR(512),
    skip_type VARCHAR(32),
    initial_wait_duration_ms BIGINT,

    -- Metadata
    created_at BIGINT,
    last_updated_at BIGINT NOT NULL DEFAULT 0,
    valid_until TIMESTAMPTZ NOT NULL,

    -- Retry handling - nodes marked as old retry should be excluded from queries
    old_retry BOOLEAN DEFAULT FALSE,

    -- Strategy type (LOOP, MATRIX, PARALLELISM) - for STRATEGY nodes only
    strategy_type VARCHAR(32),

    -- Module name (cd, ci, pms, etc.) - derived from step_type via NodeTypeLookupService
    module VARCHAR(64),

    -- Module info (JSONB) - contains module-level metadata from graphUpdateInfo
    -- Structure keyed by graphUpdateInfo document _id:
    -- {"docId": {"stepCategory": "STAGE", "stageUuid": "...", "moduleInfo": {...}}}
    module_info JSONB DEFAULT '{}'::jsonb,

    -- CDC document ID tracking for secondary collections
    -- Used to find target vertex rows on UPDATE events (where fullDocument is null)
    node_executions_info_id VARCHAR(64),         -- 1:1 mapping from nodeExecutionsInfo._id
    outcome_instance_ids TEXT[] DEFAULT '{}',     -- 1:many mapping from outcomeInstances._id
    graph_update_info_ids TEXT[] DEFAULT '{}'     -- 1:many mapping from graphUpdateInfo._id
);

-- ============================================
-- Indexes for graph_vertex
-- ============================================

-- Index for fetching all vertices for a plan execution
CREATE INDEX IF NOT EXISTS idx_graph_vertex_plan
    ON graph_vertex(plan_execution_id);

-- Index for account-based queries and TTL cleanup
CREATE INDEX IF NOT EXISTS idx_graph_vertex_account
    ON graph_vertex(account_identifier, valid_until);

-- Paginated child queries (covering index for common fields)
-- This is the key index for efficient pagination of matrix/loop children
-- Orders by created_at for natural insertion order
CREATE INDEX IF NOT EXISTS idx_vertex_children_page
    ON graph_vertex(plan_execution_id, parent_id, created_at)
    INCLUDE (node_execution_id, status, name, identifier, step_type);

-- TTL cleanup for vertices
CREATE INDEX IF NOT EXISTS idx_vertex_valid_until
    ON graph_vertex(valid_until);

-- Index for stage/strategy/fork nodes lookup (used to derive layoutNodeMap)
-- Covers getStageLayoutNodes() which filters node_group IN ('STAGE', 'STRATEGY', 'FORK')
CREATE INDEX IF NOT EXISTS idx_vertex_stages
    ON graph_vertex(plan_execution_id, node_group)
    WHERE node_group IN ('STAGE', 'STRATEGY', 'FORK');

-- CDC document ID indexes for secondary collection UPDATE lookups
-- nodeExecutionsInfo: 1:1 mapping, simple B-tree index
CREATE INDEX IF NOT EXISTS idx_graph_vertex_node_exec_info_id
    ON graph_vertex(node_executions_info_id)
    WHERE node_executions_info_id IS NOT NULL;

-- outcomeInstances: 1:many mapping, GIN index for array contains (@>) queries
CREATE INDEX IF NOT EXISTS idx_graph_vertex_outcome_ids
    ON graph_vertex USING GIN (outcome_instance_ids)
    WHERE outcome_instance_ids IS NOT NULL AND outcome_instance_ids != '{}';

-- graphUpdateInfo: 1:many mapping, GIN index for array contains (@>) queries
CREATE INDEX IF NOT EXISTS idx_graph_vertex_update_info_ids
    ON graph_vertex USING GIN (graph_update_info_ids)
    WHERE graph_update_info_ids IS NOT NULL AND graph_update_info_ids != '{}';
