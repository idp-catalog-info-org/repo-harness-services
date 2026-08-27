-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- Drop and recreate index on stage_execution with parent_unique_id as the first field
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'idx_stage_exec_accid_parentid_pipeline_starttime'
    ) THEN
        EXECUTE 'DROP INDEX IF EXISTS public.idx_stage_exec_accid_parentid_pipeline_starttime';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'idx_stage_exec_parentid_accid_pipeline_starttime'
    ) THEN
        PERFORM create_index(
            'public.stage_execution',
            'idx_stage_exec_parentid_accid_pipeline_starttime',
            'parent_unique_id, account_identifier, pipeline_identifier, start_time',
            'btree'
        );
    END IF;
END $$;

COMMIT;
