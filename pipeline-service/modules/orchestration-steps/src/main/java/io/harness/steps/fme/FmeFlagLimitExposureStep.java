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

import com.google.common.base.Strings;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagLimitExposureStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_LIMIT_EXPOSURE_STEP_TYPE;
  private static final String STEP_NAME = "FME Flag Limit Exposure";

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_FLAG_LIMIT_EXPOSURE_STEP...");
    logCallback.saveExecutionLog("Starting FME Flag Limit Exposure", LogLevel.INFO);

    Scope scope = getScope(ambiance);

    // Extract spec parameters
    FmeFlagLimitExposureParameters parameters = (FmeFlagLimitExposureParameters) stepParameters.getSpec();
    String environment =
        testStringParameter(parameters.getEnvironment())
            .orElseThrow(() -> new FmeInvalidParameterException("Missing required parameter: environment"));
    String flagName = testStringParameter(parameters.getFlagName())
                          .orElseThrow(() -> new FmeInvalidParameterException("Missing required parameter: flag name"));
    Integer limit = getParameter(parameters.getLimit())
                        .orElseThrow(() -> new FmeInvalidParameterException("Missing required parameter: limit"));

    // Log inputs
    logCallback.saveExecutionLog(format("FME Flag Limit Exposure Inputs -> "
                                         + "account: %s, org: %s, project: %s, environment: %s, flag: %s, limit: %d",
                                     scope.getAccountIdentifier(), scope.getOrgIdentifier(),
                                     scope.getProjectIdentifier(), environment, flagName, limit),
        LogLevel.INFO);

    limitExposure(logCallback, scope, flagName, environment, limit);

    logCallback.saveExecutionLog(
        format("FME Flag limit exposure updated: environment: %s, flag: %s, limit: %d", environment, flagName, limit),
        LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    return buildSuccessResponse(startTime, STEP_NAME,
        format("Limited exposure for flag %s in environment %s to %d", flagName, environment, limit));
  }

  private void limitExposure(
      NGLogCallback logCallback, Scope scope, String flagName, String environment, Integer limit) {
    logCallback.saveExecutionLog(format("Calling FME API to limit exposure for flag '%s' in environment '%s' to %d",
                                     flagName, environment, limit),
        LogLevel.INFO);

    List<FmePatchOperation> jsonPatch = List.of(FmePatchOperation.replace("/trafficAllocation", limit));

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName("limit exposure")
                                   .build();

    FmeApiExecutor.executeWrapped(
        fmePipelineClient.patchFeatureFlagDefinition(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), environment, flagName, jsonPatch),
        context, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND,
        (flag, env, errorBody) -> format("FME Flag limit exposure request failed. Error: %s", errorBody));
  }

  private <T> Optional<T> getParameter(ParameterField<T> parameter) {
    return Optional.ofNullable(parameter).map(ParameterField::obtainValue);
  }

  private Optional<String> testStringParameter(ParameterField<String> parameter) {
    return getParameter(parameter).filter(s -> !Strings.isNullOrEmpty(s));
  }
}
