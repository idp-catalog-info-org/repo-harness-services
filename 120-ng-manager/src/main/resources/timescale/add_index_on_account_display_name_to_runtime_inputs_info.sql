-- Copyright 2026 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'idx_runtime_inputs_info_account_id_display_name_plan_execution_id'
    ) THEN
        CREATE INDEX idx_runtime_inputs_info_account_id_display_name_plan_execution_id ON runtime_inputs_info(account_id, display_name, plan_execution_id);
    END IF;
END $$;

COMMIT;