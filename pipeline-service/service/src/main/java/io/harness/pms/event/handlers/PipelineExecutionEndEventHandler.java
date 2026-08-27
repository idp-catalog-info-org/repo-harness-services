/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.handlers;

import static io.harness.pms.PmsCommonConstants.EXECUTION_TTL_IN_DAYS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.pms.pipeline.service.InputFileService;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;

import com.google.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineExecutionEndEventHandler implements OrchestrationEventHandler {
  // Considering 37 days though max we support 35 days, added buffer of 2 days to prevent any discrepancy
  public static final long TTL_DAYS = EXECUTION_TTL_IN_DAYS;
  @Inject PlanService planService;

  @Inject NodeExecutionService nodeExecutionService;
  @Inject PlanExpansionService planExpansionService;
  @Inject NodeExecutionInfoService pmsGraphStepDetailsService;
  @Inject PmsOutcomeService pmsOutcomeService;
  @Inject PmsSweepingOutputService pmsSweepingOutputService;
  @Inject PlanExecutionService planExecutionService;
  @Inject InputFileService inputFileService;
  @Inject ExecutionRetentionService executionRetentionService;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;

  @Override
  public void handleEvent(OrchestrationEvent event) {
    Date ttlExpiryDate = Date.from(OffsetDateTime.now().plusDays(TTL_DAYS).toInstant());

    String planExecutionId = event.getAmbiance().getPlanExecutionId();
    String planId = event.getAmbiance().getPlanId();
    planService.updateTTLForNodesForGivenPlanId(planId, ttlExpiryDate);
    planService.updateTTLForPlans(planId, ttlExpiryDate);

    pmsOutcomeService.updateTTL(planExecutionId, ttlExpiryDate);
    pmsSweepingOutputService.updateTTL(planExecutionId, ttlExpiryDate);
    nodeExecutionService.updateTTLForNodeExecution(planExecutionId, ttlExpiryDate);

    planExpansionService.updateTTL(planExecutionId, ttlExpiryDate);
    pmsGraphStepDetailsService.updateTTLForNodesForGivenPlanExecutionId(planExecutionId, ttlExpiryDate);

    planExecutionService.updateTTL(planExecutionId, ttlExpiryDate);
    inputFileService.updateTTL(planExecutionId, ttlExpiryDate);

    if (executionRetentionService.isEnabled()) {
      int retentionPeriodInDays =
          executionRetentionService.getMongoValidUntilTTL(ExecutionRetentionObjectStoreCollection.EXECUTION_METADATA);
      Date ttlDate = Date.from(OffsetDateTime.now().plusDays(retentionPeriodInDays).toInstant());
      planExecutionMetadataService.updateTTL(planExecutionId, ttlDate);
    }
  }
}
