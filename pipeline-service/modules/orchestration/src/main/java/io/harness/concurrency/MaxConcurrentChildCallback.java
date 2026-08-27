/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.concurrency;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.constants.OrchestrationPublisherName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.tasks.ResponseData;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.waiter.OldNotifyCallback;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Data
@Builder
@Slf4j
public class MaxConcurrentChildCallback implements OldNotifyCallback {
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;

  @Inject OrchestrationEngine engine;
  @Inject NodeExecutionService nodeExecutionService;
  @Inject NodeExecutionInfoService nodeExecutionInfoService;
  @Inject PersistentLocker persistentLocker;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject PmsFeatureFlagHelper pmsFeatureFlagHelper;

  long maxConcurrency;
  String parentNodeExecutionId;
  String planExecutionId;
  @Deprecated Ambiance ambiance; // Store only planExecutionId

  Boolean proceedIfFailed;
  @Override
  public void notify(Map<String, ResponseData> response) {
    Status currentStatus = createStepResponseFromChildResponse(response).getStatus();
    ConcurrentChildInstance childInstance =
        nodeExecutionInfoService.incrementCursor(parentNodeExecutionId, currentStatus);

    if (childInstance == null) {
      log.error("[MaxConcurrentCallback]: ChildInstance found null for parentId: " + parentNodeExecutionId);
      if (EmptyPredicate.isEmpty(planExecutionId)) {
        nodeExecutionService.errorOutActiveNodes(ambiance.getPlanExecutionId());
      } else {
        nodeExecutionService.errorOutActiveNodes(planExecutionId);
      }
      return;
    }
    log.info("[MaxConcurrentCallback]: MaxConcurrentCallback called for parentId: " + parentNodeExecutionId);

    // We have reached the last child already so ignore this callback as there is no new child to run.
    if (childInstance.getCursor() >= childInstance.getChildrenNodeExecutionIds().size()) {
      log.info(
          "[MaxConcurrentCallback]: Ignoring the callback as we have traversed all the children for parentExecutionId: "
          + parentNodeExecutionId);
      return;
    }

    int cursor = childInstance.getCursor();
    String nodeExecutionToStart = childInstance.getChildrenNodeExecutionIds().get(cursor);
    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionToStart, NodeProjectionUtils.withAmbianceAndStatus);
    if (shouldSkipNodeExecution(currentStatus, childInstance, nodeExecution)) {
      skipExecution(nodeExecution);
      return;
    }
    getAmbianceAndStartExecution(nodeExecution);
  }

  @VisibleForTesting
  boolean shouldSkipNodeExecution(
      Status currentStatus, ConcurrentChildInstance childInstance, NodeExecution nodeExecution) {
    String accountId = NodeExecutionContextUtils.getAccountId(nodeExecution);
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIE_STEP_GROUP_SKIP_ON_LOOPING_STRATEGY)) {
      Ambiance nodeAmbiance = nodeExecutionService.getAmbiance(nodeExecution);
      if (AmbianceUtils.isUnderRollbackSteps(nodeAmbiance)) {
        return false;
      }
      OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
          nodeAmbiance, RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.STOP_STEPS_SEQUENCE));
      // optionalSweepingOutput returns true when rollback happens. Using this we skip rest of the steps and step
      // group combinations and mark them skipped.

      if (optionalSweepingOutput.isFound()) {
        return true;
      }
    }

    // We mark the rest of the steps as skipped when any previous combination has also failed along with the
    // proceedIfFailed is false.
    return proceedIfFailed != null && !proceedIfFailed
        && (StatusUtils.brokeStatuses().contains(currentStatus)
            || (EmptyPredicate.isNotEmpty(childInstance.getChildStatuses())
                && childInstance.getChildStatuses().stream().anyMatch(s -> StatusUtils.brokeStatuses().contains(s))));
  }

  private void skipExecution(NodeExecution nodeExecution) {
    log.info(String.format("Skipping node: %s", nodeExecution.getUuid()));
    StepResponseProto response = StepResponseProto.newBuilder().setStatus(Status.SKIPPED).build();
    engine.processStepResponse(nodeExecutionService.getAmbiance(nodeExecution), response);
  }

  private void getAmbianceAndStartExecution(NodeExecution nodeExecution) {
    if (StatusUtils.resumableStatuses().contains(nodeExecution.getStatus())) {
      log.info("[MaxConcurrentCallback]: Starting the execution with id: " + nodeExecution.getUuid());
      engine.queueOrStartExecution(nodeExecutionService.getAmbiance(nodeExecution));
    }
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    notify(response);
  }
}
