-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;

CREATE INDEX IF NOT EXISTS ACCOUNTID_ORGIDENTIFIER_PROJECTIDENTIFIER_STARTTS_INDEX ON PIPELINE_EXECUTION_SUMMARY(ACCOUNTID,ORGIDENTIFIER,PROJECTIDENTIFIER,STARTTS);

COMMIT;
