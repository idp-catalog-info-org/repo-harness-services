/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.statusreconciliation.job;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.pms.contracts.execution.Status.EXPIRED;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.statusreconciliation.config.ExecutionStatusReconciliationConfig;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineExecutionStatusReconciliationJob implements Runnable {
  @Inject PersistentLocker persistentLocker;
  @Inject private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private ExecutionStatusReconciliationConfig executionStatusReconciliationConfig;
  @Inject private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  private static final String LOCK_NAME = "PipelineExecutionStatusReconciliationJob";

  @Override
  public void run() {
    Instant jobStartTs = Instant.now();
    String currentPlanExecutionId = null;
    // Acquiring a lock for maximum of 6 hours with a maximum wait time of 5 seconds to ensure that the status
    // correction logic executes safely within the locked context
    try (AcquiredLock<?> lock = persistentLocker.waitToAcquireLockOptional(LOCK_NAME,
             Duration.ofMinutes(executionStatusReconciliationConfig.getIntervalInMins()), Duration.ofSeconds(30))) {
      if (lock == null) {
        return;
      }
      try (Stream<PipelineExecutionSummaryEntity> stream =
               pmsExecutionSummaryRepository.fetchRunningStuckPlanExecutions()) {
        Iterator<PipelineExecutionSummaryEntity> iterator = stream.iterator();
        while (iterator.hasNext()) {
          currentPlanExecutionId = iterator.next().getPlanExecutionId();
          PlanExecution planExecution = planExecutionService.getWithFieldsIncludedFromSecondary(currentPlanExecutionId,
              Set.of(PlanExecutionKeys.uuid, PlanExecutionKeys.endTs, PlanExecutionKeys.status));
          if (planExecution == null) {
            Update summaryEntityUpdate = getExpiredUpdateOpsForSummaryEntity(System.currentTimeMillis());
            pmsExecutionSummaryService.update(currentPlanExecutionId, summaryEntityUpdate);
          } else if (StatusUtils.isFinalStatus(planExecution.getStatus())) {
            Update summaryEntityUpdate = pmsExecutionSummaryService.updateStatusOps(planExecution, new Update());
            summaryEntityUpdate.set(
                PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.endTs, System.currentTimeMillis());
            pmsExecutionSummaryService.update(currentPlanExecutionId, summaryEntityUpdate);
          } else {
            long currentTimeMillis = System.currentTimeMillis();
            Update summaryEntityUpdate = getExpiredUpdateOpsForSummaryEntity(currentTimeMillis);
            pmsExecutionSummaryService.update(currentPlanExecutionId, summaryEntityUpdate);
            planExecutionService.updateStatus(currentPlanExecutionId, EXPIRED,
                ops
                -> ops.set(PlanExecutionKeys.endTs, currentTimeMillis).set(PlanExecutionKeys.status, Status.EXPIRED));
          }
          if (hasJobRunTimeExceededMaxRunTime(jobStartTs)) {
            break;
          }
          if (getMaintenanceFlag()) {
            log.warn(
                "[EXECUTION_STATUS_RECONCILIATION]: Service is going in maintenance mode so shutting down the iterator");
            break;
          }
        }
      }
    } catch (Exception ex) {
      log.error(
          String.format(
              "[EXECUTION_STATUS_RECONCILIATION]: Failed to reconcile the status for PlanExecution and PipelineExecutionSummaryEntity collection for planExecutionId: %s",
              currentPlanExecutionId),
          ex);
    }
  }

  private Update getExpiredUpdateOpsForSummaryEntity(long currentTimeMillis) {
    Update summaryEntityUpdate = new Update();
    summaryEntityUpdate.set(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.internalStatus, Status.EXPIRED);
    summaryEntityUpdate.set(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.status, ExecutionStatus.EXPIRED);
    summaryEntityUpdate.set(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.endTs, currentTimeMillis);
    return summaryEntityUpdate;
  }

  private boolean hasJobRunTimeExceededMaxRunTime(Instant jobStartTs) {
    Duration elapsedTime = Duration.between(jobStartTs, Instant.now());
    return elapsedTime.compareTo(
               Duration.ofMinutes(executionStatusReconciliationConfig.getIntervalInMins()).minusHours(1))
        > 0;
  }
}
