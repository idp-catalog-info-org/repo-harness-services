-- Copyright 2026 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='cd_stage_execution'
                   AND column_name='infra_unique_id') THEN
            ALTER TABLE cd_stage_execution
            ADD COLUMN infra_unique_id text NULL;
        RAISE NOTICE 'Added column infra_unique_id to cd_stage_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='cd_stage_execution'
                   AND column_name='infra_parent_unique_id') THEN
            ALTER TABLE cd_stage_execution
            ADD COLUMN infra_parent_unique_id text NULL;
        RAISE NOTICE 'Added column infra_parent_unique_id to cd_stage_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'idx_cse_infra_unique_id_infra_id'
    ) THEN
        CREATE INDEX idx_cse_infra_unique_id_infra_id ON public.cd_stage_execution(infra_unique_id, infra_id);
        RAISE NOTICE 'Created index idx_cse_infra_unique_id_infra_id on cd_stage_execution.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'cd_stage_execution_infra_parent_unique_id_idx'
    ) THEN
        CREATE INDEX cd_stage_execution_infra_parent_unique_id_idx ON public.cd_stage_execution(infra_parent_unique_id);
        RAISE NOTICE 'Created index cd_stage_execution_infra_parent_unique_id_idx on cd_stage_execution.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'idx_infrastructures_acct_org_project_env_identifier'
    ) THEN
        CREATE INDEX idx_infrastructures_acct_org_project_env_identifier ON public.infrastructures(account_id, org_identifier, project_identifier, env_identifier, identifier);
        RAISE NOTICE 'Created index idx_infrastructures_acct_org_project_env_identifier on infrastructures.';
    END IF;
END $$;

COMMIT;
