-- Copyright 2026 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'idx_unique_event_hourly'
    ) THEN
        CREATE UNIQUE INDEX idx_unique_event_hourly ON license_usage_hourly (
            utc_timestamp, account_identifier, organization_identifier,
            project_identifier, pipeline_identifier, stage_identifier,
            ci_os_type, ci_resource_class, module_type
        );
    END IF;
END $$;

DO $$
BEGIN
ALTER TABLE license_usage_hourly REPLICA IDENTITY USING INDEX idx_unique_event_hourly;
END $$;

COMMIT;
