-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'service_infra_info_gitops_env_ids_gin_idx'
    ) THEN
        PERFORM create_index('public.service_infra_info',
        'service_infra_info_gitops_env_ids_gin_idx',
        'gitops_env_ids',
        'gin');
    END IF;
END $$;
