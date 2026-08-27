-- Copyright 2026 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- ADD SERVICEID TO GITOPS_APP_INFO START ------------
BEGIN;
ALTER TABLE gitops_app_info ADD COLUMN IF NOT EXISTS serviceid TEXT;
COMMIT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'gitops_app_info_serviceid_index'
    ) THEN
        PERFORM create_index(
            'public.gitops_app_info',
            'gitops_app_info_serviceid_index',
            'accountid, serviceid',
            'btree');
    END IF;
END $$;
---------- ADD SERVICEID TO GITOPS_APP_INFO END ------------
