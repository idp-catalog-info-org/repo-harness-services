/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.pipeline.service.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.pipeline.ForceAbortExecutionsRequestDTO;
import io.harness.pms.pipeline.ForceAbortExecutionsResponseDTO;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class ForceAbortPlanExecutionsHelper {
  private final PlanExecutionService planExecutionService;
  private final NodeExecutionService nodeExecutionService;
  private final ResourceRestraintInstanceService resourceRestraintInstanceService;

  public ForceAbortExecutionsResponseDTO forceAbortPlanExecutions(ForceAbortExecutionsRequestDTO request) {
    if (request == null || isEmpty(request.getPlanExecutionIds())) {
      throw new InvalidRequestException("Plan execution ids cannot be null or empty.");
    }
    EnumSet<Status> activeStatuses = EnumSet.copyOf(StatusUtils.ACTIVE_STATUSES_WITH_QUEUED);
    AtomicInteger planExecutionsUpdated = new AtomicInteger(0);
    AtomicInteger nodeExecutionsUpdated = new AtomicInteger(0);
    List<String> notFoundPlanExecutionIds = new ArrayList<>();

    for (String planExecutionId : request.getPlanExecutionIds()) {
      log.info("Force abort requested for planExecutionId: {}", planExecutionId);
      try (Stream<NodeExecution> nodeExecutions =
               nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
                   planExecutionId, activeStatuses, NodeProjectionUtils.fieldsForForceAbort)) {
        nodeExecutions.forEach(nodeExecution -> {
          NodeExecution updated =
              nodeExecutionService.updateStatusWithOps(nodeExecution.getUuid(), Status.ABORTED, null, activeStatuses);
          if (updated != null) {
            nodeExecutionsUpdated.incrementAndGet();
          }
        });
      }
      try (Stream<NodeExecution> nodeExecutions = nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesIterator(
               planExecutionId, NodeProjectionUtils.withStepTypeAndExecutableResponses)) {
        nodeExecutions.forEach(nodeExecution -> {
          if (!isResourceConstraintNode(nodeExecution)) {
            return;
          }
          String callbackId = getAsyncCallbackId(nodeExecution);
          if (isNotEmpty(callbackId)) {
            // If the Resource-restraint was not released by node-execution-update, then we would release forcefully
            // here.
            resourceRestraintInstanceService.finishInstance(callbackId, null);
          }
        });
      }
      if (planExecutionService.updateStatusForceful(planExecutionId, Status.ABORTED, null, true) != null) {
        planExecutionsUpdated.incrementAndGet();
      } else {
        log.warn("PlanExecution not found for force abort: {}", planExecutionId);
        notFoundPlanExecutionIds.add(planExecutionId);
      }
    }

    log.info("Force abort summary: requested={}, planExecutionsUpdated={}, nodeExecutionsUpdated={}, "
            + "notFound={}",
        request.getPlanExecutionIds().size(), planExecutionsUpdated.get(), nodeExecutionsUpdated.get(),
        notFoundPlanExecutionIds.size());
    return ForceAbortExecutionsResponseDTO.builder()
        .requestedPlanExecutionIds(request.getPlanExecutionIds())
        .notFoundPlanExecutionIds(notFoundPlanExecutionIds)
        .planExecutionsUpdated(planExecutionsUpdated.get())
        .nodeExecutionsUpdated(nodeExecutionsUpdated.get())
        .build();
  }

  private boolean isResourceConstraintNode(NodeExecution nodeExecution) {
    return nodeExecution != null && nodeExecution.getStepType() != null
        && StepSpecTypeConstants.RESOURCE_CONSTRAINT.equals(nodeExecution.getStepType().getType());
  }

  private String getAsyncCallbackId(NodeExecution nodeExecution) {
    if (nodeExecution == null || isEmpty(nodeExecution.getExecutableResponses())
        || !nodeExecution.getExecutableResponses().get(0).hasAsync()
        || nodeExecution.getExecutableResponses().get(0).getAsync().getCallbackIdsCount() == 0) {
      return null;
    }
    return nodeExecution.getExecutableResponses().get(0).getAsync().getCallbackIds(0);
  }
}
