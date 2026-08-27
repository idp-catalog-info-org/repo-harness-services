-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

DROP INDEX IF EXISTS idx_unique_event_hourly;
ALTER TABLE license_usage_hourly ADD CONSTRAINT license_usage_hourly_pkey PRIMARY KEY (utc_timestamp,
                                                                                       account_identifier,
                                                                                       organization_identifier,
                                                                                       project_identifier,
                                                                                       pipeline_identifier,
                                                                                       stage_identifier,
                                                                                       ci_os_type,
                                                                                       ci_resource_class,
                                                                                       module_type
                                                                                       );

DROP INDEX IF EXISTS idx_unique_event_daily;
ALTER TABLE license_usage_daily ADD CONSTRAINT license_usage_daily_pkey PRIMARY KEY (utc_timestamp,
                                                                                     account_identifier,
                                                                                     organization_identifier,
                                                                                     project_identifier,
                                                                                     pipeline_identifier,
                                                                                     stage_identifier,
                                                                                     ci_os_type,
                                                                                     ci_resource_class,
                                                                                     module_type
                                                                                     );

DROP INDEX IF EXISTS idx_unique_event_monthly;
ALTER TABLE license_usage_monthly ADD CONSTRAINT license_usage_monthly_pkey PRIMARY KEY (utc_timestamp,
                                                                                         account_identifier,
                                                                                         organization_identifier,
                                                                                         project_identifier,
                                                                                         pipeline_identifier,
                                                                                         stage_identifier,
                                                                                         ci_os_type,
                                                                                         ci_resource_class,
                                                                                         module_type
                                                                                         );


DROP INDEX IF EXISTS idx_unique_event_yearly;
ALTER TABLE license_usage_yearly ADD CONSTRAINT license_usage_yearly_pkey PRIMARY KEY (utc_timestamp,
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