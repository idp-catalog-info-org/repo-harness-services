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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagSetTargets extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_SET_TARGETS_STEP_TYPE;

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME Flag Set Targets step...");
    logCallback.saveExecutionLog("Starting FME Flag Set Individual Targets", LogLevel.INFO);

    Scope scope = getScope(ambiance);
    FmeFlagSetTargetsParameters p = (FmeFlagSetTargetsParameters) stepParameters.getSpec();

    String environment = getRequiredParam(p.getEnvironment(), "environment");
    String flagName = getRequiredParam(p.getFlagName(), "flagName");
    List<Target> treatments = getRequiredParam(p.getTreatments(), "treatments");

    validateTreatments(treatments);

    logCallback.saveExecutionLog(
        format("Setting individual keys for flag '%s' in environment '%s'", flagName, environment), LogLevel.INFO);

    setTargets(logCallback, scope, flagName, environment, treatments);

    logCallback.saveExecutionLog(
        format("Successfully set targets for flag '%s'", flagName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return buildSuccessResponseNoOutcome(startTime);
  }

  protected void setTargets(
      NGLogCallback logCallback, Scope scope, String flagName, String environment, List<Target> treatments) {
    List<Treatment> targetsAsTreatments = treatments.stream().map(Treatment::convertTargetToTreatment).toList();

    FeatureFlagDefinition definition = getDefinition(logCallback, scope, flagName, environment);
    List<Treatment> mergedTreatments = mergeTreatments(definition.getTreatments(), targetsAsTreatments, logCallback);

    patchTreatments(logCallback, scope, flagName, environment, mergedTreatments);
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

  private List<Treatment> mergeTreatments(
      List<Treatment> existing, List<Treatment> incoming, NGLogCallback logCallback) {
    Map<String, Treatment> treatmentMap = new LinkedHashMap<>();
    if (existing != null) {
      existing.forEach(t -> treatmentMap.put(t.getName(), t));
    }

    for (Treatment newTreatment : incoming) {
      String name = newTreatment.getName();
      Treatment existingTreatment = treatmentMap.get(name);

      if (existingTreatment != null) {
        treatmentMap.put(name,
            Treatment.from(existingTreatment)
                .keys(newTreatment.getKeys())
                .segments(newTreatment.getSegments())
                .build());
      } else {
        treatmentMap.put(name, newTreatment);
      }

      int keyCount = newTreatment.getKeys() != null ? newTreatment.getKeys().size() : 0;
      int segmentCount = newTreatment.getSegments() != null ? newTreatment.getSegments().size() : 0;
      logCallback.saveExecutionLog(
          format("Setting treatment '%s': %d keys, %d segments", name, keyCount, segmentCount), LogLevel.INFO);
    }

    return new ArrayList<>(treatmentMap.values());
  }

  private <T> T getRequiredParam(ParameterField<T> field, String paramName) {
    if (field == null || field.getValue() == null) {
      throw new FmeInvalidParameterException(format("%s is required", paramName));
    }
    return field.getValue();
  }

  private void validateTreatments(List<Target> treatments) {
    if (treatments == null || treatments.isEmpty()) {
      throw new FmeInvalidParameterException("treatments list cannot be empty");
    }
    for (Target t : treatments) {
      if (ParameterField.isNull(t.getTreatment()) || StringUtils.isBlank(t.getTreatment().getValue())) {
        throw new FmeInvalidParameterException("treatment name is required for each treatment");
      }
    }
  }
}
