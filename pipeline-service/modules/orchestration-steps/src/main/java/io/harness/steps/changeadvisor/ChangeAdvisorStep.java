/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.changeadvisor;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.changeadvisor.ChangeAdvisorEvaluationHelper.EvaluationResponse;
import io.harness.steps.changeadvisor.ChangeAdvisorEvaluationHelper.EvaluationStatus;
import io.harness.steps.executables.PipelineSyncExecutable;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CV)
@Slf4j
public class ChangeAdvisorStep extends PipelineSyncExecutable {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.CHANGE_ADVISOR_STEP_TYPE;
  static final String OUTCOME_NAME = "changeAdvisor";

  @Inject private ChangeAdvisorEvaluationHelper evaluationHelper;

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, StepBaseParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ChangeAdvisorStepSpecParameters params = ChangeAdvisorEvaluationHelper.extractParams(stepParameters);
    EvaluationResponse evaluationResponse = evaluationHelper.evaluate(ambiance, params);

    if (evaluationResponse.getStatus() == EvaluationStatus.FEATURE_DISABLED
        || evaluationResponse.getStatus() == EvaluationStatus.CALL_FAILED) {
      return StepResponse.builder().status(Status.SUCCEEDED).build();
    }

    if (evaluationResponse.getStatus() == EvaluationStatus.COMING_SOON) {
      return StepResponse.builder()
          .status(Status.SUCCEEDED)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(OUTCOME_NAME)
                           .outcome(evaluationResponse.getComingSoonOutcome())
                           .build())
          .build();
    }

    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OUTCOME_NAME)
                         .outcome(evaluationResponse.getAdvisorOutcome())
                         .build())
        .build();
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }
}
