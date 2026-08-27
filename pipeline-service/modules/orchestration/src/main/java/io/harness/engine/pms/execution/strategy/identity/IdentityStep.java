/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;
import static io.harness.plan.NodeType.IDENTITY_PLAN_NODE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.constants.OrchestrationStepTypes;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.engine.pms.steps.identity.IdentityStepParameters;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.PIPELINE)
public class IdentityStep
    implements ChildExecutable<IdentityStepParameters>, ChildrenExecutable<IdentityStepParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(OrchestrationStepTypes.IDENTITY_STEP).setStepCategory(StepCategory.STEP).build();

  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsSweepingOutputService pmsSweepingOutputService;
  @Inject private IdentityStepHelper identityStepHelper;
  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, IdentityStepParameters identityParams, StepInputPackage inputPackage) {
    NodeExecution originalNodeExecution = nodeExecutionService.getWithFieldsIncluded(
        identityParams.getOriginalNodeExecutionId(), NodeProjectionUtils.withExecutableResponses);
    // Copying the outputs
    pmsSweepingOutputService.cloneForRetryExecution(ambiance, identityParams.getOriginalNodeExecutionId());
    if (originalNodeExecution.getExecutableResponses().isEmpty()) {
      return ChildExecutableResponse.newBuilder().setSkip(true).build();
    }
    // Find the first executable response that has a child, required because in case of manual run, the first entry will
    // be facilitator
    return originalNodeExecution.getExecutableResponses()
        .stream()
        .filter(ExecutableResponse::hasChild)
        .findFirst()
        .map(ExecutableResponse::getChild)
        .orElse(ChildExecutableResponse.newBuilder().setSkip(true).build());
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, IdentityStepParameters identityParams, Map<String, ResponseData> responseDataMap) {
    return identityStepHelper.handleChildResponse(ambiance, identityParams, responseDataMap);
  }

  @Override
  public ChildrenExecutableResponse obtainChildren(
      Ambiance ambiance, IdentityStepParameters identityParams, StepInputPackage inputPackage) {
    NodeExecution originalNodeExecution = nodeExecutionService.get(identityParams.getOriginalNodeExecutionId());
    // Copying the outputs here
    pmsSweepingOutputService.cloneForRetryExecution(ambiance, originalNodeExecution.getUuid());

    // Find the first executable response that has children, required because in case of manual run, the first entry
    // will be facilitator
    return originalNodeExecution.getExecutableResponses()
        .stream()
        .filter(ExecutableResponse::hasChildren)
        .findFirst()
        .map(ExecutableResponse::getChildren)
        .orElse(ChildrenExecutableResponse.newBuilder().build());
  }

  @Override
  public StepResponse handleChildrenResponse(
      Ambiance ambiance, IdentityStepParameters identityParams, Map<String, ResponseData> responseDataMap) {
    return identityStepHelper.handleChildrenResponse(ambiance, identityParams, responseDataMap);
  }

  @Override
  public Class<IdentityStepParameters> getStepParametersClass() {
    return IdentityStepParameters.class;
  }

  public static Ambiance modifyAmbiance(Ambiance ambiance) {
    Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
    StepCategory stepCategory = level.getStepType().getStepCategory();
    Optional<Level> strategyLevel = AmbianceUtils.getStrategyLevelFromAmbiance(ambiance);
    if (strategyLevel.isPresent() && strategyLevel.get().getNodeType().equals(IDENTITY_PLAN_NODE.name())) {
      String stepType = IdentityStrategyStep.STEP_TYPE.getType();
      if (stepCategory != StepCategory.STRATEGY) {
        stepType = IdentityStrategyInternalStep.STEP_TYPE.getType();
      }
      return AmbianceUtils.cloneForFinish(ambiance,
          level.toBuilder()
              .setStepType(StepType.newBuilder().setType(stepType).setStepCategory(StepCategory.STRATEGY).build())
              .build());
    }
    return AmbianceUtils.cloneForFinish(ambiance,
        level.toBuilder()
            .setStepType(StepType.newBuilder().setType("IDENTITY_STEP").setStepCategory(StepCategory.STEP).build())
            .build());
  }
}
