/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.steps;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.constants.OrchestrationStepTypes;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.ChildrenExecutableWithRollbackAndRbac;
import io.harness.tasks.ResponseData;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * StagesStepWithDag that supports multiple children execution for dependency-based execution.
 * This class implements ChildrenExecutableWithRollbackAndRbac
 */
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class StagesStepWithChildrenSupport extends ChildrenExecutableWithRollbackAndRbac<StagesStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(OrchestrationStepTypes.STAGES_STEP_WITH_DEPENDENCY)
                                               .setStepCategory(StepCategory.STAGES)
                                               .build();

  @Override
  public void validateResources(Ambiance ambiance, StagesStepParameters stepParameters) {
    // Default implementation - no additional validation required for StagesStepWithDag
  }

  /**
   * Multiple children execution - for dependency-based execution
   * This is the new functionality added to support multiple initial stages
   */
  @Override
  public ChildrenExecutableResponse obtainChildrenAfterRbac(
      Ambiance ambiance, StagesStepParameters stepParameters, StepInputPackage inputPackage) {
    if (!isEmpty(stepParameters.getChildrenIds())) {
      ChildrenExecutableResponse.Builder responseBuilder = ChildrenExecutableResponse.newBuilder();
      for (String childId : stepParameters.getChildrenIds()) {
        responseBuilder.addChildren(ChildrenExecutableResponse.Child.newBuilder().setChildNodeId(childId).build());
      }
      // Allow all children to start simultaneously for dependency-based execution
      return responseBuilder.setMaxConcurrency(stepParameters.getChildrenIds().size()).build();
    }
    log.warn("No children IDs found for Stages Step [{}]", stepParameters);
    return ChildrenExecutableResponse.newBuilder().build();
  }

  /**
   * Handle multiple children response - for dependency-based execution
   */
  @Override
  public StepResponse handleChildrenResponse(
      Ambiance ambiance, StagesStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    return createStepResponseFromChildResponse(responseDataMap);
  }

  @Override
  public StepResponse handleChildrenResponseInternal(
      Ambiance ambiance, StagesStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Completed multiple children execution for Stages Step [{}]", stepParameters);
    return createStepResponseFromChildResponse(responseDataMap);
  }

  /**
   * Return the step parameters class
   */
  @Override
  public Class<StagesStepParameters> getStepParametersClass() {
    return StagesStepParameters.class;
  }
}
