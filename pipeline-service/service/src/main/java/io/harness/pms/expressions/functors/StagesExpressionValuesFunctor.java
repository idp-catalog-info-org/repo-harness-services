/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.plan.PlanExecutionMigrationHelper;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.expression.LateBindingValue;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import java.util.Optional;
import java.util.Set;

@OwnedBy(PIPELINE)
public class StagesExpressionValuesFunctor implements LateBindingValue {
  private final PlanExecutionMetadataService planExecutionMetadataService;
  private final PlanExecutionService planExecutionService;
  private final Ambiance ambiance;
  public StagesExpressionValuesFunctor(PlanExecutionMetadataService planExecutionMetadataService, Ambiance ambiance,
      PlanExecutionService planExecutionService) {
    this.planExecutionMetadataService = planExecutionMetadataService;
    this.planExecutionService = planExecutionService;
    this.ambiance = ambiance;
  }

  @Override
  public Object bind() {
    PlanExecutionMetadata planExecutionMetadata =
        planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(AmbianceUtils.getAccountId(ambiance),
            ambiance.getPlanExecutionId(), Set.of(PlanExecutionMetadataKeys.stageExpressionValuesMap));
    PlanExecution planExecution = null;
    boolean readSwitchEnabled =
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
    if (readSwitchEnabled) {
      Optional<PlanExecution> planExecutionOptional = planExecutionService.getWithFieldsIncludedOptional(
          ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.stageExpressionValuesMap));
      if (planExecutionOptional.isPresent()) {
        planExecution = planExecutionOptional.get();
      }
    }
    return PlanExecutionMigrationHelper.readStageExpressionValuesMapWithFallBackOnMetadata(
        planExecutionMetadata, planExecution);
  }
}
