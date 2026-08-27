-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- BACKSTAGE_SCAFFOLDER_TASKS TABLE START ------------

BEGIN;

CREATE TABLE IF NOT EXISTS backstage_scaffolder_tasks (
    id text NOT NULL,
    account_identifier VARCHAR(64) NOT NULL,
    identifier TEXT NOT NULL,
    entity_ref TEXT NOT NULL,
    name VARCHAR(128) NOT NULL,
    number_of_steps SMALLINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL,
    last_heartbeat_at BIGINT NULL,
    task_run_time_minutes SMALLINT NULL,
    PRIMARY KEY (id)
);

COMMIT;

BEGIN;

CREATE UNIQUE INDEX IF NOT EXISTS backstage_scaffolder_tasks_unique_idx ON backstage_scaffolder_tasks USING btree (account_identifier, identifier);
CREATE INDEX IF NOT EXISTS backstage_scaffolder_tasks_account_identifier_created_at_idx ON backstage_scaffolder_tasks USING btree (account_identifier, created_at);

COMMIT;

---------- BACKSTAGE_SCAFFOLDER_TASKS END ------------
