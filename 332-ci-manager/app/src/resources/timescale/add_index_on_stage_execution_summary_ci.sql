-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'stage_execution_summary_ci_account_stage_exec_idx'
    ) THEN
        CREATE INDEX stage_execution_summary_ci_account_stage_exec_idx 
        ON stage_execution_summary_ci USING btree (accountidentifier, stageexecutionid);
    END IF;
END $$;
COMMIT;