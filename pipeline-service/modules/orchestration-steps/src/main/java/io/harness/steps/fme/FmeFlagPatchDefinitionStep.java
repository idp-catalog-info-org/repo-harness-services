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
import io.harness.fme.FmePatchOperation;
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagPatchDefinitionStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_PATCH_DEFINITION_STEP_TYPE;
  private static final String STEP_NAME = "FME Flag Patch Definition";

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_FLAG_PATCH_DEFINITION_STEP...");
    logCallback.saveExecutionLog("Starting FME Flag Patch Definition", LogLevel.INFO);

    Scope scope = getScope(ambiance);

    // Extract spec parameters
    FmeFlagPatchDefinitionParameters parameters = (FmeFlagPatchDefinitionParameters) stepParameters.getSpec();
    String flagName = testStringParameter(parameters.getFlagName())
                          .orElseThrow(() -> new FmeInvalidParameterException("Missing required parameter: flagName"));
    String environment =
        testStringParameter(parameters.getEnvironment())
            .orElseThrow(() -> new FmeInvalidParameterException("Missing required parameter: environment"));
    String operations =
        testStringParameter(parameters.getOperations())
            .orElseThrow(() -> new FmeInvalidParameterException("Missing required parameter: operations"));

    // Log inputs (avoid secrets)
    logCallback.saveExecutionLog(format("FME Flag Patch Definition Inputs -> "
                                         + "account: %s, org: %s, project: %s, flag: %s, environment: %s",
                                     scope.getAccountIdentifier(), scope.getOrgIdentifier(),
                                     scope.getProjectIdentifier(), flagName, environment),
        LogLevel.INFO);

    ObjectMapper objectMapper = new ObjectMapper();
    List<FmePatchOperation> jsonPatch;
    try {
      jsonPatch = objectMapper.readValue(operations, new TypeReference<List<FmePatchOperation>>() {});
    } catch (Exception e) {
      throw new FmeInvalidParameterException(format("Failed to parse operations as JSON patch: %s", e.getMessage()));
    }

    patchDefinition(logCallback, scope, flagName, environment, jsonPatch);

    logCallback.saveExecutionLog(format("FME Flag Patch Definition: flag: %s, environment: %s", flagName, environment),
        LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    return buildSuccessResponse(
        startTime, STEP_NAME, format("patched flag definition %s in environment %s", flagName, environment));
  }

  private void patchDefinition(
      NGLogCallback logCallback, Scope scope, String flagName, String environment, List<FmePatchOperation> jsonPatch) {
    logCallback.saveExecutionLog(
        format("Calling FME API to patch definition for flag '%s' in environment '%s'", flagName, environment),
        LogLevel.INFO);

    var call = fmePipelineClient.patchFeatureFlagDefinition(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
        scope.getProjectIdentifier(), environment, flagName, jsonPatch);

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName("patch definition")
                                   .build();

    FmeApiExecutor.executeWrapped(call, context, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND,
        (flag, env, errorBody) -> format("FME Flag Patch Definition request failed. Error: %s", errorBody));

    logCallback.saveExecutionLog(
        format("Successfully patched definition for flag '%s' in environment '%s'", flagName, environment),
        LogLevel.INFO);
  }

  private <T> Optional<T> getParameter(ParameterField<T> parameter) {
    return Optional.ofNullable(parameter).map(ParameterField::obtainValue);
  }

  private Optional<String> testStringParameter(ParameterField<String> parameter) {
    return getParameter(parameter).filter(s -> !Strings.isNullOrEmpty(s));
  }
}
