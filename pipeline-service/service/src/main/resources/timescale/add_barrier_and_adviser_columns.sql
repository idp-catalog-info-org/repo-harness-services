-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.


-- Add has_barrier_child column to graph_vertex table.
-- This column is set to TRUE on stage nodes that contain a Barrier step child.
-- Used to populate GraphLayoutNodeDTO.barrierFound field without additional queries.
ALTER TABLE graph_vertex ADD COLUMN IF NOT EXISTS has_barrier_child BOOLEAN DEFAULT FALSE;

-- Add adviser_response column to graph_vertex table.
-- Stores the AdviserResponse protobuf message as JSONB.
-- Used to derive manualInterventionAvailableActions from interventionWaitAdvise.availableActions.
ALTER TABLE graph_vertex ADD COLUMN IF NOT EXISTS adviser_response JSONB;

-- Add children_count column to graph_vertex table.
-- Count of direct children for container/wrapper nodes (NG_FORK, STRATEGY, GROUP).
-- Used by UI to display children count.
-- DEFAULT NULL allows distinguishing "not yet populated by CDC" from "genuinely 0 children".
-- Pre-migration rows will have NULL until touched by a CDC event; the mapper returns 0 for NULL
-- to match NodeExecution's default behavior.
ALTER TABLE graph_vertex ADD COLUMN IF NOT EXISTS children_count BIGINT DEFAULT NULL;
