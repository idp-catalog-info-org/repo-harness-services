-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- Drop and recreate service_infra_info index with parent_unique_id first (no accountid)
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'service_infra_info_account_parent_unique_id_service_index'
    ) THEN
        EXECUTE 'DROP INDEX IF EXISTS public.service_infra_info_account_parent_unique_id_service_index';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'service_infra_info_parent_unique_id_service_index'
    ) THEN
        PERFORM create_index(
            'public.service_infra_info',
            'service_infra_info_parent_unique_id_service_index',
            'parent_unique_id, service_id',
            'btree'
        );
    END IF;
END $$;

COMMIT;
