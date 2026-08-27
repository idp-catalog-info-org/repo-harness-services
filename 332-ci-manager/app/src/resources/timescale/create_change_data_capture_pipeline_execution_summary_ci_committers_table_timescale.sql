-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- PIPELINE EXECUTION SUMMARY CI COMMITTERS TABLE START ------------
BEGIN;
 CREATE TABLE IF NOT EXISTS public.pipeline_execution_summary_ci_committers (
     id text  NOT NULL,
     accountid text  NULL,
     orgidentifier text  NULL,
     projectidentifier text  NULL,
     moduleinfo_is_private boolean NULL,
     scmProvider text NULL,
     commitId text NULL,
     moduleInfo_type text NULL,
     buildType text NULL,
     committer_name text NULL,
     committer_email text NULL,
     startts bigint  NOT NULL,
     endts bigint  NULL,
     planexecutionid text  NULL);
COMMIT;

BEGIN;
CREATE UNIQUE INDEX IF NOT EXISTS pipeline_execution_summary_ci_committers_pkey ON pipeline_execution_summary_ci_committers USING btree (id, planexecutionid, startts);
CREATE INDEX IF NOT EXISTS pipeline_execution_summary_ci_committers_startts_idx ON pipeline_execution_summary_ci_committers USING btree (startts DESC);
CREATE INDEX IF NOT EXISTS pipeline_execution_summary_ci_committers_account_org_proj_idx ON pipeline_execution_summary_ci_committers USING btree (accountid, orgidentifier, projectidentifier);
COMMIT;

BEGIN;
SELECT createPartitioningTable(
    param_table_name := 'pipeline_execution_summary_ci_committers',
    param_column_name := 'startts',
    p_interval := '604800000'
);
COMMIT;

---------- PIPELINE EXECUTION SUMMARY CI COMMITTERS TABLE END ------------