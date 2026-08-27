-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

-- Only drop the existing parent_unique_id-only index if it exists.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'pipeline_execution_summary_idx_parent_unique_id'
    ) THEN
        EXECUTE 'DROP INDEX IF EXISTS public.pipeline_execution_summary_idx_parent_unique_id';
    END IF;
END $$;

COMMIT;
