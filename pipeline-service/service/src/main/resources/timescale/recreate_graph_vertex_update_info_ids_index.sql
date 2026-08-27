-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.


-- Recreate GIN index on graph_update_info_ids as non-partial.
-- The old partial index (WHERE graph_update_info_ids IS NOT NULL AND graph_update_info_ids != '{}')
-- was not usable by the query planner for @> queries with FOR UPDATE, causing full table scans.
DROP INDEX IF EXISTS idx_graph_vertex_update_info_ids;
CREATE INDEX IF NOT EXISTS idx_graph_vertex_graph_update_info_ids
    ON graph_vertex USING GIN (graph_update_info_ids);
