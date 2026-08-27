-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

-- Create the enum type if it doesn't already exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'optimization_state') THEN
        CREATE TYPE optimization_state AS ENUM ('FULL_RUN', 'OPTIMIZED', 'DISABLED');
        RAISE NOTICE 'Created enum type optimization_state.';
    END IF;
END $$;

-- Add the 'optimizationstate' column if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='stage_execution_summary_ci'
                   AND column_name='optimizationstate') THEN
        ALTER TABLE stage_execution_summary_ci
        ADD COLUMN optimizationstate optimization_state NULL;
        RAISE NOTICE 'Added column optimizationstate to stage_execution_summary_ci.';
    END IF;
END $$;

-- Add the 'timesaved' column if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='stage_execution_summary_ci'
                   AND column_name='timesaved') THEN
        ALTER TABLE stage_execution_summary_ci
        ADD COLUMN timesaved bigint NULL;
        RAISE NOTICE 'Added column timesaved to stage_execution_summary_ci.';
    END IF;
END $$;

COMMIT;