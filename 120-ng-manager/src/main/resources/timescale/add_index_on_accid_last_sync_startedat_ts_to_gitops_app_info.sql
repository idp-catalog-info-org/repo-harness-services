-- Copyright 2026 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- ADD INDEX ON ACCID AND LAST_SYNC_STARTEDAT_TS TO GITOPS_APP_INFO START ------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'gitops_app_info_accid_last_sync_startedat_ts_idx'
    ) THEN
        PERFORM create_index(
            'public.gitops_app_info',
            'gitops_app_info_accid_last_sync_startedat_ts_idx',
            'accountid, last_sync_startedat_ts',
            'btree');
    END IF;
END $$;
---------- ADD INDEX ON ACCID AND LAST_SYNC_STARTEDAT_TS TO GITOPS_APP_INFO END ------------
