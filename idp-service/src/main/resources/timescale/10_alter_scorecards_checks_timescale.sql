-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- SCORECARDS_CHECKS TABLE START ------------

BEGIN;

ALTER TABLE scorecards_checks ADD COLUMN IF NOT EXISTS custom BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE scorecards_checks DROP CONSTRAINT IF EXISTS scorecards_checks_pkey;
ALTER TABLE scorecards_checks DROP CONSTRAINT IF EXISTS scorecards_checks_account_identifier_scorecard_identifier_fkey;
ALTER TABLE scorecards_checks DROP CONSTRAINT IF EXISTS scorecards_checks_account_identifier_check_identifier_fkey;
ALTER TABLE scorecards_checks ADD PRIMARY KEY (account_identifier, scorecard_identifier, check_identifier, custom);

COMMIT;

---------- SCORECARDS_CHECKS TABLE END ------------