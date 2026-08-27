-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

CREATE INDEX IF NOT EXISTS stage_execution_accid_starttime_idx ON stage_execution(account_identifier, start_time);
CREATE INDEX IF NOT EXISTS stage_execution_accid_orgid_projectid_pipeline_id_starttime_idx
    ON stage_execution(account_identifier, org_identifier, project_identifier, pipeline_identifier);

COMMIT;
