/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions.functors;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionMigrationHelper;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.LateBindingValue;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_EXPRESSION_ENGINE})
@OwnedBy(PIPELINE)
public class EventPayloadFunctor implements LateBindingValue {
  private final Ambiance ambiance;
  private final PlanExecutionMetadataService planExecutionMetadataService;
  private PlanExecutionService planExecutionService;
  public EventPayloadFunctor(Ambiance ambiance, PlanExecutionMetadataService planExecutionMetadataService,
      PlanExecutionService planExecutionService) {
    this.ambiance = ambiance;
    this.planExecutionMetadataService = planExecutionMetadataService;
    this.planExecutionService = planExecutionService;
  }

  @Override
  public Object bind() {
    PlanExecutionMetadata planExecutionMetadata =
        planExecutionMetadataService
            .findByPlanExecutionId(AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId())
            .orElseThrow(()
                             -> new IllegalStateException(
                                 "PlanExecution metadata null for planExecutionId " + ambiance.getPlanExecutionId()));
    boolean readSwitchEnabled =
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
    PlanExecution planExecution = null;
    if (readSwitchEnabled) {
      Optional<PlanExecution> planExecutionOptional = planExecutionService.getWithFieldsIncludedOptional(
          ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.triggerJsonPayload));
      if (planExecutionOptional.isPresent()) {
        planExecution = planExecutionOptional.get();
      }
    }
    String triggerJsonPayload =
        PlanExecutionMigrationHelper.readTriggerJsonPayloadWithFallBackOnMetadata(planExecutionMetadata, planExecution);
    try {
      if (EmptyPredicate.isEmpty(triggerJsonPayload)) {
        return null;
      }
      return JsonPipelineUtils.read(triggerJsonPayload, HashMap.class);
    } catch (IOException e) {
      try {
        return JsonPipelineUtils.read(triggerJsonPayload, List.class);
      } catch (IOException toListEx) {
        return triggerJsonPayload;
      }
    }
  }
}
