/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.barrier;

import static io.harness.expression.EngineExpressionEvaluator.hasExpressions;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.utils.PlanCreatorUtilsCommon;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class BarrierStepPlanCreator extends PMSStepPlanCreatorV2<BarrierStepNode> {
  @Inject private BarrierService barrierService;

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.BARRIER);
  }

  @Override
  public Class<BarrierStepNode> getFieldClass() {
    return BarrierStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, BarrierStepNode field) {
    if (StrategyUtils.isWrappedUnderStrategy(ctx.getCurrentField())) {
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
    List<String> allStrategyIds = PlanCreatorUtilsCommon.getFromParentInfo(PlanCreatorConstants.ALL_STRATEGY_IDS, ctx)
                                      .getListValue()
                                      .getValuesList()
                                      .stream()
                                      .map(HarnessValue::getStringValue)
                                      .collect(Collectors.toList());

    String barrierIdentifier = field.getBarrierStepInfo().getIdentifier();

    if (hasExpressions(barrierIdentifier)) {
      throw new InvalidRequestException(
          "Variable expressions are not allowed in Barrier Reference. Found: " + barrierIdentifier);
    }

    boolean isParentBarrier = field.getBarrierStepInfo().getIdentifier().startsWith(YAMLFieldNameConstants.PARENT_DOT);

    if (isParentBarrier) {
      barrierIdentifier = normalizeBarrierIdentifier(barrierIdentifier);

      ChainedPipelineResult result = processChainedPipelineBarrier(ctx, barrierIdentifier);
      if (result.shouldUpdatePlanExecutionId()) {
        planExecutionId = result.getParentPlanExecutionId();
      }
    }

    barrierService.upsertBarrierExecutionInstance(field.getUuid(), barrierIdentifier,
        field.getBarrierStepInfo().getName(), planExecutionId, parentInfoStrategyNodeType, stageId, stepGroupId,
        strategyId, allStrategyIds);
    return super.createPlanForField(ctx, field);
  }

  /**
   * Normalizes barrier identifiers by removing parent prefix if present.
   * For chained pipelines, barriers will be referenced with a "parent." prefix.
   *
   * @param barrierIdentifier Original barrier identifier
   * @return Normalized barrier identifier without parent prefix
   */
  private String normalizeBarrierIdentifier(String barrierIdentifier) {
    if (barrierIdentifier.startsWith(YAMLFieldNameConstants.PARENT_DOT)) {
      return barrierIdentifier.split("\\.")[1];
    }
    return barrierIdentifier;
  }

  /**
   * Processes barrier for chained pipeline scenarios.
   * When a child pipeline references a barrier from its parent pipeline,
   * this method handles the synchronization between them.
   *
   * @param ctx               Plan creation context with execution information
   * @param barrierIdentifier Normalized barrier identifier
   * @return Result indicating if parent plan execution ID should be used
   */
  private ChainedPipelineResult processChainedPipelineBarrier(PlanCreationContext ctx, String barrierIdentifier) {
    // Only process for child pipelines with parent relationships
    if (!ctx.getMetadata().getExecutionContext().hasPipelineStageInfo()
        || !ctx.getMetadata().getExecutionContext().getPipelineStageInfo().getHasParentPipeline()) {
      return ChainedPipelineResult.noUpdate();
    }

    // Extract parent pipeline information
    String pipelineStageNodeId = ctx.getMetadata().getExecutionContext().getPipelineStageInfo().getStageNodeId();
    String parentPlanExecutionId = ctx.getMetadata().getExecutionContext().getPipelineStageInfo().getExecutionId();

    // Find the barrier in the parent pipeline
    BarrierExecutionInstance barrierExecutionInstance =
        barrierService.findByIdentifierAndPlanExecutionId(barrierIdentifier, parentPlanExecutionId);

    if (barrierExecutionInstance == null) {
      log.error(
          "Barrier '{}' was not found in parent execution '{}', even though its identifier had the [parent.] prefix.",
          barrierIdentifier, parentPlanExecutionId);
      return ChainedPipelineResult.noUpdate();
    }

    // Process dummy barrier entries created for child pipelines
    return processDummyBarrierEntries(
        barrierExecutionInstance, barrierIdentifier, parentPlanExecutionId, pipelineStageNodeId);
  }

  /**
   * Processes dummy barrier entries that were created in the parent pipeline for child pipeline steps.
   * Removes matching dummy entries when the child pipeline executes.
   *
   * @param barrierInstance     Barrier execution instance from parent pipeline
   * @param barrierIdentifier   Barrier identifier
   * @param parentPlanExecutionId Parent plan execution ID
   * @param pipelineStageNodeId  Current pipeline stage node ID
   * @return Result indicating if parent plan execution ID should be used
   */
  private ChainedPipelineResult processDummyBarrierEntries(BarrierExecutionInstance barrierInstance,
      String barrierIdentifier, String parentPlanExecutionId, String pipelineStageNodeId) {
    // First check if there are positions to process
    if (null == barrierInstance.getPositionInfo()
        || EmptyPredicate.isEmpty(barrierInstance.getPositionInfo().getBarrierPositionList())) {
      return ChainedPipelineResult.noUpdate();
    }

    // Use the atomic MongoDB $pull operation to remove dummy entries in a thread-safe way
    boolean positionRemoved = barrierService.atomicallyRemoveDummyBarrierPosition(
        barrierIdentifier, parentPlanExecutionId, pipelineStageNodeId);

    // If a position was removed, return the appropriate result
    if (positionRemoved) {
      log.debug("Atomically removed dummy barrier position for barrier: {} in parent execution: {} for stage: {}",
          barrierIdentifier, parentPlanExecutionId, pipelineStageNodeId);
      return ChainedPipelineResult.useParentPlan(parentPlanExecutionId);
    }

    return ChainedPipelineResult.useParentPlan(parentPlanExecutionId);
  }

  /**
   * Value object to encapsulate the result of chained pipeline barrier processing.
   */
  private static class ChainedPipelineResult {
    private final String parentPlanExecutionId;
    private final boolean updatePlanExecutionId;

    private ChainedPipelineResult(String parentPlanExecutionId, boolean updatePlanExecutionId) {
      this.parentPlanExecutionId = parentPlanExecutionId;
      this.updatePlanExecutionId = updatePlanExecutionId;
    }

    public String getParentPlanExecutionId() {
      return parentPlanExecutionId;
    }

    public boolean shouldUpdatePlanExecutionId() {
      return updatePlanExecutionId;
    }

    public static ChainedPipelineResult noUpdate() {
      return new ChainedPipelineResult(null, false);
    }

    public static ChainedPipelineResult useParentPlan(String parentPlanExecutionId) {
      return new ChainedPipelineResult(parentPlanExecutionId, true);
    }
  }
}
