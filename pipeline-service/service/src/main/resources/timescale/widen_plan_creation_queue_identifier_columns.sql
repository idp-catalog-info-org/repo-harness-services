-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- Widen plan_creation_queue.{org_id,project_id,parent_unique_id} from VARCHAR(64) to VARCHAR(128).
-- org_id / project_id store user-supplied NG entity identifiers, which allow up to 128 chars
-- (EntityIdentifier.maxLength), so a 64-char column overflows on insert for long identifiers.
-- Widening is a metadata-only change and does not rewrite the table.
ALTER TABLE plan_creation_queue ALTER COLUMN org_id TYPE VARCHAR(128);
ALTER TABLE plan_creation_queue ALTER COLUMN project_id TYPE VARCHAR(128);
ALTER TABLE plan_creation_queue ALTER COLUMN parent_unique_id TYPE VARCHAR(128);
