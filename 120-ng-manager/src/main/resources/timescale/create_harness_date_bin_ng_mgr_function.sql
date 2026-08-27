-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

--Simulates https://www.postgresql.org/docs/14/functions-datetime.html#FUNCTIONS-DATETIME-BIN

-- there are two overloads for this function
-- harness_date_bin_ng_mgr(p_bucket_width_ms bigint, p_epoch_time_ms bigint)
-- harness_date_bin_ng_mgr(p_bucket_width INTERVAL, p_timestamp timestamptz)

CREATE OR REPLACE FUNCTION harness_date_bin_ng_mgr(
p_bucket_width_ms bigint,
p_epoch_time_ms bigint)
RETURNS bigint
LANGUAGE PLPGSQL
IMMUTABLE
PARALLEL SAFE
AS $$
DECLARE
BEGIN
    RETURN ((p_epoch_time_ms / p_bucket_width_ms)::bigint * p_bucket_width_ms);
END;
$$ ;


CREATE OR REPLACE FUNCTION harness_date_bin_ng_mgr(
p_bucket_width INTERVAL,
p_timestamp timestamptz)
RETURNS timestamptz
LANGUAGE PLPGSQL IMMUTABLE PARALLEL SAFE AS $$
    DECLARE v_bucket_width_bigint bigint;
    v_timestamp_bigint bigint;
BEGIN
    v_bucket_width_bigint := EXTRACT(epoch FROM p_bucket_width);
    v_timestamp_bigint := EXTRACT(epoch FROM p_timestamp)::bigint;

    RETURN to_timestamp((v_timestamp_bigint / v_bucket_width_bigint)::bigint * v_bucket_width_bigint);
END;
$$ ;