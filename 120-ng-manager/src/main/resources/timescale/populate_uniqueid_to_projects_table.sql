-- Copyright 2025 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- Ensure pgcrypto extension is available for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
BEGIN
    UPDATE projects
    SET unique_id = regexp_replace(replace(replace(encode(uuid_send(gen_random_uuid()), 'base64'), '/', '_'), '+', '-'), '=+$', '')
    WHERE unique_id is NULL;
END $$;

COMMIT;
