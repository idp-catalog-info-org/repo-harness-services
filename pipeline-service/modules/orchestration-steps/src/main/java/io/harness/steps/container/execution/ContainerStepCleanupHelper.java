/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.execution;

import static io.harness.beans.FeatureName.CI_K8CLEANUP_DEFAULT_GRACE_PERIOD;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_PORT;
import static io.harness.common.ParameterFieldHelper.getParameterFieldFinalValueString;
import static io.harness.steps.StepUtils.buildAbstractions;
import static io.harness.steps.container.constants.ContainerStepExecutionConstants.CLEANUP_DETAILS;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.sweepingoutputs.EcsStageInfraDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.ci.ecs.CIECSCleanupTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8CleanupTaskParams;
import io.harness.delegate.beans.ci.pod.CICommonConstants;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.taskparams.CIVmCleanupTaskParams;
import io.harness.encryption.Scope;
import io.harness.engine.pms.data.ResolverUtils;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.runner.request.builder.RunnerRequestBuilder;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.StepUtils;
import io.harness.steps.container.utils.ConnectorUtils;
import io.harness.steps.plugin.infrastructure.ContainerCleanupDetails;
import io.harness.steps.plugin.infrastructure.ContainerInfraYamlSpec;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.utils.PmsFeatureFlagService;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepCleanupHelper {
  @Inject DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ConnectorUtils connectorUtils;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject LogStreamingStepClientFactory logStreamingClient;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private OutcomeService outcomeService;
  @Inject ContainerExecutionConfig containerExecutionConfig;
  @Inject private RunnerRequestBuilder runnerRequestBuilder;
  private final int MAX_ATTEMPTS = 3;

  public void sendCleanupRequest(Ambiance ambiance) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    try {
      RetryPolicy<Object> retryPolicy = getRetryPolicy();

      Failsafe.with(retryPolicy).run(() -> {
        Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
        closeLogStream(ambiance);
        ContainerCleanupDetails podCleanupDetails = null;
        OptionalSweepingOutput sweepingOutputs = executionSweepingOutputService.resolveOptional(ambiance,
            RefObject.newBuilder()
                .setName(CLEANUP_DETAILS)
                .setLevelRuntimeIdx(ResolverUtils.prepareLevelRuntimeIdIdx(ambiance.getLevelsList()))
                .build());
        if (!sweepingOutputs.isFound()) {
          return;
        } else {
          ExecutionSweepingOutput podCleanupDetailsOutput = sweepingOutputs.getOutput();
          if (podCleanupDetailsOutput instanceof ContainerCleanupDetails) {
            podCleanupDetails = (ContainerCleanupDetails) podCleanupDetailsOutput;
          }
        }

        if (podCleanupDetails == null) {
          return;
        }

        CICleanupTaskParams ciCleanupTaskParams;

        if (podCleanupDetails.getInfrastructure().getType() == ContainerStepInfra.Type.KUBERNETES_DIRECT) {
          ciCleanupTaskParams = buildK8CleanupParameters(ambiance, podCleanupDetails);
        } else if (podCleanupDetails.getInfrastructure().getType() == ContainerStepInfra.Type.ECS_DIRECT) {
          ciCleanupTaskParams = buildEcsCleanupParameters(podCleanupDetails);
        } else {
          ciCleanupTaskParams = buildVmCleanupParameters(ambiance, podCleanupDetails);
        }

        log.info("Received event to clean planExecutionId {}, level Id {}", ambiance.getPlanExecutionId(),
            level.getIdentifier());

        DelegateTaskRequest delegateTaskRequest = getDelegateCleanupTaskRequest(
            ambiance, ciCleanupTaskParams, accountId, podCleanupDetails.getDelegateSelectors());

        // Check if cleanup should be routed to runner (matching CI's StageCleanupUtility pattern)
        OptionalSweepingOutput stageInfraOutput = executionSweepingOutputService.resolveOptional(
            ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS));
        boolean routeToRunner = false;
        StageInfraDetails stageInfraDetails = null;
        if (stageInfraOutput.isFound()) {
          stageInfraDetails = (StageInfraDetails) stageInfraOutput.getOutput();
          routeToRunner = stageInfraDetails.shouldRouteStageToRunner();
        }

        String taskId;
        if (routeToRunner) {
          // Route cleanup to runner if initialization was done via runner
          if ((stageInfraDetails instanceof K8StageInfraDetails || stageInfraDetails instanceof EcsStageInfraDetails)
              && EmptyPredicate.isNotEmpty(stageInfraDetails.getTransactionId())) {
            taskId = delegateGrpcClientWrapper
                         .submitScheduleTask(runnerRequestBuilder.buildCleanupRequestV1(
                             ambiance, stageInfraDetails, delegateTaskRequest))
                         .taskId();
          } else {
            String cleanupLogKey = generateCleanupLogKey(ambiance);
            taskId = delegateGrpcClientWrapper.submit(runnerRequestBuilder.buildCleanupRequest(
                ambiance, stageInfraDetails, cleanupLogKey, delegateTaskRequest));
          }
          log.info("Submitted cleanup request to runner with taskId {} for planExecutionId {}, stage {}", taskId,
              ambiance.getPlanExecutionId(), level.getIdentifier());
        } else {
          // Use traditional delegate path
          taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
          log.info("Submitted cleanup request with taskId {} for planExecutionId {}, stage {}", taskId,
              ambiance.getPlanExecutionId(), level.getIdentifier());
          log.info("Eligible delegate sorted list: {}", podCleanupDetails.getDelegateSelectors());
        }
      });
    } catch (Exception ex) {
      log.error("Failed to send cleanup call for plan {}", ambiance.getPlanExecutionId(), ex);
    }
  }

  private RetryPolicy<Object> getRetryPolicy() {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withBackoff(5, 60, ChronoUnit.SECONDS)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event
            -> log.info(
                "[Retrying failed call to clean pod attempt: {}", event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event
            -> log.error("Failed to clean pod after retrying {} times", event.getAttemptCount(), event.getFailure()));
  }

  public CIVmCleanupTaskParams buildVmCleanupParameters(
      Ambiance ambiance, ContainerCleanupDetails containerCleanupDetails) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS));
    VmStageInfraDetails vmStageInfraDetails = null;
    if (optionalSweepingOutput.isFound()) {
      StageInfraDetails stageInfraDetails = (StageInfraDetails) optionalSweepingOutput.getOutput();
      vmStageInfraDetails = (VmStageInfraDetails) stageInfraDetails;
    }

    return CIVmCleanupTaskParams.builder()
        .stageRuntimeId(containerCleanupDetails.getStepGroupRuntimeId())
        .poolId(vmStageInfraDetails.getPoolId())
        .infraInfo(vmStageInfraDetails.getInfraInfo())
        .build();
  }

  private CIECSCleanupTaskParams buildEcsCleanupParameters(ContainerCleanupDetails podDetails) {
    return CIECSCleanupTaskParams.builder().taskName(podDetails.getPodName()).build();
  }

  public CIK8CleanupTaskParams buildK8CleanupParameters(Ambiance ambiance, ContainerCleanupDetails podDetails) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ContainerInfraYamlSpec containerInfraYamlSpec = ((ContainerK8sInfra) podDetails.getInfrastructure()).getSpec();
    String clusterConnectorRef = containerInfraYamlSpec.getConnectorRef().getValue();
    String namespace = getParameterFieldFinalValueString(containerInfraYamlSpec.getNamespace());
    final List<String> podNames = new ArrayList<>();
    podNames.add(podDetails.getPodName());

    boolean useSocketCapability = pmsFeatureFlagService.isEnabled(
        ngAccess.getAccountIdentifier(), FeatureName.CDS_K8S_SOCKET_CAPABILITY_CHECK_NG);

    // nested step group case is handled in CLEANUP_DETAILS, hence directly fetching
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME));

    String liteEngineIp = null;
    if (optionalOutcome.isFound()) {
      LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome =
          (LiteEnginePodDetailsOutcome) optionalOutcome.getOutcome();
      if (liteEnginePodDetailsOutcome != null) {
        liteEngineIp = liteEnginePodDetailsOutcome.getIpAddress();
      }
    }

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, clusterConnectorRef);

    boolean useDefaultGracePeriod = false;
    if (pmsFeatureFlagService.isEnabled(ngAccess.getAccountIdentifier(), CI_K8CLEANUP_DEFAULT_GRACE_PERIOD)) {
      useDefaultGracePeriod = true;
    }

    return CIK8CleanupTaskParams.builder()
        .k8sConnector(connectorDetails)
        .cleanupContainerNames(podDetails.getCleanUpContainerNames())
        .namespace(namespace)
        .podNameList(podNames)
        .LiteEnginePort(LITE_ENGINE_PORT)
        .isLocal(containerExecutionConfig.isLocal())
        .LiteEngineIP(liteEngineIp)
        .serviceNameList(new ArrayList<>())
        .useDefaultGracePeriod(useDefaultGracePeriod)
        .useSocketCapability(useSocketCapability)
        .build();
  }

  private DelegateTaskRequest getDelegateCleanupTaskRequest(Ambiance ambiance, CICleanupTaskParams ciCleanupTaskParams,
      String accountId, List<TaskSelector> delegateSelectors) {
    Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);
    String taskType = TaskType.CONTAINER_CLEANUP.name();
    SerializationFormat serializationFormat = SerializationFormat.KRYO;

    return DelegateTaskRequest.builder()
        .accountId(accountId)
        .selectors(delegateSelectors)
        .executeOnHarnessHostedDelegates(false)
        .eligibleToExecuteDelegateIds(new ArrayList<>())
        .taskSetupAbstractions(abstractions)
        .executionTimeout(java.time.Duration.ofSeconds(900))
        .taskType(taskType)
        .serializationFormat(serializationFormat)
        .taskParameters(ciCleanupTaskParams)
        .taskDescription("Cleanup pod task")
        .build();
  }

  public void closeLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingClient.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeAllOpenStreamsWithPrefix(StepUtils.generateLogKeys(ambiance, emptyList()).get(0));
  }

  private String generateCleanupLogKey(Ambiance ambiance) {
    // Generate cleanup log key similar to CI's pattern
    String baseLogKey = LogStreamingStepClientFactory.getLogBaseKey(ambiance);
    return baseLogKey + "/" + CICommonConstants.LITE_ENGINE_LOG_KEY_SUFFIX;
  }
}