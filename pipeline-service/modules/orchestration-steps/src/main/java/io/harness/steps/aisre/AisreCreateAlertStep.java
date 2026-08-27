/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.aisre;

import static java.lang.String.format;

import io.harness.aisre.AlertResponse;
import io.harness.aisre.CreateAlertRequest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.aisre.exception.AisreInvalidParameterException;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Creates (or updates, when {@code alertId} is present) an AI SRE alert. Backend/network failures
 * are non-fatal: the step returns {@link Status#IGNORE_FAILED} (UI warning, pipeline continues) so
 * a flaky alerting backend never breaks a deploy. Input validation still fails the step.
 */
@OwnedBy(HarnessTeam.CHAOS)
@Slf4j
public class AisreCreateAlertStep extends AisreBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.AISRE_CREATE_ALERT_STEP_TYPE;
  public static final String OUTPUT = "output";
  private static final String TELEMETRY_IS_UPDATE = "is_update";

  @Inject private AisrePipelineContextFormatter aisrePipelineContextFormatter;

  @Override
  protected StepType getAisreStepType() {
    return STEP_TYPE;
  }

  @Override
  protected void augmentTelemetryProperties(Ambiance ambiance, StepBaseParameters stepParameters,
      StepResponse stepResponse, HashMap<String, Object> properties) {
    if (!(stepParameters.getSpec() instanceof AisreCreateAlertStepParameters)) {
      return;
    }
    String alertId = resolve(((AisreCreateAlertStepParameters) stepParameters.getSpec()).getAlertId());
    if (StringUtils.isNotBlank(alertId)) {
      properties.put(TELEMETRY_IS_UPDATE, true);
    }
  }

  @Override
  protected StepResponse executeAisreStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing AISRE_CREATE_ALERT step...");
    logCallback.saveExecutionLog("Starting AI SRE Create Alert", LogLevel.INFO);

    AisreCreateAlertStepParameters p = (AisreCreateAlertStepParameters) stepParameters.getSpec();
    Scope scope = getScope(ambiance, p.getOrgIdentifier(), p.getProjectIdentifier());

    String alertId = resolve(p.getAlertId());
    String title = resolve(p.getTitle());
    if (Strings.isNullOrEmpty(alertId) && Strings.isNullOrEmpty(title)) {
      throw new AisreInvalidParameterException("Missing required parameter: title");
    }

    CreateAlertRequest request = CreateAlertRequest.builder()
                                     .alertId(alertId)
                                     .title(title)
                                     .status(resolve(p.getStatus()))
                                     .priority(resolve(p.getPriority()))
                                     .service(resolve(p.getService()))
                                     .environment(resolve(p.getEnvironment()))
                                     .description(resolve(p.getDescription()))
                                     .pipelineUrl(aisrePipelineContextFormatter.resolveExecutionUrl(ambiance))
                                     .build();

    String logLabel = Strings.isNullOrEmpty(alertId) ? format("Creating AI SRE alert: %s", title)
                                                     : format("Updating AI SRE alert %s", alertId);
    logCallback.saveExecutionLog(logLabel, LogLevel.INFO);

    AlertResponse response = executeAisreApiCall(aiSrePipelineClient.createAlert(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), request));

    if (response == null || Strings.isNullOrEmpty(response.getPrettyId())) {
      throw new InvalidRequestException("AI SRE alert request succeeded but returned no alert id");
    }

    String alertUrl = extractWebUrl(response);
    logCallback.saveExecutionLog(
        format("AI SRE alert %s", response.getPrettyId()), LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    AisreCreateAlertOutcome outcome =
        AisreCreateAlertOutcome.builder().alertId(response.getPrettyId()).alertUrl(alertUrl).build();

    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .unitProgressList(buildUnitProgress(startTime, UnitStatus.SUCCESS))
        .stepOutcome(StepOutcome.builder().name(OUTPUT).outcome(outcome).build())
        .build();
  }

  @Override
  protected StepResponse handleException(long startTime, NGLogCallback logCallback, Exception e) {
    if (e instanceof AisreInvalidParameterException) {
      return buildValidationFailureResponse(startTime, logCallback, e);
    }
    return buildNonFatalWarningResponse(startTime, logCallback, "alert", e);
  }

  private static String extractWebUrl(AlertResponse response) {
    if (response.getCommsLinks() == null) {
      return null;
    }
    return response.getCommsLinks()
        .stream()
        .filter(l -> "WEB".equalsIgnoreCase(l.getLinkType()))
        .map(io.harness.aisre.IncidentResponse.CommsLink::getUrl)
        .findFirst()
        .orElse(null);
  }

  private static <T> T resolve(ParameterField<T> field) {
    return field == null ? null : field.getValue();
  }
}
