-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;


DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='connectors'
                   AND column_name='row_created_at') THEN
        ALTER TABLE connectors
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to connectors.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='connectors'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE connectors
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to connectors.';
    END IF;
END $$;


DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='services'
                   AND column_name='row_created_at') THEN
        ALTER TABLE services
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to services.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='services'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE services
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to services.';
    END IF;
END $$;


DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='environments'
                   AND column_name='row_created_at') THEN
        ALTER TABLE environments
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to environments.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='environments'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE environments
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to environments.';
    END IF;
END $$;


DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='runtime_inputs_info'
                   AND column_name='row_created_at') THEN
        ALTER TABLE runtime_inputs_info
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to runtime_inputs_info.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='runtime_inputs_info'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE runtime_inputs_info
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to runtime_inputs_info.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='stage_execution'
                   AND column_name='row_created_at') THEN
        ALTER TABLE stage_execution
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to stage_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='stage_execution'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE stage_execution
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to stage_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='cd_stage_execution'
                   AND column_name='row_created_at') THEN
        ALTER TABLE cd_stage_execution
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to cd_stage_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='cd_stage_execution'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE cd_stage_execution
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to cd_stage_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='cd_stage_helm_manifest_info'
                   AND column_name='row_created_at') THEN
        ALTER TABLE cd_stage_helm_manifest_info
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to cd_stage_helm_manifest_info.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='cd_stage_helm_manifest_info'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE cd_stage_helm_manifest_info
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to cd_stage_helm_manifest_info.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='custom_stage_execution'
                   AND column_name='row_created_at') THEN
        ALTER TABLE custom_stage_execution
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to custom_stage_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='custom_stage_execution'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE custom_stage_execution
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to custom_stage_execution.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='gitops_app_info'
                   AND column_name='row_created_at') THEN
        ALTER TABLE gitops_app_info
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to gitops_app_info.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='gitops_app_info'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE gitops_app_info
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to gitops_app_info.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='license_usage_hourly'
                   AND column_name='row_created_at') THEN
        ALTER TABLE license_usage_hourly
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to license_usage_hourly.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='license_usage_hourly'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE license_usage_hourly
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to license_usage_hourly.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='license_usage_daily'
                   AND column_name='row_created_at') THEN
        ALTER TABLE license_usage_daily
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to license_usage_daily.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='license_usage_daily'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE license_usage_daily
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to license_usage_daily.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='license_usage_monthly'
                   AND column_name='row_created_at') THEN
        ALTER TABLE license_usage_monthly
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to license_usage_monthly.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='license_usage_monthly'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE license_usage_monthly
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to license_usage_monthly.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='license_usage_yearly'
                   AND column_name='row_created_at') THEN
        ALTER TABLE license_usage_yearly
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to license_usage_yearly.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='license_usage_yearly'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE license_usage_yearly
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to license_usage_yearly.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='service_instances_license_daily_report'
                   AND column_name='row_created_at') THEN
        ALTER TABLE service_instances_license_daily_report
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to service_instances_license_daily_report.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='service_instances_license_daily_report'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE service_instances_license_daily_report
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to service_instances_license_daily_report.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='services_license_daily_report'
                   AND column_name='row_created_at') THEN
        ALTER TABLE services_license_daily_report
        ADD COLUMN row_created_at bigint NULL;
        RAISE NOTICE 'Added column row_created_at to services_license_daily_report.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='services_license_daily_report'
                   AND column_name='row_updated_at') THEN
        ALTER TABLE services_license_daily_report
        ADD COLUMN row_updated_at bigint NULL;
        RAISE NOTICE 'Added column row_updated_at to services_license_daily_report.';
    END IF;
END $$;

COMMIT;