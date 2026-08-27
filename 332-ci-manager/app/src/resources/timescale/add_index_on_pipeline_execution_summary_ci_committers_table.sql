-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;
CREATE INDEX IF NOT EXISTS pipeline_execution_summary_ci_committers_account_type_name_private_idx ON pipeline_execution_summary_ci_committers USING btree (accountid, moduleinfo_type, committer_name, moduleinfo_is_private);
CREATE INDEX IF NOT EXISTS pipeline_execution_summary_ci_committers_name_proj_org_idx ON pipeline_execution_summary_ci_committers USING btree (committer_name, projectidentifier, orgidentifier);
COMMIT;