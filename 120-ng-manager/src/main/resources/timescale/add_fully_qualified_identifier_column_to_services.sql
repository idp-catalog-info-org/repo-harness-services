-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

ALTER TABLE services ADD COLUMN IF NOT EXISTS fully_qualified_identifier text;

UPDATE services
SET fully_qualified_identifier = CASE
    WHEN project_identifier IS NULL AND org_identifier IS NULL THEN CONCAT('account.', identifier)
    WHEN project_identifier IS NULL THEN CONCAT('org.', identifier)
    ELSE identifier
END;

COMMIT;
