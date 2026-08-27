-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- CHECKS TABLE START ------------

BEGIN;

CREATE TABLE IF NOT EXISTS checks (
    id text NOT NULL,
    account_identifier VARCHAR(64) NOT NULL,
    identifier TEXT NOT NULL,
    name VARCHAR(128) NOT NULL,
    custom BOOLEAN DEFAULT FALSE NOT NULL,
    description TEXT NULL,
    rule_strategy VARCHAR(32) NOT NULL,
    expression TEXT NULL,
    default_behaviour VARCHAR(32) NOT NULL,
    fail_message TEXT NULL,
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    created_at BIGINT NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    last_updated_at BIGINT NULL,
    last_updated_by VARCHAR(128) NULL,
    PRIMARY KEY (id)
);

COMMIT;

BEGIN;

CREATE UNIQUE INDEX IF NOT EXISTS checks_unique_idx ON checks USING btree (account_identifier, identifier);
CREATE INDEX IF NOT EXISTS checks_account_identifier_created_at_idx ON checks USING btree (account_identifier, created_at);

COMMIT;

---------- CHECKS TABLE END ------------