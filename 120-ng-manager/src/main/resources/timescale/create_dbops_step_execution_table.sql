-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;

CREATE TABLE IF NOT EXISTS public.dbops_step_execution (
                                                      id text NOT NULL,
                                                      schemaId text NOT NULL,
                                                      schemaName text NOT NULL,
                                                      changeLog text NOT NULL,
                                                      schemaSource text NOT NULL,
                                                      schemaConnector text NOT NULL,
                                                      schemaRepository text NOT NULL,
                                                      service text,
                                                      instanceId text NOT NULL,
                                                      instanceName text NOT NULL,
                                                      instanceType text NOT NULL,
                                                      dbConnector text NOT NULL,
                                                      branch text,
                                                      instanceTags text[],
                                                      pluginImage text NOT NULL,
                                                      delegateSelectors text[],
                                                      command text NOT NULL,
                                                      tag text,
                                                      PRIMARY KEY (id)
    );

COMMIT;