/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.eraro.ErrorCode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.FacilitatorResponseRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumSet;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class FacilitateResponseRequestProcessor implements SdkResponseProcessor {
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private NodeExecutionService nodeExecutionService;

  @Override
  public void handleEvent(SdkResponseEventProto event) {
    log.info("Starting to process facilitation response");
    FacilitatorResponseRequest request = event.getFacilitatorResponseRequest();
    FacilitatorResponseProto facilitatorResponseProto = request.getFacilitatorResponse();
    Ambiance ambiance = event.getAmbiance();

    if (facilitatorResponseProto.getStatus() != Status.NO_OP
        && facilitatorResponseProto.getStatus() != Status.RUNNING) {
      // Status should never be manually set to RUNNING. This will be done by starting the node
      String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
      nodeExecutionService.updateStatusWithOps(
          nodeExecutionId, facilitatorResponseProto.getStatus(), null, EnumSet.noneOf(Status.class));
      return;
    }

    if (facilitatorResponseProto.getIsSuccessful()) {
      orchestrationEngine.processFacilitatorResponse(ambiance, facilitatorResponseProto);
      if (facilitatorResponseProto.getStatus() == Status.RUNNING) {
        // Update the status of the plan to RUNNING after the node has been started since it is not done automatically
        planExecutionService.calculateAndUpdateRunningStatusForStageAndPlanUnderLock(ambiance);
      }
    } else {
      StepResponseProto stepResponseProto =
          StepResponseProto.newBuilder()
              .setStatus(Status.FAILED)
              .setFailureInfo(FailureInfo.newBuilder()
                                  .addFailureData(FailureData.newBuilder()
                                                      .setMessage(facilitatorResponseProto.getPassThroughData())
                                                      .setCode(ErrorCode.GENERAL_ERROR.name())
                                                      .setLevel(io.harness.eraro.Level.ERROR.name())
                                                      .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                      .build())
                                  .setErrorMessage(facilitatorResponseProto.getPassThroughData())
                                  .build())
              .build();
      orchestrationEngine.processStepResponse(ambiance, stepResponseProto);
    }
  }
}
