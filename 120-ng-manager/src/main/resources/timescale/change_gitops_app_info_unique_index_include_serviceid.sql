-- Copyright 2026 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- CHANGE GITOPS_APP_INFO UNIQUE INDEX TO INCLUDE SERVICEID START ------------
-- Requires CDS-127425 (migration v123) extended create_index with UNIQUE + partial WHERE.
-- PostgreSQL 14 compatible: two partial unique indexes (NULL-aware pattern, see access-control
-- NullAwarePartialIndexes.md) instead of NULLS NOT DISTINCT (PG 15+ only).

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'gitops_app_info_unique_application_index'
    ) THEN
        EXECUTE 'DROP INDEX IF EXISTS public.gitops_app_info_unique_application_index';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'gitops_app_info_unique_application_with_serviceid'
    ) THEN
        PERFORM create_index(
            'public.gitops_app_info',
            'gitops_app_info_unique_application_with_serviceid',
            'accountid, orgidentifier, projectidentifier, agent_id, applicationname, serviceid',
            'btree',
            true,
            'serviceid IS NOT NULL');
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'gitops_app_info_unique_application_without_serviceid'
    ) THEN
        PERFORM create_index(
            'public.gitops_app_info',
            'gitops_app_info_unique_application_without_serviceid',
            'accountid, orgidentifier, projectidentifier, agent_id, applicationname',
            'btree',
            true,
            'serviceid IS NULL');
    END IF;
END $$;

COMMIT;

---------- CHANGE GITOPS_APP_INFO UNIQUE INDEX TO INCLUDE SERVICEID END ------------
