/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.pms.execution.modifier.ambiance;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.plan.PlanExecutionMigrationHelper;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.PlanExecutionProjectionConstants;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
public class StageLevelAmbianceModifier implements AmbianceModifier {
  private final PlanExecutionMetadataService planExecutionMetadataService;

  @Inject
  public StageLevelAmbianceModifier(PlanExecutionMetadataService planExecutionMetadataService) {
    this.planExecutionMetadataService = planExecutionMetadataService;
  }

  @Override
  public Ambiance modify(Ambiance givenAmbiance, PlanExecutionService planExecutionService) {
    Level currentLevel = AmbianceUtils.obtainCurrentLevel(givenAmbiance);
    Ambiance.Builder clonedBuilder = givenAmbiance.toBuilder().setStageExecutionId(currentLevel.getRuntimeId());
    if (AmbianceUtils.isRollbackModeExecution(givenAmbiance)) {
      clonedBuilder.setOriginalStageExecutionIdForRollbackMode(
          obtainOriginalStageExecutionIdForRollbackMode(givenAmbiance, currentLevel, planExecutionService));
    }
    return clonedBuilder.build();
  }

  String obtainOriginalStageExecutionIdForRollbackMode(
      Ambiance ambiance, Level stageLevel, PlanExecutionService planExecutionService) {
    List<PostExecutionRollbackInfo> postExecutionRollbackInfoList =
        getPostExecutionRollbackInfo(ambiance, planExecutionService);
    boolean fixOriginalStageExecutionIdInAmbiance = AmbianceUtils.checkIfFeatureFlagEnabled(
        ambiance, FeatureName.PIPE_FIX_ORIGINAL_STAGE_EXECUTION_IN_AMBIANCE.name());
    boolean isStrategyAppliedOnStage;
    if (fixOriginalStageExecutionIdInAmbiance) {
      isStrategyAppliedOnStage = AmbianceUtils.hasStrategyMetadata(stageLevel);
    } else {
      isStrategyAppliedOnStage =
          AmbianceUtils.obtainCurrentLevel(ambiance).getStepType().getStepCategory().equals(StepCategory.STRATEGY);
    }
    if (isStrategyAppliedOnStage) {
      // postExecutionRollbackStageId will be the strategy setup id, that is what we need as the current setup id
      String strategySetupId = "";
      if (fixOriginalStageExecutionIdInAmbiance) {
        strategySetupId = AmbianceUtils.getFirstLevelStrategySetupIdAmbiance(ambiance);
      } else {
        strategySetupId = AmbianceUtils.obtainCurrentSetupId(ambiance);
      }
      int currentIteration = AmbianceUtils.getCurrentIteration(stageLevel);
      // Need to assign to finalStrategySetupId as it is used in a lambda function and thus needs to be a final variable
      String finalStrategySetupId = strategySetupId;
      return postExecutionRollbackInfoList.stream()
          .filter(info -> Objects.equals(info.getPostExecutionRollbackStageId(), finalStrategySetupId))
          .filter(info -> info.getRollbackStageStrategyMetadata().getCurrentIteration() == currentIteration)
          .map(PostExecutionRollbackInfo::getOriginalStageExecutionId)
          .findFirst()
          .orElse("");
    }
    String currentSetupId = stageLevel.getSetupId();
    return postExecutionRollbackInfoList.stream()
        .filter(info -> Objects.equals(info.getPostExecutionRollbackStageId(), currentSetupId))
        .map(PostExecutionRollbackInfo::getOriginalStageExecutionId)
        .findFirst()
        .orElse("");
  }

  private List<PostExecutionRollbackInfo> getPostExecutionRollbackInfo(
      Ambiance ambiance, PlanExecutionService planExecutionService) {
    PlanExecutionMetadata planExecutionMetadata =
        planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(AmbianceUtils.getAccountId(ambiance),
            ambiance.getPlanExecutionId(), PlanExecutionProjectionConstants.fieldsForPostProdRollback);
    boolean readSwitchEnabled =
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
    PlanExecution planExecution = null;
    if (readSwitchEnabled) {
      Optional<PlanExecution> planExecutionOptional = planExecutionService.getWithFieldsIncludedOptional(
          ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.postExecutionRollbackInfos));
      if (planExecutionOptional.isPresent()) {
        planExecution = planExecutionOptional.get();
      }
    }

    return PlanExecutionMigrationHelper.readPostExecutionRollbackInfoWithFallbackOnMetadata(
        planExecutionMetadata, planExecution);
  }
}
