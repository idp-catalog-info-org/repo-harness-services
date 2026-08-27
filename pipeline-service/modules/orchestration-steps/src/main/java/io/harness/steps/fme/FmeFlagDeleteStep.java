/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.fme.FeatureFlagDefinition;
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

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagDeleteStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_DELETE_STEP_TYPE;

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_FLAG_DELETE_STEP...");
    logCallback.saveExecutionLog("Starting FME Flag Delete", LogLevel.INFO);

    Scope scope = getScope(ambiance);
    FmeFlagDeleteParameters p = (FmeFlagDeleteParameters) stepParameters.getSpec();

    String flagName = getStringParam(p.getName())
                          .orElseThrow(() -> new FmeInvalidParameterException("Missing required parameter: flag name"));
    boolean deleteAllDefinitions =
        Optional.ofNullable(p.getDeleteAllDefinitions()).map(ParameterField::obtainValue).orElse(false);

    if (deleteAllDefinitions) {
      deleteAllFlagDefinitions(logCallback, scope, flagName);
    }

    logCallback.saveExecutionLog(format("Deleting feature flag '%s'", flagName), LogLevel.INFO);

    ExecutionContext context =
        ExecutionContext.builder().logCallback(logCallback).flagName(flagName).operationName("delete").build();

    FmeApiExecutor.executeWrapped(fmePipelineClient.deleteFeatureFlag(flagName, scope.getAccountIdentifier(),
                                      scope.getOrgIdentifier(), scope.getProjectIdentifier()),
        context, NotFoundBehavior.THROW_FLAG_NOT_FOUND,
        (flag, env, errorBody) -> format("FME Flag delete request failed. Error: %s", errorBody));

    logCallback.saveExecutionLog(
        format("FME Flag Deleted: %s", flagName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return buildSuccessResponse(startTime, "FME Flag Delete", format("deleted flag %s", flagName));
  }

  private void deleteAllFlagDefinitions(NGLogCallback logCallback, Scope scope, String flagName) {
    logCallback.saveExecutionLog(format("Fetching all definitions for flag '%s' to delete", flagName), LogLevel.INFO);

    ExecutionContext listContext = ExecutionContext.builder()
                                       .logCallback(logCallback)
                                       .flagName(flagName)
                                       .operationName("list definitions")
                                       .build();

    List<FeatureFlagDefinition> definitions = FmeApiExecutor.executeWrapped(
        fmePipelineClient.listFeatureFlagDefinitions(
            flagName, scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier()),
        listContext, NotFoundBehavior.RETURN_NULL,
        (flag, env, errorBody) -> format("Failed to list definitions for flag '%s'. Error: %s", flag, errorBody));

    if (isEmpty(definitions)) {
      logCallback.saveExecutionLog(format("No definitions found for flag '%s'", flagName), LogLevel.INFO);
      return;
    }

    logCallback.saveExecutionLog(
        format("Found %d definition(s) for flag '%s'", definitions.size(), flagName), LogLevel.INFO);

    for (FeatureFlagDefinition def : definitions) {
      String envId = def.getEnvironment() != null
          ? StringUtils.defaultIfBlank(def.getEnvironment().name, def.getEnvironment().id)
          : null;

      if (StringUtils.isBlank(envId)) {
        logCallback.saveExecutionLog("Skipping definition with null environment", LogLevel.WARN);
        continue;
      }

      logCallback.saveExecutionLog(format("Deleting definition for environment '%s'", envId), LogLevel.INFO);

      ExecutionContext deleteContext = ExecutionContext.builder()
                                           .logCallback(logCallback)
                                           .flagName(flagName)
                                           .environment(envId)
                                           .operationName("delete definition")
                                           .build();

      FmeApiExecutor.executeWrapped(
          fmePipelineClient.deleteFeatureFlagDefinition(
              flagName, scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), envId),
          deleteContext, NotFoundBehavior.RETURN_NULL,
          (flag, env, errorBody) -> format("Failed to delete definition in '%s'. Error: %s", env, errorBody));
    }

    logCallback.saveExecutionLog(format("Deleted all definitions for flag '%s'", flagName), LogLevel.INFO);
  }

  private Optional<String> getStringParam(ParameterField<String> param) {
    return Optional.ofNullable(param).map(ParameterField::obtainValue).filter(StringUtils::isNotBlank);
  }
}
