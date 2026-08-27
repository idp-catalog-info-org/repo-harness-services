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
import io.harness.steps.fme.exception.FmeFeatureFlagDefinitionNotFoundException;
import io.harness.steps.fme.exception.FmeInvalidParameterException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagSetDynamicConfigurationsStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_SET_DYNAMIC_CONFIGURATIONS_STEP_TYPE;

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME Flag Set Dynamic Configurations step...");
    logCallback.saveExecutionLog("Starting FME Flag Set Dynamic Configurations", LogLevel.INFO);

    Scope scope = getScope(ambiance);
    FmeFlagSetDynamicConfigurationsParameters p = (FmeFlagSetDynamicConfigurationsParameters) stepParameters.getSpec();

    String environment = getRequiredParam(p.getEnvironment(), "environment");
    String flagName = getRequiredParam(p.getFlagName(), "flagName");
    List<TreatmentDynamicConfiguration> treatments = getRequiredParam(p.getTreatments(), "treatments");

    validateTreatments(treatments);

    logCallback.saveExecutionLog(
        format("Processing dynamic configuration for flag '%s' in environment '%s'", flagName, environment),
        LogLevel.INFO);

    processDynamicConfiguration(logCallback, scope, flagName, environment, treatments);

    logCallback.saveExecutionLog(
        "Flag dynamic configuration updated successfully", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return buildSuccessResponseNoOutcome(startTime);
  }

  protected void processDynamicConfiguration(NGLogCallback logCallback, Scope scope, String flagName,
      String environment, List<TreatmentDynamicConfiguration> treatments) {
    FeatureFlagDefinition definition = getDefinition(logCallback, scope, flagName, environment);
    applyTreatmentConfigurations(flagName, environment, definition, treatments, logCallback);
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
        context, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND,
        (flag, env, errorBody) -> format("Failed to get feature flag definition. Error: %s", errorBody));

    if (definition == null || definition.getTreatments() == null) {
      throw new FmeFeatureFlagDefinitionNotFoundException(flagName, environment);
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

  private void applyTreatmentConfigurations(String flagName, String environment, FeatureFlagDefinition definition,
      List<TreatmentDynamicConfiguration> treatmentConfigs, NGLogCallback logCallback) {
    Map<String, TreatmentDynamicConfiguration> configByName = new HashMap<>();
    for (TreatmentDynamicConfiguration config : treatmentConfigs) {
      configByName.put(getRequiredParam(config.getTreatment(), "treatment name"), config);
    }

    Set<String> existingNames = definition.getTreatments().stream().map(Treatment::getName).collect(Collectors.toSet());
    Set<String> nonexistent =
        configByName.keySet().stream().filter(name -> !existingNames.contains(name)).collect(Collectors.toSet());

    if (!nonexistent.isEmpty()) {
      throw new FmeInvalidParameterException(
          format("Treatments not found %s for flag '%s' in environment '%s'", nonexistent, flagName, environment));
    }

    List<String> updated = new ArrayList<>();
    for (Treatment treatment : definition.getTreatments()) {
      TreatmentDynamicConfiguration config = configByName.get(treatment.getName());
      if (config != null) {
        String configuration = config.getConfiguration() != null ? config.getConfiguration().getValue() : null;
        validateJsonSyntax(treatment.getName(), configuration);
        treatment.setConfigurations(configuration);
        updated.add(treatment.getName());
      }
    }

    logCallback.saveExecutionLog(format("Updating configuration for treatments %s", updated), LogLevel.INFO);
  }

  private void validateJsonSyntax(String treatmentName, String json) {
    if (json == null) {
      return;
    }
    try {
      JsonNode node = new ObjectMapper().readTree(json);
      if (!node.isObject()) {
        throw new FmeInvalidParameterException(
            format("Configuration must be a JSON object for treatment %s, got: %s", treatmentName, json));
      }
    } catch (FmeInvalidParameterException e) {
      throw e;
    } catch (Exception e) {
      throw new FmeInvalidParameterException(format("Invalid JSON for treatment %s: %s", treatmentName, json));
    }
  }

  private <T> T getRequiredParam(ParameterField<T> field, String paramName) {
    if (field == null || field.getValue() == null) {
      throw new FmeInvalidParameterException(format("%s is required", paramName));
    }
    return field.getValue();
  }

  private void validateTreatments(List<TreatmentDynamicConfiguration> treatments) {
    if (treatments == null || treatments.isEmpty()) {
      throw new FmeInvalidParameterException("treatments list cannot be empty");
    }
    for (TreatmentDynamicConfiguration t : treatments) {
      if (ParameterField.isNull(t.getTreatment()) || StringUtils.isBlank(t.getTreatment().getValue())) {
        throw new FmeInvalidParameterException("treatment name is required for each treatment");
      }
    }
  }
}
