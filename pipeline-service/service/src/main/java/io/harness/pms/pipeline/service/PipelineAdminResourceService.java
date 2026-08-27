/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.pipeline.service;

import io.harness.entity.accountoverrides.DataRetentionEntity;
import io.harness.entity.accountoverrides.beans.AccountOverridesConfigDTO;
import io.harness.pms.pipeline.BlockExecutionResponseDTO;
import io.harness.pms.pipeline.ForceAbortExecutionsRequestDTO;
import io.harness.pms.pipeline.ForceAbortExecutionsResponseDTO;
import io.harness.pms.pipeline.PlanConcurrencyCounterResponseDTO;
import io.harness.pms.pipeline.StepConcurrencyCounterResponseDTO;

public interface PipelineAdminResourceService {
  BlockExecutionResponseDTO blockPipelineExecution(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  BlockExecutionResponseDTO unblockPipelineExecution(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  DataRetentionEntity getPipelineDataRetentionConfig(String accountIdentifier);

  AccountOverridesConfigDTO createAccountOverrides(AccountOverridesConfigDTO configDTO);

  AccountOverridesConfigDTO updateAccountOverrides(String accountIdentifier, AccountOverridesConfigDTO configDTO);

  void replayNodeExecutions(String accountIdentifier, String orgIdentifier, String projectIdentifier, String module,
      long startTs, long endTs);

  ForceAbortExecutionsResponseDTO forceAbortPlanExecutions(ForceAbortExecutionsRequestDTO request);

  void recomputeStepConcurrencyCounters();

  StepConcurrencyCounterResponseDTO getStepConcurrencyCounter(String scope, String accountIdentifier);

  void recomputePlanConcurrencyCounters();

  PlanConcurrencyCounterResponseDTO getPlanConcurrencyCounters(String accountIdentifier);
}
