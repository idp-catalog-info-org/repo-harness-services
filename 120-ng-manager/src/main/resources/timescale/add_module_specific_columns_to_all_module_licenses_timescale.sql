-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;


--- When moduleType is IDP, IDP specific additional attributes ---
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS idp_number_of_developers bigint;

--- When moduleType is IAC, IAC specific additional attributes ---
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS iac_number_of_developers bigint;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS iac_number_of_execution_applies bigint;

--- When moduleType is CET, CET specific additional attributes ---
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS cet_number_of_agents bigint;

--- When moduleType is SEI, SEI specific additional attributes ---
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS sei_number_of_contributors bigint;

--- When moduleType is CODE, CODE specific additional attributes ---
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS code_number_of_developers bigint;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS code_number_of_repositories bigint;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS code_max_repo_size_string text;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS code_max_repo_in_bytes bigint;

--- When moduleType is SSCA, SSCA specific additional attributes ---
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS ssca_number_of_developers bigint;
ALTER TABLE module_licenses ADD COLUMN IF NOT EXISTS ssca_number_of_executions bigint;

COMMIT;