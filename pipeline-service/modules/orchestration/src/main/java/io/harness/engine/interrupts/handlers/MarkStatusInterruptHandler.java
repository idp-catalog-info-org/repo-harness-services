/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;
import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;

import io.harness.advisers.AdvisersHelper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.handlers.intfc.InterruptHandler;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.InterruptEffect;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.core.failurestrategy.action.NGFailureActionTypeConstants;

import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.Set;
import javax.validation.Valid;
import lombok.NonNull;

@OwnedBy(HarnessTeam.PIPELINE)
public abstract class MarkStatusInterruptHandler implements InterruptHandler {
  @Inject protected NodeExecutionService nodeExecutionService;
  @Inject protected InterruptService interruptService;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private AdvisersHelper advisersHelper;

  @Override
  public Interrupt registerInterrupt(Interrupt interrupt) {
    Interrupt savedInterrupt = validateAndSave(interrupt);
    return handleInterruptForNodeExecution(savedInterrupt, interrupt.getNodeExecutionId());
  }

  private Interrupt validateAndSave(@Valid @NonNull Interrupt interrupt) {
    if (isEmpty(interrupt.getNodeExecutionId())) {
      throw new InvalidRequestException("NodeExecutionId Cannot be empty for MARK_SUCCESS interrupt");
    }

    NodeExecution nodeExecution = nodeExecutionService.getWithFieldsIncluded(interrupt.getNodeExecutionId(),
        Set.of(NodeExecutionKeys.status, NodeExecutionKeys.oldRetry, NodeExecutionKeys.ambiance,
            NodeExecutionKeys.executionContext));
    if (shouldThrowFailedInterruptError(nodeExecution)) {
      throw new InvalidRequestException("Failed to interrupt node execution " + interrupt.getType()
          + ". Either another interrupt is already in process or the current status: " + nodeExecution.getStatus()
          + "does not allow interruption");
    }
    savePipelineRollbackExecutionSweepingOutput(interrupt, nodeExecutionService.getAmbiance(nodeExecution));
    interrupt.setState(Interrupt.State.PROCESSING);
    return interruptService.save(interrupt);
  }

  private void savePipelineRollbackExecutionSweepingOutput(Interrupt interrupt, Ambiance ambiance) {
    if (interrupt == null || interrupt.getMetadata() == null) {
      return;
    }
    String rollbackAction = interrupt.getMetadata().get("ROLLBACK");
    if (NGFailureActionTypeConstants.PIPELINE_ROLLBACK.equals(rollbackAction)) {
      AdvisingEvent advisingEvent = AdvisingEvent.builder().ambiance(ambiance).build();

      advisersHelper.savePipelineRollbackExecutionSweepingOutput(advisingEvent);
    }
  }

  private boolean shouldThrowFailedInterruptError(NodeExecution nodeExecution) {
    boolean shouldFailDueToStatus = !StatusUtils.brokeStatuses().contains(nodeExecution.getStatus())
        && nodeExecution.getStatus() != INTERVENTION_WAITING;
    /* If the node is an old retry, it means there is already a new node created for the retry.
       We don't need to proceed with marking status for the old node. */
    boolean shouldFailForOldInterrupt =
        pmsFeatureFlagService.isEnabled(nodeExecution.getAccountId(), FeatureName.CDS_DO_NOT_INTERRUPT_OLD_RETRIED_NODE)
        && Boolean.TRUE.equals(nodeExecution.getOldRetry());
    return shouldFailDueToStatus || shouldFailForOldInterrupt;
  }

  @Override
  public Interrupt handleInterrupt(Interrupt interrupt) {
    throw new UnsupportedOperationException(interrupt.getType() + " handling Not required on plan");
  }

  protected Interrupt handleInterruptStatus(Interrupt interrupt, String nodeExecutionId, Status status) {
    return handleInterruptStatus(interrupt, nodeExecutionId, status, EnumSet.noneOf(Status.class));
  }

  protected Interrupt handleInterruptStatus(
      Interrupt interrupt, String nodeExecutionId, Status status, EnumSet<Status> overrideStatusSet) {
    try {
      NodeExecution nodeExecution = nodeExecutionService.update(nodeExecutionId,
          ops
          -> ops.addToSet(NodeExecutionKeys.interruptHistories,
              InterruptEffect.builder()
                  .interruptType(interrupt.getType())
                  .tookEffectAt(System.currentTimeMillis())
                  .interruptId(interrupt.getUuid())
                  .interruptConfig(interrupt.getInterruptConfig())
                  .build()),
          NodeProjectionUtils.withAmbianceAndStatus);
      Ambiance ambiance = nodeExecutionService.getAmbiance(nodeExecution);
      planExecutionService.calculateAndUpdateRunningStatusForStageAndPlanUnderLock(ambiance);
      orchestrationEngine.concludeNodeExecution(ambiance, status, nodeExecution.getStatus(), overrideStatusSet);
    } catch (Exception ex) {
      interruptService.markProcessed(interrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
      throw ex;
    }
    return interruptService.markProcessed(interrupt.getUuid(), PROCESSED_SUCCESSFULLY);
  }
}
