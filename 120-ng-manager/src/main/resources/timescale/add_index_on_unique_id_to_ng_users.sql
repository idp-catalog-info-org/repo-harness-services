-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'idx_ng_users_unique_id'
    ) THEN
        PERFORM create_index(
            'public.ng_users',
            'idx_ng_users_unique_id',
            'unique_id',
            'btree'
        );
    END IF;
END $$;

COMMIT;
