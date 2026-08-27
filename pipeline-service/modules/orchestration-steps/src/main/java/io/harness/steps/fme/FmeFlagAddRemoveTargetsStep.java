/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.fme.FeatureFlagDefinition;
import io.harness.fme.FmePatchOperation;
import io.harness.fme.Treatment;
import io.harness.fme.Treatment.TreatmentBuilder;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeApiExecutor.ExecutionContext;
import io.harness.steps.fme.FmeApiExecutor.NotFoundBehavior;
import io.harness.steps.fme.exception.FmeInvalidParameterException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagAddRemoveTargetsStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_ADD_REMOVE_TARGETS_STEP_TYPE;

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME Flag Add/Remove Individual Targets step...");
    logCallback.saveExecutionLog("Starting FME Flag Add/Remove Individual Targets", LogLevel.INFO);

    Scope scope = getScope(ambiance);
    FmeFlagAddRemoveTargetsStepParameters p = (FmeFlagAddRemoveTargetsStepParameters) stepParameters.getSpec();

    String environment = getRequiredParam(p.getEnvironment(), "environment");
    String flagName = getRequiredParam(p.getFlagName(), "flagName");
    List<TreatmentTarget> treatments = getRequiredParam(p.getTreatments(), "treatments");

    validateTreatments(treatments);

    logCallback.saveExecutionLog(
        format("Processing flag '%s' in environment '%s'", flagName, environment), LogLevel.INFO);

    processTargets(logCallback, scope, flagName, environment, treatments);

    logCallback.saveExecutionLog("Flag targets updated successfully", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return buildSuccessResponseNoOutcome(startTime);
  }

  protected void processTargets(
      NGLogCallback logCallback, Scope scope, String flagName, String environment, List<TreatmentTarget> treatments) {
    FeatureFlagDefinition definition = getDefinition(logCallback, scope, flagName, environment);
    applyTreatmentChanges(definition, treatments, logCallback);
    patchTreatments(logCallback, scope, flagName, environment, definition.getTreatments());
  }

  private FeatureFlagDefinition getDefinition(
      NGLogCallback logCallback, Scope scope, String flagName, String environment) {
    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName("get definition")
                                   .build();

    FeatureFlagDefinition definition = FmeApiExecutor.executeWrapped(
        fmePipelineClient.getFeatureFlagDefinitionInEnvironment(flagName, scope.getAccountIdentifier(),
            scope.getOrgIdentifier(), scope.getProjectIdentifier(), environment),
        context, NotFoundBehavior.RETURN_NULL,
        (flag, env, errorBody) -> format("Failed to get feature flag definition. Error: %s", errorBody));

    if (definition == null) {
      logCallback.saveExecutionLog("No definition found, using default", LogLevel.INFO);
      return FeatureFlagDefinition.getDefaultRolloutDefinition(flagName, environment);
    }
    return definition;
  }

  private void patchTreatments(
      NGLogCallback logCallback, Scope scope, String flagName, String environment, List<Treatment> treatments) {
    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName("update definition")
                                   .build();

    List<FmePatchOperation> patch = List.of(FmePatchOperation.replace("/treatments", treatments));

    FmeApiExecutor.executeWrapped(
        fmePipelineClient.patchFeatureFlagDefinition(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), environment, flagName, patch),
        context, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND,
        (flag, env, errorBody) -> format("Failed to update feature flag definition. Error: %s", errorBody));

    logCallback.saveExecutionLog(format("Flag '%s' definition update successful", flagName), LogLevel.INFO);
  }

  private void applyTreatmentChanges(
      FeatureFlagDefinition definition, List<TreatmentTarget> treatmentTargets, NGLogCallback logCallback) {
    Map<String, Treatment> existing = new LinkedHashMap<>();
    Objects.requireNonNullElse(definition.getTreatments(), List.<Treatment>of())
        .forEach(treatment -> existing.put(treatment.getName(), treatment));

    for (TreatmentTarget target : treatmentTargets) {
      String treatmentName = getRequiredParam(target.getTreatment(), "treatment");
      Treatment existingTreatment = existing.get(treatmentName);

      Set<String> keys = new HashSet<>();
      Set<String> segments = new HashSet<>();

      if (existingTreatment != null) {
        if (existingTreatment.getKeys() != null) {
          keys.addAll(existingTreatment.getKeys());
        }
        if (existingTreatment.getSegments() != null) {
          segments.addAll(existingTreatment.getSegments());
        }
      }

      List<String> addKeys = getListValue(target.getAddKeys());
      if (addKeys != null && !addKeys.isEmpty()) {
        keys.addAll(addKeys);
        logCallback.saveExecutionLog(
            format("Adding %d keys to treatment '%s'", addKeys.size(), treatmentName), LogLevel.INFO);
      }

      List<String> removeKeys = getListValue(target.getRemoveKeys());
      if (removeKeys != null && !removeKeys.isEmpty()) {
        keys.removeAll(removeKeys);
        logCallback.saveExecutionLog(
            format("Removing %d keys from treatment '%s'", removeKeys.size(), treatmentName), LogLevel.INFO);
      }

      List<String> addSegments = getListValue(target.getAddSegments());
      if (addSegments != null && !addSegments.isEmpty()) {
        segments.addAll(addSegments);
        logCallback.saveExecutionLog(
            format("Adding %d segments to treatment '%s'", addSegments.size(), treatmentName), LogLevel.INFO);
      }

      List<String> removeSegments = getListValue(target.getRemoveSegments());
      if (removeSegments != null && !removeSegments.isEmpty()) {
        segments.removeAll(removeSegments);
        logCallback.saveExecutionLog(
            format("Removing %d segments from treatment '%s'", removeSegments.size(), treatmentName), LogLevel.INFO);
      }

      TreatmentBuilder builder = existingTreatment != null ? Treatment.from(existingTreatment) : Treatment.builder();
      existing.put(treatmentName,
          builder.name(treatmentName).keys(new ArrayList<>(keys)).segments(new ArrayList<>(segments)).build());
    }

    definition.setTreatments(new ArrayList<>(existing.values()));
  }

  private <T> T getRequiredParam(ParameterField<T> field, String paramName) {
    if (field == null || field.getValue() == null) {
      throw new FmeInvalidParameterException(format("%s is required", paramName));
    }
    return field.getValue();
  }

  private List<String> getListValue(ParameterField<List<String>> field) {
    return field != null ? field.getValue() : null;
  }

  private void validateTreatments(List<TreatmentTarget> treatments) {
    if (treatments == null || treatments.isEmpty()) {
      throw new FmeInvalidParameterException("treatments list cannot be empty");
    }
    if (treatments.stream().anyMatch(
            t -> ParameterField.isNull(t.getTreatment()) || StringUtils.isBlank(t.getTreatment().getValue()))) {
      throw new FmeInvalidParameterException("treatment name is required for each treatment");
    }
  }
}
