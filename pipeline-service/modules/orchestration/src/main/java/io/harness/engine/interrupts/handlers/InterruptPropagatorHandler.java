/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;
import static io.harness.pms.contracts.execution.Status.DISCONTINUING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * This serves as base class for the interrupts that are registered with parent but they recursively need to traverse
 * through  the tree and take appropriate action on the leaf node like ABORT_ALL, EXPIRE_ALL
 * <p>
 * TODO: Evaluate this an extract PAUSE_ALL and RESUME_ALL here too
 */

@OwnedBy(PIPELINE)
@Slf4j
public abstract class InterruptPropagatorHandler {
  @Inject protected InterruptService interruptService;
  @Inject protected NodeExecutionService nodeExecutionService;
  @Inject protected PlanExecutionService planExecutionService;

  public Interrupt handleAllNodes(Interrupt interrupt) {
    Interrupt updatedInterrupt = interruptService.markProcessing(interrupt.getUuid());

    // discontinue everything at once, no need for synchronization
    var updatedCount = nodeExecutionService.markAllFinalizableNodesDiscontinuing(interrupt.getPlanExecutionId());

    log.info(String.format("Marked %s nodeExecutions as discontinuing", updatedCount));
    return handleDiscontinuingNodes(updatedInterrupt, updatedCount);
  }

  public List<String> getTargetIds(List<NodeExecution> finalList) {
    return finalList.stream()
        .filter(ne
            -> ExecutionModeUtils.isLeafMode(ne.getMode()) || StatusUtils.abortingStatuses().contains(ne.getStatus()))
        .map(NodeExecution::getUuid)
        .collect(Collectors.toList());
  }

  public Interrupt handleChildNodes(Interrupt interrupt, String nodeExecutionId) {
    Interrupt updatedInterrupt = interruptService.markProcessing(interrupt.getUuid());
    // Find all the nodeExecutions for this plan
    List<NodeExecution> allExecutions = new LinkedList<>();
    try (Stream<NodeExecution> stream = nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             interrupt.getPlanExecutionId(), StatusUtils.abortAndExpireStatuses(),
             NodeProjectionUtils.fieldsForInterruptPropagatorHandler)) {
      stream.forEach(nodeExecution -> { allExecutions.add(nodeExecution); });
    }

    Map<String, String> metadata =
        updatedInterrupt.getMetadata() != null ? updatedInterrupt.getMetadata() : new HashMap<>();

    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withGroupAndIdentifier);
    metadata.put(NodeExecutionKeys.group, nodeExecution.getGroup());
    metadata.put(NodeExecutionKeys.identifier, nodeExecution.getIdentifier());
    updatedInterrupt = updatedInterrupt.toBuilder().metadata(metadata).build();
    List<NodeExecution> finalList = new ArrayList<>();
    // Extract all the running leaf nodes and queued nodeswith the parent id as nodeExecutionId passed in as param
    nodeExecutionService.extractChildExecutions(nodeExecutionId, true, finalList, allExecutions, true);

    List<String> targetIds = getTargetIds(finalList);

    long updatedCount = nodeExecutionService.markLeavesDiscontinuing(targetIds);
    return handleDiscontinuingNodes(updatedInterrupt, updatedCount);
  }

  protected Interrupt handleDiscontinuingNodes(Interrupt updatedInterrupt, long updatedCount) {
    if (updatedCount < 0) {
      // IF count is less than 0 then the update didn't go through
      return interruptService.markProcessed(updatedInterrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
    } else if (updatedCount == 0) {
      var currentStatus = planExecutionService.getStatus(updatedInterrupt.getPlanExecutionId());
      if (StatusUtils.isFinalStatus(currentStatus)) {
        return interruptService.markProcessed(updatedInterrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
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
    try {
      for (NodeExecution discontinuingNodeExecution : discontinuingNodeExecutions) {
        handleMarkedInstance(discontinuingNodeExecution, updatedInterrupt);
      }
    } catch (Exception ex) {
      interruptService.markProcessed(updatedInterrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
      throw ex;
    }
    return updatedInterrupt;
  }

  protected abstract void handleMarkedInstance(NodeExecution nodeExecution, Interrupt interrupt);

  protected abstract Status getStatusToBeTransitioned();
}
