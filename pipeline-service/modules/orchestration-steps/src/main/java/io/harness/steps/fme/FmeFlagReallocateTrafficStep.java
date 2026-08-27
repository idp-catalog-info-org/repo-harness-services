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
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagReallocateTrafficStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_REALLOCATE_TRAFFIC_STEP_TYPE;
  private static final String STEP_NAME = "FME Flag Reallocate Traffic";

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_FLAG_REALLOCATE_TRAFFIC_STEP...");
    logCallback.saveExecutionLog("Starting FME Flag Reallocate Traffic", LogLevel.INFO);

    Scope scope = getScope(ambiance);

    // Extract spec parameters
    FmeFlagReallocateTrafficParameters parameters = (FmeFlagReallocateTrafficParameters) stepParameters.getSpec();
    String environment =
        testStringParameter(parameters.getEnvironment())
            .orElseThrow(() -> new FmeInvalidParameterException("Missing required parameter: environment"));
    String flagName = testStringParameter(parameters.getFlagName())
                          .orElseThrow(() -> new FmeInvalidParameterException("Missing required parameter: flag name"));

    // Log inputs
    logCallback.saveExecutionLog(format("FME Flag Reallocate Traffic Inputs -> "
                                         + "account: %s, org: %s, project: %s, environment: %s, flag: %s",
                                     scope.getAccountIdentifier(), scope.getOrgIdentifier(),
                                     scope.getProjectIdentifier(), environment, flagName),
        LogLevel.INFO);

    reallocateTraffic(logCallback, scope, flagName, environment);

    logCallback.saveExecutionLog(
        format("FME Flag traffic reallocated: environment: %s, flag: %s", environment, flagName), LogLevel.INFO,
        CommandExecutionStatus.SUCCESS);

    return buildSuccessResponse(
        startTime, STEP_NAME, format("Reallocated traffic for flag %s in environment %s", flagName, environment));
  }

  private void reallocateTraffic(NGLogCallback logCallback, Scope scope, String flagName, String environment) {
    logCallback.saveExecutionLog(
        format("Calling FME API to reallocate traffic for flag '%s' in environment '%s'", flagName, environment),
        LogLevel.INFO);

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName("reallocate traffic")
                                   .build();

    FmeApiExecutor.executeWrapped(fmePipelineClient.reallocateFeatureFlag(flagName, scope.getAccountIdentifier(),
                                      scope.getOrgIdentifier(), scope.getProjectIdentifier(), environment),
        context, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND,
        (flag, env, errorBody) -> format("FME Flag reallocate traffic request failed. Error: %s", errorBody));
  }

  private <T> Optional<T> getParameter(ParameterField<T> parameter) {
    return Optional.ofNullable(parameter).map(ParameterField::obtainValue);
  }

  private Optional<String> testStringParameter(ParameterField<String> parameter) {
    return getParameter(parameter).filter(s -> !Strings.isNullOrEmpty(s));
  }
}
