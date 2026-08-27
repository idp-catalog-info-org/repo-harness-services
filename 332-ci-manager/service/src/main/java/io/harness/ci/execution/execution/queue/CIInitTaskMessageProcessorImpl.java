/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.queue;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.STAGE_QUEUE_TIME;

import io.harness.beans.execution.CIInitTaskArgs;
import io.harness.beans.sweepingoutputs.StageQueueExecutionSweepingOutput;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.enforcement.CIBuildEnforcer;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.execution.queue.ProcessMessageResponse.ProcessMessageResponseBuilder;
import io.harness.ci.execution.states.IntegrationStageStepPMSFacilitator;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.repositories.CIExecutionRepository;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CIInitTaskMessageProcessorImpl implements CITaskMessageProcessor {
  @Inject(optional = true) CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject CIBuildEnforcer buildEnforcer;
  @Inject CIExecutionRepository ciExecutionRepository;
  @Inject QueueExecutionUtils queueExecutionUtils;
  @Inject IntegrationStageStepPMSFacilitator stepPMSFacilitator;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public ProcessMessageResponse processMessage(DequeueResponse dequeueResponse) {
    ProcessMessageResponseBuilder builder = ProcessMessageResponse.builder();
    CIInitTaskArgs ciInitTaskArgs;
    Ambiance ambiance;
    String accountId;
    String moduleType;
    StepParameters stepParameters;
    Infrastructure infrastructure;
    try {
      // Skip stale messages (older than 35 days)
      if (queueExecutionUtils.isStaleQueueMessage(dequeueResponse.getItemId())) {
        return builder.success(true).build(); // ack message
      }

      String payload = dequeueResponse.getPayload();
      ciInitTaskArgs = RecastOrchestrationUtils.fromJson(payload, CIInitTaskArgs.class);
      ambiance = ciInitTaskArgs.getAmbiance();
      accountId = AmbianceUtils.getAccountId(ambiance);
      moduleType = AmbianceUtils.getStageModuleType(ambiance);
      stepParameters = ciInitTaskArgs.getStepParameters();
      infrastructure = QueueExecutionUtils.getInfrastructure(stepParameters);

      if (!buildEnforcer.shouldRun(accountId, infrastructure, moduleType, ambiance.getMetadata().getPrincipalInfo())) {
        log.info(String.format(
            "skipping execution for account id: %s because of concurrency enforcement failure", ambiance));
        return builder.success(false).build();
      }

      // Try to atomically acquire the message processor lock to ensure only one thread processes the concurrency task
      boolean lockAcquired = ciExecutionRepository.tryAcquireConcurrencyQueueMessageProcessorLock(
          accountId, ambiance.getStageExecutionId());

      if (!lockAcquired) {
        CIExecutionMetadata existingMetadata =
            ciExecutionRepository.getExecutionMetadata(accountId, ambiance.getStageExecutionId());

        if (existingMetadata == null) {
          log.error("Failed to process execution as ciExecutionMetadata is null for stageExecutionId: {}. It generally "
                  + "happens aborted executions",
              ambiance.getStageExecutionId());
          return builder.success(true).build(); // ack message
        }
        log.info(
            "Concurrency queue message already being processed by another thread. AccountID={}, StageExecutionID={}",
            accountId, ambiance.getStageExecutionId());
        // We should unack incase the other thread crashes then we can process it again later
        return builder.success(false).build();
      }

      queueExecutionUtils.publishQueueCountMetrics(ambiance, infrastructure);

      if (!queueExecutionUtils.isGlobalQueueEnabled(ambiance, infrastructure)) {
        CIExecutionMetadata ciExecutionMetadata = ciExecutionRepository.updateExecutionStatus(
            accountId, ambiance.getStageExecutionId(), Status.RUNNING.toString());
        if (ciExecutionMetadata == null) {
          log.error(String.format("Failed to process execution as ciExecutionMetadata is null for stageExecutionId Id: "
                  + "%s , It generally happens for aborted executions",
              ambiance.getStageExecutionId()));
          return builder.success(true).build();
        }
        queueExecutionUtils.publishQueueTimeMetrics(ambiance, infrastructure, ciExecutionMetadata.getQueueId());
        Double queueTimeMs = queueExecutionUtils.computeQueueTimeInMillis(ciExecutionMetadata.getQueueId());
        if (queueTimeMs != null) {
          publishQueueTimeSweepingOutput(ambiance, queueTimeMs.longValue());
        }
        stepPMSFacilitator.sendFacilitatorResponse(ambiance, Status.RUNNING);
        builder.success(true);
        return builder.build();
      }
    } catch (Exception ex) {
      log.warn("ci init task processing failed", ex);
      return builder.success(false).build();
    }

    try {
      CIExecutionMetadata ciExecutionMetadata =
          ciExecutionRepository.getExecutionMetadata(accountId, ambiance.getStageExecutionId());
      if (ciExecutionMetadata != null) {
        queueExecutionUtils.publishQueueTimeMetrics(ambiance, infrastructure, ciExecutionMetadata.getQueueId());
        Double queueTimeMs = queueExecutionUtils.computeQueueTimeInMillis(ciExecutionMetadata.getQueueId());
        if (queueTimeMs != null) {
          publishQueueTimeSweepingOutput(ambiance, queueTimeMs.longValue());
        }
      }
      stepPMSFacilitator.enqueueBuild(ambiance, stepParameters);
      stepPMSFacilitator.sendFacilitatorResponse(ambiance, Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED);
      builder.success(true).build();
    } catch (Exception ex) {
      log.warn("sending ci capacity task failed", ex);
      // ack from concurrency queue and let task proceed
      stepPMSFacilitator.sendFacilitatorResponse(ambiance, Status.RUNNING);
      return builder.success(true).build();
    }
    return builder.build();
  }

  @Override
  public String getTopic() {
    return ciExecutionServiceConfig.getQueueServiceClientConfig().getTopic();
  }

  /**
   * Persists the queue time on a stage-scoped sweeping output so it can be read back at stage completion without racing
   * the cleanup handler that deletes the CIExecutionMetadata record on the same terminal stage event.
   */
  private void publishQueueTimeSweepingOutput(Ambiance ambiance, long queueTimeMs) {
    try {
      OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_QUEUE_TIME));
      if (optionalSweepingOutput == null || !optionalSweepingOutput.isFound()) {
        executionSweepingOutputService.consume(ambiance, STAGE_QUEUE_TIME,
            StageQueueExecutionSweepingOutput.builder().queueTimeMs(queueTimeMs).build(),
            StepOutcomeGroup.STAGE.name());
      }
    } catch (Exception ex) {
      log.warn(
          "Failed to publish queue time sweeping output for stageExecutionId: {}", ambiance.getStageExecutionId(), ex);
    }
  }
}