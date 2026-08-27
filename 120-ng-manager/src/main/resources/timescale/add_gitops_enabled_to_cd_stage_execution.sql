-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE UPPER(table_name) = UPPER('cd_stage_execution')
      AND UPPER(column_name) = UPPER('gitOpsEnabled')
  ) THEN
    ALTER TABLE CD_STAGE_EXECUTION ADD COLUMN gitOpsEnabled boolean;
  END IF;
END;
$$;
