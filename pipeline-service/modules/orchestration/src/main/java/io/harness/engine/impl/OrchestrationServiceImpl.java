/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.impl;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.algorithm.HashGenerator;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.engine.interrupts.manager.InterruptManager;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.interrupts.Interrupt;
import io.harness.plan.Plan;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.data.NGWorkflowType;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import java.util.Map;
import javax.validation.Valid;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class OrchestrationServiceImpl implements OrchestrationService {
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private InterruptManager interruptManager;
  @Inject private PlanService planService;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Override
  public PlanExecution startExecution(@Valid Plan plan, Map<String, String> setupAbstractions,
      ExecutionMetadata metadata, PlanExecutionMetadataWithContext planExecutionMetadataWithContext) {
    long start = System.currentTimeMillis();
    Plan savedPlan = planService.save(plan);
    log.info("[PMS_EXECUTE] PlanService plan save time {}ms", System.currentTimeMillis() - start);
    return executePlan(savedPlan, setupAbstractions, metadata, planExecutionMetadataWithContext);
  }

  public PlanExecution startExecutionV2(String planId, Map<String, String> setupAbstractions,
      ExecutionMetadata metadata, PlanExecutionMetadataWithContext planExecutionMetadataWithContext) {
    return executePlan(planService.fetchPlan(planId), setupAbstractions, metadata, planExecutionMetadataWithContext);
  }

  @Override
  public PlanExecution executePlan(@Valid Plan plan, @NonNull Map<String, String> setupAbstractions,
      ExecutionMetadata metadata, PlanExecutionMetadataWithContext planExecutionMetadataWithContext) {
    Long expressionFunctorToken = getExpressionFunctorToken(planExecutionMetadataWithContext);
    if (isNull(expressionFunctorToken)) {
      log.warn("[PMS_EXECUTE] Expression token missing in plan metadata, generating a new token for ambiance");
      expressionFunctorToken = (long) HashGenerator.generateIntegerHash();
    }
    NGWorkflowType workflowMode = planExecutionMetadataWithContext.getWorkflowMode();
    if (workflowMode != null) {
      setupAbstractions.put(SetupAbstractionKeys.workflowType, workflowMode.name());
    }
    Ambiance.Builder ambianceBuilder = Ambiance.newBuilder()
                                           .putAllSetupAbstractions(setupAbstractions)
                                           .setPlanExecutionId(metadata.getExecutionUuid())
                                           .setPlanId(plan.getUuid())
                                           .setMetadata(metadata)
                                           .setExpressionFunctorToken(expressionFunctorToken)
                                           .setStartTs(System.currentTimeMillis());
    // Seed the pipeline-level identity context onto the ambiance; null (feature off) => nothing set.
    if (planExecutionMetadataWithContext.getIdentityExecutionContext() != null) {
      ambianceBuilder.setIdentityExecutionContext(planExecutionMetadataWithContext.getIdentityExecutionContext());
    }
    Ambiance ambiance = ambianceBuilder.build();

    return orchestrationEngine.runNode(ambiance, plan, planExecutionMetadataWithContext);
  }

  private Long getExpressionFunctorToken(PlanExecutionMetadataWithContext planExecutionMetadataWithContext) {
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataWithContext.getPlanExecutionMetadata();
    boolean readSwitchEnabled = pmsFeatureFlagHelper.isEnabled(
        planExecutionMetadata.getAccountIdentifier(), FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
    if (readSwitchEnabled) {
      if (nonNull(planExecutionMetadataWithContext.getExpressionFunctorToken())) {
        return planExecutionMetadataWithContext.getExpressionFunctorToken();
      }
      if (nonNull(planExecutionMetadata.getExpressionFunctorToken())) {
        // This is an unexpected state, and we need to debug this if we reach it. Fallback on planExecutionMetadata.
        log.warn("ExpressionFunctorToken Disparity detected between planExecutionMetadataWithContext and "
                + "planExecutionMetadata : null vs {}",
            planExecutionMetadata.getExpressionFunctorToken());
      }
    }
    return planExecutionMetadata.getExpressionFunctorToken();
  }

  @Override
  public Interrupt registerInterrupt(InterruptPackage interruptPackage) {
    return interruptManager.register(interruptPackage);
  }
}
