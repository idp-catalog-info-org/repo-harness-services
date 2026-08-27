/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.account.settings.service;

import io.harness.account.settings.response.PlanExecutionSettingResponse;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecutionConcurrencyMode;
import io.harness.execution.PriorityConcurrentExecutionsMetadata;
import io.harness.execution.PriorityType;
import io.harness.pms.accountoverrides.ExpressionCallType;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PipelineSettingsService {
  PlanExecutionSettingResponse shouldQueuePlanExecution(String accountIdentifier);

  PlanExecutionSettingResponse shouldQueuePlanExecution(
      String accountIdentifier, PriorityType priorityTypeOfCurrentExecution);

  long getMaxPipelineCreationCount(String accountIdentifier);

  /**
   * Returns the max concurrency for stages/steps based on account edition. Throws InvalidRequestException
   * if childCount exceeds the limit — use only where enforcement (rejection) is intended.
   */
  int getMaxConcurrencyBasedOnEdition(String accountIdentifier, long childCount);

  /**
   * Returns the max step concurrency limit for the account based on edition. Never throws — returns
   * a safe default on failure. Use for concurrency gating where we just need the limit value.
   */
  int getMaxStepConcurrency(String accountIdentifier);

  /**
   * Returns the per-account cap on concurrently running leaf steps. Resolution order: Mongo
   * override on DataRetentionEntity → cluster-wide config default. Returns 0 or negative to
   * indicate "no per-account cap configured"; callers should treat that as "skip the check".
   */
  int getMaxLeafStepConcurrency(String accountIdentifier);

  String getAccountEdition(String accountIdentifier);

  long getMaxConcurrency(String accountIdentifier);

  PriorityConcurrentExecutionsMetadata getPriorityExecutionPreferences(String accountIdentifier);

  long getMaxInputParameterSize(String accountIdentifier);

  /**
   * Resolves the per-node MongoDB call budget used during expression resolution (PIPE-34261).
   * Resolution order: per-account override → per-edition config value (when the per-edition
   * restriction toggle is on) → {@link Long#MAX_VALUE} meaning "unbounded". Never throws.
   */
  int getMaxExpressionCalls(String accountIdentifier, ExpressionCallType callType);

  long getMaxOutcomeSize(String accountIdentifier);

  long getCurrentExecutionCount(String accountIdentifier);

  boolean isStepInputSizeWithinLimit(String accountIdentifier, String inputParameters);

  boolean isOutcomeResponseWithinLimit(String accountIdentifier, String outcomeResponse);

  int getMaxQueuedExecutionLimit(String accountIdentifier);

  boolean isQueuedExecutionsWithinLimit(String accountIdentifier);

  int getMaxTriggerCreationLimit(String accountIdentifier);

  boolean isTriggerCreationWithinLimit(String accountIdentifier, long currentTriggerCount);

  boolean isPipelineCreationWithinLimit(String accountIdentifier, long currentPipelineCount);

  long getMaxFileSizeLimit(String accountIdentifier);

  long getMaxPayloadSize(String accountIdentifier);

  boolean isFileSizeWithinLimit(String accountIdentifier, long currentFileSize);
  boolean isPayloadSizeWithinLimit(String accountIdentifier, long currentFileSize);

  PriorityType getPriorityTypeOfCurrentExecution(
      String accountId, String orgId, String projectId, boolean priorityExecutionsFFEnabled);

  /**
   * Resolves the account-level concurrency mode from the {@code pipeline_execution_concurrency_mode}
   * setting. Defaults to {@link PlanExecutionConcurrencyMode#PARTITIONS} (existing High/Low behaviour)
   * on any error or when unset — so existing customers are never accidentally switched.
   */
  PlanExecutionConcurrencyMode getConcurrencyMode(String accountIdentifier);

  /**
   * Per-project effective cap used in {@code PER_PROJECT} mode: the project-scoped
   * {@code project_execution_concurrency_limit} override if set, else the account-scoped
   * {@code default_project_execution_concurrency}. Returns 0 or negative to indicate "no per-project
   * cap configured"; callers should treat that as "skip the per-project check".
   *
   * <p>The project override is read by the project's stable {@code parentUniqueId} (via
   * {@code getSettingV2}), so the cap resolves by identity and survives project-move-across-orgs.
   */
  int getEffectiveProjectConcurrency(String accountId, String parentUniqueId);
}
