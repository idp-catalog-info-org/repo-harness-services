/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.pms.plan.execution;

import io.harness.account.settings.response.PlanExecutionSettingResponse;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.concurrency.PlanConcurrencyGate;
import io.harness.execution.PlanExecutionConcurrencyMode;
import io.harness.execution.PriorityType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PmsExecutionSummaryDtoUpdateHelper {
  @Inject private PipelineSettingsService pipelineSettingsService;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private PlanConcurrencyGate planConcurrencyGate;

  @Data
  @AllArgsConstructor
  private static class CacheKey {
    private final String accountIdentifier;
    private final PriorityType priorityType;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CacheKey cacheKey = (CacheKey) o;
      return Objects.equals(accountIdentifier, cacheKey.accountIdentifier)
          && Objects.equals(priorityType, cacheKey.priorityType);
    }

    @Override
    public int hashCode() {
      return Objects.hash(accountIdentifier, priorityType);
    }
  }

  @Data
  @AllArgsConstructor
  private static class ProjectCacheKey {
    private final String accountIdentifier;
    private final String parentUniqueId;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ProjectCacheKey that = (ProjectCacheKey) o;
      return Objects.equals(accountIdentifier, that.accountIdentifier)
          && Objects.equals(parentUniqueId, that.parentUniqueId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(accountIdentifier, parentUniqueId);
    }
  }

  // Cache for partition-based concurrency (legacy)
  private final LoadingCache<CacheKey, QueuedType> booleanLoadingCache =
      CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).build(new CacheLoader<CacheKey, QueuedType>() {
        @Override
        public QueuedType load(@NotNull final CacheKey cacheKey) {
          return checkActiveExecutionsInLimit(cacheKey.getAccountIdentifier(), cacheKey.getPriorityType());
        }
      });

  // Cache for per-project concurrency (includes parentUniqueId)
  private final LoadingCache<ProjectCacheKey, QueuedType> perProjectCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(15, TimeUnit.SECONDS)
          .build(new CacheLoader<ProjectCacheKey, QueuedType>() {
            @Override
            public QueuedType load(@NotNull final ProjectCacheKey cacheKey) {
              return checkPerProjectQueuedType(cacheKey.getAccountIdentifier(), cacheKey.getParentUniqueId());
            }
          });

  // Cache for concurrency mode per account to avoid N+1 remote settings calls
  // When listing queued executions, getConcurrencyMode was called per-row causing N remote calls
  private final LoadingCache<String, PlanExecutionConcurrencyMode> concurrencyModeCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(30, TimeUnit.SECONDS)
          .build(new CacheLoader<String, PlanExecutionConcurrencyMode>() {
            @Override
            public PlanExecutionConcurrencyMode load(@NotNull String accountId) {
              return pipelineSettingsService.getConcurrencyMode(accountId);
            }
          });

  public QueuedType getQueuedReason(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    String accountId = pipelineExecutionSummaryEntity.getAccountId();
    ExecutionStatus status = pipelineExecutionSummaryEntity.getStatus();

    if ((!(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION)
            || pmsFeatureFlagHelper.isEnabled(
                accountId, FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS)))
        || status != ExecutionStatus.QUEUED_PLAN_CREATION) {
      return null;
    }

    // Check if per-project concurrency mode is enabled
    boolean perProjectFFEnabled =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_PER_PROJECT_CONCURRENCY_OVERRIDES);

    if (perProjectFFEnabled) {
      // Use cached mode lookup to avoid N+1 remote settings calls when listing queued executions
      PlanExecutionConcurrencyMode mode = getCachedConcurrencyMode(accountId);

      if (mode == PlanExecutionConcurrencyMode.PER_PROJECT) {
        // Per-project mode: use cache to avoid hitting Redis on every API call
        try {
          String parentUniqueId = pipelineExecutionSummaryEntity.getParentUniqueId();
          ProjectCacheKey cacheKey = new ProjectCacheKey(accountId, parentUniqueId);
          return perProjectCache.get(cacheKey);
        } catch (Exception e) {
          log.warn("[PLAN_CONCURRENCY] Exception while checking per-project queued reason for account: {} execution {}",
              accountId, pipelineExecutionSummaryEntity.getPlanExecutionId(), e);
          // On error, default to PROJECT_CONCURRENCY_REACHED since we're in per-project mode
          return QueuedType.PROJECT_CONCURRENCY_REACHED;
        }
      }
    }

    // Legacy partition-based concurrency check (or fallback on error)
    PriorityType priorityType = pipelineExecutionSummaryEntity.getPriorityType();
    if (priorityType == null) {
      priorityType = PriorityType.NORMAL;
    }

    QueuedType queuedType = null;
    try {
      CacheKey cacheKey = new CacheKey(accountId, priorityType);
      queuedType = booleanLoadingCache.get(cacheKey);
    } catch (Exception e) {
      log.warn("Exception while checking queued Reason for account: {} for execution {}", accountId,
          pipelineExecutionSummaryEntity.getPlanExecutionId(), e);
    }
    return queuedType;
  }

  private PlanExecutionConcurrencyMode getCachedConcurrencyMode(String accountId) {
    try {
      return concurrencyModeCache.get(accountId);
    } catch (Exception e) {
      log.warn("[PLAN_CONCURRENCY] Failed to get cached concurrency mode for account: {}, defaulting to PARTITIONS",
          accountId, e);
      return PlanExecutionConcurrencyMode.PARTITIONS;
    }
  }

  private QueuedType checkPerProjectQueuedType(String accountId, String parentUniqueId) {
    try {
      PlanConcurrencyGate.ThrottleDecision decision = planConcurrencyGate.shouldQueue(accountId, parentUniqueId);

      // If gate says queue, return the specific reason
      if (decision.isQueue()) {
        if (PlanConcurrencyGate.REASON_PROJECT.equals(decision.getReason())) {
          return QueuedType.PROJECT_CONCURRENCY_REACHED;
        } else if (PlanConcurrencyGate.REASON_ACCOUNT.equals(decision.getReason())) {
          return QueuedType.MAX_CONCURRENCY_REACHED;
        }
      }

      // Gate says allow now (capacity freed up), but execution is still queued
      // Default to PROJECT_CONCURRENCY_REACHED since we're in per-project mode
      return QueuedType.PROJECT_CONCURRENCY_REACHED;
    } catch (Exception e) {
      log.warn("[PLAN_CONCURRENCY] Exception in cache loader for account: {} parentUniqueId: {}", accountId,
          parentUniqueId, e);
      return QueuedType.PROJECT_CONCURRENCY_REACHED;
    }
  }

  public QueuedType checkActiveExecutionsInLimit(String accountIdentifier, PriorityType priorityType) {
    // This method is only called for legacy partition-based concurrency mode (via cache).
    // Per-project mode uses perProjectCache and calls checkPerProjectQueuedType().
    PlanExecutionSettingResponse planExecutionSettingResponse;
    if (!PriorityType.NORMAL.equals(priorityType)) {
      planExecutionSettingResponse = pipelineSettingsService.shouldQueuePlanExecution(accountIdentifier, priorityType);
      if (planExecutionSettingResponse.isShouldQueue()
          && planExecutionSettingResponse.isPriorityExecutionLimitReached()) {
        return QueuedType.PRIORITY_CONCURRENCY_REACHED;
      }
      return planExecutionSettingResponse.isShouldQueue() ? QueuedType.MAX_CONCURRENCY_REACHED
                                                          : QueuedType.MAX_CONCURRENCY_NOT_REACHED;
    } else {
      planExecutionSettingResponse = pipelineSettingsService.shouldQueuePlanExecution(accountIdentifier);
    }
    return planExecutionSettingResponse.isShouldQueue() ? QueuedType.MAX_CONCURRENCY_REACHED
                                                        : QueuedType.MAX_CONCURRENCY_NOT_REACHED;
  }
}
