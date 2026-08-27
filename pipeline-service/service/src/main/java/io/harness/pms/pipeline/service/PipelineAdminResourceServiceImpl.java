/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataService;
import io.harness.engine.executions.concurrency.counter.PlanConcurrencyCounterService;
import io.harness.engine.executions.concurrency.counter.StepConcurrencyCounterService;
import io.harness.engine.executions.concurrency.rebuild.PlanConcurrencyCounterRebuildJob;
import io.harness.engine.executions.concurrency.rebuild.StepConcurrencyCounterRebuildJob;
import io.harness.entity.accountoverrides.DataRetentionEntity;
import io.harness.entity.accountoverrides.beans.AccountOverridesConfigDTO;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.execution.BlockExecutionMetadata;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.BlockExecutionResponseDTO;
import io.harness.pms.pipeline.ForceAbortExecutionsRequestDTO;
import io.harness.pms.pipeline.ForceAbortExecutionsResponseDTO;
import io.harness.pms.pipeline.PlanConcurrencyCounterResponseDTO;
import io.harness.pms.pipeline.StepConcurrencyCounterResponseDTO;
import io.harness.pms.pipeline.service.helper.ForceAbortPlanExecutionsHelper;
import io.harness.pms.plan.execution.service.ExecutionSummaryBackfillService;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class PipelineAdminResourceServiceImpl implements PipelineAdminResourceService {
  private final BlockExecutionMetadataService blockExecutionMetadataService;
  private final PipelineRetentionService pipelineRetentionService;
  private final ExecutionSummaryBackfillService executionReplayService;
  private final ForceAbortPlanExecutionsHelper forceAbortPlanExecutionsHelper;
  private final StepConcurrencyCounterRebuildJob stepConcurrencyCounterRebuildJob;
  private final StepConcurrencyCounterService stepConcurrencyCounterService;
  private final PlanConcurrencyCounterRebuildJob planConcurrencyCounterRebuildJob;
  private final PlanConcurrencyCounterService planConcurrencyCounterService;

  private static final String SCOPE_CLUSTER = "cluster";
  private static final String SCOPE_ACCOUNT = "account";

  private final String HINT_PIPELINE_ACCOUNT_OVERRIDE_CONFIG_NOT_FOUND = "Account configuration override not found.";
  private final String EXPLANATION_PIPELINE_ACCOUNT_OVERRIDE_CONFIG_NOT_FOUND =
      "The account uses default configurations as no overrides have been set.";
  private final String PIPELINE_ACCOUNT_OVERRIDE_CONFIG_NOT_FOUND_ERROR_MESSAGE =
      "Account override config not found for account id: ";

  @Override
  public BlockExecutionResponseDTO blockPipelineExecution(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    try {
      BlockExecutionMetadata blockExecutionMetadata =
          blockExecutionMetadataService.block(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
      // return orgIdentifier, projectIdentifier instead of blockExecutionMetadata.getOrgId() and
      // blockExecutionMetadata.getProjectId() for cleanup.
      return BlockExecutionResponseDTO.builder()
          .accountIdentifier(blockExecutionMetadata.getAccountId())
          .pipelineIdentifier(blockExecutionMetadata.getPipelineId())
          .identifier(blockExecutionMetadata.getUuid())
          .orgIdentifier(blockExecutionMetadata.getOrgId())
          .projectIdentifier(blockExecutionMetadata.getProjectId())
          .build();
    } catch (Exception ex) {
      log.error(String.format("Failed to block the execution for account id: {%s}, error: ", accountIdentifier), ex);
      throw new InvalidRequestException(
          String.format("Failed to block the execution for account id: {%s}", accountIdentifier));
    }
  }

  @Override
  public BlockExecutionResponseDTO unblockPipelineExecution(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    DeleteResult deleteResult =
        blockExecutionMetadataService.unblock(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
    return BlockExecutionResponseDTO.builder()
        .accountIdentifier(accountIdentifier)
        .count(deleteResult.getDeletedCount())
        .build();
  }

  @Override
  public DataRetentionEntity getPipelineDataRetentionConfig(String accountIdentifier) {
    return pipelineRetentionService.getRetentionConfigByAccountId(accountIdentifier)
        .orElseThrow(
            ()
                -> NestedExceptionUtils.hintWithExplanationException(HINT_PIPELINE_ACCOUNT_OVERRIDE_CONFIG_NOT_FOUND,
                    EXPLANATION_PIPELINE_ACCOUNT_OVERRIDE_CONFIG_NOT_FOUND,
                    new EntityNotFoundException(
                        PIPELINE_ACCOUNT_OVERRIDE_CONFIG_NOT_FOUND_ERROR_MESSAGE + accountIdentifier)));
  }

  @Override
  public AccountOverridesConfigDTO createAccountOverrides(AccountOverridesConfigDTO configDTO) {
    return pipelineRetentionService.createAccountOverrides(configDTO);
  }

  @Override
  public AccountOverridesConfigDTO updateAccountOverrides(
      String accountIdentifier, AccountOverridesConfigDTO configDTO) {
    return pipelineRetentionService.updateAccountOverrides(accountIdentifier, configDTO);
  }

  @Override
  public void replayNodeExecutions(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String module, long startTs, long endTs) {
    executionReplayService.replayNodeExecutions(
        accountIdentifier, orgIdentifier, projectIdentifier, module, startTs, endTs);
  }

  @Override
  public ForceAbortExecutionsResponseDTO forceAbortPlanExecutions(ForceAbortExecutionsRequestDTO request) {
    return forceAbortPlanExecutionsHelper.forceAbortPlanExecutions(request);
  }

  @Override
  public void recomputeStepConcurrencyCounters() {
    stepConcurrencyCounterRebuildJob.rebuild();
  }

  @Override
  public StepConcurrencyCounterResponseDTO getStepConcurrencyCounter(String scope, String accountIdentifier) {
    if (SCOPE_CLUSTER.equalsIgnoreCase(scope)) {
      return StepConcurrencyCounterResponseDTO.builder()
          .scope(scope)
          .value(stepConcurrencyCounterService.getClusterCount())
          .build();
    } else if (SCOPE_ACCOUNT.equalsIgnoreCase(scope)) {
      if (EmptyPredicate.isEmpty(accountIdentifier)) {
        return StepConcurrencyCounterResponseDTO.builder()
            .scope(scope)
            .accountCounts(stepConcurrencyCounterService.getAllAccountCounts())
            .build();
      }
      return StepConcurrencyCounterResponseDTO.builder()
          .scope(scope)
          .accountIdentifier(accountIdentifier)
          .value(stepConcurrencyCounterService.getAccountCount(accountIdentifier))
          .build();
    }
    throw new InvalidRequestException("Invalid scope: " + scope + ". Must be one of: cluster, account");
  }

  @Override
  public void recomputePlanConcurrencyCounters() {
    planConcurrencyCounterRebuildJob.rebuild();
  }

  @Override
  public PlanConcurrencyCounterResponseDTO getPlanConcurrencyCounters(String accountIdentifier) {
    if (EmptyPredicate.isEmpty(accountIdentifier)) {
      throw new InvalidRequestException("accountIdentifier is required");
    }
    return PlanConcurrencyCounterResponseDTO.builder()
        .accountIdentifier(accountIdentifier)
        .accountCount(planConcurrencyCounterService.getAccountCount(accountIdentifier))
        .projectCounts(planConcurrencyCounterService.getProjectCountsForAccount(accountIdentifier))
        .build();
  }
}
