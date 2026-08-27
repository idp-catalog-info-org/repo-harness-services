/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.wait;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.execution.failure.FailureType.USER_MARKED_FAILURE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGTimeConversionHelper;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureTypeInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.executables.PipelineAsyncExecutable;
import io.harness.tasks.ResponseData;
import io.harness.telemetry.helpers.StepExecutionTelemetryEventDTO;
import io.harness.wait.WaitStepInstance;

import com.google.inject.Inject;
import java.util.Map;

@OwnedBy(PIPELINE)
public class WaitStep extends PipelineAsyncExecutable {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.WAIT_STEP_TYPE;
  @Inject WaitStepService waitStepService;
  @Inject SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  private final String WAIT_STEP_FAIL_MESSAGE = "User marked this step as failed";

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    String correlationId = UUIDGenerator.generateUuid();
    WaitStepParameters waitStepParameters = (WaitStepParameters) stepParameters.getSpec();
    int duration = 0;
    if (waitStepParameters.getDuration() != null && waitStepParameters.getDuration().getValue() != null) {
      duration =
          (int) NGTimeConversionHelper.convertTimeStringToMilliseconds(waitStepParameters.getDuration().getValue());
    }
    if (duration <= 0) {
      throw new InvalidRequestException("Invalid input for duration of wait step, Duration should be greater than 0");
    }
    waitStepService.save(WaitStepInstance.builder()
                             .waitStepInstanceId(correlationId)
                             .duration(duration)
                             .createdAt(System.currentTimeMillis())
                             .nodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                             .build());
    return AsyncExecutableResponse.newBuilder().addCallbackIds(correlationId).setTimeout(duration).build();
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String correlationId = waitStepService.findByNodeExecutionId(nodeExecutionId).get().getWaitStepInstanceId();
    if (responseDataMap.get(correlationId) instanceof WaitStepResponseData) {
      if (((WaitStepResponseData) responseDataMap.get(correlationId)).action == WaitStepAction.MARK_AS_FAIL) {
        WaitStepDetailsInfo waitStepDetailsInfo =
            WaitStepDetailsInfo.builder().actionTaken(WaitStepStatus.MARKED_AS_FAIL).build();
        sdkGraphVisualizationDataService.publishStepDetailInformation(
            ambiance, waitStepDetailsInfo, "waitStepActionTaken");
        FailureData.Builder failureData = FailureData.newBuilder();
        String stageIdentifier = AmbianceUtils.getStageIdentifierFromAmbiance(ambiance);
        // TODO: Add failure sub type
        failureData.setMessage(WAIT_STEP_FAIL_MESSAGE)
            .setCode(USER_MARKED_FAILURE.name())
            .setStepIdentifier(stepParameters.getIdentifier())
            .setStageIdentifier(stageIdentifier)
            .addFailureTypeInfos(FailureTypeInfo.newBuilder().setFailureType(USER_MARKED_FAILURE).build());
        return StepResponse.builder()
            .status(Status.FAILED)
            .failureInfo(
                FailureInfo.newBuilder().setErrorMessage(WAIT_STEP_FAIL_MESSAGE).addFailureData(failureData).build())
            .build();
      } else {
        WaitStepDetailsInfo waitStepDetailsInfo =
            WaitStepDetailsInfo.builder().actionTaken(WaitStepStatus.MARKED_AS_SUCCESS).build();
        sdkGraphVisualizationDataService.publishStepDetailInformation(
            ambiance, waitStepDetailsInfo, "waitStepActionTaken");
        return StepResponse.builder().status(Status.SUCCEEDED).build();
      }
    } else {
      return StepResponse.builder().status(Status.SUCCEEDED).build();
    }
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public StepExecutionTelemetryEventDTO getStepExecutionTelemetryEventDTO(
      Ambiance ambiance, StepBaseParameters stepParameters) {
    return StepExecutionTelemetryEventDTO.builder().stepType(STEP_TYPE.getType()).build();
  }
}