/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.queue;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.FREE_CI_ATTR;

import io.harness.beans.execution.CIInitTaskArgs;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.runtime.CloudRuntime.CloudRuntimeImageSpec;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.execution.integrationstage.VmInitializeTaskParamsBuilder;
import io.harness.ci.execution.integrationstage.vm.intfc.VmInitializeUtils;
import io.harness.ci.execution.queue.ProcessMessageResponse.ProcessMessageResponseBuilder;
import io.harness.ci.execution.states.IntegrationStageStepPMSFacilitator;
import io.harness.delegate.RunnerRequest;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.vm.runner.CapacityReservationRequest;
import io.harness.delegate.beans.ci.vm.runner.SetupVmRequest;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.dto.LicensesWithSummaryDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.repositories.CIExecutionRepository;
import io.harness.runner.request.builder.RunnerRequestBuilder;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.CILicenseUsageUtils;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class CICapacityTaskMessageProcessorImpl implements CITaskMessageProcessor {
  @Inject(optional = true) CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject CIExecutionRepository ciExecutionRepository;
  @Inject QueueExecutionUtils queueExecutionUtils;
  @Inject IntegrationStageStepPMSFacilitator stepPMSFacilitator;
  @Inject AsyncWaitEngine asyncWaitEngine;
  @Inject private RunnerRequestBuilder runnerRequestBuilder;
  @Inject private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  @Inject private VmInitializeUtils vmInitializeUtils;
  @Inject VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;
  @Inject CILicenseUsageUtils ciLicenseUsageUtils;
  @Inject private CILicenseService ciLicenseService;
  private static final long DEFAULT_CAPACITY_TASK_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
  private static final long DEFAULT_CAPACITY_TASK_MAX_WAIT_TIME_MS =
      8 * 60 * 1000; // 8 minutes. Giving a buffer over capacity task timeout

  @Override
  public ProcessMessageResponse processMessage(DequeueResponse dequeueResponse) {
    ProcessMessageResponseBuilder builder = ProcessMessageResponse.builder();
    try {
      // Skip stale messages (older than 35 days)
      if (queueExecutionUtils.isStaleQueueMessage(dequeueResponse.getItemId())) {
        return builder.success(true).build(); // ack message
      }

      String payload = dequeueResponse.getPayload();
      CIInitTaskArgs ciInitTaskArgs = RecastOrchestrationUtils.fromJson(payload, CIInitTaskArgs.class);
      StepParameters stepParameters = ciInitTaskArgs.getStepParameters();
      Ambiance ambiance = ciInitTaskArgs.getAmbiance();
      String accountId = AmbianceUtils.getAccountId(ambiance);
      Infrastructure infrastructure = QueueExecutionUtils.getInfrastructure(stepParameters);

      if (!queueExecutionUtils.isGlobalQueueEnabled(ambiance, infrastructure)) {
        CIExecutionMetadata ciExecutionMetadata = ciExecutionRepository.updateExecutionStatus(
            accountId, ambiance.getStageExecutionId(), Status.RUNNING.toString());
        queueExecutionUtils.publishQueueCountMetrics(ambiance, infrastructure);
        if (ciExecutionMetadata == null) {
          log.error(String.format("Failed to process execution as ciExecutionMetadata is null for stageExecutionId Id: "
                  + "%s , It generally happens for aborted executions",
              ambiance.getStageExecutionId()));
          return builder.success(true).build();
        }
        queueExecutionUtils.publishGlobalQueueTimeMetrics(ambiance, infrastructure, ciExecutionMetadata.getQueueId());
        stepPMSFacilitator.sendFacilitatorResponse(ambiance, Status.RUNNING);
        builder.success(true);
        return builder.build();
      }

      // Try to atomically acquire the lock with time check
      long currentTimeMillis = System.currentTimeMillis();

      boolean lockAcquired = ciExecutionRepository.tryAcquireCapacityTaskLock(accountId, ambiance.getStageExecutionId(),
          currentTimeMillis, ciExecutionServiceConfig.getGlobalQueueingConfig().getCapacityTaskRetryIntervalMs());

      if (!lockAcquired) {
        // Failed to acquire lock - either:
        // 1. Capacity task already in progress
        // 2. Processed too recently (< 1 minute ago)
        // 3. Metadata doesn't exist (aborted execution)

        CIExecutionMetadata existingMetadata =
            ciExecutionRepository.getExecutionMetadata(accountId, ambiance.getStageExecutionId());

        if (existingMetadata == null) {
          log.error("Failed to process execution as ciExecutionMetadata is null for stageExecutionId: {}",
              ambiance.getStageExecutionId());
          return builder.success(true).build(); // ack message
        }

        // Log appropriate message based on state
        if (existingMetadata.getCapacityTaskInProgress() != null && existingMetadata.getCapacityTaskInProgress()) {
          // Check if capacity task has exceeded 3-minute timeout
          Long capacityTaskStartTime = existingMetadata.getCapacityTaskProcessedTime();
          if (capacityTaskStartTime != null) {
            long timeSinceStart = currentTimeMillis - capacityTaskStartTime;
            long maxWaitTimeMillis = getCapacityTaskMaxWaitTimeMillis();
            if (timeSinceStart > maxWaitTimeMillis) {
              log.warn("Capacity task for stageExecutionId: {} has been in progress for {} ms (exceeds {} ms "
                      + "timeout). Forcing execution to proceed.",
                  ambiance.getStageExecutionId(), timeSinceStart, maxWaitTimeMillis);
              // Timeout exceeded - proceed with execution by updating status and sending facilitator response
              ciExecutionRepository.updateExecutionStatus(
                  accountId, ambiance.getStageExecutionId(), Status.RUNNING.toString());
              stepPMSFacilitator.sendFacilitatorResponse(ambiance, Status.RUNNING);
              return builder.success(true).build(); // ack message
            }
          }
          log.info("Skipping capacity task for stageExecutionId: {} as capacityTask is already in progress.",
              ambiance.getStageExecutionId());
        } else {
          Long lastProcessed = existingMetadata.getCapacityTaskProcessedTime();
          long diffInMillis = currentTimeMillis - (lastProcessed != null ? lastProcessed : 0);
          log.info("Skipping capacity task for stageExecutionId: {} as it was processed {} ms ago.",
              ambiance.getStageExecutionId(), diffInMillis);
        }

        return builder.success(false).build(); // unack message for retry
      }

      // Lock acquired successfully! Proceed with capacity task
      log.info("Successfully acquired capacity task lock for stageExecutionId: {}", ambiance.getStageExecutionId());

      String taskId =
          executeCapacityTask(ambiance, infrastructure, QueueExecutionUtils.getStageVariables(stepParameters));
      createCICapacityNotifier(stepParameters, ambiance, dequeueResponse, taskId);
      // unack task
      builder.success(false);
    } catch (Exception ex) {
      log.warn("ci capacity task processing failed", ex);
      return builder.success(false).build();
    }
    return builder.build();
  }

  @Override
  public String getTopic() {
    return QueueExecutionUtils.getGlobalQueueTopic(ciExecutionServiceConfig.getQueueServiceClientConfig().getTopic());
  }

  private void createCICapacityNotifier(
      StepParameters stepParameters, Ambiance ambiance, DequeueResponse dequeueResponse, String taskId) {
    String stepParamString = RecastOrchestrationUtils.toJson(stepParameters);
    byte[] parameterBytes =
        stepParamString == null ? new byte[] {} : ByteString.copyFromUtf8(stepParamString).toByteArray();
    byte[] ambianceBytes = ambiance.toByteArray();
    String dequeueResponseString = RecastOrchestrationUtils.toJson(dequeueResponse);
    byte[] dequeueResponseBytes =
        dequeueResponseString == null ? new byte[] {} : ByteString.copyFromUtf8(dequeueResponseString).toByteArray();

    CICapacityNotifier ciCapacityTaskStatusNotifier = CICapacityNotifier.builder()
                                                          .ambianceBytes(ambianceBytes)
                                                          .waitId(taskId)
                                                          .stepParametersBytes(parameterBytes)
                                                          .dequeueResponseBytes(dequeueResponseBytes)
                                                          .build();
    asyncWaitEngine.waitForAllOn(ciCapacityTaskStatusNotifier, null, Arrays.asList(taskId), 0);
  }

  private String executeCapacityTask(Ambiance ambiance, Infrastructure infrastructure, Map<String, Object> variables) {
    CapacityReservationRequest capacityReservationRequest =
        getCapacityReservationRequest(infrastructure, variables, ambiance);

    if (capacityReservationRequest == null) {
      throw new CIStageExecutionException("Unsupported infrastructure type for capacity task");
    }

    String platformSelector = capacityReservationRequest.getPoolID();
    TaskSelector taskSelector = TaskSelector.newBuilder().setSelector(platformSelector).build();
    List<TaskSelector> taskSelectors = new ArrayList<>();
    taskSelectors.add(taskSelector);

    return submitCapacityTaskViaRunner(ambiance, infrastructure, capacityReservationRequest, taskSelectors);
  }

  private String submitCapacityTaskViaRunner(Ambiance ambiance, Infrastructure infrastructure,
      CapacityReservationRequest capacityReservationRequest, List<TaskSelector> taskSelectors) {
    RunnerRequest runnerRequest = null;
    if (infrastructure.getType() == Infrastructure.Type.HOSTED_VM) {
      runnerRequest = runnerRequestBuilder.buildCapacityRequestWithPoolSpec(
          ambiance, taskSelectors, capacityReservationRequest, null, getCapacityTaskTimeoutMillis());
    }
    return ciDelegateTaskExecutor.submitTask(runnerRequest);
  }

  private CapacityReservationRequest getCapacityReservationRequest(
      Infrastructure infrastructure, Map<String, Object> variables, Ambiance ambiance) {
    String accountID = AmbianceUtils.getAccountId(ambiance);
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) infrastructure;
    String moduleType = AmbianceUtils.getStageModuleType(ambiance);

    LicensesWithSummaryDTO licenseSummary =
        ciLicenseService.getLicenseSummary(accountID, moduleType, ambiance.getMetadata().getPrincipalInfo());
    boolean ciFreeLicense = licenseSummary != null && licenseSummary.getEdition() == Edition.FREE;
    Optional<String> resourceClass = ciLicenseUsageUtils.getResourceClass(accountID, hostedVmInfraYaml, ciFreeLicense);
    Optional<CloudRuntimeImageSpec> imageSpec = VmInitializeUtils.getImageSpec(hostedVmInfraYaml);
    boolean isNestedVirtualizationEnabled = VmInitializeUtils.getNestedVirtualizationEnabled(hostedVmInfraYaml);

    Pair<String, List<String>> poolInfo =
        vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(infrastructure, variables, ambiance);

    // Build tags and add FREE_CI_ATTR if needed
    Map<String, String> buildTags = new HashMap<>(vmInitializeUtils.getBuildTags(ambiance));
    if (ciFreeLicense) {
      buildTags.put(FREE_CI_ATTR, "true");
    }

    return CapacityReservationRequest.builder()
        .id(AmbianceUtils.getStageRuntimeIdAmbiance(ambiance))
        .tags(buildTags)
        .poolID(poolInfo.getLeft())
        .fallbackPoolIDs(poolInfo.getRight())
        .context(SetupVmRequest.Context.builder()
                     .accountID(accountID)
                     .orgID(AmbianceUtils.getOrgIdentifier(ambiance))
                     .projectID(AmbianceUtils.getProjectIdentifier(ambiance))
                     .pipelineID(AmbianceUtils.getPipelineIdentifier(ambiance))
                     .pipelineExecutionID(ambiance.getPlanExecutionId())
                     .runSequence(ambiance.getMetadata().getRunSequence())
                     .build())
        .timeout(getCapacityTaskTimeoutMillis())
        .resourceClass(resourceClass.orElse(null))
        .vmImageConfig(vmInitializeTaskParamsBuilder.getVMConfig(infrastructure, ambiance, imageSpec))
        .nestedVirtualization(isNestedVirtualizationEnabled)
        .build();
  }

  private long getCapacityTaskTimeoutMillis() {
    if (ciExecutionServiceConfig != null && ciExecutionServiceConfig.getGlobalQueueingConfig() != null
        && ciExecutionServiceConfig.getGlobalQueueingConfig().getCapacityTaskTimeoutMs() != null) {
      return ciExecutionServiceConfig.getGlobalQueueingConfig().getCapacityTaskTimeoutMs();
    }
    return DEFAULT_CAPACITY_TASK_TIMEOUT_MS;
  }

  private long getCapacityTaskMaxWaitTimeMillis() {
    if (ciExecutionServiceConfig != null && ciExecutionServiceConfig.getGlobalQueueingConfig() != null
        && ciExecutionServiceConfig.getGlobalQueueingConfig().getCapacityTaskMaxWaitTimeMs() != null) {
      return ciExecutionServiceConfig.getGlobalQueueingConfig().getCapacityTaskMaxWaitTimeMs();
    }
    return DEFAULT_CAPACITY_TASK_MAX_WAIT_TIME_MS;
  }
}