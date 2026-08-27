/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.tasks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.ci.execution.integrationstage.ci.CIStepGroupUtils;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.CI)
public class RunnerTaskExecutor {
  @Inject private CommonAbstractStepUtils commonAbstractStepUtils;
  @Inject private RunnerTaskExecutorUtils runnerTaskExecutorUtils;

  public String submitTask(RunStepInfoV1 runStepInfo, Ambiance ambiance, String stepId, Long timeOutInMillis) {
    StepElementParametersV1 stepParameters = StepElementParametersV1.builder().spec(runStepInfo).build();
    StageDetails stageDetails = commonAbstractStepUtils.getStageDetails(ambiance, stepParameters.getType());
    StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();
    if (stageInfraType == StageInfraDetails.Type.K8 || stageInfraType.name().equals("K8")) {
      String completeStepId = CIStepGroupUtils.getUniqueStepIdentifier(ambiance.getLevelsList(), stepId);
      return runnerTaskExecutorUtils.submitK8ExecuteTask(
          stepParameters, ambiance, completeStepId, stageInfraDetails, timeOutInMillis);
    } else {
      return runnerTaskExecutorUtils.submitRunnerExecuteTask(
          stepParameters, runStepInfo, ambiance, stepId, stageDetails, stageInfraDetails, timeOutInMillis);
    }
  }
}
