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

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagSetTreatmentsStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_SET_TREATMENTS_STEP_TYPE;

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_FLAG_SET_TREATMENTS_STEP...");
    logCallback.saveExecutionLog("Starting FME Flag Set Treatments", LogLevel.INFO);

    Scope scope = getScope(ambiance);
    FmeFlagSetTreatmentsParameters p = (FmeFlagSetTreatmentsParameters) stepParameters.getSpec();

    String environment = getRequiredStringParam(p.getEnvironment(), "environment");
    String flagName = getRequiredStringParam(p.getFlagName(), "flag name");
    String defaultTreatment = getRequiredStringParam(p.getDefaultTreatment(), "defaultTreatment");
    String baselineTreatment = getRequiredStringParam(p.getBaselineTreatment(), "baselineTreatment");
    List<TreatmentConfiguration> treatments = getRequiredParam(p.getTreatments(), "treatments");

    if (treatments.isEmpty()) {
      throw new FmeInvalidParameterException("treatments list cannot be empty");
    }

    logCallback.saveExecutionLog(
        format("Setting %d treatments for flag '%s' in environment '%s'", treatments.size(), flagName, environment),
        LogLevel.INFO);

    FeatureFlagDefinition definition = getDefinition(logCallback, scope, flagName, environment);

    Map<String, Treatment> existingTreatments = definition.getTreatments() != null
        ? definition.getTreatments().stream().collect(Collectors.toMap(Treatment::getName, t -> t))
        : Map.of();

    List<Treatment> updatedTreatments = new ArrayList<>();
    for (TreatmentConfiguration tc : treatments) {
      String treatmentName = getRequiredStringParam(tc.getTreatment(), "treatment name");
      String description = tc.getDescription() != null ? tc.getDescription().getValue() : null;

      Treatment existing = existingTreatments.get(treatmentName);
      Treatment updated = existing != null ? Treatment.from(existing).description(description).build()
                                           : Treatment.builder().name(treatmentName).description(description).build();
      updatedTreatments.add(updated);
    }

    patchTreatmentsAndDefault(
        logCallback, scope, flagName, environment, updatedTreatments, defaultTreatment, baselineTreatment);

    logCallback.saveExecutionLog(format("Updated %d treatments for flag '%s'", updatedTreatments.size(), flagName),
        LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return buildSuccessResponse(
        startTime, "FME Flag Set Treatments", format("Set treatments for %s in environment %s", flagName, environment));
  }

  private FeatureFlagDefinition getDefinition(
      NGLogCallback logCallback, Scope scope, String flagName, String environment) {
    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName("get definition")
                                   .build();

    return FmeApiExecutor.executeWrapped(
        fmePipelineClient.getFeatureFlagDefinitionInEnvironment(flagName, scope.getAccountIdentifier(),
            scope.getOrgIdentifier(), scope.getProjectIdentifier(), environment),
        context, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND,
        (flag, env, errorBody) -> format("Failed to get feature flag definition. Error: %s", errorBody));
  }

  private void patchTreatmentsAndDefault(NGLogCallback logCallback, Scope scope, String flagName, String environment,
      List<Treatment> treatments, String defaultTreatment, String baselineTreatment) {
    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName("update definition")
                                   .build();

    List<FmePatchOperation> patch = List.of(FmePatchOperation.replace("/treatments", treatments),
        FmePatchOperation.replace("/defaultTreatment", defaultTreatment),
        FmePatchOperation.replace("/baselineTreatment", baselineTreatment));

    FmeApiExecutor.executeWrapped(
        fmePipelineClient.patchFeatureFlagDefinition(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), environment, flagName, patch),
        context, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND,
        (flag, env, errorBody) -> format("FME Flag Set Treatments request failed. Error: %s", errorBody));
  }

  private String getRequiredStringParam(ParameterField<String> param, String name) {
    return Optional.ofNullable(param)
        .map(ParameterField::obtainValue)
        .filter(s -> !Strings.isNullOrEmpty(s))
        .orElseThrow(() -> new FmeInvalidParameterException(format("Missing required parameter: %s", name)));
  }

  private <T> T getRequiredParam(ParameterField<T> param, String name) {
    return Optional.ofNullable(param)
        .map(ParameterField::obtainValue)
        .orElseThrow(() -> new FmeInvalidParameterException(format("Missing required parameter: %s", name)));
  }
}
