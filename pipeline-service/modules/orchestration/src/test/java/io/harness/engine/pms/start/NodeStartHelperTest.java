/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.start;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionBuilder;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.plan.PlanNode;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.timeout.contracts.TimeoutObtainment;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.EnumSet;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeStartHelperTest extends OrchestrationTestBase {
  @Mock private PlanService planService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PmsEventSender pmsEventSender;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject @InjectMocks private NodeStartHelper nodeStartHelper;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartDiscontinuingNodeExecution() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();

    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .identifier("DUMMY")
                            .serviceName("CD")
                            .stepType(StepType.newBuilder().setType("DUMMY_TYPE").build())
                            .serviceName("CD")
                            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .status(Status.DISCONTINUING)
                                      .mode(ExecutionMode.TASK)
                                      .startTs(System.currentTimeMillis())
                                      .build();

    when(planService.fetchNode(planId, planNode.getUuid())).thenReturn(planNode);

    when(nodeExecutionService.updateStatusWithOps(
             eq(nodeExecutionId), eq(Status.RUNNING), eq(null), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(null);

    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);

    assertThatCode(()
                       -> nodeStartHelper.startNode(ambiance,
                           FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.TASK).build()))
        .doesNotThrowAnyException();

    verify(pmsEventSender, times(0)).sendEvent(any(), any(), any(), any(), eq(true), eq(false));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartQueuedNodeExecution() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();

    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .identifier("DUMMY")
                            .serviceName("CD")
                            .stepType(StepType.newBuilder().setType("DUMMY_TYPE").build())
                            .timeoutObtainment(TimeoutObtainment.newBuilder()
                                                   .setDimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
                                                   .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                                                       AbsoluteSdkTimeoutTrackerParameters.builder()
                                                           .timeout(ParameterField.createValueField("30m"))
                                                           .build())))
                                                   .build())
                            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    NodeExecutionBuilder builder = NodeExecution.builder()
                                       .uuid(nodeExecutionId)
                                       .ambiance(ambiance)
                                       .mode(ExecutionMode.TASK)
                                       .startTs(System.currentTimeMillis());

    when(planService.fetchNode(planId, planNode.getUuid())).thenReturn(planNode);
    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);

    when(nodeExecutionService.updateStatusWithOps(
             eq(nodeExecutionId), eq(Status.RUNNING), any(), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(
            builder.status(Status.RUNNING).timeoutInstanceIds(Collections.singletonList(generateUuid())).build());

    nodeStartHelper.startNode(
        ambiance, FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.TASK).build());

    verify(pmsEventSender, times(1)).sendEvent(any(), any(), any(), any(), eq(true), eq(true));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateStartTsInNodeExecution() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setStartTs(100L).build())
                            .addLevels(Level.newBuilder().setStartTs(200L).build())
                            .build();
    Update update = update("key", "value");
    nodeStartHelper.updateStartTsInNodeExecution(update, ambiance, null);
    Ambiance updatedAmbiance = update.getUpdateObject().get("$set", Document.class).get("ambiance", Ambiance.class);
    ExecutionContext executionContext =
        update.getUpdateObject().get("$set", Document.class).get("executionContext", ExecutionContext.class);

    assertThat(updatedAmbiance.getLevels(0)).isEqualTo(ambiance.getLevels(0));
    assertThat(updatedAmbiance.getLevels(1)).isNotEqualTo(ambiance.getLevels(1));
    assertThat(update.getUpdateObject().get("$set", Document.class).get("startTs", Long.class))
        .isEqualTo(AmbianceUtils.getCurrentLevelStartTs(updatedAmbiance));
    assertThat(AmbianceUtils.getCurrentLevelStartTs(updatedAmbiance)).isGreaterThan(2000000000L);
    assertThat(executionContext).isNotNull();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateStartTsInNodeExecutionWithExecutionContext() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "ACCOUNT_ID")
                            .addLevels(Level.newBuilder().setStartTs(100L).build())
                            .addLevels(Level.newBuilder().setStartTs(200L).build())
                            .build();
    Update update = update("key", "value");
    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled("ACCOUNT_ID", FeatureName.PIPE_REMOVE_AMBIANCE_POPULATION_IN_NODE_EXECUTION);
    nodeStartHelper.updateStartTsInNodeExecution(update, ambiance, null);
    Ambiance updatedAmbiance = update.getUpdateObject().get("$set", Document.class).get("ambiance", Ambiance.class);
    ExecutionContext executionContext =
        update.getUpdateObject().get("$set", Document.class).get("executionContext", ExecutionContext.class);

    long startTs =
        NodeExecutionContextUtils.obtainCurrentLevel(NodeExecution.builder().executionContext(executionContext).build())
            .getStartTs();
    assertThat(executionContext.getLevels(0)).isEqualTo(ambiance.getLevels(0));
    assertThat(executionContext.getLevels(1)).isNotEqualTo(ambiance.getLevels(1));
    assertThat(update.getUpdateObject().get("$set", Document.class).get("startTs", Long.class)).isEqualTo(startTs);
    assertThat(startTs).isGreaterThan(2000000000L);
    assertThat(updatedAmbiance)
        .isEqualTo(Ambiance.newBuilder()
                       .setPlanExecutionId(ambiance.getPlanExecutionId())
                       .putSetupAbstractions(SetupAbstractionKeys.accountId, "ACCOUNT_ID")
                       .build());
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testStartNodeWithV1PipelineIncrementsParentChildrenCount() {
    String planExecutionId = generateUuid();
    String planId = generateUuid();

    PlanNode childPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("step1")
            .serviceName("CD")
            .stepType(StepType.newBuilder().setType("DUMMY_TYPE").setStepCategory(StepCategory.STEP).build())
            .build();

    // Test NG_FORK parent
    verifyChildrenCountIncrementForParentType(
        planExecutionId, planId, childPlanNode, NGCommonUtilPlanCreationConstants.NG_FORK, StepCategory.FORK);

    // Test STRATEGY_V1 parent
    verifyChildrenCountIncrementForParentType(
        planExecutionId, planId, childPlanNode, NGCommonUtilPlanCreationConstants.STRATEGY_V1, StepCategory.STRATEGY);

    // Test GROUP grandparent (with NG_SECTION_WITH_ROLLBACK_INFO intermediate)
    verifyChildrenCountIncrementForGrandparentType(planExecutionId, planId, childPlanNode,
        NGCommonUtilPlanCreationConstants.NG_SECTION_WITH_ROLLBACK_INFO, NGCommonUtilPlanCreationConstants.GROUP);

    // Test UNIFIED_STAGE grandparent (with STAGES_STEP intermediate)
    verifyChildrenCountIncrementForGrandparentType(planExecutionId, planId, childPlanNode,
        NGCommonUtilPlanCreationConstants.STAGES_STEP, NGCommonUtilPlanCreationConstants.UNIFIED_STAGE);
  }

  private void verifyChildrenCountIncrementForParentType(
      String planExecutionId, String planId, PlanNode childPlanNode, String parentType, StepCategory stepCategory) {
    String nodeExecutionId = generateUuid();
    String parentNodeExecutionId = generateUuid();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setPlanId(planId)
            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, childPlanNode))
            .build();

    NodeExecution parentNodeExecution =
        NodeExecution.builder()
            .uuid(parentNodeExecutionId)
            .stepType(StepType.newBuilder().setType(parentType).setStepCategory(stepCategory).build())
            .childrenCount(0L)
            .build();

    NodeExecution childNodeExecution = NodeExecution.builder()
                                           .uuid(nodeExecutionId)
                                           .ambiance(ambiance)
                                           .parentId(parentNodeExecutionId)
                                           .status(Status.RUNNING)
                                           .mode(ExecutionMode.SYNC)
                                           .startTs(System.currentTimeMillis())
                                           .build();

    when(planService.fetchNode(planId, childPlanNode.getUuid())).thenReturn(childPlanNode);
    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);
    when(nodeExecutionService.updateStatusWithOps(
             eq(nodeExecutionId), eq(Status.RUNNING), any(), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(childNodeExecution);
    when(nodeExecutionService.getWithFieldsIncluded(eq(parentNodeExecutionId), any())).thenReturn(parentNodeExecution);

    nodeStartHelper.startNode(
        ambiance, FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.SYNC).build());

    verify(nodeExecutionService, times(1)).updateV2(eq(parentNodeExecutionId), any());
  }

  private void verifyChildrenCountIncrementForGrandparentType(
      String planExecutionId, String planId, PlanNode childPlanNode, String intermediateType, String grandparentType) {
    String nodeExecutionId = generateUuid();
    String parentNodeExecutionId = generateUuid();
    String grandparentNodeExecutionId = generateUuid();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setPlanId(planId)
            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, childPlanNode))
            .build();

    NodeExecution parentNodeExecution = NodeExecution.builder()
                                            .uuid(parentNodeExecutionId)
                                            .parentId(grandparentNodeExecutionId)
                                            .stepType(StepType.newBuilder().setType(intermediateType).build())
                                            .build();

    NodeExecution grandparentNodeExecution = NodeExecution.builder()
                                                 .uuid(grandparentNodeExecutionId)
                                                 .stepType(StepType.newBuilder().setType(grandparentType).build())
                                                 .childrenCount(0L)
                                                 .build();

    NodeExecution childNodeExecution = NodeExecution.builder()
                                           .uuid(nodeExecutionId)
                                           .ambiance(ambiance)
                                           .parentId(parentNodeExecutionId)
                                           .status(Status.RUNNING)
                                           .mode(ExecutionMode.SYNC)
                                           .startTs(System.currentTimeMillis())
                                           .build();

    when(planService.fetchNode(planId, childPlanNode.getUuid())).thenReturn(childPlanNode);
    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);
    when(nodeExecutionService.updateStatusWithOps(
             eq(nodeExecutionId), eq(Status.RUNNING), any(), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(childNodeExecution);
    when(nodeExecutionService.getWithFieldsIncluded(eq(parentNodeExecutionId), any())).thenReturn(parentNodeExecution);
    when(nodeExecutionService.getWithFieldsIncluded(eq(grandparentNodeExecutionId), any()))
        .thenReturn(grandparentNodeExecution);

    nodeStartHelper.startNode(
        ambiance, FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.SYNC).build());

    verify(nodeExecutionService, times(1)).updateV2(eq(grandparentNodeExecutionId), any());
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testStartNodeWithV0PipelineDoesNotIncrementChildrenCount() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String parentNodeExecutionId = generateUuid();
    String planId = generateUuid();

    PlanNode childPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("step1")
            .serviceName("CD")
            .stepType(StepType.newBuilder().setType("DUMMY_TYPE").setStepCategory(StepCategory.STEP).build())
            .build();

    // V0 pipeline ambiance (default, no harnessVersion set or set to V0)
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setPlanId(planId)
            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V0).build())
            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, childPlanNode))
            .build();

    NodeExecution childNodeExecution = NodeExecution.builder()
                                           .uuid(nodeExecutionId)
                                           .ambiance(ambiance)
                                           .parentId(parentNodeExecutionId)
                                           .status(Status.RUNNING)
                                           .mode(ExecutionMode.SYNC)
                                           .startTs(System.currentTimeMillis())
                                           .build();

    when(planService.fetchNode(planId, childPlanNode.getUuid())).thenReturn(childPlanNode);
    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);
    when(nodeExecutionService.updateStatusWithOps(
             eq(nodeExecutionId), eq(Status.RUNNING), any(), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(childNodeExecution);

    nodeStartHelper.startNode(
        ambiance, FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.SYNC).build());

    // Verify that getWithFieldsIncluded was NOT called for parent lookup (V0 pipeline skips childrenCount update)
    verify(nodeExecutionService, times(0)).getWithFieldsIncluded(eq(parentNodeExecutionId), any());
  }
}
