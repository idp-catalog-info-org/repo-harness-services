-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

CREATE TABLE IF NOT EXISTS public.servicenow_step_execution (
                                                      id text NOT NULL,
                                                      type text NULL,
                                                      servicenow_url text  NULL,
                                                      ticket_type text  NULL,
                                                      ticket_status text  NULL,
                                                      ticket_number text  NULL,
                                                      staging_table_name text  NULL,
                                                      import_set_number text  NULL,
                                                      transform_map_outcome text  NULL,
                                                      PRIMARY KEY (id)
    );

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'idx_servicenow_step_execution_ticket_number'
    ) THEN
        PERFORM create_index(
            'public.servicenow_step_execution',
            'idx_servicenow_step_execution_ticket_number',
            'ticket_number',
            'btree'
        );
    END IF;
END $$;

COMMIT;
