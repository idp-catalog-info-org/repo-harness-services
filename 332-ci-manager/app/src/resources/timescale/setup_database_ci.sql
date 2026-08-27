-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CREATE OR REPLACE FUNCTION extension_setup()
RETURNS TEXT LANGUAGE plpgsql
AS $$
DECLARE
    partman_available BOOLEAN;
    timescaledb_available BOOLEAN;
BEGIN
    -- Check if pg_partman extension is available
    BEGIN
        SELECT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'pg_partman') INTO partman_available;
    EXCEPTION
        WHEN OTHERS THEN
            RETURN 'Error checking pg_partman availability.';
    END;
    -- Check if timescaledb extension is available
    BEGIN
        SELECT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'timescaledb') INTO timescaledb_available;
    EXCEPTION
        WHEN OTHERS THEN
            RETURN 'Error checking timescaledb availability.';
    END;
    -- Try to create pg_partman extension if available
    IF partman_available THEN
        BEGIN
            -- Create schema if it doesn't exist
            CREATE SCHEMA IF NOT EXISTS partman;
        EXCEPTION
            WHEN OTHERS THEN
                RETURN 'Error creating partman schema.';
        END;
        BEGIN
            -- Create pg_partman extension
            CREATE EXTENSION IF NOT EXISTS pg_partman CASCADE SCHEMA partman;
            RETURN 'PG_PARTMAN extension created successfully.';
        EXCEPTION
            WHEN OTHERS THEN
                RETURN 'Error creating PG_PARTMAN extension.';
        END;
    -- If pg_partman is not available, try to create timescaledb extension
    ELSIF timescaledb_available THEN
        BEGIN
            CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;
            RETURN 'TimescaleDB extension created successfully.';
        EXCEPTION
            WHEN OTHERS THEN
                RETURN 'Error creating TimescaleDB extension.';
        END;
    -- If neither extension is available, return a message
    ELSE
        RETURN 'No extensions available to install.';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RETURN 'An unexpected error occurred during extension setup.';
END;
$$;


CREATE OR REPLACE FUNCTION checkRetentionPeriod(table_name text)
RETURNS text AS $$
DECLARE
    partman_installed boolean;
    timescaledb_installed boolean;
    retention_period text;
BEGIN
    -- Check if pg_partman is installed
    BEGIN
        SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'pg_partman') INTO partman_installed;
    EXCEPTION
        WHEN OTHERS THEN
            RAISE EXCEPTION 'Error checking pg_partman installation: %', SQLERRM;
    END;

    -- Check if TimescaleDB is installed
    BEGIN
        SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') INTO timescaledb_installed;
    EXCEPTION
        WHEN OTHERS THEN
            RAISE EXCEPTION 'Error checking TimescaleDB installation: %', SQLERRM;
    END;

    -- If pg_partman is installed, check the retention period in partman
    IF partman_installed THEN
        BEGIN
            SELECT retention INTO retention_period
            FROM partman.part_config
            WHERE parent_table = 'public.' || table_name;
            IF retention_period IS NOT NULL THEN
                RETURN retention_period;
            ELSE
                RETURN '';
            END IF;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE EXCEPTION 'Error checking retention policy for table % using pg_partman: %', table_name, SQLERRM;
        END;
    -- If TimescaleDB is installed, check the retention policy in TimescaleDB
    ELSIF timescaledb_installed THEN
        BEGIN
            SELECT config ->> 'drop_after' INTO retention_period
            FROM timescaledb_information.jobs
            WHERE hypertable_name = table_name;
            IF retention_period IS NOT NULL THEN
                RETURN retention_period;
            ELSE
                RETURN '';
            END IF;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE EXCEPTION 'Error checking retention policy for hypertable % using TimescaleDB: %', table_name, SQLERRM;
        END;
    -- Raise an error if neither pg_partman nor TimescaleDB is installed
    ELSE
        RAISE EXCEPTION 'Neither pg_partman nor TimescaleDB extensions are installed. Cannot check retention policy.';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE EXCEPTION 'An unexpected error occurred while checking the retention policy: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION addRetentionPolicy(param_table_name text, param_column_name text, retention_period text)
RETURNS void AS $$
DECLARE
    partman_installed boolean;
    timescaledb_installed boolean;
    retention_exists boolean;
    column_type text;
    retention_in_months text;
BEGIN
    -- Check if pg_partman is installed
    BEGIN
        SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'pg_partman') INTO partman_installed;
    EXCEPTION
        WHEN OTHERS THEN
            RAISE EXCEPTION 'Error checking pg_partman installation: %', SQLERRM;
    END;

    -- Check if TimescaleDB is installed
    BEGIN
        SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') INTO timescaledb_installed;
    EXCEPTION
        WHEN OTHERS THEN
            RAISE EXCEPTION 'Error checking TimescaleDB installation: %', SQLERRM;
    END;

    -- Retrieve the column type
    BEGIN
        SELECT data_type INTO column_type
        FROM information_schema.columns
        WHERE table_name = param_table_name AND column_name = param_column_name;
    EXCEPTION
        WHEN OTHERS THEN
            RAISE EXCEPTION 'Error fetching column type for table % and column %: %', param_table_name, param_column_name, SQLERRM;
    END;

    -- Return if neither pg_partman nor TimescaleDB is installed
    IF NOT partman_installed AND NOT timescaledb_installed THEN
        RAISE EXCEPTION 'Neither pg_partman nor TimescaleDB extensions are installed. Cannot proceed with setting retention policy.';
    END IF;

    -- If pg_partman is installed, update the retention policy in partman
    IF partman_installed THEN
        BEGIN
          IF column_type IN ('timestamp without time zone', 'timestamp with time zone') THEN
            EXECUTE format(
                'UPDATE partman.part_config SET retention = ''%I'' WHERE parent_table = ''public.%I''',
                retention_period, param_table_name
            );
            RAISE NOTICE 'Retention period % set for table % using pg_partman', retention_period, param_table_name;
          ELSIF column_type = 'bigint' THEN
            BEGIN
              retention_in_months := (retention_period::bigint / 1000) / (86400) || ' days';
            EXCEPTION
                WHEN OTHERS THEN
                    RAISE EXCEPTION 'Error converting bigint to date format for % and column %: %', param_table_name, param_column_name, SQLERRM;
            END;
            EXECUTE format(
                'UPDATE partman.part_config SET retention = ''%I'' WHERE parent_table = ''public.%I''',retention_in_months, param_table_name
            );
            RAISE NOTICE 'Retention period % set for table % using pg_partman', retention_period, param_table_name;
          ELSE
            RAISE EXCEPTION 'Unsupported column type for retention policy: %', column_type;
          END IF;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE EXCEPTION 'Error setting retention policy for table % using pg_partman: %', param_table_name, SQLERRM;
        END;

    -- If TimescaleDB is installed, use TimescaleDB's add_retention_policy function
    ELSIF timescaledb_installed THEN
        -- Check if retention already exists
        BEGIN
            SELECT EXISTS(
                SELECT config ->> 'drop_after' AS retention_period
                FROM timescaledb_information.jobs
                WHERE hypertable_name = param_table_name
            ) INTO retention_exists;
--            RAISE EXCEPTION 'Error setting retention policy for table %', retention_exists;
            -- Remove existing retention if present
            IF retention_exists THEN
                EXECUTE format('SELECT remove_retention_policy(''%I'')', param_table_name);
                RAISE NOTICE 'Existing retention policy removed for table %', param_table_name;
            END IF;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE EXCEPTION 'Error checking/removing retention policy for TimescaleDB: %', SQLERRM;
        END;

        -- Add the new retention policy
        RAISE NOTICE '%', column_type;
        BEGIN
            IF column_type IN ('timestamp without time zone', 'timestamp with time zone') THEN
                EXECUTE format(
                    'SELECT add_retention_policy(''%I'', INTERVAL ''%s'')',
                    param_table_name, retention_period
                );
                RAISE NOTICE 'Retention policy set for hypertable % using TimescaleDB with interval %', param_table_name, retention_period;
            ELSIF column_type = 'bigint' THEN
                EXECUTE format(
                    'SELECT set_integer_now_func(''%I'', ''public.returnBIGINT'', true)', param_table_name
                );
                RAISE NOTICE 'Cant set the set_integer_now_func for %', param_table_name;

                EXECUTE format(
                    'SELECT add_retention_policy(''%I'', %s::bigint)',
                    param_table_name, retention_period
                );
                RAISE NOTICE 'Retention policy set for hypertable % using TimescaleDB with bigint value %', param_table_name, retention_period;
            ELSE
                RAISE EXCEPTION 'Unsupported column type for retention policy: %', column_type;
            END IF;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE EXCEPTION 'Error setting retention policy for hypertable % using TimescaleDB: %', param_table_name, SQLERRM;
        END;

    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE EXCEPTION 'An unexpected error occurred while setting the retention policy: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION createPartitioningTable(
    param_table_name text,
    param_column_name text,
    retention_period text DEFAULT '2 years'::text,
    start_time timestamp DEFAULT CURRENT_TIMESTAMP,
    p_interval text DEFAULT '7 Days'
) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    template_table_name text;
    partman_installed boolean;
    timescaledb_installed boolean;
    partitioned_table_name text := param_table_name || '_new_partitioned';
    already_partitioned boolean;
    already_hypertable boolean;
    fk_constraint RECORD;
    column_type text;
    p_epoch text;
BEGIN

	-- Check if pg_partman is installed
    SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'pg_partman') INTO partman_installed;

    -- Check if timescaledb is installed
    SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') INTO timescaledb_installed;

    -- Fetch the column's data type
    SELECT data_type INTO column_type
    FROM information_schema.columns
    WHERE table_name = param_table_name
    AND column_name = param_column_name;

    -- Return if neither pg_partman nor timescaledb is installed
    IF NOT partman_installed AND NOT timescaledb_installed THEN
        RAISE WARNING 'Neither pg_partman nor TimescaleDB extensions are installed. Skipping partitioning.';
        RETURN;
    END IF;

    -- Partition using pg_partman if installed
    IF partman_installed THEN
        -- Check if the table is already partitioned
        SELECT EXISTS(SELECT 1 FROM partman.part_config WHERE parent_table='public.'||param_table_name) INTO already_partitioned;
        IF already_partitioned THEN
            RETURN;
        ELSE
            -- Create partitioned table based on column type
            IF column_type = 'bigint' THEN
                EXECUTE format('CREATE TABLE IF NOT EXISTS public.%I (LIKE public.%I INCLUDING ALL) PARTITION BY RANGE (%I)',
                               partitioned_table_name, param_table_name, param_column_name);
            ELSIF column_type IN ('timestamp with time zone', 'timestamp without time zone') THEN
                EXECUTE format('CREATE TABLE IF NOT EXISTS public.%I (LIKE public.%I INCLUDING ALL) PARTITION BY RANGE (%I)',
                               partitioned_table_name, param_table_name, param_column_name);
            ELSE
                RAISE EXCEPTION 'Unsupported column type for partitioning: %', column_type;
            END IF;

            RAISE NOTICE 'Partitioned table % created', partitioned_table_name;

            -- Recreate foreign keys
            FOR fk_constraint IN
                SELECT conname AS fk_name, pg_get_constraintdef(c.oid) AS fk_definition
                FROM pg_constraint c
                WHERE c.conrelid = param_table_name::regclass
                AND c.contype = 'f'
            LOOP
                EXECUTE format('ALTER TABLE public.%I ADD CONSTRAINT %I %s',
                               partitioned_table_name, fk_constraint.fk_name, fk_constraint.fk_definition);
                RAISE NOTICE 'Foreign key constraint % added to %', fk_constraint.fk_name, partitioned_table_name;
            END LOOP;

            -- Drop the old table and rename the new one
            EXECUTE format('DROP TABLE IF EXISTS public.%I', param_table_name);
            EXECUTE format('ALTER TABLE public.%I RENAME TO %I', partitioned_table_name, param_table_name);

            -- Create template table for pg_partman
            template_table_name := param_table_name || '_template';
            EXECUTE format('CREATE TABLE IF NOT EXISTS partman.%I (LIKE public.%I)', template_table_name, param_table_name);
            RAISE NOTICE 'Template table % created', template_table_name;

            -- Create parent partition
            IF column_type = 'bigint' THEN
                p_interval := (p_interval::bigint / 1000)::bigint;
                p_epoch := 'milliseconds';

                IF current_setting('server_version_num')::integer >= 140000 THEN
                    -- Code for PostgreSQL 14 or higher
                    -- Create parent partition using pg_partman with custom interval
                    EXECUTE format('SELECT partman.create_parent(p_parent_table := ''public.%I'', p_control := ''%I'', p_template_table := ''partman.%I'', p_premake := 3, p_interval := ''%I'', p_start_partition := ''%I'', p_epoch := ''%I'')',
                               param_table_name, param_column_name, template_table_name, p_interval, start_time, p_epoch);
                ELSE
                    -- Code for older versions
                    -- Create parent partition using pg_partman with custom interval
                    EXECUTE format('SELECT partman.create_parent(p_parent_table := ''public.%I'', p_control := ''%I'', p_template_table := ''partman.%I'',p_type := ''native'', p_premake := 3, p_interval := ''%I'', p_start_partition := ''%I'', p_epoch := ''%I'')',
                               param_table_name, param_column_name, template_table_name, p_interval, start_time, p_epoch);
                END IF;

            ELSE
                IF current_setting('server_version_num')::integer >= 140000 THEN
                    -- Code for PostgreSQL 14 or higher
                    -- Create parent partition using pg_partman with custom interval
                    EXECUTE format('SELECT partman.create_parent(p_parent_table := ''public.%I'', p_control := ''%I'', p_template_table := ''partman.%I'', p_premake := 3, p_start_partition := ''%I'', p_interval := ''%I'')',
                               param_table_name, param_column_name, template_table_name, start_time, p_interval);
                ELSE
                    -- Code for older versions
                    -- Create parent partition using pg_partman with custom interval
                    EXECUTE format('SELECT partman.create_parent(p_parent_table := ''public.%I'', p_control := ''%I'', p_template_table := ''partman.%I'',p_type := ''native'', p_premake := 3, p_start_partition := ''%I'', p_interval := ''%I'')',
                               param_table_name, param_column_name, template_table_name, start_time, p_interval);
                END IF;
            END IF;

            -- Set retention period
            EXECUTE format('UPDATE partman.part_config SET retention = ''%I'', retention_keep_table=false, retention_keep_index=false, infinite_time_partitions=true WHERE parent_table=''public.%I''', retention_period, param_table_name);
            RAISE NOTICE 'Retention period % set', retention_period;
        END IF;

    -- Partition using TimescaleDB if installed
    ELSIF timescaledb_installed THEN
        -- Check if the table is already a hypertable
        SELECT EXISTS(SELECT 1 FROM timescaledb_information.hypertables WHERE hypertable_name = param_table_name) INTO already_hypertable;
        IF already_hypertable THEN
            RETURN;
        ELSE
            -- Create hypertable
            RAISE NOTICE '%',column_type;
            IF column_type IN ('timestamp without time zone', 'timestamp with time zone') THEN
                EXECUTE format(
                    'SELECT create_hypertable(relation => ''%I'', time_column_name => ''%I'', chunk_time_interval => interval ''%I'')',
                    param_table_name, param_column_name, p_interval);
            ELSIF column_type = 'bigint' THEN
                EXECUTE format(
                    'SELECT create_hypertable(relation => ''%I'', time_column_name => ''%I'', chunk_time_interval => %s)',
                    param_table_name, param_column_name, p_interval);
            ELSE
                RAISE EXCEPTION 'Unsupported column type for hypertable: %', column_type;
            END IF;
            RAISE NOTICE 'Created hypertable using TimescaleDB.';
        END IF;
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE EXCEPTION 'Error occurred while processing: %', SQLERRM;
END;
$$;


CREATE OR REPLACE FUNCTION dropPartitioningTable(table_name text)
RETURNS void AS $$
DECLARE
    partman_installed boolean;
    timescaledb_installed boolean;
BEGIN
    -- Check if pg_partman is installed
    BEGIN
        SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'pg_partman') INTO partman_installed;
    EXCEPTION
        WHEN OTHERS THEN
            RAISE EXCEPTION 'Error checking pg_partman installation: %', SQLERRM;
    END;
    -- Check if timescaledb is installed
    BEGIN
        SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') INTO timescaledb_installed;
    EXCEPTION
        WHEN OTHERS THEN
            RAISE EXCEPTION 'Error checking TimescaleDB installation: %', SQLERRM;
    END;
    IF partman_installed and timescaledb_installed THEN
       RETURN;
       END IF;
    -- If pg_partman is installed, drop partitioned table and configuration
    IF partman_installed THEN
        BEGIN
            EXECUTE format('DROP TABLE IF EXISTS partman.%I_template', table_name);
            RAISE NOTICE 'Template table partman.%I_template deleted', table_name;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE EXCEPTION 'Error dropping template table partman.%I_template: %', table_name, SQLERRM;
        END;
        BEGIN
            EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', table_name);
            RAISE NOTICE 'Table public.%I deleted', table_name;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE EXCEPTION 'Error dropping table public.%I: %', table_name, SQLERRM;
        END;
        BEGIN
            EXECUTE format('DELETE FROM partman.part_config WHERE parent_table = ''public.%I''', table_name);
            RAISE NOTICE 'Partman configuration for % deleted', table_name;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE EXCEPTION 'Error deleting Partman configuration for %I: %', table_name, SQLERRM;
        END;
    -- If timescaledb is installed but pg_partman is not, drop the hypertable
    ELSIF timescaledb_installed THEN
        BEGIN
            EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', table_name);
            RAISE NOTICE 'Hypertable %I deleted using TimescaleDB', table_name;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE EXCEPTION 'Error dropping hypertable %I: %', table_name, SQLERRM;
        END;
    -- If neither pg_partman nor TimescaleDB is installed, just drop the table
    ELSE
        BEGIN
            EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', table_name);
            RAISE NOTICE 'Table public.%I deleted', table_name;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE EXCEPTION 'Error dropping table public.%I: %', table_name, SQLERRM;
        END;
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE EXCEPTION 'An unexpected error occurred while dropping partitioned table: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION install_pg_cron_if_partman_installed()
RETURNS TEXT LANGUAGE plpgsql
AS $$
DECLARE
    partman_installed BOOLEAN;
    cron_available BOOLEAN;
BEGIN
    -- Check if pg_partman is installed
    BEGIN
        SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_partman') INTO partman_installed;
    EXCEPTION
        WHEN OTHERS THEN
            RETURN 'Error checking pg_partman installation status.';
    END;
    -- Check if pg_cron is available
    BEGIN
        SELECT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'pg_cron') INTO cron_available;
    EXCEPTION
        WHEN OTHERS THEN
            RETURN 'Error checking pg_cron availability.';
    END;
    -- If pg_partman is installed and pg_cron is available, create pg_cron extension
    IF partman_installed AND cron_available THEN
        BEGIN
            CREATE EXTENSION IF NOT EXISTS pg_cron CASCADE;
            RETURN 'pg_cron extension created successfully.';
        EXCEPTION
            WHEN OTHERS THEN
                RETURN 'Error creating pg_cron extension.';
        END;
    -- If pg_partman is not installed
    ELSIF NOT partman_installed THEN
        RETURN 'pg_partman is not installed. Cannot create pg_cron.';
    -- If pg_cron is not available
    ELSIF NOT cron_available THEN
        RETURN 'pg_cron is not available to install.';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RETURN 'An unexpected error occurred during the pg_cron installation process.';
END;
$$;

SELECT extension_setup();
