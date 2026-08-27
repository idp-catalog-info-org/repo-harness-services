/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.ci.execution.execution.GitBuildStatusUtilityImpl.LITE_ENGINE_TASK;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.PmsCommonConstants.AUTO_ABORT_PIPELINE_THROUGH_TRIGGER;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.pms.execution.utils.StatusUtils.isFinalStatus;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.CILogKeyMetadata;
import io.harness.beans.sweepingoutputs.CISweepingOutputNames;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.execution.intfc.GitBuildStatusUtility;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.ci.metrics.CIObservabilityConstants;
import io.harness.ci.metrics.ExecutionMetricsService;
import io.harness.ci.metrics.helper.CIMetricsHelper;
import io.harness.ci.states.codebase.CodeBaseTaskStep;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.dto.LicensesWithSummaryDTO;
import io.harness.logging.AutoLogContext;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.repositories.CILogKeyRepository;
import io.harness.repositories.CIStageOutputRepository;
import io.harness.repositories.CIStepStatusRepository;
import io.harness.repositories.StepExecutionParametersRepository;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class PipelineExecutionUpdateEventHandler implements OrchestrationEventHandler {
  @Inject private GitBuildStatusUtility gitBuildStatusUtility;
  @Inject private StageCleanupUtility stageCleanupUtility;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private CILogServiceUtils ciLogServiceUtils;
  @Inject private CILicenseService ciLicenseService;
  @Inject private CIAccountExecutionMetadataRepository ciAccountExecutionMetadataRepository;
  @Inject private QueueExecutionUtils queueExecutionUtils;
  @Inject private HsqsClientService hsqsClientService;
  @Inject private StepExecutionParametersRepository stepExecutionParametersRepository;

  @Inject private CILogKeyRepository ciLogKeyRepository;
  @Inject private CIFeatureFlagService ciFeatureFlagService;
  @Inject private ExecutionMetricsService executionMetricsService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;

  private final String SERVICE_NAME_CI = "ci";
  private final int MAX_ATTEMPTS = 3;
  @Inject @Named("ciEventHandlerExecutor") private ExecutorService executorService;
  @Inject @Named("ciRatelimitHandlerExecutor") private ExecutorService ciRatelimitHandlerExecutor;
  @Inject CIStageOutputRepository ciStageOutputRepository;
  @Inject protected CIStepStatusRepository ciStepStatusRepository;

  @Override
  public void handleEvent(OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    String moduleType = AmbianceUtils.getStageModuleType(ambiance);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
    String serviceName = event.getServiceName();
    Status status = event.getStatus();
    ExecutionPrincipalInfo principalInfo = ambiance.getMetadata().getPrincipalInfo();
    ciRatelimitHandlerExecutor.submit(
        () -> { updateDailyBuildCount(level, status, serviceName, accountId, moduleType, principalInfo); });
    executorService.submit(() -> {
      try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
        String stageInfraType = recordStageExecutionMetric(level, ambiance, status, accountId, moduleType, event);
        sendGitStatus(level, ambiance, status, event, accountId);
        sendCleanupRequest(level, ambiance, status, accountId, stageInfraType);
      }
    });
  }

  /**
   * Emit the dedicated CI stage-execution counter on every terminal CI stage. This is the single source of
   * truth for "stages executed per infra type" and also captures pre-init / init failures by deriving the stage phase
   * from sweeping outputs. Plan-creation failures (where no stage node exists) are emitted separately from the CI plan
   * creators. Best-effort and additive: failures here never affect execution.
   */
  private String recordStageExecutionMetric(
      Level level, Ambiance ambiance, Status status, String accountId, String moduleType, OrchestrationEvent event) {
    try {
      if (level == null || level.getStepType().getStepCategory() != StepCategory.STAGE || !isFinalStatus(status)
          || !SERVICE_NAME_CI.equalsIgnoreCase(moduleType)) {
        return null;
      }

      // Prefer in-memory stage spec on the event; identity-node events omit it, and USE_FROM_STAGE labels
      // unknown, so sweeping output is the fallback rather than an extra read on every terminal stage.
      String infraType = event != null ? CIMetricsHelper.infraTypeFrom(event.getResolvedStepParameters())
                                       : CIObservabilityConstants.INFRA_TYPE_UNKNOWN;

      // Success is already execution; only failures need INITIALIZE_EXECUTION to tell init from pre_init.
      boolean initFound = false;
      if (status != Status.SUCCEEDED) {
        OptionalSweepingOutput initOutput = executionSweepingOutputResolver.resolveOptional(
            ambiance, RefObjectUtils.getOutcomeRefObject(CISweepingOutputNames.INITIALIZE_EXECUTION));
        initFound = initOutput != null && initOutput.isFound();
      }

      boolean stageDetailsFound = false;
      boolean needStageDetailsForInfra = CIObservabilityConstants.INFRA_TYPE_UNKNOWN.equals(infraType);
      // Failed-before-execution still needs stageDetails to tell init from pre_init. Success and post-init
      // failures already have a phase, so skip that read when the event already supplied infra.
      boolean needStageDetailsForPhase = status != Status.SUCCEEDED && !initFound;
      if (needStageDetailsForInfra || needStageDetailsForPhase) {
        OptionalSweepingOutput stageDetailsOutput = executionSweepingOutputResolver.resolveOptional(
            ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
        stageDetailsFound = stageDetailsOutput != null && stageDetailsOutput.isFound();
        if (needStageDetailsForInfra) {
          infraType = CIMetricsHelper.infraTypeFrom(stageDetailsOutput);
        }
      }

      String phase;
      if (status == Status.SUCCEEDED || initFound) {
        phase = CIObservabilityConstants.STAGE_PHASE_EXECUTION;
      } else if (stageDetailsFound) {
        phase = CIObservabilityConstants.STAGE_PHASE_INIT;
      } else {
        phase = CIObservabilityConstants.STAGE_PHASE_PRE_INIT;
      }

      CIMetricsHelper.recordStageExecution(
          executionMetricsService, moduleType, accountId, infraType, status.name(), phase);
      return infraType;
    } catch (Exception ex) {
      log.warn(
          "Failed to record CI stage execution metric for stage {}", level != null ? level.getIdentifier() : null, ex);
      return null;
    }
  }

  private void deleteCIStageOutputs(Ambiance ambiance) {
    String stageExecutionId = ambiance.getStageExecutionId();
    try {
      ciStageOutputRepository.deleteFirstByStageExecutionId(stageExecutionId);
    } catch (Exception e) {
      log.error("Error while deleting CI outputs for stageExecutionId " + stageExecutionId, e);
    }
  }

  private void deleteCIStepStatusMetadata(Ambiance ambiance) {
    String stageExecutionId = ambiance.getStageExecutionId();
    try {
      ciStepStatusRepository.deleteByStageExecutionId(stageExecutionId);
    } catch (Exception e) {
      log.error("Error while deleting CI StepStatusMetadata for stageExecutionId " + stageExecutionId, e);
    }
  }

  private void deleteCILogKeysMetadata(Ambiance ambiance) {
    String stageExecutionId = ambiance.getStageExecutionId();
    try {
      ciLogKeyRepository.deleteByStageExecutionId(stageExecutionId);
    } catch (Exception e) {
      log.error("Error while deleting CLogKeyMetadata for stageExecutionId " + stageExecutionId, e);
    }
  }

  private void deleteCIStepParameters(Ambiance ambiance) {
    String stageRunTimeId = AmbianceUtils.getStageRuntimeIdAmbiance(ambiance);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    try {
      stepExecutionParametersRepository.deleteAllByAccountIdAndStageRunTimeId(accountId, stageRunTimeId);
    } catch (Exception e) {
      log.error("Error while deleting CI StepStatusMetadata for stageExecutionId " + stageRunTimeId, e);
    }
  }

  private void sendCleanupRequest(Level level, Ambiance ambiance, Status status, String accountId, String infraType) {
    // cleanup_infra is recorded once per stage, after the retry policy below has been exhausted, so a cleanup that
    // only succeeded on a later attempt counts as a single success rather than a success plus a failure. The retry
    // wraps more than the cleanup call, so whether the task was dispatched - not whether this method threw - decides
    // the outcome: log-stream or output-deletion failures after dispatch are not cleanup failures.
    AtomicBoolean cleanupSubmitted = new AtomicBoolean(false);
    AtomicLong dispatchLatencyMs = new AtomicLong(-1);
    AtomicReference<String> cleanupInfraType = new AtomicReference<>();
    boolean cleanupFailed = false;
    try {
      RetryPolicy<Object> retryPolicy = getRetryPolicy(format("[Retrying failed call to clean pod attempt: {}"),
          format("Failed to clean pod after retrying {} times"));

      Failsafe.with(retryPolicy).run(() -> {
        if (level.getStepType().getStepCategory() == StepCategory.STAGE && isFinalStatus(status)) {
          // TODO: Once Robust Cleanup implementation is done shift this after response from delegate is received.
          try {
            String topic = ciExecutionServiceConfig.getQueueServiceClientConfig().getTopic();
            CIExecutionMetadata ciExecutionMetadata =
                queueExecutionUtils.deleteActiveExecutionRecord(ambiance.getStageExecutionId());
            if (ciExecutionMetadata != null && StringUtils.isNotBlank(ciExecutionMetadata.getQueueId())) {
              AckRequest ackRequest = AckRequest.builder()
                                          .itemId(ciExecutionMetadata.getQueueId())
                                          .consumerName(topic)
                                          .topic(ciExecutionMetadata.getQueueTopic())
                                          .subTopic(ciExecutionMetadata.getQueueSubtopic())
                                          .build();
              hsqsClientService.ack(ackRequest);
            }
          } catch (Exception ex) {
            log.info("Failed to remove execution record from DB for stageExecutionId: {}",
                ambiance.getStageExecutionId(), ex);
          }

          deleteCIStageOutputs(ambiance);
          deleteCIStepStatusMetadata(ambiance);
          deleteCIStepParameters(ambiance);

          log.info("Received event with status {} to clean planExecutionId {}, stage {}", status,
              ambiance.getPlanExecutionId(), level.getIdentifier());
          long dispatchStartMs = System.currentTimeMillis();
          StageCleanupUtility.CleanupSubmitResult cleanupResult =
              stageCleanupUtility.submitCleanupRequest(ambiance, level.getIdentifier());
          if (cleanupResult != null) {
            cleanupInfraType.set(cleanupResult.getInfraType());
            if (cleanupResult.isSubmitted()) {
              dispatchLatencyMs.set(System.currentTimeMillis() - dispatchStartMs);
              cleanupSubmitted.set(true);
            }
          }

          String logKey = getLogKey(ambiance);

          // Note: LElogKey and memoryMetricsLogKey snapshot calls are now done in CICleanupTaskNotifyCallback
          // after cleanup response is received

          // Get all keys list from executionID
          CILogKeyMetadata ciLogKeyMetadata = ciLogKeyRepository.findByStageExecutionId(ambiance.getStageExecutionId());

          // If there are any leftover logs still in the stream (this might be possible in specific cases
          // like in k8s node pressure evictions) - then this is where we move all of them to blob storage.
          if (ciLogKeyMetadata != null) {
            for (String key : ciLogKeyMetadata.getLogKeys()) {
              ciLogServiceUtils.closeLogStream(AmbianceUtils.getAccountId(ambiance), key, true, false);
            }
            deleteCILogKeysMetadata(ambiance);
          } else {
            log.warn("Log keys not found in DB, deleting with prefix");
            // Append '/' at the end of the prefix if it's not present so that it doesn't close log streams
            // for a different key.
            if (!logKey.endsWith("/")) {
              logKey = logKey + "/";
            }
            ciLogServiceUtils.closeLogStream(AmbianceUtils.getAccountId(ambiance), logKey, true, true);
          }

          // Now Delete the build from db while cleanup is happening. \
        }
      });
    } catch (Exception ex) {
      cleanupFailed = !CIMetricsHelper.isExpectedUnprovisionedCleanup(ex);
      log.error("Failed to send cleanup call for node {}", level.getRuntimeId(), ex);
    }
    recordCleanupInfra(ambiance, accountId, cleanupSubmitted.get(), cleanupFailed, dispatchLatencyMs.get(),
        infraType != null ? infraType : cleanupInfraType.get());
  }

  /**
   * Records the terminal cleanup_infra outcome for a stage. Cleanups that were intentionally skipped
   * (e.g. CI_SKIP_CLOUD_VM_CLEANUP), never-provisioned infra (expected non-submit), and non-CI stages emit nothing.
   * Best-effort and additive: failures here never affect execution.
   */
  private void recordCleanupInfra(Ambiance ambiance, String accountId, boolean submitted, boolean failed,
      long dispatchLatencyMs, String infraType) {
    if (!submitted && !failed) {
      return;
    }
    try {
      if (infraType == null) {
        // Last resort: the same STAGE_INFRA_DETAILS output cleanup uses. stageDetails is the YAML spec and is
        // already consumed for the stage-execution metric; it is not what teardown is keyed off.
        OptionalSweepingOutput stageInfraOutput = executionSweepingOutputResolver.resolveOptional(
            ambiance, RefObjectUtils.getSweepingOutputRefObject(StageInfraDetails.STAGE_INFRA_DETAILS));
        if (stageInfraOutput != null && stageInfraOutput.isFound()
            && stageInfraOutput.getOutput() instanceof StageInfraDetails) {
          infraType = CIMetricsHelper.infraTypeFrom((StageInfraDetails) stageInfraOutput.getOutput());
        } else {
          infraType = CIObservabilityConstants.INFRA_TYPE_UNKNOWN;
        }
      }
      String outcome =
          submitted ? CIObservabilityConstants.OUTCOME_SUCCESS : CIObservabilityConstants.OUTCOME_SYSTEM_FAILURE;
      // Latency covers the dispatch call alone, so retry backoff (5-60s) never distorts the histogram.
      CIMetricsHelper.recordSystemApi(executionMetricsService, AmbianceUtils.getStageModuleType(ambiance), accountId,
          infraType, CIObservabilityConstants.OP_CLEANUP_INFRA, outcome, CIObservabilityConstants.PHASE_SUBMIT, null,
          dispatchLatencyMs);
    } catch (Exception ex) {
      log.warn("Failed to record cleanup_infra metric", ex);
    }
  }

  private String getLogKey(Ambiance ambiance) {
    return LogStreamingStepClientFactory.getLogBaseKey(ambiance);
  }

  private void sendGitStatus(
      Level level, Ambiance ambiance, Status status, OrchestrationEvent event, String accountId) {
    try {
      if (gitBuildStatusUtility.shouldSendStatus(level.getStepType().getStepCategory())
          || gitBuildStatusUtility.isCodeBaseStepSucceeded(level, status)
          || gitBuildStatusUtility.shouldSentStatusOnInitialize(level, event, ambiance, accountId)) {
        log.info("Received event with status {} to update git status for stage {}, planExecutionId {}", status,
            level.getIdentifier(), ambiance.getPlanExecutionId());
        if (isAutoAbortThroughTrigger(event)) {
          log.info("Skipping updating Git status as execution was Auto aborted by trigger due to newer execution");
        } else {
          StepParameters stepParameters = gitBuildStatusUtility.getStepParameters(ambiance, event, accountId);

          // Check if pipeline-level git status is handling this event
          if (shouldSkipCIGitStatusUpdate(stepParameters, ambiance, event, accountId)) {
            log.info("Skipping CI git status update for PR event as pipeline-level is handling it, "
                    + "stepType: {}, stepCategory: {}, stage: {}, planExecutionId: {}",
                level.getStepType().getType(), level.getStepType().getStepCategory(), level.getIdentifier(),
                ambiance.getPlanExecutionId());
            return;
          }

          if (level.getStepType().getStepCategory() == StepCategory.STAGE) {
            gitBuildStatusUtility.sendStatusToGit(status, stepParameters, ambiance, accountId, event);
          } else if (level.getStepType().getType().equals(CodeBaseTaskStep.STEP_TYPE.getType())
              || level.getStepType().getType().equals(LITE_ENGINE_TASK)) {
            // It sends Running if codebase step successfully fetched commit sha via api token
            gitBuildStatusUtility.sendStatusToGit(Status.RUNNING, stepParameters, ambiance, accountId, event);
          }
        }
      }
    } catch (Exception ex) {
      log.error("Failed to send git status update task for node {}, planExecutionId {}", level.getRuntimeId(),
          ambiance.getPlanExecutionId(), ex);
    }
  }

  // When trigger has "Auto Abort Prev Executions" ebanled, it will abort prev running execution and start a new one.
  // e.g. pull_request  event for same PR
  private boolean isAutoAbortThroughTrigger(OrchestrationEvent event) {
    if (isEmpty(event.getTags())) {
      return false;
    }

    boolean isAutoAbort = false;
    if (event.getTags().contains(AUTO_ABORT_PIPELINE_THROUGH_TRIGGER)) {
      isAutoAbort = true;
    }

    return isAutoAbort;
  }

  private boolean isPREvent(OrchestrationEvent event) {
    try {
      if (event.getTriggerPayload() == null || event.getTriggerPayload().getParsedPayload() == null) {
        return false;
      }
      return event.getTriggerPayload().getParsedPayload().hasPr();
    } catch (Exception ex) {
      log.error("Error checking if event is PR trigger, defaulting to false", ex);
      return false;
    }
  }

  /**
   * Check if CI should skip sending git status because pipeline-level is handling this specific event type.
   * OPTIMIZATION: For stage-level events, uses stepParameters already in memory (no DB call).
   * For step-level events, fetches parent stage parameters from DB via utility.
   * Only skip for PR events (where isPREvent = true). Other events like Push, Branch Create,
   * Tag Create should continue to be handled by CI since pipeline-level doesn't support them yet.
   */
  private boolean shouldSkipCIGitStatusUpdate(
      StepParameters stepParameters, Ambiance ambiance, OrchestrationEvent event, String accountId) {
    try {
      // Check FF first - early exit if disabled
      boolean ffEnabled = ciFeatureFlagService.isEnabled(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT, accountId);
      if (!ffEnabled) {
        return false;
      }

      // Check if it's a PR event - only PR events should be skipped
      if (!isPREvent(event)) {
        return false;
      }

      // Get stage parameters - optimized based on event type
      Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
      StepParameters stageParams;

      if (level.getStepType().getStepCategory() == StepCategory.STAGE) {
        // OPTIMIZATION: For stage-level events, use the stepParameters already passed in (no DB call)
        stageParams = stepParameters;
      } else {
        // For step-level events, fetch parent stage parameters from DB
        stageParams =
            gitBuildStatusUtility.getStageParameters(ambiance, level.getStepType().getStepCategory(), accountId);
        if (stageParams == null) {
          log.debug("Could not get stage parameters for step-level event, defaulting to send status");
          return false;
        }
      }

      // Extract gitStatusConfigPresent from stage parameters
      boolean gitStatusConfigPresent = extractGitStatusConfigFromStageParams(stageParams);

      // If config is present and true, skip CI git status
      if (gitStatusConfigPresent) {
        return true;
      }

      return false;
    } catch (Exception ex) {
      log.error("Error checking if CI should skip git status update, defaulting to send status", ex);
      return false; // On error, don't skip - let CI send status for safety
    }
  }

  /**
   * Helper method to extract gitStatusConfigPresent from StepParameters.
   * Extracts the config flag from IntegrationStageStepParametersPMS.
   */
  private boolean extractGitStatusConfigFromStageParams(StepParameters stageParams) {
    if (!(stageParams instanceof StageElementParameters)) {
      log.debug("Stage parameters are not StageElementParameters");
      return false;
    }

    StageElementParameters stageElementParameters = (StageElementParameters) stageParams;
    SpecParameters specParameters = stageElementParameters.getSpecConfig();

    if (!(specParameters instanceof IntegrationStageStepParametersPMS)) {
      log.debug("Stage is not a CI Integration Stage");
      return false;
    }

    IntegrationStageStepParametersPMS integrationStageParams = (IntegrationStageStepParametersPMS) specParameters;
    Boolean configPresent = integrationStageParams.getGitStatusConfigPresent();
    return Boolean.TRUE.equals(configPresent);
  }

  private void updateDailyBuildCount(Level level, Status status, String serviceName, String accountId,
      String moduleType, ExecutionPrincipalInfo principalInfo) {
    LicensesWithSummaryDTO licensesWithSummaryDTO =
        ciLicenseService.getLicenseSummary(accountId, moduleType, principalInfo);
    if (licensesWithSummaryDTO == null) {
      throw new CIStageExecutionException("Please enable CI free plan or reach out to support.");
    }
    if (licensesWithSummaryDTO != null && licensesWithSummaryDTO.getEdition() == Edition.FREE) {
      if (level != null && moduleType.equalsIgnoreCase(SERVICE_NAME_CI)
          && level.getStepType().getStepCategory() == StepCategory.STAGE && (status == RUNNING)) {
        ciAccountExecutionMetadataRepository.updateCIDailyBuilds(accountId, level.getStartTs());
      }
    }
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withBackoff(5, 60, ChronoUnit.SECONDS)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.warn(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}