-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- SCORECARDS TABLE START ------------

BEGIN;

CREATE TABLE IF NOT EXISTS scorecards (
    id text NOT NULL,
    account_identifier VARCHAR(64) NOT NULL,
    identifier TEXT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT NULL,
    filter TEXT NOT NULL,
    weightage_strategy VARCHAR(32) NOT NULL,
    total_number_of_checks SMALLINT NOT NULL DEFAULT 0,
    number_of_custom_checks SMALLINT NOT NULL DEFAULT 0,
    published BOOLEAN DEFAULT FALSE NOT NULL,
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    created_at BIGINT NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    last_updated_at BIGINT NULL,
    last_updated_by VARCHAR(128) NULL,
    PRIMARY KEY (id)
);

COMMIT;

BEGIN;

CREATE UNIQUE INDEX IF NOT EXISTS scorecards_unique_idx ON scorecards USING btree (account_identifier, identifier);
CREATE INDEX IF NOT EXISTS scorecards_account_identifier_created_at_idx ON scorecards USING btree (account_identifier, created_at);

COMMIT;

---------- SCORECARDS END ------------
