/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.fme.SegmentKeysDTO;
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

import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeSegmentAddRemoveTargetsStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_SEGMENT_ADD_REMOVE_TARGETS_STEP_TYPE;

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_SEGMENT_ADD_REMOVE_TARGETS_STEP...");
    logCallback.saveExecutionLog("Starting FME Segment Add/Remove Targets", LogLevel.INFO);

    Scope scope = getScope(ambiance);

    FmeSegmentAddRemoveTargetsParameters p = (FmeSegmentAddRemoveTargetsParameters) stepParameters.getSpec();
    String segmentName = p.getSegmentName() != null ? p.getSegmentName().obtainValue() : null;
    String environment = p.getEnvironment() != null ? p.getEnvironment().obtainValue() : null;
    List<String> addKeys = p.getAddKeys() != null ? p.getAddKeys().obtainValue() : null;
    List<String> removeKeys = p.getRemoveKeys() != null ? p.getRemoveKeys().obtainValue() : null;

    int addCount = addKeys != null ? addKeys.size() : 0;
    int removeCount = removeKeys != null ? removeKeys.size() : 0;

    logCallback.saveExecutionLog(
        format("FME Segment Add/Remove Targets Inputs -> account: %s, org: %s, project: %s, segment: %s, "
                + "environment: %s, addKeys count: %d, removeKeys count: %d",
            scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), segmentName,
            environment, addCount, removeCount),
        LogLevel.INFO);

    assertNotEmpty(segmentName, "Missing required parameter: segment name");
    assertNotEmpty(environment, "Missing required parameter: environment");
    if (addCount == 0 && removeCount == 0) {
      throw new io.harness.steps.fme.exception.FmeInvalidParameterException(
          "At least one of addKeys or removeKeys must be provided");
    }

    SegmentKeysDTO payload = SegmentKeysDTO.builder()
                                 .segmentName(segmentName)
                                 .environment(environment)
                                 .addKeys(addKeys)
                                 .removeKeys(removeKeys)
                                 .build();

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(segmentName)
                                   .environment(environment)
                                   .operationName("add/remove segment keys")
                                   .build();

    FmeApiExecutor.execute(fmePipelineClient.updateSegmentKeys(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
                               scope.getProjectIdentifier(), payload),
        context, NotFoundBehavior.THROW_SEGMENT_NOT_FOUND,
        (flag, env, errorBody) -> format("Failed to add/remove segment keys. Error: %s", errorBody));

    logCallback.saveExecutionLog(format("FME Segment Add/Remove Targets completed: segment: %s, added: %d, removed: %d",
                                     segmentName, addCount, removeCount),
        LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    return buildSuccessResponseNoOutcome(startTime);
  }
}
