-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- get_active_services_by_date is used to generate license daily usage report for Active services model

-- this must be kept in sync with CDLicenseUsageDAL::QUERY_FETCH_INSTANCES_PER_SERVICE to avoid inconsistencies
SET SEARCH_PATH = "public";

DROP FUNCTION IF EXISTS get_active_services_by_date;

CREATE OR REPLACE FUNCTION get_active_services_by_date(
  p_account_id TEXT
, p_date DATE
, p_debug BOOL DEFAULT FALSE
)
RETURNS TABLE (
-- column names need a prefix or Postgres will error because of conflict with other columns and variables
t_report_day_or_month_date DATE,
t_service_licenses INT
) AS $$
DECLARE v_rows_affected INT default 0;
        v_query_start_time TIMESTAMPTZ;
        v_query_elapsed_secs_decimal DECIMAL(8, 3);
        v_lambda_instance_type TEXT[];
		v_license_count INT;
BEGIN

    v_lambda_instance_type := ARRAY['AWS_LAMBDA_INSTANCE', 'AWS_SAM_INSTANCE', 'SERVERLESS_AWS_LAMBDA_INSTANCE', 'GOOGLE_CLOUD_FUNCTIONS_INSTANCE'];
	v_query_start_time := clock_timestamp();

	SELECT
		SUM(licensesConsumedPerService) AS service_licenses
	INTO v_license_count
	FROM
		(
			SELECT
				CASE
					WHEN instancesPerServices.instanceType = ANY(v_lambda_instance_type) THEN
						CASE
							WHEN instancesPerServices.instanceCount IS NULL OR instancesPerServices.instanceCount <= 5
								THEN 1
							WHEN instancesPerServices.instanceCount > 5
								THEN CEILING(instancesPerServices.instanceCount / 5.0)
							END
					ELSE
						CASE
							WHEN instancesPerServices.instanceCount IS NULL OR instancesPerServices.instanceCount <= 20
								THEN 1
							WHEN instancesPerServices.instanceCount > 20
								THEN CEILING(instancesPerServices.instanceCount / 20.0)
							END
				END AS licensesConsumedPerService
			FROM
				-- List all deployed services during specific day or month from service_infra_info table
				(
					SELECT
						CASE
							WHEN service_id LIKE 'account.%' THEN NULL
							ELSE orgIdentifier
						END AS orgIdentifier,
						CASE
							WHEN service_id LIKE 'account.%' OR service_id LIKE 'org.%' THEN NULL
							ELSE projectIdentifier
						END AS projectIdentifier,
						service_id AS serviceIdentifier
					FROM
						service_infra_info
					WHERE
						accountid = p_account_id
						-- Report has to be generated for last 30 days including the current reported day(p_date).
						-- For example report for 2023-06-05 day, time range interval
						-- will be from: 2023-05-07 00:00:00 including the whole May 07, to: 2023-06-06 00:00:00 including the whole Jun 05
						-- It's overall 30 days.
						AND service_startts > EXTRACT(EPOCH FROM DATE (p_date - INTERVAL '29 day')) * 1000
						AND service_startts < EXTRACT(EPOCH FROM DATE (p_date + INTERVAL '1 day')) * 1000
					GROUP BY
						CASE
							WHEN service_id LIKE 'account.%' THEN NULL
							ELSE orgidentifier
						END,
						CASE
							WHEN service_id LIKE 'account.%' OR service_id LIKE 'org.%' THEN NULL
							ELSE projectidentifier
						END,
						service_id
				) activeServices
				LEFT JOIN
				-- List services percentile instances count during specific day or month from ng_instance_stats table
				(
					SELECT
						PERCENTILE_DISC(.95) WITHIN GROUP (ORDER BY instancesPerServicesReportedAt.instanceCount) AS instanceCount,
						instancetype AS instanceType,
						orgid,
						projectid,
						serviceid
					FROM
						(
							SELECT
								DATE_TRUNC('minute', reportedat) AS reportedat,
								CASE
									WHEN serviceid LIKE 'account.%' THEN NULL
									ELSE orgid
								END AS orgid,
								CASE
									WHEN serviceid LIKE 'account.%' OR serviceid LIKE 'org.%' THEN NULL
									ELSE projectid
								END AS projectid,
								serviceid,
								SUM(instancecount) AS instanceCount,
								instancetype
							FROM
								ng_instance_stats
							WHERE
								accountid = p_account_id
								-- Report has to be generated for last 30 days including the current reported day(p_date).
								-- For example report for 2023-06-05 day, time range interval
								-- will be from: 2023-05-07 00:00:00 including the whole May 07, to: 2023-06-06 00:00:00 including the whole Jun 05
								-- It's overall 30 days.
								AND reportedat >= p_date - INTERVAL '29 day'
								AND reportedat < p_date + INTERVAL '1 day'
							GROUP BY
								CASE
									WHEN serviceid LIKE 'account.%' THEN NULL
									ELSE orgid
								END,
								CASE
									WHEN serviceid LIKE 'account.%' OR serviceid LIKE 'org.%' THEN NULL
									ELSE projectid
								END,
								serviceid,
								instancetype,
								DATE_TRUNC('minute', reportedat)
						) instancesPerServicesReportedAt
					GROUP BY
						orgid,
						projectid,
						serviceid,
						instancetype
				) instancesPerServices
				ON (activeServices.orgIdentifier = instancesPerServices.orgid
						OR (activeServices.orgIdentifier IS NULL AND instancesPerServices.orgid IS NULL))
					AND (activeServices.projectIdentifier = instancesPerServices.projectid
						OR (activeServices.projectIdentifier IS NULL AND instancesPerServices.projectid IS NULL))
					AND activeServices.serviceIdentifier = instancesPerServices.serviceid
		) servicesLicenses;


	GET DIAGNOSTICS v_rows_affected = ROW_COUNT ;

	IF p_debug THEN
		v_query_elapsed_secs_decimal := EXTRACT(epoch from (clock_timestamp() - v_query_start_time))::decimal(8, 3);
		RAISE INFO '%: v_query_elapsed_secs_decimal = %, v_rows_affected = %', clock_timestamp(), v_query_elapsed_secs_decimal, v_rows_affected;
	END IF;

    RETURN QUERY SELECT p_date, v_license_count;

END;
$$ LANGUAGE PLPGSQL;

