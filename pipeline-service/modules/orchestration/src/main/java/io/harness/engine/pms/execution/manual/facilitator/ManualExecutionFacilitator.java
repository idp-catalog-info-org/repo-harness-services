/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.manual.facilitator;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.expression.common.ExpressionMode.RETURN_NULL_IF_UNRESOLVED;
import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;
import static io.harness.pms.execution.OrchestrationFacilitatorType.MANUAL_EXECUTION;
import static io.harness.pms.yaml.YamlUtils.NULL_STR;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.common.NGTimeConversionHelper;
import io.harness.constants.OrchestrationPublisherName;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.pms.execution.manual.callback.ManualExecutionCallback;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.FacilitatorExecutableResponse;
import io.harness.pms.contracts.facilitators.FacilitatorEvent;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.facilitator.ManualExecutionFacilitatorParams;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.execution.events.node.facilitate.response.Facilitator;
import io.harness.pms.sdk.core.execution.events.node.facilitate.response.FacilitatorResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.serializer.KryoSerializer;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class ManualExecutionFacilitator implements Facilitator {
  public static final FacilitatorType FACILITATOR_TYPE = FacilitatorType.newBuilder().setType(MANUAL_EXECUTION).build();
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;
  @Inject private SdkNodeExecutionService sdkNodeExecutionService;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public void runCustomFacilitator(
      String notifyId, Ambiance ambiance, byte[] parameters, StepInputPackage inputPackage, FacilitatorEvent event) {
    String uuid = generateUuid();
    if (isEmpty(parameters)) {
      throw new InternalServerErrorException("Manual Execution Facilitator Params cannot be empty");
    }
    if (isEmpty(notifyId)) {
      throw new InternalServerErrorException("Manual Execution Facilitator notifyId cannot be empty");
    }
    ManualExecutionFacilitatorParams facilitatorParams =
        (ManualExecutionFacilitatorParams) kryoSerializer.asObject(parameters);
    if (facilitatorParams == null) {
      throw new InternalServerErrorException("Manual Execution Facilitator Params cannot be empty");
    }
    if (!facilitatorParams.getRunModeConfig().hasManual()) {
      throw new UnsupportedOperationException(
          "Currently Manual Execution Facilitator is only supported for Manual Run Mode");
    }
    String resolvedTimeout = (String) pmsEngineExpressionService.resolve(
        ambiance, facilitatorParams.getRunModeConfig().getManual().getTimeout(), RETURN_NULL_IF_UNRESOLVED);

    if (isEmpty(resolvedTimeout) || NULL_STR.equals(resolvedTimeout)) {
      throw new InvalidRequestException(
          "Invalid input for duration of manual stage execution, Duration should be greater than 0");
    }
    long callbackTimeoutInSeconds = NGTimeConversionHelper.convertTimeStringToMilliseconds(resolvedTimeout) / 1000;

    if (callbackTimeoutInSeconds <= 0) {
      throw new InvalidRequestException(
          "Invalid input for duration of manual stage execution, Duration should be greater than 0");
    }

    long startTs = System.currentTimeMillis();
    ManualExecutionCallback callback = ManualExecutionCallback.builder()
                                           .ambianceBytes(ambiance.toByteArray())
                                           .nodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                                           .notifyId(notifyId)
                                           .facilitatorEventBytes(event.toByteArray())
                                           .startTs(startTs)
                                           .build();
    waitNotifyEngine.waitForAllOn(publisherName, callback, null, Collections.singletonList(uuid),
        Duration.ofSeconds(callbackTimeoutInSeconds), null);
    /*
     Setting the callback id in executable responses so that it can be fetched in the API call via nodeExecutionId
     and then the callback can be ended.
     */
    sdkNodeExecutionService.addExecutableResponse(ambiance,
        ExecutableResponse.newBuilder()
            .setFacilitator(FacilitatorExecutableResponse.newBuilder()
                                .setType(MANUAL_EXECUTION)
                                .setStatus(INTERVENTION_WAITING)
                                .setStartTs(startTs)
                                .addCallbackIds(uuid)
                                .setTimeoutInSeconds(callbackTimeoutInSeconds)
                                .build())
            .build());
    log.info("Manual Execution Facilitator Callback started for {}", uuid);
  }

  @Override
  public FacilitatorResponse facilitate(
      Ambiance ambiance, StepParameters stepParameters, byte[] parameters, StepInputPackage inputPackage) {
    throw new UnsupportedOperationException("This is a Custom facilitator, so facilitate isn't supported");
  }
}
