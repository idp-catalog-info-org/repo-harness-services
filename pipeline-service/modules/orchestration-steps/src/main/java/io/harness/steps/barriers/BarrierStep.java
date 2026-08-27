/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.barriers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eraro.ErrorCode.BARRIER_FAILED_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.failure.FailureTypeInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierOutcome;
import io.harness.steps.barriers.beans.BarrierResponseData;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.steps.executables.PipelineAsyncExecutable;
import io.harness.tasks.ResponseData;
import io.harness.telemetry.helpers.StepExecutionTelemetryEventDTO;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(PIPELINE)
@Slf4j
public class BarrierStep extends PipelineAsyncExecutable {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.BARRIER_STEP_TYPE;

  private static final String BARRIER = "barrier";

  @Inject private BarrierService barrierService;

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepBaseParameters stepElementParameters, StepInputPackage inputPackage) {
    BarrierSpecParameters barrierSpecParameters = (BarrierSpecParameters) stepElementParameters.getSpec();
    String barrierRef = getBarrierRef(barrierSpecParameters, ambiance);
    String planExecutionId = getPlanExecutionId(ambiance, barrierSpecParameters.getBarrierRef());
    BarrierExecutionInstance barrierExecutionInstance = getBarrierExecutionInstanceOrThrow(barrierRef, planExecutionId);

    log.info("Barrier Step getting executed. RuntimeId: [{}], barrierUuid [{}], barrierIdentifier [{}]",
        AmbianceUtils.obtainCurrentRuntimeId(ambiance), barrierExecutionInstance.getUuid(), barrierRef);
    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(barrierExecutionInstance.getUuid())
        .setShouldRemoveAlreadyProcessedNotifyIds(true)
        .build();
  }

  @NotNull
  private static String getPlanExecutionId(Ambiance ambiance, String barrierRef) {
    String planExecutionId = ambiance.getPlanExecutionId();
    if (barrierRef.startsWith(YAMLFieldNameConstants.PARENT_DOT)
        && ambiance.getMetadata().getPipelineStageInfo().getHasParentPipeline()) {
      planExecutionId = ambiance.getMetadata().getPipelineStageInfo().getExecutionId();
    }
    return planExecutionId;
  }

  private static String getBarrierRef(BarrierSpecParameters barrierSpecParameters, Ambiance ambiance) {
    if (barrierSpecParameters.getBarrierRef().startsWith(YAMLFieldNameConstants.PARENT_DOT)) {
      return barrierSpecParameters.getBarrierRef().split("\\.")[1];
    }
    return barrierSpecParameters.getBarrierRef();
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepElementParameters, Map<String, ResponseData> responseDataMap) {
    BarrierSpecParameters barrierSpecParameters = (BarrierSpecParameters) stepElementParameters.getSpec();
    String planExecutionId = getPlanExecutionId(ambiance, barrierSpecParameters.getBarrierRef());

    // if barrier is still in STANDING => update barrier state
    BarrierExecutionInstance barrierExecutionInstance =
        updateBarrierExecutionInstance(getBarrierRef(barrierSpecParameters, ambiance), planExecutionId);

    log.info("Response for barrier step with state [{}] received. BarrierUuid [{}], barrierIdentifier [{}]",
        barrierExecutionInstance.getBarrierState(), barrierExecutionInstance.getUuid(),
        getBarrierRef(barrierSpecParameters, ambiance));

    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    BarrierResponseData responseData = (BarrierResponseData) responseDataMap.get(barrierExecutionInstance.getUuid());
    if (responseData.isFailed()) {
      BarrierResponseData.BarrierError barrierError = responseData.getBarrierError();
      if (barrierError.isTimedOut()) {
        stepResponseBuilder.status(Status.EXPIRED);
      } else {
        stepResponseBuilder.status(Status.FAILED);
      }
      stepResponseBuilder.failureInfo(
          FailureInfo.newBuilder()
              .addFailureData(FailureData.newBuilder()
                                  .setCode(BARRIER_FAILED_ERROR.name())
                                  .addFailureTypes(FailureType.TIMEOUT_FAILURE)
                                  .setLevel(Level.ERROR.name())
                                  .setMessage(barrierError.getErrorMessage())
                                  .addFailureTypeInfos(
                                      FailureTypeInfo.newBuilder().setFailureType(FailureType.TIMEOUT_FAILURE).build())
                                  .build())
              .setErrorMessage(barrierError.getErrorMessage())
              .build());
    } else {
      stepResponseBuilder.status(Status.SUCCEEDED);
    }

    return stepResponseBuilder
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(BARRIER)
                         .outcome(BarrierOutcome.builder().barrierRef(barrierExecutionInstance.getIdentifier()).build())
                         .build())
        .build();
  }

  @Override
  public void handleAbort(Ambiance ambiance, StepBaseParameters stepElementParameters,
      AsyncExecutableResponse executableResponse, boolean userMarked) {
    BarrierSpecParameters barrierSpecParameters = (BarrierSpecParameters) stepElementParameters.getSpec();
    String planExecutionId = getPlanExecutionId(ambiance, barrierSpecParameters.getBarrierRef());
    updateBarrierExecutionInstance(getBarrierRef(barrierSpecParameters, ambiance), planExecutionId);
  }

  @Override
  public StepExecutionTelemetryEventDTO getStepExecutionTelemetryEventDTO(
      Ambiance ambiance, StepBaseParameters stepParameters) {
    return StepExecutionTelemetryEventDTO.builder().stepType(STEP_TYPE.getType()).build();
  }

  private BarrierExecutionInstance updateBarrierExecutionInstance(String identifier, String planExecutionId) {
    BarrierExecutionInstance barrierExecutionInstance = getBarrierExecutionInstanceOrThrow(identifier, planExecutionId);
    return barrierService.update(barrierExecutionInstance);
  }

  @NotNull
  private BarrierExecutionInstance getBarrierExecutionInstanceOrThrow(String identifier, String planExecutionId) {
    BarrierExecutionInstance barrierExecutionInstance =
        barrierService.findByIdentifierAndPlanExecutionId(identifier, planExecutionId);
    if (barrierExecutionInstance == null) {
      throw new InvalidRequestException(
          String.format("Barrier not found for identifier [%s] and planExecutionId [%s]. Ensure the barrier was "
                  + "registered during plan creation with a literal barrier name (expressions are not allowed).",
              identifier, planExecutionId));
    }
    return barrierExecutionInstance;
  }
}
