-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- get_service_instances_by_date is used to generate license daily usage report for Service instance model

-- this must be kept in sync with CDLicenseUsageDAL::QUERY_FETCH_SERVICE_INSTANCE_USAGE to avoid inconsistencies
SET SEARCH_PATH = "public";

DROP FUNCTION IF EXISTS get_service_instances_by_date;

CREATE OR REPLACE FUNCTION get_service_instances_by_date(
  p_accountid TEXT
, p_date DATE
, p_debug BOOL DEFAULT FALSE
)
RETURNS TABLE (
-- column names need a prefix or Postgres will error because of conflict with other columns and variables
t_report_day_or_month_date DATE,
t_service_instance_count INT
) AS $$
DECLARE v_rows_affected INT default 0;
        v_query_start_time TIMESTAMPTZ;
        v_query_elapsed_secs_decimal DECIMAL(8, 3);
		v_service_instance_count INT;
BEGIN
		v_query_start_time := clock_timestamp();

	SELECT percentile_disc(0.95) WITHIN GROUP (ORDER BY instanceCountsPerReportedAt.instancecount) AS instanceCount
	INTO v_service_instance_count
	FROM (SELECT date_trunc('minute', reportedat) AS reportedat,
				SUM(instancecount) AS instancecount
			FROM ng_instance_stats
			WHERE accountid = p_accountid
				-- Report has to be generated for last 30 days including the current reported day(v_interim_begin_date).
				-- For example report for 2023-06-05 day, time range interval
				 -- will be from: 2023-05-07 00:00:00 including the whole May 07, to: 2023-06-06 00:00:00 including the whole Jun 05
				 -- It's overall 30 days.
				AND reportedat >= p_date - INTERVAL '29 day'
				AND reportedat < p_date + INTERVAL '1 day'
			GROUP BY
				accountid,
				date_trunc('minute', reportedat)
			ORDER BY reportedat DESC
		) instanceCountsPerReportedAt;

	GET DIAGNOSTICS v_rows_affected = ROW_COUNT ;

	IF p_debug THEN
		v_query_elapsed_secs_decimal := EXTRACT(epoch from (clock_timestamp() - v_query_start_time))::decimal(8, 3);
		RAISE INFO '%: v_query_elapsed_secs_decimal = %, v_rows_affected = %', clock_timestamp(), v_query_elapsed_secs_decimal, v_rows_affected;
	END IF;

    RETURN QUERY SELECT p_date, v_service_instance_count;
END;
$$ LANGUAGE PLPGSQL;