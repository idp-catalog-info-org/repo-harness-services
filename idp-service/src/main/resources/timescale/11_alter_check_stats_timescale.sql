-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- CHECK_STATS TABLE START ------------

BEGIN;

ALTER TABLE check_stats ADD COLUMN IF NOT EXISTS custom BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE check_stats ADD COLUMN IF NOT EXISTS total INTEGER NOT NULL;
ALTER TABLE check_stats RENAME COLUMN pass_percentage TO pass_count;
ALTER TABLE check_stats DROP CONSTRAINT IF EXISTS check_stats_pkey;
ALTER TABLE check_stats DROP CONSTRAINT IF EXISTS check_stats_account_identifier_check_identifier_fkey;
ALTER TABLE check_stats ADD PRIMARY KEY (account_identifier, check_identifier, custom, calculated_at);

COMMIT;

---------- CHECK_STATS TABLE END ------------