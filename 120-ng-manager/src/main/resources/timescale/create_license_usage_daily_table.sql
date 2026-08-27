-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

CREATE TABLE IF NOT EXISTS public.license_usage_daily (
       utc_timestamp BIGINT NOT NULL,
       account_identifier TEXT NOT NULL,
       organization_identifier TEXT NOT NULL,
       project_identifier TEXT NOT NULL,
       pipeline_identifier TEXT NOT NULL,
       stage_identifier TEXT NOT NULL,
       ci_os_type TEXT NOT NULL,
       ci_resource_class TEXT NOT NULL,
       created_at BIGINT NOT NULL,
       used_credits int NOT NULL,
       module_type TEXT NOT NULL
    );
COMMIT;

BEGIN;
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_event_daily ON public.license_usage_daily (
       utc_timestamp,
       account_identifier,
       organization_identifier,
       project_identifier,
       pipeline_identifier,
       stage_identifier,
       ci_os_type,
       ci_resource_class,
       module_type
    );
COMMIT;

BEGIN;
SELECT createPartitioningTable(
    param_table_name := 'license_usage_daily',
    param_column_name := 'utc_timestamp',
    p_interval := '604800000'
);
COMMIT;
