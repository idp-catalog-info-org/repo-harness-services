-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- SCORECARDS_CHECKS TABLE START ------------

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='scorecards_checks'
                   AND column_name='created_at') THEN
           ALTER TABLE scorecards_checks
           ADD COLUMN created_at BIGINT DEFAULT EXTRACT(epoch FROM CURRENT_TIMESTAMP)::BIGINT NOT NULL;
       RAISE NOTICE 'Added column created_at to scorecards_checks.';
    END IF;
END $$;

COMMIT;

---------- SCORECARDS_CHECKS TABLE END ------------
