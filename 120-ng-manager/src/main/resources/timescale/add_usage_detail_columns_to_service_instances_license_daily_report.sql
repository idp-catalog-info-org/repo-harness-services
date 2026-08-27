-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

ALTER TABLE service_instances_license_daily_report ADD COLUMN IF NOT EXISTS gitops_license_count INTEGER DEFAULT 0;
ALTER TABLE service_instances_license_daily_report ADD COLUMN IF NOT EXISTS service_deployment_license_count INTEGER DEFAULT 0;
ALTER TABLE service_instances_license_daily_report ADD COLUMN IF NOT EXISTS pipeline_execution_license_count INTEGER DEFAULT 0;

COMMIT;

BEGIN;

UPDATE service_instances_license_daily_report SET service_deployment_license_count = license_count;

COMMIT;
