-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- Migration: Add pattern ops index for efficient LIKE prefix queries
-- Purpose: Optimize DELETE operations that use LIKE 'prefix%' patterns for batch deletions
-- Date: 2025-11-07

-- Create pattern ops index for LIKE queries with prefix patterns
-- This index enables efficient prefix searches using LIKE 'prefix%'
-- The text_pattern_ops operator class supports B-tree indexes on text columns for pattern matching
-- This is particularly useful for deleteUsingPattern operations that delete graphs with composite keys
CREATE INDEX IF NOT EXISTS idx_orch_cache_cache_key_pattern
    ON orchestration_graph_cache (cache_key text_pattern_ops);

-- Note: This index will improve performance for queries like:
-- DELETE FROM orchestration_graph_cache WHERE cache_key LIKE 'planExecutionId%'
-- Which are used to delete both main graphs and their subgraphs (planExecutionId/nodeExecutionId)
