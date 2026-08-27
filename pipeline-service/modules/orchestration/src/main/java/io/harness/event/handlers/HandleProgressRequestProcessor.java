/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.springdata.SpringDataMongoUtils.setUnset;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.exceptions.RecasterException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.HandleProgressRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.execution.utils.SdkResponseEventUtils;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumSet;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class HandleProgressRequestProcessor implements SdkResponseProcessor {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanExecutionService planExecutionService;

  @Override
  public void handleEvent(SdkResponseEventProto event) {
    HandleProgressRequest progressRequest = event.getProgressRequest();
    Map<String, Object> progressDoc = RecastOrchestrationUtils.fromJson(progressRequest.getProgressJson());

    // Checking if event has sent any status to update for its step. If the update is successful, same is updated for
    // pipeline too.
    if (progressRequest.getStatus() != Status.NO_OP) {
      NodeExecution nodeExecution = nodeExecutionService.updateStatusWithOps(
          SdkResponseEventUtils.getNodeExecutionId(event), progressRequest.getStatus(),
          ops -> setUnset(ops, NodeExecutionKeys.progressData, progressDoc), EnumSet.noneOf(Status.class));
      if (nodeExecution != null) {
        String planExecutionId = nodeExecution.getPlanExecutionId();
        log.info("Updating plan status from Progress Request");
        planExecutionService.calculateAndUpdateRunningStatusUnderLock(planExecutionId, null);
      }
    } else {
      String nodeExecutionId = SdkResponseEventUtils.getNodeExecutionId(event);
      long timestamp = extractUnitProgressTimestamp(progressDoc);
      if (timestamp > 0) {
        // Timestamped snapshot: guard against stale/out-of-order updates via the shared timestamp fence.
        nodeExecutionService.updateWithUnitProgressTimestampFence(
            nodeExecutionId, timestamp, ops -> setUnset(ops, NodeExecutionKeys.progressData, progressDoc));
      } else {
        // Un-timestamped snapshot (older delegate) or non-unit-progress payload: always apply.
        nodeExecutionService.updateV2ForNonFinalStatusNodeExecution(
            nodeExecutionId, ops -> setUnset(ops, NodeExecutionKeys.progressData, progressDoc));
      }
    }
  }

  private long extractUnitProgressTimestamp(Map<String, Object> progressDoc) {
    if (EmptyPredicate.isEmpty(progressDoc)) {
      return 0L;
    }
    try {
      UnitProgressData unitProgressData = RecastOrchestrationUtils.fromMap(progressDoc, UnitProgressData.class);
      return unitProgressData == null ? 0L : unitProgressData.getTimestamp();
    } catch (RecasterException | ClassCastException ex) {
      log.debug("Progress doc is not a UnitProgressData snapshot, skipping timestamp fence", ex);
      return 0L;
    }
  }
}
