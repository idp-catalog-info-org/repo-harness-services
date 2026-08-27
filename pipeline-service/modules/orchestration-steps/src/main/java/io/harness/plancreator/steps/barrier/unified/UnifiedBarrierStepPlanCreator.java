/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.barrier.unified;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.expression.EngineExpressionEvaluator.hasCelExpressions;
import static io.harness.expression.EngineExpressionEvaluator.hasExpressions;
import static io.harness.steps.StepSpecTypeConstants.BARRIER_STEP_TYPE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ci.plan.creator.step.unified.UnifiedPmsAbstractStepPlanCreator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.unified.UnifiedPmsAbstractStepNode;
import io.harness.plancreator.strategy.StrategyUtilsV1;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.utils.PlanCreatorUtilsCommon;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(CI)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class UnifiedBarrierStepPlanCreator extends UnifiedPmsAbstractStepPlanCreator<UnifiedBarrierStepNode> {
  @Inject private BarrierService barrierService;
  @Override
  public Class<UnifiedBarrierStepNode> getFieldClass() {
    return UnifiedBarrierStepNode.class;
  }

  @Override
  public UnifiedBarrierStepNode getFieldObject(YamlField field) {
    try {
      return YamlUtils.read(field.getNode().toString(), UnifiedBarrierStepNode.class);
    } catch (IOException e) {
      throw new InvalidYamlException("Unable to parse barrier step yaml.", e);
    }
  }

  @Override
  public Set<String> getSupportedStepTypes() {
    return Set.of(YAMLFieldNameConstants.BARRIER_V1);
  }

  @Override
  protected SpecParameters getSpec(UnifiedPmsAbstractStepNode stepNode) {
    return ((UnifiedBarrierStepNode) stepNode).getUnifiedBarrierStepInfo().getSpecParameters();
  }

  @Override
  protected StepType getStepType() {
    return BARRIER_STEP_TYPE;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, UnifiedPmsAbstractStepNode stepNode) {
    UnifiedBarrierStepNode unifiedBarrierStepNode = (UnifiedBarrierStepNode) stepNode;
    if (StrategyUtilsV1.isWrappedUnderStrategy(ctx.getCurrentField())) {
      throw new InvalidRequestException("Barrier step cannot be configured with looping strategy.");
    }
    String planExecutionId = ctx.getExecutionUuid();
    String parentInfoStrategyNodeType =
        PlanCreatorUtilsCommon.getFromParentInfo(PlanCreatorConstants.STRATEGY_NODE_TYPE, ctx).getStringValue();
    String stageId = PlanCreatorUtilsCommon.getFromParentInfo(PlanCreatorConstants.STAGE_ID, ctx).getStringValue();
    String stepGroupId =
        PlanCreatorUtilsCommon.getFromParentInfo(PlanCreatorConstants.STEP_GROUP_ID, ctx).getStringValue();
    String strategyId =
        PlanCreatorUtilsCommon.getFromParentInfo(PlanCreatorConstants.NEAREST_STRATEGY_ID, ctx).getStringValue();
    String barrierName = unifiedBarrierStepNode.getUnifiedBarrierStepInfo().getName();
    if (hasExpressions(barrierName) || hasCelExpressions(barrierName)) {
      throw new InvalidRequestException(
          "Variable expressions are not allowed in Barrier Reference. Found: " + barrierName);
    }
    List<String> allStrategyIds = PlanCreatorUtilsCommon.getFromParentInfo(PlanCreatorConstants.ALL_STRATEGY_IDS, ctx)
                                      .getListValue()
                                      .getValuesList()
                                      .stream()
                                      .map(HarnessValue::getStringValue)
                                      .collect(Collectors.toList());
    barrierService.upsertBarrierExecutionInstance(stepNode.getUuid(), barrierName, barrierName, planExecutionId,
        parentInfoStrategyNodeType, stageId, stepGroupId, strategyId, allStrategyIds);
    return super.createPlanForField(ctx, stepNode);
  }
}
