-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- SCORECARD_STATS TABLE START ------------

BEGIN;

CREATE TABLE IF NOT EXISTS scorecard_stats (
    account_identifier VARCHAR(64) NOT NULL,
    scorecard_identifier TEXT NOT NULL,
    scores TEXT NOT NULL,
    calculated_at BIGINT NOT NULL,
    PRIMARY KEY (account_identifier, scorecard_identifier, calculated_at),
    CONSTRAINT scorecard_stats_account_identifier_scorecard_identifier_fkey FOREIGN KEY (account_identifier, scorecard_identifier) REFERENCES scorecards(account_identifier, identifier) ON DELETE CASCADE
);

COMMIT;

---------- SCORECARD_STATS TABLE END ------------