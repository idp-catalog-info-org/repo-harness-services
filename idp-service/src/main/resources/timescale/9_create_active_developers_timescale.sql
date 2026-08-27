-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- ACTIVE_DEVELOPERS TABLE START ------------

BEGIN;

CREATE TABLE IF NOT EXISTS active_developers (
    account_identifier VARCHAR(64) NOT NULL,
    identifier TEXT NOT NULL,
    email VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    last_accessed_at BIGINT NOT NULL,
    PRIMARY KEY (account_identifier, identifier)
);

COMMIT;

BEGIN;

CREATE INDEX IF NOT EXISTS active_developers_account_identifier_last_accessed_at_idx ON active_developers USING btree (account_identifier, last_accessed_at);

COMMIT;

---------- ACTIVE_DEVELOPERS TABLE END ------------
