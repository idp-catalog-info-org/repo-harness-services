-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

-- Drop old index with accountid if it exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'idx_pipeline_exec_cd_accid_parentid_pipeid'
    ) THEN
        EXECUTE 'DROP INDEX IF EXISTS public.idx_pipeline_exec_cd_accid_parentid_pipeid';
    END IF;
END $$;

-- Create new index without accountid, with parent_unique_id first
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'idx_pipeline_exec_cd_parentid_pipeid'
    ) THEN
        PERFORM create_index(
            'public.pipeline_execution_summary_cd',
            'idx_pipeline_exec_cd_parentid_pipeid',
            'parent_unique_id, pipelineidentifier',
            'btree'
        );
    END IF;
END $$;

COMMIT;
