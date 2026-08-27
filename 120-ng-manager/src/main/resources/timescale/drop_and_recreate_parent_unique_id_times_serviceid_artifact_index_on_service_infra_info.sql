-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- Drop and recreate composite index on service_infra_info with parent_unique_id first (no accountid)
BEGIN;

DO $$
BEGIN
    -- Drop the old index that began with accountid: idx_svc_info_accid_times_svcid_parentid_artifact
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'idx_svc_info_accid_times_svcid_parentid_artifact'
    ) THEN
        EXECUTE 'DROP INDEX IF EXISTS public.idx_svc_info_accid_times_svcid_parentid_artifact';
    END IF;
END $$;

DO $$
BEGIN
    -- Create the new index with parent_unique_id first
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'service_infra_info_parent_unique_id_times_serviceid_artifact_index'
    ) THEN
        PERFORM create_index(
            'public.service_infra_info',
            'service_infra_info_parent_unique_id_times_serviceid_artifact_index',
            'parent_unique_id, service_startts, service_endts, service_id, artifact_display_name',
            'btree'
        );
    END IF;
END $$;

COMMIT;
