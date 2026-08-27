/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.account.overrides;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.config.OrchestrationRestrictionConfiguration;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.pms.accountoverrides.ExpressionCallType;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class TeamAccountConfigStrategy implements EditionBasedAccountConfigStrategy {
  @Inject private OrchestrationRestrictionConfiguration orchestrationRestrictionConfiguration;
  @Inject private PipelineRetentionService pipelineRetentionService;

  @Override
  public int getMaxPipelineCreationLimit() {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      return (int) orchestrationRestrictionConfiguration.getPipelineCreationRestriction().getTeam();
    }
    return Integer.MAX_VALUE;
  }

  @Override
  public int getMaxPipelineCreationLimit(String accountIdentifier) {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      Optional<Integer> overriddenValue = pipelineRetentionService.getMaxPipelineCreationLimit(accountIdentifier);
      return overriddenValue.orElseGet(this::getMaxPipelineCreationLimit);
    }
    return Integer.MAX_VALUE;
  }

  @Override
  public long getPipelineLevelMaxConcurrency(String accountIdentifier, Long ngConcurrencyLimit) {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      return pipelineRetentionService.getMaxConcurrentPipelineExecution(accountIdentifier)
          .orElseGet(()
                         -> ngConcurrencyLimit != null
                  ? ngConcurrencyLimit
                  : orchestrationRestrictionConfiguration.getPlanExecutionRestriction().getTeam());
    }
    return ngConcurrencyLimit != null ? ngConcurrencyLimit : Long.MAX_VALUE;
  }

  @Override
  public long getMaxInputParameterSize(String accountIdentifier) {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      Optional<Long> overriddenValue = pipelineRetentionService.getMaxInputParameterSize(accountIdentifier);
      return overriddenValue.orElseGet(
          () -> orchestrationRestrictionConfiguration.getMaxInputParameterSizeRestriction().getTeam());
    }
    return Long.MAX_VALUE;
  }

  @Override
  public int getStepOrStageMaxConcurrency(String accountIdentifier) {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      Optional<Integer> overriddenValue = pipelineRetentionService.getStepOrStageMaxConcurrency(accountIdentifier);
      return overriddenValue.orElseGet(this::getStepOrStageMaxConcurrency);
    }
    return Integer.MAX_VALUE;
  }

  @Override
  public int getStepOrStageMaxConcurrency() {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      return (int) orchestrationRestrictionConfiguration.getMaxConcurrencyRestriction().getTeam();
    }
    return Integer.MAX_VALUE;
  }

  @Override
  public long getMaxOutcomeSize(String accountIdentifier) {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      Optional<Long> overriddenValue = pipelineRetentionService.getMaxOutcomeResponseSize(accountIdentifier);
      return overriddenValue.orElseGet(this::getMaxOutcomeSize);
    }
    return Long.MAX_VALUE;
  }

  @Override
  public long getMaxOutcomeSize() {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      return orchestrationRestrictionConfiguration.getMaxExecutionOutcomeSizeRestriction().getTeam();
    }
    return Long.MAX_VALUE;
  }

  @Override
  public long getMaxParallelismStopRestriction() {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      return orchestrationRestrictionConfiguration.getTotalParallelismStopRestriction().getTeam();
    }
    return Long.MAX_VALUE;
  }

  @Override
  public int getMaxQueuedExecutionLimit(String accountIdentifier) {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      Optional<Integer> overriddenValue = pipelineRetentionService.getMaxQueuedExecutionLimit(accountIdentifier);
      return overriddenValue.orElseGet(this::getMaxQueuedExecutionLimit);
    }
    return Integer.MAX_VALUE;
  }

  @Override
  public int getMaxQueuedExecutionLimit() {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      return (int) orchestrationRestrictionConfiguration.getMaxQueuedExecutionsRestriction().getTeam();
    }
    return Integer.MAX_VALUE;
  }

  @Override
  public int getMaxTriggerCreationLimit(String accountIdentifier) {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      Optional<Integer> overriddenValue = pipelineRetentionService.getMaxTriggerCreationLimit(accountIdentifier);
      return overriddenValue.orElseGet(this::getMaxTriggerCreationLimit);
    }
    return Integer.MAX_VALUE;
  }

  @Override
  public int getMaxTriggerCreationLimit() {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      return (int) orchestrationRestrictionConfiguration.getTriggerCreationRestriction().getTeam();
    }
    return Integer.MAX_VALUE;
  }

  @Override
  public long getMaxFileSizeLimit(String accountIdentifier) {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      Optional<Long> overriddenValue = pipelineRetentionService.getMaxFileSizeLimit(accountIdentifier);
      return overriddenValue.orElseGet(this::getMaxFileSizeLimit);
    }
    return Long.MAX_VALUE;
  }

  @Override
  public long getMaxFileSizeLimit() {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      return (int) orchestrationRestrictionConfiguration.getFileSizeRestriction().getTeam();
    }
    return Long.MAX_VALUE;
  }

  @Override
  public long getPayloadSizeLimit(String accountIdentifier) {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      Optional<Long> overriddenValue = pipelineRetentionService.getPayloadSizeLimit(accountIdentifier);
      return overriddenValue.orElseGet(this::getPayloadSizeLimit);
    }
    return Long.MAX_VALUE;
  }

  @Override
  public long getPayloadSizeLimit() {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      return (int) orchestrationRestrictionConfiguration.getPayloadSizeRestriction().getTeam();
    }
    return Long.MAX_VALUE;
  }

  @Override
  public int getMaxExpressionCalls(String accountIdentifier, ExpressionCallType callType) {
    if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
      Optional<Integer> overriddenValue = pipelineRetentionService.getMaxExpressionCalls(accountIdentifier, callType);
      return overriddenValue.orElseGet(
          () -> (int) orchestrationRestrictionConfiguration.getMaxExpressionCallsRestriction().get(callType).getTeam());
    }
    return Integer.MAX_VALUE;
  }
}
