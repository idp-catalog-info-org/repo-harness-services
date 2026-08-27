-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='service_infra_info'
                   AND column_name='row_created_at') THEN
        ALTER TABLE service_infra_info
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to service_infra_info.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='service_infra_info'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE service_infra_info
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to service_infra_info.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='pipeline_execution_summary_ci'
                   AND column_name='row_created_at') THEN
        ALTER TABLE pipeline_execution_summary_ci
        ADD COLUMN row_created_at timestamp NULL;
        RAISE NOTICE 'Added column row_created_at to pipeline_execution_summary_ci.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='pipeline_execution_summary_ci'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE pipeline_execution_summary_ci
        ADD COLUMN row_updated_at timestamp NULL;
        RAISE NOTICE 'Added column row_updated_at to pipeline_execution_summary_ci.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='pipeline_execution_summary_cd'
                   AND column_name='row_created_at') THEN
        ALTER TABLE pipeline_execution_summary_cd
        ADD COLUMN row_created_at timestamp NULL;
        RAISE NOTICE 'Added column row_created_at to pipeline_execution_summary_cd.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='pipeline_execution_summary_cd'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE pipeline_execution_summary_cd
        ADD COLUMN row_updated_at timestamp NULL;
        RAISE NOTICE 'Added column row_updated_at to pipeline_execution_summary_cd.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='pipelines'
                   AND column_name='row_created_at') THEN
        ALTER TABLE pipelines
        ADD COLUMN row_created_at timestamp NULL;
        RAISE NOTICE 'Added column row_created_at to pipelines.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='pipelines'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE pipelines
        ADD COLUMN row_updated_at timestamp NULL;
        RAISE NOTICE 'Added column row_updated_at to pipelines.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='pipeline_execution_summary'
                   AND column_name='row_created_at') THEN
        ALTER TABLE pipeline_execution_summary
        ADD COLUMN row_created_at timestamp NULL;
        RAISE NOTICE 'Added column row_created_at to pipeline_execution_summary.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='pipeline_execution_summary'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE pipeline_execution_summary
        ADD COLUMN row_updated_at timestamp NULL;
        RAISE NOTICE 'Added column row_updated_at to pipeline_execution_summary.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='step_execution'
                   AND column_name='row_created_at') THEN
        ALTER TABLE step_execution
        ADD COLUMN row_created_at timestamp NULL;
        RAISE NOTICE 'Added column row_created_at to step_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='step_execution'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE step_execution
        ADD COLUMN row_updated_at timestamp NULL;
        RAISE NOTICE 'Added column row_updated_at to step_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='harness_approval_step_execution'
                   AND column_name='row_created_at') THEN
        ALTER TABLE harness_approval_step_execution
        ADD COLUMN row_created_at timestamp NULL;
        RAISE NOTICE 'Added column row_created_at to harness_approval_step_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='harness_approval_step_execution'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE harness_approval_step_execution
        ADD COLUMN row_updated_at timestamp NULL;
        RAISE NOTICE 'Added column row_updated_at to harness_approval_step_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='jira_step_execution'
                   AND column_name='row_created_at') THEN
        ALTER TABLE jira_step_execution
        ADD COLUMN row_created_at timestamp NULL;
        RAISE NOTICE 'Added column row_created_at to jira_step_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='jira_step_execution'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE jira_step_execution
        ADD COLUMN row_updated_at timestamp NULL;
        RAISE NOTICE 'Added column row_updated_at to jira_step_execution.';
    END IF;
END $$;

COMMIT;
