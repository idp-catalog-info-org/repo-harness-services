-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'service_infra_info_account_org_project_service_index'
    ) THEN
        CREATE INDEX service_infra_info_account_org_project_service_index ON public.service_infra_info USING btree (accountid, orgidentifier, projectidentifier, service_id);
    END IF;
END $$;
COMMIT;