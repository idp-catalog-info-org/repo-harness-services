-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- CATALOG TABLE START ------------

BEGIN;

CREATE TABLE IF NOT EXISTS catalog (
    id TEXT NOT NULL,
    account_identifier VARCHAR(64) NOT NULL,
    identifier TEXT NOT NULL,
    entity_ref TEXT NOT NULL,
    org_identifier TEXT NULL,
    project_identifier TEXT NULL,
    name TEXT NOT NULL,
    kind VARCHAR(32) NOT NULL,
    type TEXT NULL,
    tags TEXT[] NULL,
    number_of_relations SMALLINT NOT NULL DEFAULT 0,
    owner TEXT NULL,
    created_at BIGINT NOT NULL,
    last_updated_at BIGINT NULL,
    PRIMARY KEY (id)
);

COMMIT;

BEGIN;

CREATE UNIQUE INDEX IF NOT EXISTS catalog_unique_idx ON catalog USING btree (account_identifier, entity_ref);
CREATE INDEX IF NOT EXISTS catalog_account_identifier_created_at_idx ON catalog USING btree (account_identifier, created_at);
CREATE INDEX IF NOT EXISTS catalog_account_org_project_idx ON catalog USING btree (account_identifier, org_identifier, project_identifier);

COMMIT;

---------- CATALOG END ------------