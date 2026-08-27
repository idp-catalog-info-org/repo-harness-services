/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.stage.v1;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.constants.OrchestrationStepTypes;
import io.harness.plancreator.steps.common.v1.StageElementParametersV1;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.approval.stage.ApprovalStageSpecParameters;
import io.harness.tasks.ResponseData;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ApprovalStageStep implements ChildExecutable<StageElementParametersV1> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(OrchestrationStepTypes.APPROVAL_STAGE_V1)
                                               .setStepCategory(StepCategory.STAGE)
                                               .build();
  @Override
  public Class<StageElementParametersV1> getStepParametersClass() {
    return StageElementParametersV1.class;
  }

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, StageElementParametersV1 stepParameters, StepInputPackage inputPackage) {
    ApprovalStageSpecParameters specParameters = (ApprovalStageSpecParameters) stepParameters.getSpec();
    String executionNodeId = specParameters.getChildNodeID();
    return ChildExecutableResponse.newBuilder().setChildNodeId(executionNodeId).build();
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, StageElementParametersV1 stepParameters, Map<String, ResponseData> responseDataMap) {
    return createStepResponseFromChildResponse(responseDataMap);
  }
}
