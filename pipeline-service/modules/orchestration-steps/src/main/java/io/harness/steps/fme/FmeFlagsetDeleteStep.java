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

import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagsetDeleteStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAGSET_DELETE_STEP_TYPE;
  private static final String STEP_NAME = "FME Flagset Delete";

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_FLAGSET_DELETE_STEP...");
    logCallback.saveExecutionLog("Starting FME Flagset Delete", LogLevel.INFO);

    Scope scope = getScope(ambiance);

    FmeFlagsetDeleteParameters p = (FmeFlagsetDeleteParameters) stepParameters.getSpec();
    String flagsetName = p.getName() != null ? p.getName().obtainValue() : null;

    logCallback.saveExecutionLog(
        format("FME Flagset Delete Inputs -> account: %s, org: %s, project: %s, flagset: %s",
            scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), flagsetName),
        LogLevel.INFO);

    assertNotEmpty(flagsetName, "Missing required parameter: flagset name");

    deleteFlagset(logCallback, scope, flagsetName);

    logCallback.saveExecutionLog(
        format("FME Flagset Deleted: flagset: %s", flagsetName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    return buildSuccessResponse(startTime, STEP_NAME, format("deleted flagset %s", flagsetName));
  }

  protected void deleteFlagset(NGLogCallback logCallback, Scope scope, String flagsetName) {
    ExecutionContext context =
        ExecutionContext.builder().logCallback(logCallback).flagName(flagsetName).operationName("delete").build();

    FmeApiExecutor.execute(fmePipelineClient.deleteFlagset(flagsetName, scope.getAccountIdentifier(),
                               scope.getOrgIdentifier(), scope.getProjectIdentifier()),
        context, NotFoundBehavior.THROW_FLAGSET_NOT_FOUND,
        (flagName, env, errorBody) -> format("FME Flagset deletion request failed. Error: %s", errorBody));
  }
}
