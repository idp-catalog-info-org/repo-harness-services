-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- BACKSTAGE_CATALOG TABLE START ------------

BEGIN;

CREATE TABLE IF NOT EXISTS backstage_catalog (
    id text NOT NULL,
    account_identifier VARCHAR(64) NOT NULL,
    identifier TEXT NOT NULL,
    name VARCHAR(128) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    type VARCHAR(32) NULL,
    number_of_relations SMALLINT NOT NULL DEFAULT 0,
    owner VARCHAR(128) NULL,
    created_at BIGINT NOT NULL,
    last_updated_at BIGINT NULL,
    PRIMARY KEY (id)
);

COMMIT;

BEGIN;

CREATE UNIQUE INDEX IF NOT EXISTS backstage_catalog_unique_idx ON backstage_catalog USING btree (account_identifier, identifier);
CREATE INDEX IF NOT EXISTS backstage_catalog_account_identifier_created_at_idx ON backstage_catalog USING btree (account_identifier, created_at);

COMMIT;

---------- BACKSTAGE_CATALOG END ------------
