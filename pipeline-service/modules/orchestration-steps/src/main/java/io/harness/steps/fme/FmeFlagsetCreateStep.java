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
import io.harness.fme.Flagset;
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

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagsetCreateStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAGSET_CREATE_STEP_TYPE;
  private static final String STEP_NAME = "FME Flagset Create";

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_FLAGSET_CREATE_STEP...");
    logCallback.saveExecutionLog("Starting FME Flagset Create", LogLevel.INFO);

    Scope scope = getScope(ambiance);

    FmeFlagsetCreateParameters p = (FmeFlagsetCreateParameters) stepParameters.getSpec();
    String flagsetName = p.getName() != null ? p.getName().obtainValue() : null;
    String description = Optional.ofNullable(p.getDescription()).map(ParameterField::obtainValue).orElse(null);

    logCallback.saveExecutionLog(
        format("FME Flagset Create Inputs -> account: %s, org: %s, project: %s, flagset: %s, description: %s",
            scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), flagsetName,
            description),
        LogLevel.INFO);

    assertNotEmpty(flagsetName, "Missing required parameter: flagset name");

    Flagset flagset = Flagset.builder().name(flagsetName).description(description).build();

    createFlagset(logCallback, scope, flagset);

    logCallback.saveExecutionLog(
        format("FME Flagset Created: flagset: %s", flagsetName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    return buildSuccessResponse(startTime, STEP_NAME, format("created flagset %s", flagsetName));
  }

  protected void createFlagset(NGLogCallback logCallback, Scope scope, Flagset flagset) {
    ExecutionContext context =
        ExecutionContext.builder().logCallback(logCallback).flagName(flagset.getName()).operationName("create").build();

    FmeApiExecutor.execute(fmePipelineClient.createFlagset(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
                               scope.getProjectIdentifier(), flagset),
        context, NotFoundBehavior.THROW_FLAGSET_NOT_FOUND,
        (flagName, env,
            errorBody) -> format("FME Flagset creation failed (HTTP error from Main service). Error: %s", errorBody));
  }
}
