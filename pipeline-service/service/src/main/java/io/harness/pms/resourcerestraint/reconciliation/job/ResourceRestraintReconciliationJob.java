/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.resourcerestraint.reconciliation.job;

import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.engine.interrupts.manager.InterruptManager;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.SystemIssuer;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.resourcerestraint.reconciliation.config.ResourceRestraintReconciliationConfig;
import io.harness.repositories.ResourceRestraintInstanceCustomRepository;
import io.harness.steps.resourcerestraint.ResourceRestraintObserver;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class ResourceRestraintReconciliationJob implements Runnable {
  @Inject PersistentLocker persistentLocker;
  @Inject ResourceRestraintObserver resourceRestraintObserver;
  @Inject private ResourceRestraintInstanceCustomRepository resourceRestraintInstanceCustomRepository;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private InterruptManager interruptManager;
  @Inject private ResourceRestraintReconciliationConfig resourceRestraintReconciliationConfig;
  private static final String LOCK_NAME = "ResourceRestraintReconciliationJob";
  private static final String LOG_CONTEXT = "[RESOURCE_RESTRAINT_RECONCILIATION]: ";

  @Override
  public void run() {
    // Check maintenance flag
    if (getMaintenanceFlag()) {
      log.warn(LOG_CONTEXT + "Service is going in maintenance mode. Will try again after {} mins.",
          resourceRestraintReconciliationConfig.getIntervalInMins());
      return;
    }
    String currentInstanceId = null;
    String currentNodeExecutionId = null;
    String currentPlanExecutionId = null;

    try {
      // Acquiring a lock for 10m duration with a maximum wait time of 30 seconds
      AcquiredLock<?> lock = persistentLocker.waitToAcquireLockOptional(LOCK_NAME,
          Duration.ofMinutes(resourceRestraintReconciliationConfig.getIntervalInMins()), Duration.ofSeconds(30));
      if (lock == null) {
        log.info(LOG_CONTEXT + "Could not acquire lock. Skipping this run.");
        return;
      }

      log.info(LOG_CONTEXT + "Starting resource restraint reconciliation job...");

      // Fetch all active ResourceRestraintInstances
      List<ResourceRestraintInstance> instances =
          resourceRestraintInstanceCustomRepository.findAllByState(EnumSet.of(ACTIVE));

      int processedCount = 0;
      int abortedCount = 0;

      for (ResourceRestraintInstance instance : instances) {
        currentInstanceId = instance.getUuid();
        String currentEntityId = instance.getReleaseEntityId();

        try {
          if (instance.getReleaseEntityType().equals("PIPELINE")) {
            currentPlanExecutionId = currentEntityId;
            // Fetch PlanExecution using planExecutionId
            PlanExecution planExecution =
                planExecutionService.getWithFieldsIncludedFromSecondary(currentPlanExecutionId,
                    Set.of(PlanExecutionKeys.uuid, PlanExecutionKeys.ambiance, PlanExecutionKeys.status));

            if (planExecution == null) {
              log.warn(LOG_CONTEXT + "PlanExecution not found for planExecutionId: {}", currentPlanExecutionId);
              processedCount++;
              continue;
            }

            if (StatusUtils.isFinalStatus(planExecution.getStatus())) {
              log.info(LOG_CONTEXT + "PlanExecution {} is in final status {}", currentPlanExecutionId,
                  planExecution.getStatus());
              resourceRestraintObserver.onEnd(planExecution.getAmbiance(), planExecution.getStatus());
            }
          } else {
            currentNodeExecutionId = currentEntityId;
            // Fetch NodeExecution using releaseEntityId
            NodeExecution nodeExecution = nodeExecutionService.getWithFieldsIncludedFromSecondary(
                currentNodeExecutionId, Set.of(NodeExecutionKeys.uuid, NodeExecutionKeys.planExecutionId));

            if (nodeExecution == null) {
              log.warn(LOG_CONTEXT + "NodeExecution not found for releaseEntityId: {}", currentNodeExecutionId);
              processedCount++;
              continue;
            }

            currentPlanExecutionId = nodeExecution.getPlanExecutionId();

            // Fetch PlanExecution using planExecutionId from NodeExecution
            PlanExecution planExecution = planExecutionService.getWithFieldsIncludedFromSecondary(
                currentPlanExecutionId, Set.of(PlanExecutionKeys.uuid, PlanExecutionKeys.status));

            if (planExecution == null) {
              log.warn(LOG_CONTEXT + "PlanExecution not found for planExecutionId: {}", currentPlanExecutionId);
              processedCount++;
              continue;
            }

            // Check if PlanExecution is in final status
            if (StatusUtils.isFinalStatus(planExecution.getStatus())) {
              log.info(
                  LOG_CONTEXT + "PlanExecution {} is in final status {}. Sending abort interrupt for NodeExecution {}",
                  currentPlanExecutionId, planExecution.getStatus(), currentNodeExecutionId);

              // Send abort interrupt for the NodeExecution
              InterruptConfig interruptConfig =
                  InterruptConfig.newBuilder()
                      .setIssuedBy(IssuedBy.newBuilder().setSystemIssuer(SystemIssuer.newBuilder().build()).build())
                      .build();

              InterruptPackage interruptPackage = InterruptPackage.builder()
                                                      .planExecutionId(currentPlanExecutionId)
                                                      .nodeExecutionId(currentNodeExecutionId)
                                                      .interruptType(InterruptType.ABORT_ALL)
                                                      .interruptConfig(interruptConfig)
                                                      .build();

              interruptManager.register(interruptPackage);
              abortedCount++;
            }
          }
          processedCount++;

        } catch (Exception ex) {
          log.error(
              LOG_CONTEXT + "Failed to process ResourceRestraintInstance: {}, NodeExecution: {}, PlanExecution: {}",
              currentInstanceId, currentNodeExecutionId, currentPlanExecutionId, ex);
        }
      }

      log.info(
          LOG_CONTEXT + "Resource restraint reconciliation job completed. Processed {} instances, aborted {} nodes.",
          processedCount, abortedCount);

    } catch (Exception ex) {
      log.error(LOG_CONTEXT + "Failed to run resource restraint reconciliation job. Last processed instance: {}, "
              + "NodeExecution: {}, PlanExecution: {}",
          currentInstanceId, currentNodeExecutionId, currentPlanExecutionId, ex);
    }
  }
}
