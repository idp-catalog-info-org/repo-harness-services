-- Copyright 2024 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_step_execution_stepexecid
ON public.step_execution (step_execution_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_step_execution_stageexecid
ON public.step_execution (stage_execution_id);
