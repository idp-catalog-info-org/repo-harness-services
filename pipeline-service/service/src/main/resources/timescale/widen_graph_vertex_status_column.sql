-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.


-- Widen graph_vertex.status column from VARCHAR(32) to VARCHAR(64).
-- Some Status enum values exceed 32 characters (e.g., QUEUED_GLOBAL_INFRA_CAPACITY_REACHED = 36 chars).
-- The longest current value is 36 chars; using 64 for future safety.
ALTER TABLE graph_vertex ALTER COLUMN status TYPE VARCHAR(64);
