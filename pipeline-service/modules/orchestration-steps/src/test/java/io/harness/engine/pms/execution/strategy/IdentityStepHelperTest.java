/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy;

import static io.harness.execution.NodeExecution.builder;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.data.RawOptionalSweepingOutput;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.engine.pms.execution.strategy.identity.IdentityStepHelper;
import io.harness.engine.pms.steps.identity.IdentityStepParameters;
import io.harness.execution.NodeExecution;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class IdentityStepHelperTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PmsSweepingOutputService pmsSweepingOutputService;
  @Mock private PmsOutcomeService pmsOutcomeService;

  @Mock PlanExpansionService planExpansionService;
  @Inject @InjectMocks private IdentityStepHelper identityStep;

  String originalPlanExecutionId = "originalPlanExecutionId";
  private String outputValue =
      "{\"__recast\":\"io.harness.cdng.pipeline.steps.CombinedRollbackSweepingOutput\",\"responseDataMap\":{\"h7lxb7IHQ1igjy5qx63KBQ\":{\"__recast\":\"io.harness.pms.sdk.core.steps.io.StepResponseNotifyData\",\"identifier\":\"rollbackSteps\",\"nodeUuid\":\"yblRWcwPTfC__Gi_d7bihQsteps_combinedRollback\",\"failureInfo\":{\"__recast\":\"io.harness.pms.contracts.execution.failure.FailureInfo\",\"__encodedValue\":\"{\\n}\"},\"status\":{\"__recast\":\"io.harness.pms.contracts.execution.Status\",\"__encodedValue\":\"SUCCEEDED\"},\"adviserResponse\":{\"__recast\":\"io.harness.pms.contracts.advisers.AdviserResponse\"},\"nodeExecutionId\":\"h7lxb7IHQ1igjy5qx63KBQ\",\"nodeExecutionEndTs\":1709293455782}}}";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
        .build();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleChildResponse() {
    Ambiance ambiance = buildAmbiance();
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // nodeExecution formation
    NodeExecution nodeExecution =
        builder()
            .uuid("nodeUuid")
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(originalPlanExecutionId).build())
            .status(Status.ABORTED)
            .build();
    doReturn(nodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded("nodeUuid", NodeProjectionUtils.withStatusAndStepTypeAndAmbiance);

    StepResponse stepResponse = identityStep.handleChildResponse(ambiance, identityParams, null);
    verify(planExpansionService, times(1)).updateExpansionForRetriedNode(ambiance, originalPlanExecutionId);
    verify(pmsOutcomeService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(stepResponse.getStatus()).isEqualTo(Status.ABORTED);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleChildrenResponse() {
    Ambiance ambiance = buildAmbiance();
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // nodeExecution formation
    NodeExecution nodeExecution =
        builder()
            .uuid("nodeUuid")
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(originalPlanExecutionId).build())
            .status(Status.ABORTED)
            .build();
    doReturn(nodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded("nodeUuid", NodeProjectionUtils.withAmbianceAndStatus);

    StepResponse stepResponse = identityStep.handleChildrenResponse(ambiance, identityParams, null);
    verify(planExpansionService, times(1)).updateExpansionForRetriedNode(ambiance, originalPlanExecutionId);
    verify(pmsOutcomeService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(stepResponse.getStatus()).isEqualTo(Status.ABORTED);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleChildResponseWithSweepingOutput() {
    Ambiance ambiance = buildAmbiance();
    ambiance = ambiance.toBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                                    .putFeatureFlagToValueMap(
                                        FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK.name(), true)
                                    .build())
                   .build();
    when(pmsSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.COMBINED_ROLLBACK_STATUS)))
        .thenReturn(RawOptionalSweepingOutput.builder().output(outputValue).found(true).build());
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // nodeExecution formation
    NodeExecution nodeExecution =
        builder()
            .uuid("nodeUuid")
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(originalPlanExecutionId).build())
            .status(Status.ABORTED)
            .build();
    doReturn(nodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded("nodeUuid", NodeProjectionUtils.withStatusAndStepTypeAndAmbiance);

    StepResponse stepResponse = identityStep.handleChildResponse(ambiance, identityParams, null);
    verify(pmsOutcomeService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testHandleChildResponseWithSweepingOutputForStageWithoutRollbackStep() {
    Ambiance ambiance = buildAmbiance();
    ambiance = ambiance.toBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                                    .putFeatureFlagToValueMap(
                                        FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK.name(), true)
                                    .build())
                   .build();
    when(pmsSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.COMBINED_ROLLBACK_STATUS)))
        .thenReturn(RawOptionalSweepingOutput.builder().output(outputValue).found(false).build());
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // nodeExecution formation
    NodeExecution nodeExecution =
        builder()
            .uuid("nodeUuid")
            .status(Status.ABORTED)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(originalPlanExecutionId).build())
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
            .build();
    doReturn(nodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded("nodeUuid", NodeProjectionUtils.withStatusAndStepTypeAndAmbiance);

    StepResponse stepResponse = identityStep.handleChildResponse(ambiance, identityParams, null);
    verify(pmsOutcomeService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testHandleChildResponseWithSweepingOutputAndAllChildHavingStatusAsSuspended() {
    String outputValue =
        "{\"__recast\":\"io.harness.cdng.pipeline.steps.CombinedRollbackSweepingOutput\",\"responseDataMap\":{\"ignore-UVeuMSSsR5K9SWBq3S9twg\":{\"__recast\":\"io.harness.pms.sdk.core.steps.io.StepResponseNotifyData\",\"identifier\":\"rollbackSteps\",\"nodeUuid\":\"QHmESZxPQTiRECY3zXW3Ew_combinedRollback\",\"failureInfo\":{\"__recast\":\"io.harness.pms.contracts.execution.failure.FailureInfo\"},\"status\":{\"__recast\":\"io.harness.pms.contracts.execution.Status\",\"__encodedValue\":\"SUSPENDED\"},\"description\":\"Ignoring Execution as next child found to be null\",\"adviserResponse\":{\"__recast\":\"io.harness.pms.contracts.advisers.AdviserResponse\"},\"nodeExecutionId\":\"UVeuMSSsR5K9SWBq3S9twg\"}}}";
    Ambiance ambiance = buildAmbiance();
    ambiance = ambiance.toBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                                    .putFeatureFlagToValueMap(
                                        FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK.name(), true)
                                    .build())
                   .build();
    when(pmsSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.COMBINED_ROLLBACK_STATUS)))
        .thenReturn(RawOptionalSweepingOutput.builder().output(outputValue).found(true).build());
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // nodeExecution formation
    NodeExecution nodeExecution =
        builder()
            .uuid("nodeUuid")
            .status(Status.ABORTED)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(originalPlanExecutionId).build())
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
            .build();
    doReturn(nodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded("nodeUuid", NodeProjectionUtils.withStatusAndStepTypeAndAmbiance);

    StepResponse stepResponse = identityStep.handleChildResponse(ambiance, identityParams, null);
    verify(pmsOutcomeService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
  }
}
