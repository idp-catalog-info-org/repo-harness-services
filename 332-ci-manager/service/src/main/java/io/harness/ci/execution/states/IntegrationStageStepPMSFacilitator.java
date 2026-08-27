/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.beans.steps.StepSpecTypeConstants.INTEGRATIONSTAGESTEPPMS_FACILITATOR;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.CIInitTaskArgs;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.enforcement.CIBuildEnforcer;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.execution.integrationstage.VmInitializeTaskParamsBuilder;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.execution.events.node.facilitate.response.Facilitator;
import io.harness.pms.sdk.core.execution.events.node.facilitate.response.FacilitatorResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIExecutionRepository;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class IntegrationStageStepPMSFacilitator implements Facilitator {
  public static final FacilitatorType FACILITATOR_TYPE =
      FacilitatorType.newBuilder().setType(INTEGRATIONSTAGESTEPPMS_FACILITATOR).build();

  @Inject private SdkNodeExecutionService sdkNodeExecutionService;
  @Inject QueueExecutionUtils queueExecutionUtils;
  @Inject CIBuildEnforcer buildEnforcer;
  @Inject private HsqsClientService hsqsClientService;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject CIExecutionRepository ciExecutionRepository;
  @Inject CIFeatureFlagService featureFlagService;
  @Inject CILicenseService ciLicenseService;
  @Inject VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;

  @Override
  public FacilitatorResponse facilitate(
      Ambiance ambiance, StepParameters stepParameters, byte[] parameters, StepInputPackage inputPackage) {
    String moduleType = AmbianceUtils.getStageModuleType(ambiance);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    Infrastructure infrastructure = QueueExecutionUtils.getInfrastructure(stepParameters);

    // Validate the hosted platform before any queueing so that a platform the account cannot use fails the stage
    // immediately instead of waiting for capacity and only failing once the capacity task is picked up.
    vmInitializeTaskParamsBuilder.validateHostedPlatform(infrastructure, accountId);

    ParameterField<Timeout> stageTimeout = QueueExecutionUtils.getStageTimeout(stepParameters);
    Long stageTimeoutSeconds = IntegrationStageUtils.getStageTimeOut(accountId, featureFlagService, ciLicenseService,
        ambiance.getStageExecutionId(), stageTimeout, infrastructure, ambiance.getMetadata().getPrincipalInfo());

    queueExecutionUtils.addExecutionRecord(
        infrastructure, accountId, ambiance.getStageExecutionId(), stageTimeoutSeconds);

    boolean shouldQueue = false;
    boolean queueConcurrencyEnabled = (infrastructure.getType() == Infrastructure.Type.HOSTED_VM);

    // only check if queue is enabled
    if (queueConcurrencyEnabled) {
      shouldQueue =
          buildEnforcer.shouldQueue(accountId, infrastructure, moduleType, ambiance.getMetadata().getPrincipalInfo());
    }

    if (shouldQueue) {
      String topic = ciExecutionServiceConfig.getQueueServiceClientConfig().getTopic();
      log.info("start IntegrationStageStepPMSFacilitator for initialize step with queue. Topic: {}", topic);
      String payload = RecastOrchestrationUtils.toJson(
          CIInitTaskArgs.builder().ambiance(ambiance).stepParameters(stepParameters).version("v2").build());
      EnqueueRequest enqueueRequest =
          EnqueueRequest.builder().topic(topic).subTopic(accountId).producerName(topic).payload(payload).build();
      try {
        EnqueueResponse execute = hsqsClientService.enqueue(enqueueRequest);
        log.info("build queued. message id: {}", execute.getItemId());
        if (StringUtils.isNotBlank(execute.getItemId())) {
          ciExecutionRepository.updateQueueId(
              accountId, ambiance.getStageExecutionId(), execute.getItemId(), topic, accountId);
        }
      } catch (Exception e) {
        log.info("failed to queue build", e);
        throw new CIStageExecutionException(format("failed to process execution, queuing failed. runtime Id: {}",
            AmbianceUtils.getStageRuntimeIdAmbiance(ambiance)));
      }
      return FacilitatorResponse.builder()
          .executionMode(ExecutionMode.CHILD)
          .status(Status.QUEUED_LICENSE_LIMIT_REACHED)
          .build();
    } else {
      if (queueExecutionUtils.isGlobalQueueEnabled(ambiance, infrastructure)) {
        enqueueBuild(ambiance, stepParameters);
        return FacilitatorResponse.builder()
            .executionMode(ExecutionMode.CHILD)
            .status(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED)
            .build();
      }
      CIExecutionMetadata ciExecutionMetadata = ciExecutionRepository.updateExecutionStatus(
          AmbianceUtils.getAccountId(ambiance), ambiance.getStageExecutionId(), Status.RUNNING.toString());
      if (ciExecutionMetadata == null) {
        throw new CIStageExecutionException(
            format("Failed to process execution as ciExecutionMetadata is null for stageExecutionId Id: %s , It "
                    + "generally happens for aborted executions",
                ambiance.getStageExecutionId()));
      }
    }
    return FacilitatorResponse.builder().executionMode(ExecutionMode.CHILD).status(Status.RUNNING).build();
  }

  public void sendFacilitatorResponse(Ambiance ambiance, Status status) {
    sdkNodeExecutionService.handleFacilitationResponse(ambiance, "",
        FacilitatorResponseProto.newBuilder()
            .setExecutionMode(ExecutionMode.CHILD)
            .setIsSuccessful(true)
            .setStatus(status)
            .build());
  }

  public void enqueueBuild(Ambiance ambiance, StepParameters stepParameters) {
    String payload = RecastOrchestrationUtils.toJson(
        CIInitTaskArgs.builder().ambiance(ambiance).stepParameters(stepParameters).build());
    String topic =
        QueueExecutionUtils.getGlobalQueueTopic(ciExecutionServiceConfig.getQueueServiceClientConfig().getTopic());
    String subTopic = queueExecutionUtils.getGlobalQueueSubTopic(ambiance, stepParameters);
    EnqueueRequest request = EnqueueRequest.builder()
                                 .topic(topic)
                                 .subTopic(subTopic)
                                 .producerName(ciExecutionServiceConfig.getQueueServiceClientConfig().getTopic())
                                 .payload(payload)
                                 .build();

    try {
      EnqueueResponse response = hsqsClientService.enqueue(request);
      log.info("Build queued successfully. MessageId={}", response.getItemId());
      if (StringUtils.isNotBlank(response.getItemId())) {
        ciExecutionRepository.updateQueueId(AmbianceUtils.getAccountId(ambiance), ambiance.getStageExecutionId(),
            response.getItemId(), topic, subTopic);
      }
      ciExecutionRepository.updateExecutionStatus(AmbianceUtils.getAccountId(ambiance), ambiance.getStageExecutionId(),
          Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString());
      queueExecutionUtils.publishQueueCountMetrics(ambiance, QueueExecutionUtils.getInfrastructure(stepParameters));
    } catch (Exception e) {
      log.error("Failed to queue build for stageExecutionId, letting it proceed {}: {}", ambiance.getStageExecutionId(),
          e.getMessage(), e);
      sendFacilitatorResponse(ambiance, Status.RUNNING);
    }
  }
}