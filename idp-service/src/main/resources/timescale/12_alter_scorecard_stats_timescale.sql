-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- SCORECARDS_STATS TABLE START ------------

BEGIN;

ALTER TABLE scorecard_stats DROP CONSTRAINT IF EXISTS scorecard_stats_account_identifier_scorecard_identifier_fkey;

COMMIT;

---------- SCORECARDS_STATS TABLE END ------------