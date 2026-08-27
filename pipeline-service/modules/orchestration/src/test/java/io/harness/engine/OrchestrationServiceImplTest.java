/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.impl.OrchestrationServiceImpl;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class OrchestrationServiceImplTest extends OrchestrationTestBase {
  private static final String PLAN_EXECUTION_ID = generateUuid();
  private static final String PLAN_EXECUTION_ID_2 = generateUuid();
  private static final String PLAN_ID = generateUuid();
  private static final String PLAN_ID_2 = generateUuid();
  private static final String DUMMY_NODE_1_ID = generateUuid();
  private static final String DUMMY_NODE_2_ID = generateUuid();
  private static final String DUMMY_NODE_3_ID = generateUuid();

  private static final String ACCOUNT_ID = generateUuid();
  private static final String APP_ID = generateUuid();

  @Inject @InjectMocks private OrchestrationServiceImpl orchestrationService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private static final StepType DUMMY_STEP_TYPE =
      StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build();
  private static final Long EXPRESSION_TOKEN = 1234L;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartExecution() {
    Plan plan = Plan.builder()
                    .uuid(PLAN_ID)
                    .planNode(PlanNode.builder()
                                  .uuid(DUMMY_NODE_1_ID)
                                  .name("Dummy Node 1")
                                  .stepType(DUMMY_STEP_TYPE)
                                  .identifier("dummy1")
                                  .serviceName("PIPELINE_SERVICE")
                                  .build())
                    .planNode(PlanNode.builder()
                                  .uuid(DUMMY_NODE_2_ID)
                                  .name("Dummy Node 2")
                                  .stepType(DUMMY_STEP_TYPE)
                                  .identifier("dummy2")
                                  .serviceName("PIPELINE_SERVICE")
                                  .build())
                    .planNode(PlanNode.builder()
                                  .uuid(DUMMY_NODE_3_ID)
                                  .name("Dummy Node 3")
                                  .stepType(DUMMY_STEP_TYPE)
                                  .identifier("dummy3")
                                  .serviceName("PIPELINE_SERVICE")
                                  .build())
                    .startingNodeId(DUMMY_NODE_1_ID)
                    .accountIdentifier(ACCOUNT_ID)
                    .build();
    Map<String, String> setupAbstractions = ImmutableMap.of("accountId", ACCOUNT_ID, "appId", APP_ID);
    ExecutionMetadata metadata = ExecutionMetadata.newBuilder().setExecutionUuid(PLAN_EXECUTION_ID).build();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().accountIdentifier(ACCOUNT_ID).expressionFunctorToken(EXPRESSION_TOKEN).build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .expressionFunctorToken(EXPRESSION_TOKEN)
            .planExecutionMetadata(planExecutionMetadata)
            .build();
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name()))
        .thenReturn(false);
    PlanExecution planExecution =
        orchestrationService.startExecution(plan, setupAbstractions, metadata, planExecutionMetadataWithContext);
    assertThat(planExecution.getExpressionFunctorToken()).isEqualTo(null);
    assertThat(planExecution.getAmbiance().getExpressionFunctorToken()).isEqualTo(EXPRESSION_TOKEN);
    assertPlanExecutionValues(planExecution, setupAbstractions, metadata, plan);
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name()))
        .thenReturn(false);
    ExecutionMetadata metadata2 = ExecutionMetadata.newBuilder().setExecutionUuid(PLAN_EXECUTION_ID_2).build();
    Plan plan2 = plan.withUuid(PLAN_ID_2);
    planExecution =
        orchestrationService.startExecution(plan2, setupAbstractions, metadata2, planExecutionMetadataWithContext);
    assertThat(planExecution.getExpressionFunctorToken()).isEqualTo(null);
    assertThat(planExecution.getAmbiance().getExpressionFunctorToken()).isEqualTo(EXPRESSION_TOKEN);
    assertPlanExecutionValues(planExecution, setupAbstractions, metadata2, plan2);
  }

  private static void assertPlanExecutionValues(
      PlanExecution planExecution, Map<String, String> setupAbstractions, ExecutionMetadata metadata, Plan plan) {
    assertThat(planExecution.getUuid()).isEqualTo(metadata.getExecutionUuid());
    assertThat(planExecution.getStatus()).isEqualTo(Status.RUNNING);
    assertThat(planExecution.getPlanId()).isEqualTo(plan.getUuid());
    assertThat(planExecution.getSetupAbstractions()).isEqualTo(setupAbstractions);
    assertThat(planExecution.getMetadata()).isEqualTo(metadata);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartExecutionWhenTokenAbsentInPlanMetadata() {
    Plan plan = Plan.builder()
                    .uuid(PLAN_ID)
                    .planNode(PlanNode.builder()
                                  .uuid(DUMMY_NODE_1_ID)
                                  .name("Dummy Node 1")
                                  .stepType(DUMMY_STEP_TYPE)
                                  .identifier("dummy1")
                                  .serviceName("PIPELINE_SERVICE")
                                  .build())
                    .planNode(PlanNode.builder()
                                  .uuid(DUMMY_NODE_2_ID)
                                  .name("Dummy Node 2")
                                  .stepType(DUMMY_STEP_TYPE)
                                  .identifier("dummy2")
                                  .serviceName("PIPELINE_SERVICE")
                                  .build())
                    .planNode(PlanNode.builder()
                                  .uuid(DUMMY_NODE_3_ID)
                                  .name("Dummy Node 3")
                                  .stepType(DUMMY_STEP_TYPE)
                                  .identifier("dummy3")
                                  .serviceName("PIPELINE_SERVICE")
                                  .build())
                    .startingNodeId(DUMMY_NODE_1_ID)
                    .accountIdentifier(ACCOUNT_ID)
                    .build();
    Map<String, String> setupAbstractions = ImmutableMap.of("accountId", ACCOUNT_ID, "appId", APP_ID);
    ExecutionMetadata metadata = ExecutionMetadata.newBuilder().setExecutionUuid(PLAN_EXECUTION_ID).build();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().accountIdentifier("accountId").build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().planExecutionMetadata(planExecutionMetadata).build();
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name()))
        .thenReturn(false);
    PlanExecution planExecution =
        orchestrationService.startExecution(plan, setupAbstractions, metadata, planExecutionMetadataWithContext);
    assertPlanExecutionValues(planExecution, setupAbstractions, metadata, plan);
    assertThat(planExecution.getAmbiance().getExpressionFunctorToken()).isNotEqualTo(0L);
    assertThat(planExecution.getAmbiance().getExpressionFunctorToken()).isNotEqualTo(0L);
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name()))
        .thenReturn(true);
    ExecutionMetadata metadata2 = ExecutionMetadata.newBuilder().setExecutionUuid(PLAN_EXECUTION_ID_2).build();
    Plan plan2 = plan.withUuid(PLAN_ID_2);
    planExecution =
        orchestrationService.startExecution(plan2, setupAbstractions, metadata2, planExecutionMetadataWithContext);
    assertPlanExecutionValues(planExecution, setupAbstractions, metadata2, plan2);
    assertThat(planExecution.getAmbiance().getExpressionFunctorToken()).isNotEqualTo(0L);
    assertThat(planExecution.getAmbiance().getExpressionFunctorToken()).isNotEqualTo(0L);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void shouldTestStartExecutionWithWorkflowMode() {
    Plan plan = Plan.builder()
                    .uuid(PLAN_ID)
                    .planNode(PlanNode.builder()
                                  .uuid(DUMMY_NODE_1_ID)
                                  .name("Dummy Node 1")
                                  .stepType(DUMMY_STEP_TYPE)
                                  .identifier("dummy1")
                                  .serviceName("PIPELINE_SERVICE")
                                  .build())
                    .startingNodeId(DUMMY_NODE_1_ID)
                    .accountIdentifier(ACCOUNT_ID)
                    .build();
    Map<String, String> setupAbstractions = new java.util.HashMap<>();
    setupAbstractions.put("accountId", ACCOUNT_ID);
    setupAbstractions.put("appId", APP_ID);
    ExecutionMetadata metadata = ExecutionMetadata.newBuilder().setExecutionUuid(PLAN_EXECUTION_ID).build();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().accountIdentifier(ACCOUNT_ID).expressionFunctorToken(EXPRESSION_TOKEN).build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .expressionFunctorToken(EXPRESSION_TOKEN)
            .planExecutionMetadata(planExecutionMetadata)
            .workflowMode(io.harness.pms.data.NGWorkflowType.ORCHESTRATION)
            .build();
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name()))
        .thenReturn(true);
    PlanExecution planExecution =
        orchestrationService.startExecution(plan, setupAbstractions, metadata, planExecutionMetadataWithContext);
    assertThat(planExecution).isNotNull();
    assertThat(planExecution.getAmbiance().getSetupAbstractionsMap()).containsEntry("workflowType", "ORCHESTRATION");
  }
}
