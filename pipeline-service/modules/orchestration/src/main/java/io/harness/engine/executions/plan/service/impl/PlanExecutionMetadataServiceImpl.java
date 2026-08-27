/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan.service.impl;

import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dataretention.PipelineRetentionHelper;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.execution.RetryStagesMetadata;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.metrics.service.api.MetricService;
import io.harness.repositories.planexecution.PlanExecutionMetadataRepository;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PlanExecutionMetadataServiceImpl implements PlanExecutionMetadataService {
  @Inject PipelineRetentionService pipelineRetentionService;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject ExecutionRetentionService executionRetentionService;
  @Inject PersistentLocker persistentLocker;
  @Inject MetricService metricService;

  private final PlanExecutionMetadataRepository planExecutionMetadataRepository;

  private static final String EVALUATION_ID_OPA = "EVALUATION_ID_OPA_%s";
  private static final String PIPELINE_EXECUTION_METADATA_DELETION_TIME = "pipeline_execution_metadata_deletion_time";

  @Inject
  public PlanExecutionMetadataServiceImpl(PlanExecutionMetadataRepository planExecutionMetadataRepository) {
    this.planExecutionMetadataRepository = planExecutionMetadataRepository;
  }

  @Override
  public Optional<PlanExecutionMetadata> findByPlanExecutionId(String accountIdentifier, String planExecutionId) {
    PlanExecutionMetadata planExecutionMetadata = fetchMetadataIfTTLExpired(accountIdentifier, planExecutionId);
    if (planExecutionMetadata == null) {
      return planExecutionMetadataRepository.findByPlanExecutionId(planExecutionId);
    }
    return Optional.of(planExecutionMetadata);
  }

  @Override
  public PlanExecutionMetadata findByPlanExecutionIdWithFieldsIncluded(
      String accountIdentifier, String planExecutionId, Set<String> fieldsToInclude) {
    PlanExecutionMetadata planExecutionMetadata = fetchMetadataIfTTLExpired(accountIdentifier, planExecutionId);
    if (planExecutionMetadata == null) {
      planExecutionMetadata = planExecutionMetadataRepository.getWithFieldsIncluded(planExecutionId, fieldsToInclude);
    }
    return planExecutionMetadata;
  }

  @Override
  public PlanExecutionMetadata save(PlanExecutionMetadata planExecutionMetadata) {
    String accountId = planExecutionMetadata.getAccountIdentifier();
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.CDS_CUSTOMIZE_PIPELINE_TTL)) {
      int retentionPeriodInMonths = pipelineRetentionService.getRetentionPeriodInMonths(accountId);
      planExecutionMetadata.setValidUntil(PipelineRetentionHelper.getValidUntilAsDate(retentionPeriodInMonths));
    }
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_STOP_PLAN_EXECUTION_METADATA_DATA_DUPLICATION)) {
      // These fields are being migrated from PlanExecutionMetadata to PlanExecution.
      // While this migration is in progress, the fields are temporarily stored in both collections, causing
      // duplication. Once the feature flag is enabled, the duplication will stop, and these fields will no longer be
      // written to PlanExecutionMetadata.
      planExecutionMetadata = planExecutionMetadata.toBuilder()
                                  .processedYaml(null)
                                  .expressionFunctorToken(null)
                                  .stagesExecutionMetadata(null)
                                  .triggerPayload(null)
                                  .triggerJsonPayload(null)
                                  .triggerHeader(null)
                                  .postExecutionRollbackInfos(new ArrayList<>())
                                  .stageExpressionValuesMap(null)
                                  .build();
    }
    return planExecutionMetadataRepository.save(planExecutionMetadata);
  }

  @Override
  public void deleteMetadataForGivenPlanExecutionIds(Set<String> planExecutionIds) {
    if (EmptyPredicate.isEmpty(planExecutionIds)) {
      return;
    }
    // Record duration using metricService
    long startTime = System.currentTimeMillis();
    try {
      Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
        planExecutionMetadataRepository.deleteAllByPlanExecutionIdIn(planExecutionIds);
        return true;
      });
    } finally {
      long duration = System.currentTimeMillis() - startTime;
      metricService.recordDuration(PIPELINE_EXECUTION_METADATA_DELETION_TIME, Duration.ofMillis(duration));
    }
  }

  @Override
  public void updateTTL(String planExecutionId, Date ttlDate) {
    if (EmptyPredicate.isEmpty(planExecutionId)) {
      return;
    }

    Criteria criteria = where(PlanExecutionMetadataKeys.planExecutionId).is(planExecutionId);
    Update update = new Update();
    update.set(PlanExecutionMetadataKeys.validUntil, ttlDate);
    planExecutionMetadataRepository.multiUpdatePlanExecution(criteria, update);
  }

  @Override
  public void updatePlanExecutionMetadata(String planExecutionId, Consumer<Update> ops) {
    if (EmptyPredicate.isEmpty(planExecutionId)) {
      return;
    }

    Criteria criteria = where(PlanExecutionMetadataKeys.planExecutionId).is(planExecutionId);
    Update update = new Update();
    if (ops != null) {
      ops.accept(update);
    }
    planExecutionMetadataRepository.updatePlanExecution(criteria, update);
  }

  public String getNotesForExecution(String accountIdentifier, String planExecutionId) {
    return getNotesOrEmptyString(
        fetchMetadataWithFields(accountIdentifier, planExecutionId, Set.of(PlanExecutionMetadataKeys.notes)));
  }

  public void updateEvaluatedPolicyIds(String planExecutionId, List<Integer> evaluatedPolicyIds) {
    if (!EmptyPredicate.isEmpty(evaluatedPolicyIds)) {
      String lockName = String.format(EVALUATION_ID_OPA, planExecutionId);
      try (AcquiredLock<?> lock =
               persistentLocker.waitToAcquireLockOptional(lockName, Duration.ofSeconds(5), Duration.ofSeconds(10))) {
        if (lock == null) {
          log.error("[EVALUATION_ID_OPA]: Could not acquire lock for planExecutionId: [{}]", planExecutionId);
          throw new UnexpectedException(
              String.format("Unable to occupy lock %s therefore throwing the exception", lockName));
        }
        Criteria criteria = where(PlanExecutionMetadataKeys.planExecutionId).is(planExecutionId);
        Update update = new Update();
        update.addToSet(PlanExecutionMetadataKeys.evaluatedPolicyIds).each(evaluatedPolicyIds);
        planExecutionMetadataRepository.updatePlanExecution(criteria, update);
      }
    }
  }

  public RetryStagesMetadata getRetryStagesMetadata(String accountIdentifier, String planExecutionId) {
    return fetchMetadataWithFields(
        accountIdentifier, planExecutionId, Set.of(PlanExecutionMetadataKeys.retryStagesMetadata))
        .getRetryStagesMetadata();
  }

  public String updateNotesForExecution(String planExecutionId, String notes) {
    Criteria criteria = where(PlanExecutionMetadataKeys.planExecutionId).is(planExecutionId);
    Update update = new Update();
    update.set(PlanExecutionMetadataKeys.notes, notes);

    Optional<PlanExecutionMetadata> planExecutionMetadata =
        Optional.ofNullable(planExecutionMetadataRepository.updatePlanExecution(criteria, update));
    if (!planExecutionMetadata.isPresent()) {
      throw new InvalidRequestException(format("Execution with id [%s] is not present or deleted", planExecutionId));
    }

    return getNotesOrEmptyString(planExecutionMetadata.get());
  }

  private String getNotesOrEmptyString(PlanExecutionMetadata planExecutionMetadata) {
    if (EmptyPredicate.isEmpty(planExecutionMetadata.getNotes())) {
      return "";
    }
    return planExecutionMetadata.getNotes();
  }

  @Override
  public PlanExecutionMetadata getWithFieldsIncludedFromSecondary(
      String accountIdentifier, String planExecutionId, Set<String> fieldsToInclude) {
    return fetchMetadataWithFields(accountIdentifier, planExecutionId, fieldsToInclude);
  }

  public Optional<String> getYaml(String accountIdentifier, String planExecutionId) {
    try {
      PlanExecutionMetadata planExecutionMetadata = findByPlanExecutionIdWithFieldsIncluded(
          accountIdentifier, planExecutionId, Set.of(PlanExecutionMetadataKeys.yaml));
      return Optional.ofNullable(planExecutionMetadata.getYaml());
    } catch (Exception ex) {
      log.error("Failed to get planExecutionMetadata for planExeucutionId {}", planExecutionId, ex);
      return Optional.empty();
    }
  }

  private PlanExecutionMetadata fetchMetadataWithFields(
      String accountIdentifier, String planExecutionId, Set<String> fieldsToInclude) {
    PlanExecutionMetadata planExecutionMetadata = fetchMetadataIfTTLExpired(accountIdentifier, planExecutionId);
    if (planExecutionMetadata == null) {
      planExecutionMetadata =
          planExecutionMetadataRepository.getWithFieldsIncludedFromSecondary(planExecutionId, fieldsToInclude);
    }
    return planExecutionMetadata;
  }

  private PlanExecutionMetadata fetchMetadataIfTTLExpired(String accountIdentifier, String planExecutionId) {
    return (PlanExecutionMetadata) executionRetentionService.readExpiredRecordFromObjectStore(accountIdentifier,
        planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_METADATA, PlanExecutionMetadata.class);
  }
}
