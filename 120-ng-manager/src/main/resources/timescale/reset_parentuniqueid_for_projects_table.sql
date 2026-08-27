-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

DO $$
BEGIN
    ------------------------------------------------------------------
    -- Skip migration if parent_unique_id_old already exists
    -- This indicates the migration has already been completed
    ------------------------------------------------------------------
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'projects'
          AND column_name = 'parent_unique_id_old'
    ) THEN
        RAISE NOTICE 'Migration already completed: parent_unique_id_old column exists. Skipping migration.';
        RETURN;
    END IF;

    ------------------------------------------------------------------
    -- 1. Rename existing parent_unique_id → parent_unique_id_old
    ------------------------------------------------------------------
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'projects'
          AND column_name = 'parent_unique_id'
    ) THEN
        ALTER TABLE projects
            RENAME COLUMN parent_unique_id TO parent_unique_id_old;
        RAISE NOTICE 'Renamed parent_unique_id to parent_unique_id_old.';
    END IF;

    ------------------------------------------------------------------
    -- 2. Create a fresh parent_unique_id column
    ------------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'projects'
          AND column_name = 'parent_unique_id'
    ) THEN
        ALTER TABLE projects
            ADD COLUMN parent_unique_id TEXT NULL;
        RAISE NOTICE 'Created new empty parent_unique_id column.';
    END IF;

    ------------------------------------------------------------------
    -- 3. Drop old index if it exists
    ------------------------------------------------------------------
    IF EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE tablename = 'projects'
          AND indexname = 'idx_projects_parent_unique_id'
    ) THEN
        EXECUTE 'DROP INDEX idx_projects_parent_unique_id';
        RAISE NOTICE 'Dropped existing index idx_projects_parent_unique_id.';
    END IF;

    ------------------------------------------------------------------
    -- 4. Create new index on NEW parent_unique_id column
    --    only if it does not already exist
    ------------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE tablename = 'projects'
          AND indexname = 'idx_projects_parent_unique_id'
    ) THEN
        EXECUTE 'CREATE INDEX idx_projects_parent_unique_id
                 ON projects (parent_unique_id)';
        RAISE NOTICE 'Created new index idx_projects_parent_unique_id on parent_unique_id.';
    END IF;

END $$;

COMMIT;
