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
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeApiExecutor.ExecutionContext;
import io.harness.steps.fme.FmeApiExecutor.NotFoundBehavior;
import io.harness.steps.fme.exception.FmeInvalidParameterException;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagRestoreStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_RESTORE_STEP_TYPE;

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME Restore Feature Flag step...");
    logCallback.saveExecutionLog("Starting Restore Feature Flag", LogLevel.INFO);

    Scope scope = getScope(ambiance);
    FmeFlagRestoreStepParameters p = (FmeFlagRestoreStepParameters) stepParameters.getSpec();

    String flagName = p.getFlagName() != null ? p.getFlagName().obtainValue() : null;
    String environment = p.getEnvironment() != null ? p.getEnvironment().obtainValue() : null;

    if (Strings.isNullOrEmpty(flagName)) {
      throw new FmeInvalidParameterException("Feature flag name is required");
    }
    if (Strings.isNullOrEmpty(environment)) {
      throw new FmeInvalidParameterException("Environment is required");
    }

    logCallback.saveExecutionLog(
        format("Restoring feature flag '%s' in environment '%s'", flagName, environment), LogLevel.INFO);

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName("restore")
                                   .build();

    FmeApiExecutor.executeWrapped(fmePipelineClient.restoreFeatureFlag(flagName, scope.getAccountIdentifier(),
                                      scope.getOrgIdentifier(), scope.getProjectIdentifier(), environment),
        context, NotFoundBehavior.THROW_FLAG_NOT_FOUND,
        (flag, env, errorBody) -> format("Error restoring feature flag '%s'. Error: %s", flag, errorBody));

    logCallback.saveExecutionLog(
        format("Successfully restored feature flag '%s' in environment '%s'", flagName, environment), LogLevel.INFO,
        CommandExecutionStatus.SUCCESS);
    return buildSuccessResponseNoOutcome(startTime);
  }
}
