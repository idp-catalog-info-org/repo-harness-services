-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ng_users'
        AND column_name = 'parent_unique_id'
    ) THEN
        ALTER TABLE ng_users ADD COLUMN parent_unique_id TEXT NULL;
        RAISE NOTICE 'Added column parent_unique_id to ng_users.';
    END IF;
END $$;


DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ng_users'
        AND column_name = 'unique_id'
    ) THEN
        ALTER TABLE ng_users ADD COLUMN unique_id TEXT NULL;
        RAISE NOTICE 'Added column unique_id to ng_users.';
    END IF;
END $$;

COMMIT;