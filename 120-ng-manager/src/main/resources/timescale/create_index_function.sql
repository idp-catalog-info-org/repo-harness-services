-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- Create a function to handle index creation for both regular and partitioned tables
CREATE OR REPLACE FUNCTION create_index(
    table_name text,
    index_name text,
    column_list text,
    index_type text DEFAULT 'btree'
) RETURNS void AS $BODY$
DECLARE
    sql_command text;
    qualified_table_name text := table_name;
    schema_name text;
    base_table_name text;
    is_partitioned boolean;
BEGIN
    -- Split table name into schema and table
    IF position('.' IN table_name) > 0 THEN
        schema_name := split_part(table_name, '.', 1);
        base_table_name := split_part(table_name, '.', 2);
    ELSE
        schema_name := 'public';
        base_table_name := table_name;
        qualified_table_name := 'public.' || table_name;
    END IF;

    -- Check if table is partitioned
    SELECT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relkind = 'p' AND c.relname = base_table_name AND n.nspname = schema_name
    ) INTO is_partitioned;

    -- Create the index using the appropriate method based on whether the table is partitioned
    -- Start a nested transaction for index creation
    BEGIN
        RAISE NOTICE 'Creating index % on table % (partitioned: %)', index_name, qualified_table_name, is_partitioned;
        
        sql_command := FORMAT(
            'CREATE INDEX %I ON %s USING %I (%s);',
            index_name, qualified_table_name, index_type, column_list
        );
        EXECUTE sql_command;
        
        RAISE NOTICE 'Successfully created index % on table %', index_name, qualified_table_name;
    EXCEPTION
        WHEN OTHERS THEN
            RAISE EXCEPTION 'Error while creating index % on table %: %', index_name, table_name, SQLERRM;
    END;
END;
$BODY$ LANGUAGE plpgsql;
