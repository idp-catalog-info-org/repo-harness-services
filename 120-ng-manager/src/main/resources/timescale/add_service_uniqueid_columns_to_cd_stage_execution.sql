-- Copyright 2026 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='cd_stage_execution'
                   AND column_name='service_unique_id') THEN
            ALTER TABLE cd_stage_execution
            ADD COLUMN service_unique_id text NULL;
        RAISE NOTICE 'Added column service_unique_id to cd_stage_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='cd_stage_execution'
                   AND column_name='service_parent_unique_id') THEN
            ALTER TABLE cd_stage_execution
            ADD COLUMN service_parent_unique_id text NULL;
        RAISE NOTICE 'Added column service_parent_unique_id to cd_stage_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'idx_cse_service_unique_id_service_id'
    ) THEN
        CREATE INDEX idx_cse_service_unique_id_service_id ON public.cd_stage_execution(service_unique_id, service_id);
        RAISE NOTICE 'Created index idx_cse_service_unique_id_service_id on cd_stage_execution.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'cd_stage_execution_service_parent_unique_id_idx'
    ) THEN
        CREATE INDEX cd_stage_execution_service_parent_unique_id_idx ON public.cd_stage_execution(service_parent_unique_id);
        RAISE NOTICE 'Created index cd_stage_execution_service_parent_unique_id_idx on cd_stage_execution.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'idx_services_acct_identifier_org_project'
    ) THEN
        CREATE INDEX idx_services_acct_identifier_org_project ON public.services(account_id, identifier, org_identifier, project_identifier);
        RAISE NOTICE 'Created index idx_services_acct_identifier_org_project on services.';
    END IF;
END $$;

COMMIT;
