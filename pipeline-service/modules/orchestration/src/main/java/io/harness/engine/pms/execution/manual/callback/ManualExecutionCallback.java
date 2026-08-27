/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.manual.callback;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.engine.pms.execution.manual.beans.ManualExecutionAction.MARK_AS_FAIL;
import static io.harness.pms.execution.OrchestrationFacilitatorType.MANUAL_EXECUTION;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.facilitation.FacilitationHelper;
import io.harness.engine.facilitation.facilitator.CoreFacilitator;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.engine.pms.execution.manual.beans.ManualExecutionDetailsInfo;
import io.harness.engine.pms.execution.manual.beans.ManualExecutionResponseData;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.facilitators.FacilitatorEvent;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Transient;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class ManualExecutionCallback implements OldNotifyCallback {
  protected static final String STEP_DETAILS_NAME = "manualExecution";
  @Inject @Transient private SdkNodeExecutionService sdkNodeExecutionService;
  @Inject @Transient private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsEventSender eventSender;
  @Inject private FacilitationHelper facilitationHelper;
  byte[] ambianceBytes;
  String notifyId; // Notify ID where we have to send back the facilitator response
  String nodeExecutionId;
  byte[] primaryFacilitatorObtainmentBytes;
  byte[] facilitatorEventBytes;
  long startTs;

  @Override
  public void notify(Map<String, ResponseData> response) {
    try {
      if (isEmpty(response.values())) {
        notifyError(response, "Response can not be empty to resolve callback");
        return;
      }
      Ambiance ambiance = Ambiance.parseFrom(ambianceBytes);
      ResponseData responseData = response.values().iterator().next();
      if (responseData instanceof ManualExecutionResponseData) {
        ManualExecutionResponseData executionResponseData = (ManualExecutionResponseData) responseData;
        if (MARK_AS_FAIL.equals(executionResponseData.getAction())) {
          log.info("User marked the Manual Execution to fail for nodeExecutionId {}", nodeExecutionId);
          sdkNodeExecutionService.handleFacilitationResponse(ambiance, notifyId,
              FacilitatorResponseProto.newBuilder()
                  .setPassThroughData("User marked the manual execution to fail")
                  .setIsSuccessful(false)
                  .build());
          sdkGraphVisualizationDataService.publishStepDetailInformation(
              ambiance, buildDetailsInfo(), STEP_DETAILS_NAME);
        } else {
          handleSuccessResponse(ambiance);
          sdkGraphVisualizationDataService.publishStepDetailInformation(
              ambiance, buildDetailsInfo(), STEP_DETAILS_NAME);
        }
      } else {
        notifyError(response, "Response data is not of type ManualExecutionResponseData");
      }
    } catch (InvalidProtocolBufferException e) {
      log.error(
          "Not able to deserialize Ambiance bytes. ManualExecutionCallback will not be executed for nodeExecutionId {}",
          nodeExecutionId);
    }
  }

  @Override
  public void notifyTimeout(Map<String, ResponseData> response) {
    try {
      log.error("Timeout received for Manual Execution callback for nodeExecutionId {}", nodeExecutionId);
      Ambiance ambiance = Ambiance.parseFrom(ambianceBytes);
      sdkNodeExecutionService.handleFacilitationResponse(ambiance, notifyId,
          FacilitatorResponseProto.newBuilder()
              .setPassThroughData("Timed out while waiting for user input")
              .setIsSuccessful(false)
              .build());
      sdkGraphVisualizationDataService.publishStepDetailInformation(ambiance, buildDetailsInfo(), STEP_DETAILS_NAME);
    } catch (InvalidProtocolBufferException e) {
      log.error(
          "Not able to deserialize Ambiance bytes. ManualExecutionCallback will not be executed for nodeExecutionId {}",
          nodeExecutionId);
    }
  }

  private ManualExecutionDetailsInfo buildDetailsInfo() {
    return ManualExecutionDetailsInfo.builder().startTs(startTs).endTs(System.currentTimeMillis()).build();
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    notifyError(response, "Failed to process Manual Execution callback");
  }

  private void notifyError(Map<String, ResponseData> response, String errorMsg) {
    try {
      if (isNotEmpty(response.values())) {
        ResponseData responseData = response.values().iterator().next();
        if (responseData instanceof ErrorResponseData) {
          errorMsg = ((ErrorResponseData) responseData).getErrorMessage();
        }
      }
      log.error("Manual Execution event failed for nodeExecutionId {}, with error: {}", nodeExecutionId, errorMsg);
      Ambiance ambiance = Ambiance.parseFrom(ambianceBytes);
      sdkNodeExecutionService.handleFacilitationResponse(ambiance, notifyId,
          FacilitatorResponseProto.newBuilder().setPassThroughData(errorMsg).setIsSuccessful(false).build());
      sdkGraphVisualizationDataService.publishStepDetailInformation(ambiance, buildDetailsInfo(), STEP_DETAILS_NAME);
    } catch (InvalidProtocolBufferException e) {
      log.error(
          "Not able to deserialize Ambiance bytes. ManualExecutionCallback will not be executed for nodeExecutionId {}",
          nodeExecutionId);
    }
  }

  private void handleSuccessResponse(Ambiance ambiance) throws InvalidProtocolBufferException {
    log.info("Progressing forward the Manual Execution for nodeExecutionId {}", nodeExecutionId);
    if (isNotEmpty(primaryFacilitatorObtainmentBytes)) {
      // This is for backward compatibility for old callbacks stored
      FacilitatorObtainment facilitatorObtainment = FacilitatorObtainment.parseFrom(primaryFacilitatorObtainmentBytes);
      handlePrimaryFacilitatorResponse(ambiance, facilitatorObtainment);
      return;
    }
    FacilitatorEvent facilitatorEvent = getFacilitatorEvent();
    for (FacilitatorObtainment obtainment : facilitatorEvent.getFacilitatorObtainmentsList()) {
      if (isPrimaryFacilitator(obtainment)) {
        handlePrimaryFacilitatorResponse(ambiance, obtainment);
        return;
      }
      // We want to send the FACILITATOR_EVENT again, because in this case the other facilitator is a custom one
      // So the provided node's service can execute the custom facilitator again based on the logic.
      NodeExecution nodeExecution =
          nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.forFacilitation);
      eventSender.sendEvent(
          ambiance, facilitatorEvent, PmsEventCategory.FACILITATOR_EVENT, nodeExecution.getModule(), true, true);
      return;
    }
  }

  public boolean isPrimaryFacilitator(FacilitatorObtainment obtainment) {
    if (isNull(obtainment)) {
      return false;
    }
    return OrchestrationFacilitatorType.ALL_PRIMARY_FACILITATOR_TYPES.contains(obtainment.getType().getType());
  }

  private FacilitatorEvent getFacilitatorEvent() throws InvalidProtocolBufferException {
    // This function filters the obtainment's and removes the manual execution facilitator from the list
    FacilitatorEvent facilitatorEvent = FacilitatorEvent.parseFrom(facilitatorEventBytes);
    FacilitatorEvent.Builder builder = facilitatorEvent.toBuilder().clearFacilitatorObtainments();
    for (FacilitatorObtainment obtainment : facilitatorEvent.getFacilitatorObtainmentsList()) {
      if (!MANUAL_EXECUTION.equals(obtainment.getType().getType())) {
        builder.addFacilitatorObtainments(obtainment);
      }
    }
    return builder.build();
  }

  private void handlePrimaryFacilitatorResponse(Ambiance ambiance, FacilitatorObtainment facilitatorObtainment) {
    /*
     We want to send the primary(core) facilitator response back to the engine, because the engine will then
     run the corresponding execution mode to start the node execution.
     */
    CoreFacilitator facilitator = facilitationHelper.getFacilitatorFromType(facilitatorObtainment.getType());
    FacilitatorResponseProto facilitatorResponseProto =
        facilitator.facilitate(ambiance, facilitatorObtainment.getParameters().toByteArray());
    FacilitatorResponseProto responseWithRunningStatus =
        facilitatorResponseProto.toBuilder().setStatus(Status.RUNNING).build();
    sdkNodeExecutionService.handleFacilitationResponse(ambiance, notifyId, responseWithRunningStatus);
  }
}
