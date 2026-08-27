/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.ro;

import static io.harness.eraro.ErrorCode.GENERAL_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.data.OutcomeException;
import io.harness.expression.EngineExpressionService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.remote.client.PipelineRestUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.executables.PipelineSyncExecutable;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class RONotifyStep extends PipelineSyncExecutable {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.RO_NOTIFY_STEP_TYPE;

  @Inject private ReleaseManagementClient releaseManagementClient;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject @Named(ReleaseManagementClientModule.RELEASE_MANAGEMENT_EVENT_TYPE) private String eventType;
  @Inject private ArtifactsResolver artifactsResolver;
  @Inject private EngineExpressionService engineExpressionService;

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, new ArrayList<>());
  }

  // Mirrors IntegrationStageStepPMS.STEP_TYPE.getType() in 332-ci-manager;
  // can't import directly (would invert the module dep direction).
  private static final String CI_INTEGRATION_STAGE_STEP_TYPE = "IntegrationStageStepPMS";

  private static final String EXPR_COMMIT_SHA = "<+codebase.commitSha>";
  private static final String EXPR_REPO_URL = "<+codebase.repoUrl>";
  private static final String EXPR_BRANCH = "<+codebase.branch>";

  // Wire contract with release-management's signal handler. Keep in sync.
  private static final String META_COMMIT_SHA = "commit_sha";
  private static final String META_REPO_NAME = "repo_name";
  private static final String META_BRANCH = "branch";

  private static ArtifactsResolver.Scope detectScope(Ambiance ambiance) {
    for (int i = ambiance.getLevelsCount() - 1; i >= 0; i--) {
      Level level = ambiance.getLevels(i);
      if (level.getStepType().getStepCategory() == StepCategory.STAGE) {
        if (CI_INTEGRATION_STAGE_STEP_TYPE.equals(level.getStepType().getType())) {
          return ArtifactsResolver.Scope.STAGE;
        }
        return ArtifactsResolver.Scope.PIPELINE;
      }
    }
    return ArtifactsResolver.Scope.PIPELINE;
  }

  private ArtifactsResolver.ResolvedArtifacts resolveArtifactsWithFallback(
      Ambiance ambiance, NGLogCallback logCallback) {
    ArtifactsResolver.Scope scope = detectScope(ambiance);
    ArtifactsResolver.ResolvedArtifacts resolved = artifactsResolver.resolve(ambiance, scope);
    if (resolved.isEmpty() && scope == ArtifactsResolver.Scope.STAGE) {
      ArtifactsResolver.ResolvedArtifacts fallback =
          artifactsResolver.resolve(ambiance, ArtifactsResolver.Scope.PIPELINE);
      if (!fallback.isEmpty()) {
        logCallback.saveExecutionLog("STAGE scope returned empty; falling back to PIPELINE scope and resolving "
            + fallback.getImages().size() + " images");
        return fallback;
      }
    }
    return resolved;
  }

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, StepBaseParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    long startTime = System.currentTimeMillis();
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, null, true);

    if (!(stepParameters.getSpec() instanceof RONotifyStepParameters)) {
      throw new IllegalArgumentException(
          "Expected RONotifyStepParameters but got: " + stepParameters.getSpec().getClass().getName());
    }
    RONotifyStepParameters params = (RONotifyStepParameters) stepParameters.getSpec();

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String pipelineId = ambiance.getMetadata().getPipelineIdentifier();
    String executionId = ambiance.getPlanExecutionId();

    Map<String, String> metadata = new HashMap<>();
    if (params.getMetadata() != null && params.getMetadata().getValues() != null) {
      metadata =
          params.getMetadata()
              .getValues()
              .stream()
              .filter(p -> p.getKey() != null && p.getValue() != null)
              .collect(Collectors.toMap(RONotifyKeyValuePair::getKey, RONotifyKeyValuePair::getValue, (v1, v2) -> v2));
    }

    // Mutable copy: enrichWithCodebase puts into this map below.
    metadata = new HashMap<>(metadata);

    logCallback.saveExecutionLog("RO Notify step execution started");
    logCallback.saveExecutionLog(String.format("Pipeline: %s/%s/%s", orgIdentifier, projectIdentifier, pipelineId));
    logCallback.saveExecutionLog(String.format("PlanExecutionID: %s", executionId));
    if (!metadata.isEmpty()) {
      logCallback.saveExecutionLog(String.format("Metadata: %s", metadata));
    }
    logCallback.saveExecutionLog("Notifying Release Management Service...");

    ArtifactsResolver.ResolvedArtifacts resolved;
    try {
      resolved = resolveArtifactsWithFallback(ambiance, logCallback);
    } catch (OutcomeException e) {
      // Fail the step rather than ship a notify-without-artifacts: a successful
      // notify would burn the idempotency key and block recovery on retry.
      log.error("Failed to resolve artifacts for pipeline: {}/{}/{}", orgIdentifier, projectIdentifier, pipelineId, e);
      logCallback.saveExecutionLog(
          String.format("Failed to resolve artifacts from CI outcomes. Error: %s", e.getMessage()), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      return failureResponse(e, startTime);
    }

    // eventType stays as-configured: RM side-dispatches the artifact-tracker
    // handler when `artifacts` is non-null, without replacing the customer's
    // existing webhook-activity subscription.
    RONotifyRequestBody.Artifacts artifacts = null;
    if (!resolved.isEmpty()) {
      artifacts = RONotifyRequestBody.Artifacts.builder()
                      .images(resolved.getImages())
                      .files(resolved.getFiles())
                      .sbom(resolved.getSbom())
                      .build();
      logCallback.saveExecutionLog(String.format("Resolved %d images / %d files / %d sboms",
          resolved.getImages().size(), resolved.getFiles().size(), resolved.getSbom().size()));

      int cap = artifactsResolver.getMaxItemsPerType();
      if (resolved.getImages().size() == cap || resolved.getFiles().size() == cap || resolved.getSbom().size() == cap) {
        logCallback.saveExecutionLog(
            String.format("Per-type artifact cap of %d reached; additional items were dropped. "
                    + "If your pipeline genuinely publishes more, raise this with the RM team.",
                cap),
            LogLevel.WARN);
      }

      enrichWithCodebase(metadata, ambiance, logCallback);
    }

    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String stageExecutionId = AmbianceUtils.getStageLevelFromAmbiance(ambiance).map(Level::getRuntimeId).orElse(null);

    RONotifyRequestBody requestBody = RONotifyRequestBody.builder()
                                          .eventType(eventType)
                                          .pipeline(RONotifyRequestBody.RONotifyPipelineInfo.builder()
                                                        .orgId(orgIdentifier)
                                                        .projectId(projectIdentifier)
                                                        .identifier(pipelineId)
                                                        .executionId(executionId)
                                                        .stepExecutionId(stepExecutionId)
                                                        .stageExecutionId(stageExecutionId)
                                                        .build())
                                          .metadata(metadata)
                                          .artifacts(artifacts)
                                          .build();

    // setupId (stable across engine retries) — runtimeId would flip per retry
    // and let RM ingest the same artifact twice when a response was lost.
    String currentSetupId = AmbianceUtils.obtainCurrentSetupId(ambiance);
    String idempotencyKey = executionId + ":" + currentSetupId;

    try {
      PipelineRestUtils.getResponse(
          releaseManagementClient.notifyReleaseOrchestration(accountId, idempotencyKey, requestBody));

      logCallback.saveExecutionLog(
          "Successfully notified Release Management Service", LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    } catch (Exception e) {
      log.error("Failed to notify Release Management Service for pipeline: {}/{}/{}", orgIdentifier, projectIdentifier,
          pipelineId, e);
      logCallback.saveExecutionLog(
          String.format("Failed to notify Release Management Service. Error: %s", e.getMessage()), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      return failureResponse(e, startTime);
    }

    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setStatus(UnitStatus.SUCCESS)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))
        .build();
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  /**
   * Normalizes engine output to "" when the expression didn't resolve: null,
   * literal "null", or a verbatim echo of the input (no functor matched).
   * Uses an exact-string check rather than {@code startsWith("<+")} so a real
   * rendered value containing {@code <+} (e.g. a commit message) isn't silenced.
   */
  String renderExpressionOrBlank(Ambiance ambiance, String expression) {
    try {
      String rendered = engineExpressionService.renderExpression(ambiance, expression, true);
      log.debug("RO Notify resolve {} -> [{}]", expression, rendered);
      if (rendered == null) {
        return "";
      }
      String trimmed = rendered.trim();
      if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("null") || trimmed.equals(expression)) {
        return "";
      }
      return trimmed;
    } catch (Exception e) {
      log.warn("RO Notify failed to render expression {}: {}", expression, e.getMessage());
      return "";
    }
  }

  private static void putIfResolved(Map<String, String> metadata, String key, String value) {
    if (value != null && !value.isEmpty()) {
      metadata.put(key, value);
    }
  }

  private void enrichWithCodebase(Map<String, String> metadata, Ambiance ambiance, NGLogCallback logCallback) {
    putIfResolved(metadata, META_COMMIT_SHA, renderExpressionOrBlank(ambiance, EXPR_COMMIT_SHA));
    putIfResolved(metadata, META_REPO_NAME, renderExpressionOrBlank(ambiance, EXPR_REPO_URL));
    putIfResolved(metadata, META_BRANCH, renderExpressionOrBlank(ambiance, EXPR_BRANCH));
    if (metadata.containsKey(META_COMMIT_SHA) || metadata.containsKey(META_REPO_NAME)
        || metadata.containsKey(META_BRANCH)) {
      logCallback.saveExecutionLog(
          String.format("Codebase context: commit=%s repo=%s branch=%s", metadata.getOrDefault(META_COMMIT_SHA, ""),
              metadata.getOrDefault(META_REPO_NAME, ""), metadata.getOrDefault(META_BRANCH, "")));
    }
  }

  private static StepResponse failureResponse(Throwable cause, long startTime) {
    FailureData failureData =
        FailureData.newBuilder()
            .addFailureTypes(FailureType.APPLICATION_FAILURE)
            .setLevel(io.harness.eraro.Level.ERROR.name())
            .setCode(GENERAL_ERROR.name())
            .setMessage(cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName())
            .build();
    return StepResponse.builder()
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().addFailureData(failureData).build())
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setStatus(UnitStatus.FAILURE)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))
        .build();
  }
}
