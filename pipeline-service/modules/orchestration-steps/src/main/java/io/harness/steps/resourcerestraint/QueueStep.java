/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.telemetry.helpers.StepExecutionTelemetryEventDTO;
import io.harness.telemetry.helpers.StepsInstrumentationHelper;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * A dedicated step to represent a queue, even the queue be a kind of resource constraint.
 *
 * We decide create this one to avoid any side effect from changing the original resource restraint step type. While
 * {@link ResourceRestraintStep} is used for internal stuffs, the queue step is exposed to the customer.
 */
@Slf4j
@OwnedBy(PIPELINE)
public class QueueStep extends ResourceRestraintStep {
  @Inject private StepsInstrumentationHelper stepsInstrumentationHelper;

  public static final StepType STEP_TYPE = StepSpecTypeConstants.QUEUE_STEP_TYPE;

  @Override
  public StepResponse executeSync(Ambiance ambiance, StepBaseParameters stepElementParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    sendTelemetryEvent(ambiance, stepElementParameters);
    return super.executeSync(ambiance, stepElementParameters, inputPackage, passThroughData);
  }

  public void sendTelemetryEvent(Ambiance ambiance, StepBaseParameters stepElementParameters) {
    try {
      StepExecutionTelemetryEventDTO telemetryEventDTO =
          StepExecutionTelemetryEventDTO.builder().stepType(STEP_TYPE.getType()).build();
      stepsInstrumentationHelper.publishStepEvent(ambiance, telemetryEventDTO);
    } catch (Exception ex) {
      log.error(String.format("Failed to publish Telemetry event for - [%s]", this.getClass()), ex);
    }
  }
}
