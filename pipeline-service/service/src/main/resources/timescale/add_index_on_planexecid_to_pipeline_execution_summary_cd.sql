-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BEGIN;
--  CONCURRENTLY cannot be used with hypertables
CREATE INDEX IF NOT EXISTS idx_pipeline_execution_summary_cd_planexecid
ON public.pipeline_execution_summary_cd (planexecutionid);

COMMIT;
