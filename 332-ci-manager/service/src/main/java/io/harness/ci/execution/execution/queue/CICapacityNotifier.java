/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.queue;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.execution.states.IntegrationStageStepPMSFacilitator;
import io.harness.delegate.beans.ci.vm.CapacityReservation;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.repositories.CIExecutionRepository;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Transient;

@OwnedBy(CI)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class CICapacityNotifier implements NotifyCallbackWithErrorHandling {
  @Inject @Transient private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject @Transient private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject @Transient private CIExecutionRepository ciExecutionRepository;
  @Inject @Transient private IntegrationStageStepPMSFacilitator stepPMSFacilitator;
  @Inject @Transient private QueueExecutionUtils queueExecutionUtils;
  @Inject @Transient private CICapacityPollerUtils executionPollerUtils;

  private String waitId;
  private byte[] ambianceBytes;
  private byte[] stepParametersBytes;
  private byte[] dequeueResponseBytes;

  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    log.info("Processing CI capacity notifier for waitId={}", waitId);

    Ambiance ambiance = parseAmbiance();
    StepParameters stepParameters = parseStepParameters();
    DequeueResponse dequeueResponse = parseDequeueResponse();
    if (ambiance == null || stepParameters == null || dequeueResponse == null) {
      log.error("Failed to parse ambiance or step parameters or dequeueResponse for waitId {}", waitId);
      return;
    }

    Optional<String> optionalTaskID = response.keySet().stream().findFirst();
    if (optionalTaskID.isEmpty()) {
      log.warn("No response received for capacity task with waitId {}. StageExecutionId={}, AccountId={}", waitId,
          ambiance.getStageExecutionId(), AmbianceUtils.getAccountId(ambiance));
      return;
    }

    try {
      ResponseData responseData = response.get(optionalTaskID.get()).get();
      handleResponse(ambiance, responseData, stepParameters, dequeueResponse);
    } catch (Exception e) {
      log.error("Error during capacity task processing. StageExecutionId={}, AccountId={}, Message={}",
          ambiance.getStageExecutionId(), AmbianceUtils.getAccountId(ambiance), e.getMessage(), e);
      try {
        executionPollerUtils.processResults(ProcessMessageResponse.builder().success(true).build(), dequeueResponse);
        stepPMSFacilitator.sendFacilitatorResponse(ambiance, Status.RUNNING);
      } catch (Exception ex) {
        log.error("Error when letting execution proceed. StageExecutionId={}, AccountId={}, Message={}",
            ambiance.getStageExecutionId(), AmbianceUtils.getAccountId(ambiance), e.getMessage(), e);
      }
    }
  }

  private Ambiance parseAmbiance() {
    try {
      return Ambiance.parseFrom(ambianceBytes);
    } catch (Exception e) {
      log.error("Failed to parse Ambiance", e);
      return null;
    }
  }

  private StepParameters parseStepParameters() {
    try {
      String json = ByteString.copyFrom(stepParametersBytes).toStringUtf8();
      return RecastOrchestrationUtils.fromJson(json, StepParameters.class);
    } catch (Exception e) {
      log.error("Failed to parse StepParameters", e);
      return null;
    }
  }

  private DequeueResponse parseDequeueResponse() {
    try {
      String dequeueResponseJson = ByteString.copyFrom(dequeueResponseBytes).toStringUtf8();
      return RecastOrchestrationUtils.fromJson(dequeueResponseJson, DequeueResponse.class);
    } catch (Exception e) {
      log.error("Failed to parse DequeueResponse from JSON", e);
      return null;
    }
  }

  private void handleResponse(
      Ambiance ambiance, ResponseData rawResponse, StepParameters stepParameters, DequeueResponse dequeueResponse) {
    Infrastructure infrastructure = QueueExecutionUtils.getInfrastructure(stepParameters);
    ResponseData responseData = serializedResponseDataHelper.deserialize(rawResponse);
    if (!(responseData instanceof VmTaskExecutionResponse)) {
      log.warn("Unexpected response type: {}", responseData.getClass().getSimpleName());
      return;
    }

    VmTaskExecutionResponse vmResponse = (VmTaskExecutionResponse) responseData;
    CommandExecutionStatus status = vmResponse.getCommandExecutionStatus();

    if (status == CommandExecutionStatus.FAILURE) {
      // If there is some failure in the capacity reservation task, we allow the init to proceed
      log.info("Error in Capacity reservation task for StageExecutionId={}, AccountId={}. Allowing init to proceed",
          ambiance.getStageExecutionId(), AmbianceUtils.getAccountId(ambiance));
      proceedWithExecution(ambiance, infrastructure, dequeueResponse);
      return;
    }

    if (status == CommandExecutionStatus.SUCCESS) {
      CapacityReservation reservation = vmResponse.getCapacityReservation();
      if (reservation != null && isNotEmpty(reservation.getPoolID())) {
        // if capacity is reserved, proceed with execution
        log.info("Capacity reserved successfully for StageExecutionId={}, AccountId={}", ambiance.getStageExecutionId(),
            AmbianceUtils.getAccountId(ambiance));
        proceedWithExecution(ambiance, infrastructure, dequeueResponse);
      } else {
        log.info("Capacity reservation unsuccessful, enqueueing build. StageExecutionId={}, AccountId={}",
            ambiance.getStageExecutionId(), AmbianceUtils.getAccountId(ambiance));
        queueExecutionForRetry(ambiance, dequeueResponse);
      }
    }
  }

  private void proceedWithExecution(Ambiance ambiance, Infrastructure infrastructure, DequeueResponse dequeueResponse) {
    CIExecutionMetadata ciExecutionMetadata = ciExecutionRepository.updateExecutionStatus(
        AmbianceUtils.getAccountId(ambiance), ambiance.getStageExecutionId(), Status.RUNNING.toString());
    queueExecutionUtils.publishQueueCountMetrics(ambiance, infrastructure);
    if (ciExecutionMetadata != null && isNotEmpty(ciExecutionMetadata.getQueueId())) {
      queueExecutionUtils.publishGlobalQueueTimeMetrics(ambiance, infrastructure, ciExecutionMetadata.getQueueId());
    }
    executionPollerUtils.processResults(ProcessMessageResponse.builder().success(true).build(), dequeueResponse);
    stepPMSFacilitator.sendFacilitatorResponse(ambiance, Status.RUNNING);
  }

  private void queueExecutionForRetry(Ambiance ambiance, DequeueResponse dequeueResponse) {
    executionPollerUtils.processResults(ProcessMessageResponse.builder().success(false).build(), dequeueResponse);
    ciExecutionRepository.updateCapacityTaskInProgress(
        AmbianceUtils.getAccountId(ambiance), ambiance.getStageExecutionId(), false);
  }
}
