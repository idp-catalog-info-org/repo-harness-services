-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

ALTER TABLE dbops_step_execution ADD COLUMN IF NOT EXISTS archivePath text;
ALTER TABLE dbops_step_execution ADD COLUMN IF NOT EXISTS schemaFile text;
ALTER TABLE dbops_step_execution ALTER COLUMN command DROP NOT NULL;
ALTER TABLE dbops_step_execution ALTER COLUMN schemarepository DROP NOT NULL;

COMMIT;