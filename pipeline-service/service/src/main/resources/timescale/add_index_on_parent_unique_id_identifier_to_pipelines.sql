-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'idx_pipelines_parent_unique_id_identifier_deleted'
    ) THEN
        PERFORM create_index(
            'public.pipelines',
            'idx_pipelines_parent_unique_id_identifier_deleted',
            'parent_unique_id, identifier, deleted, deleted_at',
            'btree'
        );
    END IF;
END $$;

COMMIT;
