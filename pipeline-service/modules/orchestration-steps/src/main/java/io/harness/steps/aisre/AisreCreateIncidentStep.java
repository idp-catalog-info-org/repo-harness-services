/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.aisre;

import static java.lang.String.format;

import io.harness.aisre.CreateIncidentRequest;
import io.harness.aisre.IncidentResponse;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates an AI SRE incident. Backend/network create failures are non-fatal: the step returns
 * {@link Status#IGNORE_FAILED} (UI warning, pipeline continues) so a flaky AI SRE backend never
 * breaks a deploy. Input validation still fails the step.
 */
@OwnedBy(HarnessTeam.CHAOS)
@Slf4j
public class AisreCreateIncidentStep extends AisreBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.AISRE_CREATE_INCIDENT_STEP_TYPE;
  public static final String OUTPUT = "output";
  // Default AI SRE incident template short id when incidentType is not provided.
  private static final String DEFAULT_INCIDENT_TYPE = "INC";
  private static final String TELEMETRY_SEVERITY = "severity";
  private static final Set<String> RESERVED_CUSTOM_FIELD_IDS =
      Set.of("title", "summary", "description", "severity", "status", "commander");

  @Inject private AisrePipelineContextFormatter aisrePipelineContextFormatter;

  @Override
  protected StepType getAisreStepType() {
    return STEP_TYPE;
  }

  @Override
  protected void augmentTelemetryProperties(Ambiance ambiance, StepBaseParameters stepParameters,
      StepResponse stepResponse, HashMap<String, Object> properties) {
    if (!(stepParameters.getSpec() instanceof AisreCreateIncidentStepParameters)) {
      return;
    }
    String severity = resolve(((AisreCreateIncidentStepParameters) stepParameters.getSpec()).getSeverity());
    if (!Strings.isNullOrEmpty(severity)) {
      properties.put(TELEMETRY_SEVERITY, severity);
    }
  }

  @Override
  protected StepResponse executeAisreStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing AISRE_CREATE_INCIDENT step...");
    logCallback.saveExecutionLog("Starting AI SRE Create Incident", LogLevel.INFO);

    AisreCreateIncidentStepParameters p = (AisreCreateIncidentStepParameters) stepParameters.getSpec();
    Scope scope = getScope(ambiance, p.getOrgIdentifier(), p.getProjectIdentifier());

    String title = resolve(p.getTitle());
    if (Strings.isNullOrEmpty(title)) {
      throw new AisreInvalidParameterException("Missing required parameter: title");
    }

    String severity = resolve(p.getSeverity());
    if (Strings.isNullOrEmpty(severity)) {
      throw new AisreInvalidParameterException("Missing required parameter: severity");
    }

    String service = resolve(p.getService());
    if (Strings.isNullOrEmpty(service)) {
      throw new AisreInvalidParameterException("Missing required parameter: service");
    }

    String incidentType =
        Optional.ofNullable(resolve(p.getIncidentType())).filter(s -> !s.isBlank()).orElse(DEFAULT_INCIDENT_TYPE);
    boolean attachPipelineContext = Optional.ofNullable(resolve(p.getAttachPipelineContext())).orElse(Boolean.TRUE);
    // Opt-out: an incident raised by a pipeline should reach the on-call owner unless the author
    // explicitly disables paging.
    boolean pageOnCall = Optional.ofNullable(resolve(p.getPageOnCall())).orElse(Boolean.TRUE);
    // Build once: formatPipelineContextBlock fetches the execution summary (Mongo + URL).
    String pipelineContextBlock =
        attachPipelineContext ? aisrePipelineContextFormatter.formatPipelineContextBlock(ambiance) : null;
    String userDescription = resolve(p.getDescription());
    String description = pipelineContextBlock == null
        ? userDescription
        : (Strings.isNullOrEmpty(userDescription) ? pipelineContextBlock
                                                  : userDescription + "\n\n" + pipelineContextBlock);
    String pipelineContextTimelineMessage = pipelineContextBlock;
    List<String> impactedServices = buildImpactedServices(p);
    String status = resolveStatus(p);

    CreateIncidentRequest request = CreateIncidentRequest.builder()
                                        .templateShortId(incidentType)
                                        .title(title)
                                        .summary(description)
                                        .severity(severity)
                                        .status(status)
                                        .impactedServices(impactedServices)
                                        .environments(buildEnvironments(p))
                                        .commanderHarnessUserId(resolve(p.getCommanderHarnessUserId()))
                                        .labels(buildLabels(p))
                                        .pageOnCall(pageOnCall)
                                        .pipelineContextTimelineMessage(pipelineContextTimelineMessage)
                                        .customFields(buildCustomFields(p))
                                        .build();

    logCallback.saveExecutionLog(format("Creating AI SRE incident (type=%s): %s", incidentType, title), LogLevel.INFO);

    IncidentResponse response = executeAisreApiCall(aiSrePipelineClient.createIncident(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), request));

    if (response == null || Strings.isNullOrEmpty(response.getPrettyId())) {
      throw new InvalidRequestException("AI SRE incident creation succeeded but returned no incident id");
    }

    String incidentUrl = extractWebUrl(response);
    logCallback.saveExecutionLog(
        format("Created AI SRE incident %s", response.getPrettyId()), LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    AisreCreateIncidentOutcome outcome = AisreCreateIncidentOutcome.builder()
                                             .incidentId(response.getPrettyId())
                                             .incidentUrl(incidentUrl)
                                             .assignedResponders(mapAssignedResponders(response))
                                             .build();

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
    return buildNonFatalWarningResponse(startTime, logCallback, "incident", e);
  }

  private List<String> buildImpactedServices(AisreCreateIncidentStepParameters p) {
    List<String> services = new ArrayList<>();
    String primary = resolve(p.getService());
    if (!Strings.isNullOrEmpty(primary)) {
      services.add(primary);
    }
    List<String> additional =
        Optional.ofNullable(p.getAdditionalImpactedServices()).map(ParameterField::getValue).orElse(List.of());
    if (additional != null) {
      additional.stream().filter(s -> !Strings.isNullOrEmpty(s)).forEach(services::add);
    }
    return services.isEmpty() ? null : services;
  }

  private List<String> buildLabels(AisreCreateIncidentStepParameters p) {
    List<String> labels = Optional.ofNullable(p.getLabels()).map(ParameterField::getValue).orElse(null);
    if (labels == null || labels.isEmpty()) {
      return null;
    }
    List<String> normalized =
        labels.stream().filter(s -> !Strings.isNullOrEmpty(s)).map(String::trim).collect(Collectors.toList());
    return normalized.isEmpty() ? null : normalized;
  }

  private Map<String, Object> buildCustomFields(AisreCreateIncidentStepParameters p) {
    Map<String, String> fields = Optional.ofNullable(p.getFields()).map(ParameterField::getValue).orElse(null);
    if (fields == null || fields.isEmpty()) {
      return null;
    }
    Map<String, Object> customFields = new HashMap<>();
    fields.forEach((key, value) -> {
      if (!Strings.isNullOrEmpty(key) && value != null && !RESERVED_CUSTOM_FIELD_IDS.contains(key)) {
        customFields.put(key, value);
      }
    });
    return customFields.isEmpty() ? null : customFields;
  }

  private String resolveStatus(AisreCreateIncidentStepParameters p) {
    String status = resolve(p.getStatus());
    if (!Strings.isNullOrEmpty(status)) {
      return status;
    }
    Map<String, String> fields = Optional.ofNullable(p.getFields()).map(ParameterField::getValue).orElse(null);
    if (fields == null) {
      return null;
    }
    return fields.get("status");
  }

  private List<String> buildEnvironments(AisreCreateIncidentStepParameters p) {
    String env = resolve(p.getEnvironment());
    return Strings.isNullOrEmpty(env) ? null : List.of(env);
  }

  private static String extractWebUrl(IncidentResponse response) {
    if (response.getCommsLinks() == null) {
      return null;
    }
    return response.getCommsLinks()
        .stream()
        .filter(l -> "WEB".equalsIgnoreCase(l.getLinkType()))
        .map(IncidentResponse.CommsLink::getUrl)
        .findFirst()
        .orElse(null);
  }

  private static List<AisreCreateIncidentOutcome.AssignedResponder> mapAssignedResponders(IncidentResponse response) {
    if (response.getAssignedResponders() == null || response.getAssignedResponders().isEmpty()) {
      return null;
    }
    List<AisreCreateIncidentOutcome.AssignedResponder> responders = new ArrayList<>();
    for (IncidentResponse.OncallUser user : response.getAssignedResponders()) {
      if (user == null) {
        continue;
      }
      responders.add(AisreCreateIncidentOutcome.AssignedResponder.builder()
                         .userId(user.getUserId())
                         .displayName(user.getDisplayName())
                         .email(user.getEmail())
                         .build());
    }
    return responders.isEmpty() ? null : responders;
  }

  private static <T> T resolve(ParameterField<T> field) {
    return field == null ? null : field.getValue();
  }
}
