-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- Simulates https://docs.timescale.com/api/latest/hyperfunctions/gapfilling/time_bucket_gapfill/
-- It creates groups based on time interval. This is typically used in left join to simulate time_bucket_gapfill

-- there are two overloads for this function
-- harness_time_bucket_list(p_started_at_epoch bigint, p_ended_at_epoch bigint, p_interval TEXT, p_time_magnitude INT)
    -- e.g. to get time buckets at a 4 hour interval
    -- harness_time_bucket_list(<started_at>, <ended_at>, 'hours', 4)
-- harness_time_bucket_list(p_started_at_epoch bigint, p_ended_at_epoch bigint, p_interval TEXT)


CREATE OR REPLACE FUNCTION harness_time_bucket_list(
    p_started_at_epoch bigint,
    p_ended_at_epoch bigint,
    p_interval TEXT,
    p_time_magnitude INT
)
RETURNS TABLE (t_seq_datetime timestamptz)
LANGUAGE PLPGSQL IMMUTABLE PARALLEL SAFE AS $$
DECLARE
    v_started_at_epoch_dtm timestamptz;
    v_ended_at_epoch_dtm timestamptz;
    v_dtm_stop_num INT;
BEGIN
    v_started_at_epoch_dtm := TO_TIMESTAMP(p_started_at_epoch);
    v_ended_at_epoch_dtm := TO_TIMESTAMP(p_ended_at_epoch);
    v_dtm_stop_num := EXTRACT(EPOCH FROM (v_ended_at_epoch_dtm - v_started_at_epoch_dtm)) / EXTRACT(epoch FROM concat(p_time_magnitude, ' ', p_interval)::INTERVAL)::INTEGER;
    RETURN QUERY (
        SELECT (v_started_at_epoch_dtm + (concat(t.series * p_time_magnitude, ' ', p_interval)::INTERVAL)) AS t_seq_datetime
        FROM (SELECT *
        FROM generate_series(0, v_dtm_stop_num) series) t
    );
END;
$$;

CREATE OR REPLACE FUNCTION harness_time_bucket_list(
p_started_at_epoch bigint,
p_ended_at_epoch bigint,
p_interval TEXT
)
RETURNS TABLE (t_seq_datetime timestamptz)
    LANGUAGE PLPGSQL IMMUTABLE PARALLEL SAFE AS $$
    DECLARE v_started_at_epoch_dtm timestamptz;
    v_ended_at_epoch_dtm timestamptz;
    v_dtm_stop_num INT;
BEGIN
    v_started_at_epoch_dtm := DATE_TRUNC(p_interval, TO_TIMESTAMP(p_started_at_epoch));
    v_ended_at_epoch_dtm := DATE_TRUNC(p_interval, TO_TIMESTAMP(p_ended_at_epoch));

    v_dtm_stop_num := EXTRACT(EPOCH FROM (v_ended_at_epoch_dtm - v_started_at_epoch_dtm)) / EXTRACT(epoch FROM concat('1 ', p_interval)::INTERVAL)::INTEGER;

    RETURN QUERY (SELECT (v_started_at_epoch_dtm + (concat(t.series, p_interval)::INTERVAL)) AS t_seq_datetime
        FROM (SELECT *
        FROM generate_series(0, v_dtm_stop_num) series) t);
END;
$$;