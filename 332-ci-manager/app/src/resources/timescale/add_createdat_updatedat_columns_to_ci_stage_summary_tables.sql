-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='stage_execution_summary_ci'
                   AND column_name='row_created_at') THEN
        ALTER TABLE stage_execution_summary_ci
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to stage_execution_summary_ci.';
    END IF;
END $$;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='stage_execution_summary_ci'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE stage_execution_summary_ci
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to stage_execution_summary_ci.';
    END IF;
END $$;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='pipeline_execution_summary_ci_committers'
                   AND column_name='row_created_at') THEN
        ALTER TABLE pipeline_execution_summary_ci_committers
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to pipeline_execution_summary_ci_committers.';
    END IF;
END $$;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='pipeline_execution_summary_ci_committers'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE pipeline_execution_summary_ci_committers
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to pipeline_execution_summary_ci_committers.';
    END IF;
END $$;
COMMIT;