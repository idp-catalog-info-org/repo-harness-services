-- Copyright 2026 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- ADD INDEX ON LAST_SYNC_STARTEDAT_TS TO GITOPS_APP_INFO START ------------
-- CDBillingMetricJob.fetchActiveGitOpsAppsByAccount runs an unscoped (cross-account) range scan
-- on last_sync_startedat_ts every 2 hours. All existing gitops_app_info indexes lead with
-- accountid, so the query degenerates to a full seq scan. This index leads with the timestamp.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'gitops_app_info_last_sync_startedat_ts_idx'
    ) THEN
        PERFORM create_index(
            'public.gitops_app_info',
            'gitops_app_info_last_sync_startedat_ts_idx',
            'last_sync_startedat_ts',
            'btree');
    END IF;
END $$;
---------- ADD INDEX ON LAST_SYNC_STARTEDAT_TS TO GITOPS_APP_INFO END ------------
