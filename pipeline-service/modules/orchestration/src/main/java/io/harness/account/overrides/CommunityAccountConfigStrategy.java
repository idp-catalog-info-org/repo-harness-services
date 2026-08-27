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
import io.harness.pms.accountoverrides.ExpressionCallType;

import lombok.extern.slf4j.Slf4j;

/**
 * This implementation is to support SMP customers, where do not expect any limits to be enforced.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class CommunityAccountConfigStrategy implements EditionBasedAccountConfigStrategy {
  @Override
  public int getMaxPipelineCreationLimit() {
    return Integer.MAX_VALUE;
  }

  @Override
  public int getMaxPipelineCreationLimit(String accountIdentifier) {
    return Integer.MAX_VALUE;
  }

  @Override
  public long getPipelineLevelMaxConcurrency(String accountIdentifier, Long ngConcurrencyLimit) {
    return ngConcurrencyLimit != null ? ngConcurrencyLimit : Long.MAX_VALUE;
  }

  @Override
  public long getMaxInputParameterSize(String accountIdentifier) {
    return Long.MAX_VALUE;
  }

  @Override
  public int getStepOrStageMaxConcurrency() {
    return Integer.MAX_VALUE;
  }

  @Override
  public long getMaxOutcomeSize(String accountIdentifier) {
    return Long.MAX_VALUE;
  }

  @Override
  public long getMaxOutcomeSize() {
    return Long.MAX_VALUE;
  }

  @Override
  public long getMaxParallelismStopRestriction() {
    return Long.MAX_VALUE;
  }

  @Override
  public int getMaxQueuedExecutionLimit(String accountIdentifier) {
    return Integer.MAX_VALUE;
  }

  @Override
  public int getMaxQueuedExecutionLimit() {
    return Integer.MAX_VALUE;
  }

  @Override
  public int getMaxTriggerCreationLimit(String accountIdentifier) {
    return Integer.MAX_VALUE;
  }

  @Override
  public int getMaxTriggerCreationLimit() {
    return Integer.MAX_VALUE;
  }

  @Override
  public long getMaxFileSizeLimit(String accountIdentifier) {
    return Long.MAX_VALUE;
  }

  @Override
  public long getPayloadSizeLimit(String accountIdentifier) {
    return Long.MAX_VALUE;
  }

  @Override
  public long getMaxFileSizeLimit() {
    return Long.MAX_VALUE;
  }

  @Override
  public long getPayloadSizeLimit() {
    return Long.MAX_VALUE;
  }

  @Override
  public int getStepOrStageMaxConcurrency(String accountIdentifier) {
    return Integer.MAX_VALUE;
  }

  @Override
  public int getMaxExpressionCalls(String accountIdentifier, ExpressionCallType callType) {
    return Integer.MAX_VALUE;
  }
}
