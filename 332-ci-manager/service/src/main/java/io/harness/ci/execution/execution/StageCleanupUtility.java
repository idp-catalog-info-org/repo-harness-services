/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.beans.FeatureName.CI_ENABLE_CAPABILITY_CHECK_INIT_CLEANUP;
import static io.harness.beans.FeatureName.CI_K8CLEANUP_DEFAULT_GRACE_PERIOD;
import static io.harness.beans.FeatureName.CI_SKIP_CLOUD_VM_CLEANUP;
import static io.harness.beans.sweepingoutputs.PodCleanupDetails.CLEANUP_DETAILS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_PORT;
import static io.harness.common.ParameterFieldHelper.getParameterFieldFinalValueString;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;
import static io.harness.steps.StepUtils.buildAbstractions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.dto.CITaskDetails;
import io.harness.app.beans.entities.CIResourceCleanup;
import io.harness.app.beans.entities.CIResourceCleanup.CIResourceCleanupResponseKeys;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.PodCleanupDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.utils.ci.CIStepInfoUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.ff.CIFeatureFlagTarget;
import io.harness.ci.metrics.helper.CIMetricsHelper;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8CleanupTaskParams;
import io.harness.delegate.beans.ci.pod.CICommonConstants;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmCleanupTaskParams;
import io.harness.delegate.beans.ci.vm.taskparams.CIVmCleanupTaskParams;
import io.harness.encryption.Scope;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.logging.AutoLogContext;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.NGAccess;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.repositories.CITaskDetailsRepository;
import io.harness.runner.request.builder.RunnerRequestBuilder;
import io.harness.runnercommons.logging.TransactionalTaskLogContext;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CI)
public class StageCleanupUtility {
  public static final String CLEANUP = "cleanup";
  private static final String SUPPORT_BUNDLE_STAGE_VAR = "HARNESS_CI_SUPPORT_BUNDLE_ENABLED";
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private OutcomeService outcomeService;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private CITaskDetailsRepository ciTaskDetailsRepository;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private HPersistence persistence;
  @Inject(optional = true) @Nullable private CIFeatureFlagService featureFlagService;
  @Inject private RunnerRequestBuilder runnerRequestBuilder;
  @Inject private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  private final int WAIT_TIME_IN_SECOND = 30;

  public CleanupSubmitResult submitCleanupRequest(Ambiance ambiance, String stageIdentifier)
      throws InterruptedException {
    return submitCleanupRequest(ambiance, stageIdentifier, false);
  }

  /**
   * @return whether a cleanup task was actually submitted, plus the infra type already loaded for that cleanup. False
   *     {@code submitted} means cleanup was intentionally skipped (e.g. CI_SKIP_CLOUD_VM_CLEANUP).
   */
  public CleanupSubmitResult submitCleanupRequest(
      Ambiance ambiance, String stageIdentifier, boolean alreadyDeferredByFF) throws InterruptedException {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS));
    boolean routeToRunner = false;
    boolean initWithUnifiedAPI = false;
    StageInfraDetails stageInfraDetails = null;
    if (optionalSweepingOutput.isFound()) {
      stageInfraDetails = (StageInfraDetails) optionalSweepingOutput.getOutput();
      routeToRunner = stageInfraDetails.shouldRouteStageToRunner();
      initWithUnifiedAPI = stageInfraDetails.isInitWithUnifiedAPI();
    }
    if (shouldSkipCloudVmCleanup(ambiance, stageInfraDetails, stageIdentifier, alreadyDeferredByFF)) {
      return new CleanupSubmitResult(false, CIMetricsHelper.infraTypeFrom(stageInfraDetails));
    }
    String taskId;
    Pair<CICleanupTaskParams, StageInfraDetails> cleanupParams =
        buildAndfetchCleanUpParameters(ambiance, optionalSweepingOutput);
    DelegateTaskRequest delegateTaskRequest =
        getDelegateCleanupTaskRequest(ambiance, AmbianceUtils.getAccountId(ambiance), cleanupParams);
    StageInfraDetails effectiveStageInfraDetails =
        cleanupParams.getRight() != null ? cleanupParams.getRight() : stageInfraDetails;
    StageInfraDetails.Type infraType = stageInfraDetails != null ? stageInfraDetails.getType() : null;
    boolean isUnifiedRunner = false;
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion()) || routeToRunner || initWithUnifiedAPI) {
      isUnifiedRunner = true;
      if (stageInfraDetails.getTransactionId() != null
          && (stageInfraDetails instanceof VmStageInfraDetails
              || (stageInfraDetails instanceof K8StageInfraDetails
                  && featureFlagService.isEnabled(
                      FeatureName.PL_RUNNER_K8S_INFRA_USE_SCHEDULED_TASK_API, AmbianceUtils.getAccountId(ambiance))))) {
        // Submit cleanup request for Delegate 3.x (Runner) via the newer Scheduled Task API
        taskId = delegateGrpcClientWrapper
                     .submitScheduleTask(
                         runnerRequestBuilder.buildCleanupRequestV1(ambiance, stageInfraDetails, delegateTaskRequest))
                     .taskId();
      } else {
        taskId = delegateGrpcClientWrapper.submit(runnerRequestBuilder.buildCleanupRequest(
            ambiance, stageInfraDetails, generateCleanupLogKey(ambiance), delegateTaskRequest));
      }
    } else {
      taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
    }
    // memoryMetrics should be set only for DLITE_VM/Cloud and VM infra
    boolean supportsMemoryMetrics =
        infraType == StageInfraDetails.Type.DLITE_VM || infraType == StageInfraDetails.Type.VM;
    String memoryMetricsLogKey = supportsMemoryMetrics ? generateMemoryMetricsLogKey(ambiance) : null;
    waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION,
        CICleanupTaskNotifyCallback.builder()
            .stageExecutionID(ambiance.getStageExecutionId())
            .planExecutionID(ambiance.getPlanExecutionId())
            .accountID(AmbianceUtils.getAccountId(ambiance))
            .leLogKey(generateCleanupLogKey(ambiance))
            .memoryMetricsLogKey(memoryMetricsLogKey)
            .build(),
        taskId);
    try (AutoLogContext ignore = new TransactionalTaskLogContext(isUnifiedRunner, taskId, CLEANUP,
             ambiance.getStageExecutionId(), infraType != null ? infraType.toString() : null,
             ambiance.getPlanExecutionId(), AmbianceUtils.getAccountId(ambiance), null, null)) {
      log.info("Submitted cleanup request with taskId {} for planExecutionId {}, stage {}", taskId,
          ambiance.getPlanExecutionId(), stageIdentifier);
    }
    return new CleanupSubmitResult(true, CIMetricsHelper.infraTypeFrom(effectiveStageInfraDetails));
  }

  @Value
  public static class CleanupSubmitResult {
    boolean submitted;
    String infraType;
  }

  @VisibleForTesting
  boolean shouldSkipCloudVmCleanup(
      Ambiance ambiance, StageInfraDetails stageInfraDetails, String stageIdentifier, boolean alreadyDeferredByFF) {
    if (featureFlagService == null || stageInfraDetails == null
        || stageInfraDetails.getType() != StageInfraDetails.Type.DLITE_VM) {
      return false;
    }
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String pipelineIdentifier = ambiance.getMetadata() != null ? ambiance.getMetadata().getPipelineIdentifier() : null;
    CIFeatureFlagTarget target = CIFeatureFlagTarget.builder()
                                     .accountId(accountId)
                                     .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
                                     .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
                                     .pipelineIdentifier(pipelineIdentifier)
                                     .stageIdentifier(stageIdentifier)
                                     .build();
    if (!featureFlagService.isEnabledForTarget(CI_SKIP_CLOUD_VM_CLEANUP, target)) {
      return false;
    }
    if (alreadyDeferredByFF) {
      log.warn("Deferral window elapsed; dispatching cleanup despite FF still set for accountId {}, "
              + "pipelineId {}, stageExecutionId {}",
          accountId, pipelineIdentifier, ambiance.getStageExecutionId());
      return false;
    }
    log.warn("Skipping cloud VM cleanup for accountId {}, pipelineId {}, planExecutionId {}, "
            + "stageExecutionId {}, stage {}",
        accountId, pipelineIdentifier, ambiance.getPlanExecutionId(), ambiance.getStageExecutionId(), stageIdentifier);
    markCleanupRecordDeferred(ambiance);
    return true;
  }

  private static final Duration DEFERRED_CLEANUP_DELAY = Duration.ofDays(2);

  private void markCleanupRecordDeferred(Ambiance ambiance) {
    try {
      long deferredUntilMillis = System.currentTimeMillis() + DEFERRED_CLEANUP_DELAY.toMillis();
      Date extendedValidUntil = new Date(deferredUntilMillis);
      Query<CIResourceCleanup> query =
          persistence.createQuery(CIResourceCleanup.class, excludeAuthority)
              .filter(CIResourceCleanupResponseKeys.stageExecutionId, ambiance.getStageExecutionId());
      query.field(CIResourceCleanupResponseKeys.deferredByFF).notEqual(true);
      persistence.update(query,
          persistence.createUpdateOperations(CIResourceCleanup.class)
              .set(CIResourceCleanupResponseKeys.deferredByFF, true)
              .set(CIResourceCleanupResponseKeys.validUntil, extendedValidUntil)
              .set(CIResourceCleanupResponseKeys.processAfter, deferredUntilMillis));
    } catch (Exception ex) {
      log.warn("Failed to mark CIResourceCleanup row as deferredByFF for stageExecutionId {}",
          ambiance.getStageExecutionId(), ex);
    }
  }

  @VisibleForTesting
  public Pair<CICleanupTaskParams, StageInfraDetails> buildAndfetchCleanUpParameters(
      Ambiance ambiance, OptionalSweepingOutput stageInfraDetailsSweepingOutput) {
    StageInfraDetails stageInfraDetails;
    if (!stageInfraDetailsSweepingOutput.isFound()) {
      // At upgrade time, stage infra sweeping output may not be set.
      OptionalSweepingOutput optionalCleanupSweepingOutput = executionSweepingOutputResolver.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(CLEANUP_DETAILS));
      if (!optionalCleanupSweepingOutput.isFound()) {
        log.warn("Sweeping Output PodCleanupDetails is not set, unable to do cleanup since pod might not be created");
        throw new CIStageExecutionException("Unable to do cleanup as PodCleanupDetails was not set");
      } else {
        PodCleanupDetails podCleanupDetails = (PodCleanupDetails) optionalCleanupSweepingOutput.getOutput();
        stageInfraDetails = K8StageInfraDetails.builder()
                                .infrastructure(podCleanupDetails.getInfrastructure())
                                .podName(podCleanupDetails.getPodName())
                                .containerNames(podCleanupDetails.getCleanUpContainerNames())
                                .build();
      }
    } else {
      stageInfraDetails = (StageInfraDetails) stageInfraDetailsSweepingOutput.getOutput();
    }

    CICleanupTaskParams ciCleanupTaskParams;
    StageInfraDetails.Type type = stageInfraDetails.getType();
    if (type == StageInfraDetails.Type.K8) {
      K8StageInfraDetails k8StageInfraDetails = (K8StageInfraDetails) stageInfraDetails;
      ciCleanupTaskParams = buildK8CleanupParameters(k8StageInfraDetails, ambiance);
    } else if (type == StageInfraDetails.Type.VM) {
      VmStageInfraDetails vmStageInfraDetails = (VmStageInfraDetails) stageInfraDetails;
      ciCleanupTaskParams = buildVmCleanupParameters(ambiance, vmStageInfraDetails);
    } else if (stageInfraDetails.getType() == StageInfraDetails.Type.DLITE_VM) {
      DliteVmStageInfraDetails dliteVmStageInfraDetails = (DliteVmStageInfraDetails) stageInfraDetails;
      ciCleanupTaskParams = buildHostedVmCleanupParameters(ambiance, dliteVmStageInfraDetails);
    } else {
      throw new CIStageExecutionException("Unknown infra type");
    }
    return Pair.of(ciCleanupTaskParams, stageInfraDetails);
  }

  private CIK8CleanupTaskParams buildK8CleanupParameters(K8StageInfraDetails k8StageInfraDetails, Ambiance ambiance) {
    Infrastructure infrastructure = k8StageInfraDetails.getInfrastructure();

    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    String clusterConnectorRef;
    String namespace;
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
      K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) infrastructure;
      clusterConnectorRef = k8sDirectInfraYaml.getSpec().getConnectorRef().getValue();
      namespace = getParameterFieldFinalValueString(k8sDirectInfraYaml.getSpec().getNamespace());
      if (namespace != null) {
        namespace = namespace.replaceAll("\\s+", "");
      }
    } else {
      throw new CIStageExecutionException("Infra type:" + infrastructure.getType().name() + "is not of k8s type");
    }

    final List<String> podNames = new ArrayList<>();
    podNames.add(k8StageInfraDetails.getPodName());

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, clusterConnectorRef);

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
    boolean useDefaultGracePeriod = false;
    if (featureFlagService.isEnabled(CI_K8CLEANUP_DEFAULT_GRACE_PERIOD, ngAccess.getAccountIdentifier())) {
      useDefaultGracePeriod = true;
    }

    // When support bundle collection is enabled, give the LE 90s to collect K8s diagnostics
    // and upload to GCS before K8s force-kills the pod. Enabled when EITHER the account-level FF
    // OR the stage-level opt-in variable is set (OR gating), mirroring the lite-engine.
    long gracePeriodSeconds = 0;
    boolean supportBundleFFEnabled =
        featureFlagService.isEnabled(FeatureName.CI_SUPPORT_BUNDLE_COLLECTION, ngAccess.getAccountIdentifier());
    boolean supportBundleStageVarEnabled =
        CIStepInfoUtils.checkStageVarState(k8StageInfraDetails.getVariables(), SUPPORT_BUNDLE_STAGE_VAR, "true");
    boolean supportBundleEnabled = supportBundleFFEnabled || supportBundleStageVarEnabled;
    if (supportBundleEnabled) {
      gracePeriodSeconds = 90;
    }
    log.info("Support bundle: cleanup for pod {} with gracePeriodSeconds={} (FF CI_SUPPORT_BUNDLE_COLLECTION={}, "
            + "stageVar HARNESS_CI_SUPPORT_BUNDLE_ENABLED={})",
        podNames, gracePeriodSeconds, supportBundleFFEnabled, supportBundleStageVarEnabled);

    boolean useCapabilityCheckInIPAbsence = false;
    if (featureFlagService.isEnabled(CI_ENABLE_CAPABILITY_CHECK_INIT_CLEANUP, ngAccess.getAccountIdentifier())) {
      useCapabilityCheckInIPAbsence = true;
    }

    return CIK8CleanupTaskParams.builder()
        .k8sConnector(connectorDetails)
        .cleanupContainerNames(k8StageInfraDetails.getContainerNames())
        .namespace(namespace)
        .podNameList(podNames)
        .useDefaultGracePeriod(useDefaultGracePeriod)
        .gracePeriodSeconds(gracePeriodSeconds)
        .useCapabilityCheckInIPAbsence(useCapabilityCheckInIPAbsence)
        .LiteEnginePort(LITE_ENGINE_PORT)
        .isLocal(ciExecutionServiceConfig.isLocal())
        .LiteEngineIP(liteEngineIp)
        .serviceNameList(new ArrayList<>())
        .build();
  }

  private CIVmCleanupTaskParams buildVmCleanupParameters(Ambiance ambiance, VmStageInfraDetails vmStageInfraDetails) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Unable to fetch stage details. Please retry or verify pipeline yaml");
    }

    StageDetails stageDetails = (StageDetails) optionalSweepingOutput.getOutput();

    return CIVmCleanupTaskParams.builder()
        .stageRuntimeId(stageDetails.getStageRuntimeID())
        .poolId(vmStageInfraDetails.getPoolId())
        .infraInfo(vmStageInfraDetails.getInfraInfo())
        .build();
  }

  private DliteVmCleanupTaskParams buildHostedVmCleanupParameters(
      Ambiance ambiance, DliteVmStageInfraDetails stageInfraDetails) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Unable to fetch stage details. Please retry or verify pipeline yaml");
    }

    String liteEngineLogKey = generateCleanupLogKey(ambiance);

    StageDetails stageDetails = (StageDetails) optionalSweepingOutput.getOutput();
    return DliteVmCleanupTaskParams.builder()
        .stageRuntimeId(stageDetails.getStageRuntimeID())
        .poolId(stageInfraDetails.getPoolId())
        .logKey(liteEngineLogKey)
        .context(DliteVmCleanupTaskParams.Context.builder()
                     .accountID(AmbianceUtils.getAccountId(ambiance))
                     .orgID(AmbianceUtils.getOrgIdentifier(ambiance))
                     .projectID(AmbianceUtils.getProjectIdentifier(ambiance))
                     .pipelineID(AmbianceUtils.getPipelineIdentifier(ambiance))
                     .runSequence(ambiance.getMetadata().getRunSequence())
                     .build())
        .build();
  }

  private String generateCleanupLogKey(Ambiance ambiance) {
    String baseLogKey = LogStreamingStepClientFactory.getLogBaseKey(ambiance);
    return baseLogKey + "/" + CICommonConstants.LITE_ENGINE_LOG_KEY_SUFFIX;
  }

  private String generateMemoryMetricsLogKey(Ambiance ambiance) {
    String baseLogKey = LogStreamingStepClientFactory.getLogBaseKey(ambiance);
    return baseLogKey + "/" + CICommonConstants.MEMORY_METRICS_LOG_KEY_SUFFIX;
  }

  private DelegateTaskRequest getDelegateCleanupTaskRequest(Ambiance ambiance, String accountId,
      Pair<CICleanupTaskParams, StageInfraDetails> cleanupParams) throws InterruptedException {
    List<TaskSelector> taskSelectors = fetchDelegateSelector(ambiance);

    Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);
    String taskType = "CI_CLEANUP";
    SerializationFormat serializationFormat = SerializationFormat.KRYO;
    boolean executeOnHarnessHostedDelegates = false;
    String stageId = ambiance.getStageExecutionId();
    List<String> eligibleToExecuteDelegateIds = new ArrayList<>();

    CICleanupTaskParams ciCleanupTaskParams = cleanupParams.getLeft();
    StageInfraDetails stageInfraDetails = cleanupParams.getRight();
    CICleanupTaskParams.Type type = ciCleanupTaskParams.getType();
    if (type == CICleanupTaskParams.Type.DLITE_VM) {
      DliteVmStageInfraDetails dliteVmStageInfraDetails = (DliteVmStageInfraDetails) stageInfraDetails;
      DliteVmCleanupTaskParams dliteVmCleanupTaskParams = (DliteVmCleanupTaskParams) ciCleanupTaskParams;
      taskType = TaskType.DLITE_CI_VM_CLEANUP_TASK.getDisplayName();
      executeOnHarnessHostedDelegates = true;
      serializationFormat = SerializationFormat.JSON;

      if (dliteVmStageInfraDetails.isDistributed()) {
        taskType = TaskType.DLITE_CI_VM_CLEANUP_TASK_V2.getDisplayName();
        dliteVmCleanupTaskParams.setDistributed(true);
      } else {
        String delegateId = fetchDelegateId(ambiance);
        if (isNotEmpty(delegateId)) {
          eligibleToExecuteDelegateIds.add(delegateId);
          ciTaskDetailsRepository.deleteFirstByStageExecutionId(stageId);
        } else {
          log.warn("Unable to locate delegate ID for stage ID: {}. Cleanup task may be routed to the wrong delegate",
              stageId);
        }
      }
    }
    // Since we use a same class to handle both VM and DOCKER cases due to they share a lot of similarities in
    // processing logic, and we use a CICleanupTaskParams type name `VM` to represent them. Only docker scenario
    // needs additional step to add matching docker delegate id into the eligible to execute delegate id list.
    else if (type == CICleanupTaskParams.Type.VM) {
      if (((CIVmCleanupTaskParams) ciCleanupTaskParams).getInfraInfo() == CIInitializeTaskParams.Type.DOCKER) {
        // TODO: Start using fetchDelegateId once we start emitting & processing the event for Docker as well
        OptionalOutcome optionalOutput = outcomeService.resolveOptional(
            ambiance, RefObjectUtils.getOutcomeRefObject(VmDetailsOutcome.VM_DETAILS_OUTCOME));
        VmDetailsOutcome vmDetailsOutcome = (VmDetailsOutcome) optionalOutput.getOutcome();
        if (vmDetailsOutcome != null && isNotEmpty(vmDetailsOutcome.getDelegateId())) {
          eligibleToExecuteDelegateIds.add(vmDetailsOutcome.getDelegateId());
        }
      }
    }

    return DelegateTaskRequest.builder()
        .accountId(accountId)
        .executeOnHarnessHostedDelegates(executeOnHarnessHostedDelegates)
        .stageId(stageId)
        .eligibleToExecuteDelegateIds(eligibleToExecuteDelegateIds)
        .taskSelectors(taskSelectors.stream().map(TaskSelector::getSelector).collect(Collectors.toList()))
        .selectors(taskSelectors)
        .taskSetupAbstractions(abstractions)
        .executionTimeout(java.time.Duration.ofSeconds(900))
        .taskType(taskType)
        .serializationFormat(serializationFormat)
        .taskParameters(ciCleanupTaskParams)
        .taskDescription("CI cleanup pod task")
        .build();
  }

  private String fetchDelegateId(Ambiance ambiance) throws InterruptedException {
    OptionalOutcome optionalOutput = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(VmDetailsOutcome.VM_DETAILS_OUTCOME));
    VmDetailsOutcome vmDetailsOutcome = (VmDetailsOutcome) optionalOutput.getOutcome();

    if (vmDetailsOutcome != null && isNotEmpty(vmDetailsOutcome.getDelegateId())) {
      return vmDetailsOutcome.getDelegateId();
    } else {
      String stageId = ambiance.getStageExecutionId();
      log.info("Could not process the delegate ID for stage ID: {} from the init response. Trying to look in the DB",
          stageId);

      long currentTime = System.currentTimeMillis();
      long waitTill = currentTime + WAIT_TIME_IN_SECOND * 1000;

      while (System.currentTimeMillis() < waitTill) {
        Optional<CITaskDetails> taskDetailsOptional = ciTaskDetailsRepository.findFirstByStageExecutionId(stageId);

        if (taskDetailsOptional.isPresent()) {
          CITaskDetails taskDetails = taskDetailsOptional.get();
          if (isNotEmpty(taskDetails.getDelegateId())) {
            log.info("Successfully found delegate ID: {} corresponding to stage ID: {}", taskDetails.getDelegateId(),
                stageId);
            return taskDetails.getDelegateId();
          }
          break;
        } else {
          Thread.sleep(1000);
        }
      }
    }
    return null;
  }

  private List<TaskSelector> fetchDelegateSelector(Ambiance ambiance) {
    return connectorUtils.fetchDelegateSelector(ambiance, executionSweepingOutputResolver);
  }
}
