/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.aisre;

import static java.lang.String.format;

import io.harness.aisre.AiSrePipelineClient;
import io.harness.aisre.AiSrePipelineContextData;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.network.SafeHttpCall;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepUtils;
import io.harness.steps.aisre.exception.AisreInvalidParameterException;
import io.harness.steps.executables.PipelineSyncExecutable;
import io.harness.telemetry.helpers.StepExecutionTelemetryEventDTO;
import io.harness.telemetry.helpers.StepsInstrumentationHelper;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;

@Slf4j
@OwnedBy(HarnessTeam.CHAOS)
public abstract class AisreBaseStep extends PipelineSyncExecutable {
  protected static final String COMMAND_UNIT = "Execute";

  static final String TELEMETRY_STATUS = "status";
  static final String TELEMETRY_API_SUCCESS = "api_success";
  static final String TELEMETRY_EXECUTION_TIME_MS = "execution_time_ms";

  @Inject protected LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject protected AiSrePipelineClient aiSrePipelineClient;
  @Inject protected AisreStepResponseBuilder aisreStepResponseBuilder;
  @Inject private StepsInstrumentationHelper stepsInstrumentationHelper;

  @Override
  public final StepResponse executeSyncAfterRbac(Ambiance ambiance, StepBaseParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    long startTime = System.currentTimeMillis();
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, null, true);

    try {
      AiSrePipelineContextData.setFromAmbiance(ambiance);
      return executeAisreStep(ambiance, stepParameters, logCallback, startTime);
    } catch (Exception e) {
      // Subclasses own user-facing log level (WARN for non-fatal create steps, ERROR for hard fail).
      return handleException(startTime, logCallback, e);
    } finally {
      AiSrePipelineContextData.clear();
    }
  }

  @Override
  public StepResponse postSyncValidate(
      Ambiance ambiance, StepBaseParameters stepParameters, StepResponse stepResponse) {
    publishTelemetryEvent(ambiance, stepParameters, stepResponse);
    return super.postSyncValidate(ambiance, stepParameters, stepResponse);
  }

  protected abstract StepResponse executeAisreStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime);

  protected abstract StepType getAisreStepType();

  /**
   * How a step reacts to an execution failure. Defaults to failing the step; create-alert and
   * create-incident override this to return IGNORE_FAILED (UI warning, pipeline continues) so a
   * flaky AI SRE backend never breaks a deploy.
   */
  protected StepResponse handleException(long startTime, NGLogCallback logCallback, Exception e) {
    log.error("AI SRE step execution failed: {}", e.getMessage(), e);
    logCallback.saveExecutionLog("Step failed: " + e.getMessage(), LogLevel.ERROR, CommandExecutionStatus.FAILURE);
    return aisreStepResponseBuilder.getFailedStepResponse(startTime, System.currentTimeMillis(), e);
  }

  /**
   * Calls the AI SRE API and surfaces transposit error bodies (404/400/etc.) instead of returning a
   * null body that would look like a silent success path.
   */
  protected <T> T executeAisreApiCall(Call<T> call) {
    try {
      return SafeHttpCall.executeWithErrorMessage(call);
    } catch (IOException e) {
      throw new InvalidRequestException(format("AI SRE request failed: %s", e.getMessage()), e);
    }
  }

  protected StepResponse buildValidationFailureResponse(long startTime, NGLogCallback logCallback, Exception e) {
    log.error("AI SRE step failed validation: {}", e.getMessage());
    logCallback.saveExecutionLog(e.getMessage(), LogLevel.ERROR, CommandExecutionStatus.FAILURE);
    return StepResponse.builder()
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(e.getMessage()).build())
        .unitProgressList(buildUnitProgress(startTime, UnitStatus.FAILURE))
        .build();
  }

  /**
   * Non-fatal backend failures: {@link Status#IGNORE_FAILED} keeps the pipeline green while the
   * execution graph shows the ignoreFailed warning icon on the step.
   */
  protected StepResponse buildNonFatalWarningResponse(
      long startTime, NGLogCallback logCallback, String stepLabel, Exception e) {
    log.warn("AI SRE {} failed but is non-fatal; continuing pipeline: {}", stepLabel, e.getMessage());
    logCallback.saveExecutionLog(
        format("AI SRE %s failed (non-fatal, pipeline continues): %s", stepLabel, e.getMessage()), LogLevel.WARN);
    return StepResponse.builder()
        .status(Status.IGNORE_FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(e.getMessage()).build())
        .unitProgressList(buildUnitProgress(startTime, UnitStatus.FAILURE))
        .build();
  }

  protected StepExecutionTelemetryEventDTO getStepExecutionTelemetryEventDTO(
      Ambiance ambiance, StepBaseParameters stepParameters, StepResponse stepResponse) {
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(TELEMETRY_STATUS, stepResponse.getStatus().name());
    properties.put(TELEMETRY_API_SUCCESS, hasAisreStepOutcome(stepResponse));
    Long executionTimeMs = extractExecutionTimeMs(stepResponse);
    if (executionTimeMs != null) {
      properties.put(TELEMETRY_EXECUTION_TIME_MS, executionTimeMs);
    }
    augmentTelemetryProperties(ambiance, stepParameters, stepResponse, properties);
    return StepExecutionTelemetryEventDTO.builder()
        .stepType(getAisreStepType().getType())
        .properties(properties)
        .build();
  }

  /**
   * The step response is the only execution state {@link #postSyncValidate} receives, so duration
   * is read back off the command unit rather than held on this (singleton, concurrently reused)
   * step instance.
   */
  protected List<UnitProgress> buildUnitProgress(long startTime, UnitStatus status) {
    return Collections.singletonList(UnitProgress.newBuilder()
                                         .setUnitName(COMMAND_UNIT)
                                         .setStatus(status)
                                         .setStartTime(startTime)
                                         .setEndTime(System.currentTimeMillis())
                                         .build());
  }

  private static Long extractExecutionTimeMs(StepResponse stepResponse) {
    List<UnitProgress> unitProgressList = stepResponse.getUnitProgressList();
    if (unitProgressList == null || unitProgressList.isEmpty()) {
      return null;
    }
    long start = Long.MAX_VALUE;
    long end = Long.MIN_VALUE;
    for (UnitProgress unitProgress : unitProgressList) {
      if (unitProgress.getStartTime() <= 0 || unitProgress.getEndTime() <= 0) {
        continue;
      }
      start = Math.min(start, unitProgress.getStartTime());
      end = Math.max(end, unitProgress.getEndTime());
    }
    return end < start ? null : end - start;
  }

  protected void augmentTelemetryProperties(Ambiance ambiance, StepBaseParameters stepParameters,
      StepResponse stepResponse, HashMap<String, Object> properties) {
    // Subclasses may add step-specific analytics properties.
  }

  protected Scope getScope(
      Ambiance ambiance, ParameterField<String> orgIdentifierField, ParameterField<String> projectIdentifierField) {
    Scope scope = Scope.builder()
                      .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
                      .orgIdentifier(resolveOrgIdentifier(ambiance, orgIdentifierField))
                      .projectIdentifier(resolveProjectIdentifier(ambiance, projectIdentifierField))
                      .parentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
                      .build();
    // JWT claims must match the query params on the AI SRE call (including step overrides).
    AiSrePipelineContextData.setTargetScope(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier());
    return scope;
  }

  protected String resolveOrgIdentifier(Ambiance ambiance, ParameterField<String> orgIdentifierField) {
    return resolveScopeIdentifier(orgIdentifierField, AmbianceUtils.getOrgIdentifier(ambiance));
  }

  protected String resolveProjectIdentifier(Ambiance ambiance, ParameterField<String> projectIdentifierField) {
    return resolveScopeIdentifier(projectIdentifierField, AmbianceUtils.getProjectIdentifier(ambiance));
  }

  private String resolveScopeIdentifier(ParameterField<String> identifierField, String fallbackIdentifier) {
    String override = identifierField == null ? null : identifierField.getValue();
    if (StringUtils.isNotBlank(override)) {
      return override;
    }
    return fallbackIdentifier;
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, new ArrayList<>());
  }

  protected void assertNotEmpty(String value, String errorMsg) {
    if (StringUtils.isBlank(value)) {
      throw new AisreInvalidParameterException(errorMsg);
    }
  }

  private void publishTelemetryEvent(Ambiance ambiance, StepBaseParameters stepParameters, StepResponse stepResponse) {
    try {
      StepExecutionTelemetryEventDTO telemetryEventDTO =
          getStepExecutionTelemetryEventDTO(ambiance, stepParameters, stepResponse);
      if (telemetryEventDTO != null) {
        stepsInstrumentationHelper.publishStepEvent(ambiance, telemetryEventDTO);
      }
    } catch (Exception ex) {
      log.error("Failed to publish Telemetry event for - [{}]", getClass(), ex);
    }
  }

  private static boolean hasAisreStepOutcome(StepResponse stepResponse) {
    return stepResponse.getStepOutcomes() != null && !stepResponse.getStepOutcomes().isEmpty();
  }
}
