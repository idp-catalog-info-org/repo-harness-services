-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='cd_stage_execution'
                   AND column_name='execution_strategy_type') THEN
        ALTER TABLE cd_stage_execution
        ADD COLUMN execution_strategy_type text NULL;
        RAISE NOTICE 'Added column execution_strategy_type to cd_stage_execution.';
    END IF;
END $$;
