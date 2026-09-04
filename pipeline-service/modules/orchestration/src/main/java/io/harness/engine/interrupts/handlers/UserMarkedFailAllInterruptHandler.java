/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.interrupts.Interrupt.State.DISCARDED;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSING;
import static io.harness.pms.contracts.execution.Status.DISCONTINUING;
import static io.harness.pms.contracts.execution.Status.FAILED;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.constants.OrchestrationPublisherName;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.interrupts.handlers.intfc.InterruptHandler;
import io.harness.engine.interrupts.helpers.intfc.UserMarkedFailAllHelper;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.interrupts.Interrupt;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.Valid;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
@OwnedBy(PIPELINE)
public class UserMarkedFailAllInterruptHandler extends InterruptPropagatorHandler implements InterruptHandler {
  @Inject private InterruptService interruptService;
  @Inject private UserMarkedFailAllHelper userMarkedFailAllHelper;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) String publisherName;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private NodeExecutionService nodeExecutionService;

  @Override
  public Interrupt registerInterrupt(Interrupt interrupt) {
    Interrupt savedInterrupt = validateAndSave(interrupt);
    return isNotEmpty(savedInterrupt.getNodeExecutionId())
        ? handleInterruptForNodeExecution(interrupt, interrupt.getNodeExecutionId())
        : handleInterrupt(savedInterrupt);
  }

  private Interrupt validateAndSave(Interrupt interrupt) {
    return isNotEmpty(interrupt.getNodeExecutionId()) ? validateAndSaveWithNodeExecution(interrupt)
                                                      : validateAndSaveWithoutNodeExecution(interrupt);
  }

  private Interrupt validateAndSaveWithoutNodeExecution(@Valid @NonNull Interrupt interrupt) {
    List<Interrupt> interrupts = interruptService.fetchActiveInterrupts(interrupt.getPlanExecutionId());
    // Use projections
    Status status = planExecutionService.getStatus(interrupt.getPlanExecutionId());
    if (StatusUtils.isFinalStatus(status)) {
      throw new InvalidRequestException(String.format("Execution is already finished with status: [%s]", status));
    }

    return processInterrupt(interrupt, interrupts);
  }

  private Interrupt validateAndSaveWithNodeExecution(@Valid @NonNull Interrupt interrupt) {
    List<Interrupt> interrupts = interruptService.fetchActiveInterruptsForNodeExecution(
        interrupt.getPlanExecutionId(), interrupt.getNodeExecutionId());
    return processInterrupt(interrupt, interrupts);
  }

  private Interrupt processInterrupt(@Valid @NonNull Interrupt interrupt, List<Interrupt> interrupts) {
    if (isEmpty(interrupts)) {
      return interruptService.save(interrupt);
    }

    interrupts.forEach(savedInterrupt
        -> interruptService.markProcessed(
            savedInterrupt.getUuid(), savedInterrupt.getState() == PROCESSING ? PROCESSED_SUCCESSFULLY : DISCARDED));
    return interruptService.save(interrupt);
  }

  /**
   * This method is applicable for parent node i.e stage/stepGroup etc.
   * For complete pipeline refer Handle interrupt
   */
  @Override
  public Interrupt handleInterruptForNodeExecution(Interrupt interrupt, String nodeExecutionId) {
    try (AutoLogContext ignore = interrupt.autoLogContext()) {
      log.info("Starting to handle interrupt for Node Execution");
      return handleChildNodes(interrupt, nodeExecutionId);
    }
  }

  @Override
  public Interrupt handleInterrupt(@NonNull @Valid Interrupt interrupt) {
    try (AutoLogContext ignore = interrupt.autoLogContext()) {
      log.info("Starting to handle interrupt for Plan Execution");
      return handleAllNodes(interrupt);
    }
  }

  public Interrupt handleChildNodes(Interrupt interrupt, String nodeExecutionId) {
    Interrupt updatedInterrupt = interruptService.markProcessing(interrupt.getUuid());
    // Find all the nodeExecutions for this plan
    List<NodeExecution> allExecutions = new LinkedList<>();
    try (Stream<NodeExecution> stream = nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             interrupt.getPlanExecutionId(), StatusUtils.userMarkedFailureStatuses(),
             NodeProjectionUtils.fieldsForInterruptPropagatorHandler)) {
      stream.forEach(nodeExecution -> { allExecutions.add(nodeExecution); });
    }

    List<NodeExecution> finalList = new ArrayList<>();
    // Extract all the running leaf nodes and queued nodeswith the parent id as nodeExecutionId passed in as param
    nodeExecutionService.extractChildExecutions(nodeExecutionId, true, finalList, allExecutions, true);

    List<String> targetIds = finalList.stream()
                                 .filter(ne
                                     -> ExecutionModeUtils.isLeafMode(ne.getMode())
                                         || StatusUtils.abortingStatuses().contains(ne.getStatus()))
                                 .map(NodeExecution::getUuid)
                                 .collect(Collectors.toList());

    long updatedCount = nodeExecutionService.markLeavesDiscontinuing(targetIds);
    return handleDiscontinuingNodes(updatedInterrupt, updatedCount);
  }

  public Interrupt handleAllNodes(Interrupt interrupt) {
    Interrupt updatedInterrupt = interruptService.markProcessing(interrupt.getUuid());
    // Marking all finalizable leaf nodes as DISCONTINUING
    long updatedCount = nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(
        interrupt.getPlanExecutionId(), StatusUtils.userMarkedFailureStatuses());
    return handleDiscontinuingNodes(updatedInterrupt, updatedCount);
  }

  protected Interrupt handleDiscontinuingNodes(Interrupt updatedInterrupt, long updatedCount) {
    if (updatedCount < 0) {
      // IF count is less than 0 then the update didn't go through
      return interruptService.markProcessed(updatedInterrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
    } else if (updatedCount == 0) {
      PlanExecution planExecution = planExecutionService.getWithFieldsIncluded(
          updatedInterrupt.getPlanExecutionId(), Set.of(PlanExecutionKeys.uuid, PlanExecutionKeys.status));
      if (StatusUtils.isFinalStatus(planExecution.getStatus())) {
        return interruptService.markProcessed(updatedInterrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
      }
      if (isIssuedByAdviser(updatedInterrupt)) {
        // Adviser-issued failAll interrupts fire mid-strategy, so finding no leaves is expected, not stuck
        // don't force-fail the plan here, or the next queued node gets blocked forever.
        log.info("No interruptible leaf nodes found for adviser-issued {} interrupt. Leaving plan status to the "
                + "in-flight failure strategy",
            updatedInterrupt.getType());
        return interruptService.markProcessed(updatedInterrupt.getUuid(), PROCESSED_SUCCESSFULLY);
      }
      // If count is 0 that means no running leaf node and hence needs to update planExecution forcefully
      planExecutionService.updateStatus(updatedInterrupt.getPlanExecutionId(), this.getStatusToBeTransitioned());
      return interruptService.markProcessed(updatedInterrupt.getUuid(), PROCESSED_SUCCESSFULLY);
    } else {
      List<NodeExecution> discontinuingNodeExecutions = new LinkedList<>();
      try (Stream<NodeExecution> stream = nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
               updatedInterrupt.getPlanExecutionId(), EnumSet.of(DISCONTINUING),
               NodeProjectionUtils.fieldsForDiscontinuingNodes)) {
        stream.forEach(nodeExecution -> { discontinuingNodeExecutions.add(nodeExecution); });
      }

      if (isEmpty(discontinuingNodeExecutions)) {
        var currentStatus = planExecutionService.getStatus(updatedInterrupt.getPlanExecutionId());
        if (StatusUtils.isFinalStatus(currentStatus)) {
          return interruptService.markProcessed(updatedInterrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
        }
        planExecutionService.updateStatus(updatedInterrupt.getPlanExecutionId(), this.getStatusToBeTransitioned());
        return interruptService.markProcessed(updatedInterrupt.getUuid(), PROCESSED_SUCCESSFULLY);
      }
      log.info(
          String.format("Starting to process %s discontinuing nodeExecutions", discontinuingNodeExecutions.size()));
      var processedInterrupt = processDiscontinuedInstances(updatedInterrupt, discontinuingNodeExecutions);
      // Best-effort bookkeeping for the stuck-execution monitor. A transient Mongo failure here must not fail the
      // interrupt processing that has already completed. See PIPE-35791.
      try {
        nodeExecutionService.markNodesProcessing(
            discontinuingNodeExecutions.stream().map(NodeExecution::getUuid).toList(), false);
      } catch (Exception ex) {
        log.error("Failed to mark discontinuing nodes as not processing after handling interrupt {}.",
            updatedInterrupt.getUuid(), ex);
      }
      return processedInterrupt;
    }
  }

  protected Interrupt processDiscontinuedInstances(
      Interrupt updatedInterrupt, List<NodeExecution> discontinuingNodeExecutions) {
    List<String> notifyIds = new ArrayList<>();
    try (AutoLogContext ignore = updatedInterrupt.autoLogContext()) {
      for (NodeExecution discontinuingNodeExecution : discontinuingNodeExecutions) {
        log.info("Trying to abort discontinuing instance {}", discontinuingNodeExecution.getUuid());
        handleMarkedInstance(discontinuingNodeExecution, updatedInterrupt);
        if (!(discontinuingNodeExecution.getMode() == ExecutionMode.SYNC
                || ExecutionModeUtils.isParentMode(discontinuingNodeExecution.getMode()))) {
          notifyIds.add(discontinuingNodeExecution.getUuid() + "|" + updatedInterrupt.getUuid());
        }
      }

    } catch (Exception ex) {
      log.info("Exception occurred while aborting instance marking interrupt as PROCESSED_UNSUCCESSFULLY");
      interruptService.markProcessed(updatedInterrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
      throw ex;
    }

    waitNotifyEngine.waitForAllOnInList(
        publisherName, AllInterruptCallback.builder().interrupt(updatedInterrupt).build(), notifyIds);
    return updatedInterrupt;
  }

  /**
   * True when this interrupt was raised by an adviser (i.e. a failure strategy with failAll) rather than by a user
   * manually marking the execution as failed.
   */
  private static boolean isIssuedByAdviser(Interrupt interrupt) {
    return interrupt.getInterruptConfig() != null && interrupt.getInterruptConfig().hasIssuedBy()
        && interrupt.getInterruptConfig().getIssuedBy().hasAdviserIssuer();
  }

  @Override
  protected void handleMarkedInstance(NodeExecution nodeExecution, Interrupt interrupt) {
    userMarkedFailAllHelper.discontinueMarkedInstance(nodeExecution, interrupt);
  }

  @Override
  protected Status getStatusToBeTransitioned() {
    return FAILED;
  }
}
