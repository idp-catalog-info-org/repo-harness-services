-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- GITOPS APP INFO TABLE START ------------

BEGIN;
 CREATE TABLE IF NOT EXISTS gitops_app_info (
     accountid text  NOT NULL,
     orgidentifier text  NULL,
     projectidentifier text  NULL,
     agent_id text  NULL,
     applicationname text  NULL,
     last_sync_startedat_ts bigint  NULL,
     last_sync_finishedat_ts bigint  NULL);
COMMIT;

---------- GITOPS APP INFO TABLE END ------------

BEGIN;
CREATE UNIQUE INDEX IF NOT EXISTS GITOPS_APP_INFO_UNIQUE_APPLICATION_INDEX ON gitops_app_info (accountid, orgidentifier, projectidentifier, agent_id, applicationname);
COMMIT;