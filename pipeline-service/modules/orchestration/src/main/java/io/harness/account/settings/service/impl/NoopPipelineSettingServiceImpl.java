/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.account.settings.service.impl;

import io.harness.account.settings.response.PlanExecutionSettingResponse;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecutionConcurrencyMode;
import io.harness.execution.PriorityConcurrentExecutionsMetadata;
import io.harness.execution.PriorityType;
import io.harness.pms.accountoverrides.ExpressionCallType;

@OwnedBy(HarnessTeam.PIPELINE)
public class NoopPipelineSettingServiceImpl implements PipelineSettingsService {
  @Override
  public PlanExecutionSettingResponse shouldQueuePlanExecution(String accountId) {
    return PlanExecutionSettingResponse.builder().useNewFlow(false).shouldQueue(false).build();
  }

  public PlanExecutionSettingResponse shouldQueuePlanExecution(
      String accountIdentifier, PriorityType priorityTypeOfCurrentExecution) {
    return PlanExecutionSettingResponse.builder().useNewFlow(false).shouldQueue(false).build();
  }

  @Override
  public long getMaxPipelineCreationCount(String accountId) {
    return Long.MAX_VALUE;
  }

  @Override
  public int getMaxConcurrencyBasedOnEdition(String accountId, long childCount) {
    return Integer.MAX_VALUE;
  }

  @Override
  public int getMaxStepConcurrency(String accountIdentifier) {
    return Integer.MAX_VALUE;
  }

  @Override
  public int getMaxLeafStepConcurrency(String accountIdentifier) {
    return Integer.MAX_VALUE;
  }

  @Override
  public String getAccountEdition(String accountId) {
    return null;
  }

  @Override
  public long getMaxConcurrency(String accountId) {
    return 0;
  }

  @Override
  public PriorityConcurrentExecutionsMetadata getPriorityExecutionPreferences(String accountIdentifier) {
    return PriorityConcurrentExecutionsMetadata.builder().build();
  }

  @Override
  public long getCurrentExecutionCount(String accountId) {
    return 0;
  }

  @Override
  public long getMaxInputParameterSize(String accountIdentifier) {
    return 0;
  }

  @Override
  public int getMaxExpressionCalls(String accountIdentifier, ExpressionCallType callType) {
    return Integer.MAX_VALUE;
  }

  @Override
  public long getMaxOutcomeSize(String accountIdentifier) {
    return 0;
  }

  @Override
  public boolean isStepInputSizeWithinLimit(String accountIdentifier, String inputParameters) {
    return true;
  }

  @Override
  public boolean isOutcomeResponseWithinLimit(String accountIdentifier, String outcomeResponse) {
    return true;
  }

  @Override
  public int getMaxQueuedExecutionLimit(String accountIdentifier) {
    return Integer.MAX_VALUE;
  }

  @Override
  public boolean isQueuedExecutionsWithinLimit(String accountIdentifier) {
    return true;
  }

  @Override
  public int getMaxTriggerCreationLimit(String accountIdentifier) {
    return Integer.MAX_VALUE;
  }

  @Override
  public boolean isTriggerCreationWithinLimit(String accountIdentifier, long currentTriggerCount) {
    return true;
  }

  @Override
  public boolean isPipelineCreationWithinLimit(String accountIdentifier, long currentPipelineCount) {
    return true;
  }

  @Override
  public long getMaxFileSizeLimit(String accountIdentifier) {
    return Long.MAX_VALUE;
  }

  @Override
  public long getMaxPayloadSize(String accountIdentifier) {
    return Long.MAX_VALUE;
  }

  @Override
  public boolean isFileSizeWithinLimit(String accountIdentifier, long currentFileSize) {
    return true;
  }

  @Override
  public boolean isPayloadSizeWithinLimit(String accountIdentifier, long currentFileSize) {
    return true;
  }

  @Override
  public PriorityType getPriorityTypeOfCurrentExecution(
      String accountId, String orgId, String projectId, boolean priorityExecutionsFFEnabled) {
    return PriorityType.NORMAL;
  }

  @Override
  public PlanExecutionConcurrencyMode getConcurrencyMode(String accountId) {
    return PlanExecutionConcurrencyMode.PARTITIONS;
  }

  @Override
  public int getEffectiveProjectConcurrency(String accountId, String parentUniqueId) {
    return Integer.MAX_VALUE;
  }
}
