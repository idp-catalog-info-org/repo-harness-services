-- Copyright 2026 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

---------- UPDATE CREATE_INDEX FUNCTION (UNIQUE, PARTIAL) START ------------

BEGIN;

-- v2 bootstrap installed the 4-arg create_index; replace with extended signature (CDS-127425).
DROP FUNCTION IF EXISTS create_index(text, text, text, text);

CREATE OR REPLACE FUNCTION create_index(
    table_name text,
    index_name text,
    column_list text,
    index_type text DEFAULT 'btree',
    is_unique boolean DEFAULT false,
    where_clause text DEFAULT NULL
) RETURNS void AS $BODY$
DECLARE
    child_table text;
    sql_command text;
    partition RECORD;
    qualified_table_name text := table_name;
    schema_name text;
    base_table_name text;
    child_index_name text;
    where_suffix text := '';
    unique_clause text;
BEGIN
    IF position('.' IN table_name) > 0 THEN
        schema_name := split_part(table_name, '.', 1);
        base_table_name := split_part(table_name, '.', 2);
    ELSE
        schema_name := 'public';
        base_table_name := table_name;
        qualified_table_name := 'public.' || table_name;
    END IF;

    unique_clause := CASE WHEN is_unique THEN 'UNIQUE ' ELSE '' END;
    -- where_clause: hardcoded migration literals only (same trust model as column_list).
    IF where_clause IS NOT NULL AND where_clause != '' THEN
        where_suffix := ' WHERE ' || where_clause;
    END IF;

    RAISE NOTICE 'Creating index on partitioned table: %', qualified_table_name;

    sql_command := FORMAT(
        'CREATE %sINDEX %I ON ONLY %s USING %I (%s)%s;',
        unique_clause, index_name, qualified_table_name, index_type, column_list, where_suffix
    );
    EXECUTE sql_command;
    RAISE NOTICE 'Parent index created. Proceeding with child partitions...';

    FOR partition IN
        SELECT child.relname AS child_name
        FROM pg_inherits
        JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
        JOIN pg_class child ON pg_inherits.inhrelid = child.oid
        JOIN pg_namespace nmsp_child ON nmsp_child.oid = child.relnamespace
        WHERE parent.relname = base_table_name
          AND nmsp_child.nspname = schema_name
    LOOP
        child_table := schema_name || '.' || partition.child_name;

        child_index_name := LEFT(
            CONCAT(
                substring(md5(partition.child_name), 1, 6), '_', index_name
            ),
            63
        );

        sql_command := FORMAT(
            'CREATE %sINDEX %I ON %s USING %s (%s)%s;',
            unique_clause, child_index_name, child_table, index_type, column_list, where_suffix
        );
        EXECUTE sql_command;

        sql_command := FORMAT(
            'ALTER INDEX %I ATTACH PARTITION %I;',
            index_name, child_index_name
        );
        EXECUTE sql_command;
        RAISE NOTICE 'Attached index % to parent index %', child_index_name, index_name;
    END LOOP;

    RAISE NOTICE 'Verifying index validity...';
    PERFORM 1 FROM pg_index WHERE indisvalid = false AND indexrelid::regclass::text = index_name;
    IF FOUND THEN
        RAISE WARNING 'Some partition indexes are not valid. Check manually.';
    ELSE
        RAISE NOTICE 'All partition indexes are valid.';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE EXCEPTION 'Error while creating index % on table %: %', index_name, table_name, SQLERRM;
END;
$BODY$ LANGUAGE plpgsql;

COMMIT;

---------- UPDATE CREATE_INDEX FUNCTION (UNIQUE, PARTIAL) END ------------
