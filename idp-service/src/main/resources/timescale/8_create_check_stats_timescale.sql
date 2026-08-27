-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- CHECK_STATS TABLE START ------------

BEGIN;

CREATE TABLE IF NOT EXISTS check_stats (
    account_identifier VARCHAR(64) NOT NULL,
    check_identifier TEXT NOT NULL,
    pass_percentage SMALLINT NOT NULL,
    calculated_at BIGINT NOT NULL,
    PRIMARY KEY (account_identifier, check_identifier, calculated_at),
    CONSTRAINT check_stats_account_identifier_check_identifier_fkey FOREIGN KEY (account_identifier, check_identifier) REFERENCES checks(account_identifier, identifier) ON DELETE CASCADE
);

COMMIT;

---------- CHECK_STATS TABLE END ------------