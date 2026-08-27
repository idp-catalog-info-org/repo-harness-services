/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.ChildrenExecutableResponse.Child;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.pms.resume.callback.resume.EngineResumeCallback;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.helpers.ChildrenStartRequestBatch;
import io.harness.execution.helpers.InitiateNodeHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.InitiateNodeBatchEvent;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.contracts.execution.events.SpawnChildrenRequest;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.PlanExecutionProjectionConstants;
import io.harness.rule.Owner;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Update;

public class SpawnChildrenRequestProcessorTest extends OrchestrationTestBase {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock InitiateNodeHelper initiateNodeHelper;
  @Mock WaitNotifyEngine waitNotifyEngine;
  @Mock OrchestrationEngine engine;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Mock PlanExecutionService planExecutionService;

  @Inject @InjectMocks SpawnChildrenRequestProcessor processor;
  String accountIdentifier = "accountIdentifier";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    MockedStatic<AmbianceUtils> ambianceUtilsMock;
    ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class, invocation -> invocation.callRealMethod());
    ambianceUtilsMock
        .when(()
                  -> AmbianceUtils.checkIfFeatureFlagEnabled(
                      any(), eq(FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name())))
        .thenReturn(true);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testHandleSpawnChildrenEvent() throws Exception {
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    String nodeExecutionId = generateUuid();
    String child1Id = generateUuid();
    String child2Id = generateUuid();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .setPlanExecutionId(planExecutionId)
            .addLevels(
                Level.newBuilder()
                    .setIdentifier("IDENTIFIER")
                    .setStepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.FORK).build())
                    .setRuntimeId(nodeExecutionId)
                    .setSetupId(planNodeId)
                    .build())
            .build();

    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder()
            .setSdkResponseEventType(SdkResponseEventType.SPAWN_CHILDREN)
            .setSpawnChildrenRequest(
                SpawnChildrenRequest.newBuilder()
                    .setChildren(ChildrenExecutableResponse.newBuilder()
                                     .addChildren(Child.newBuilder().setChildNodeId(child1Id).build())
                                     .addChildren(Child.newBuilder().setChildNodeId(child2Id).build())
                                     .build())
                    .build())
            .setAmbiance(ambiance)
            .build();

    processor.handleEvent(event);

    ArgumentCaptor<OldNotifyCallback> callbackCaptor = ArgumentCaptor.forClass(OldNotifyCallback.class);
    ArgumentCaptor<String[]> exIdCaptor = ArgumentCaptor.forClass(String[].class);
    verify(waitNotifyEngine, times(1)).waitForAllOn(any(), callbackCaptor.capture(), exIdCaptor.capture());

    assertThat(callbackCaptor.getAllValues().get(0)).isInstanceOf(EngineResumeCallback.class);
    EngineResumeCallback engineResumeCallback = (EngineResumeCallback) callbackCaptor.getAllValues().get(0);
    assertThat(engineResumeCallback.getAmbiance()).isEqualTo(ambiance);

    // Verify that in normal (non-rollback) mode all children are stored in executableResponses
    ArgumentCaptor<Consumer> updateConsumerCaptor = ArgumentCaptor.forClass(Consumer.class);
    verify(nodeExecutionService).updateV2(eq(nodeExecutionId), updateConsumerCaptor.capture());
    Update ops = new Update();
    updateConsumerCaptor.getValue().accept(ops);
    Document addToSet = ops.getUpdateObject().get("$addToSet", Document.class);
    ExecutableResponse storedResponse = addToSet.get("executableResponses", ExecutableResponse.class);
    assertThat(storedResponse.getChildren().getChildrenList())
        .extracting(Child::getChildNodeId)
        .containsExactly(child1Id, child2Id);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleSpawnChildrenEventWithMaxConcurrency() throws Exception {
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    String nodeExecutionId = generateUuid();
    String child1Id = generateUuid();
    String child2Id = generateUuid();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .setPlanExecutionId(planExecutionId)
            .addLevels(
                Level.newBuilder()
                    .setIdentifier("IDENTIFIER")
                    .setStepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.FORK).build())
                    .setRuntimeId(nodeExecutionId)
                    .setSetupId(planNodeId)
                    .build())
            .build();

    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);
    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder()
            .setSdkResponseEventType(SdkResponseEventType.SPAWN_CHILDREN)
            .setSpawnChildrenRequest(
                SpawnChildrenRequest.newBuilder()
                    .setChildren(ChildrenExecutableResponse.newBuilder()
                                     .addChildren(Child.newBuilder().setChildNodeId(child1Id).build())
                                     .addChildren(Child.newBuilder().setChildNodeId(child2Id).build())
                                     .setMaxConcurrency(1)
                                     .build())
                    .build())
            .setAmbiance(ambiance)
            .build();

    when(engine.initiateNode(any(), anyString(), anyString(), any(), any(), any()))
        .thenReturn(NodeExecution.builder().ambiance(ambiance).build());
    processor.handleEvent(event);

    ArgumentCaptor<String> notRunningNodeIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> notRunningRuntimeIdCaptor = ArgumentCaptor.forClass(String.class);

    verify(initiateNodeHelper, times(1)).publishEvent(eq(ambiance), eq(InitiateMode.START));

    verify(engine, times(2))
        .initiateNode(
            eq(ambiance), notRunningNodeIdCaptor.capture(), notRunningRuntimeIdCaptor.capture(), any(), any(), any());

    List<String> nodeIds = notRunningNodeIdCaptor.getAllValues();
    assertThat(nodeIds).hasSize(2);
    assertThat(nodeIds).containsExactly(child1Id, child2Id);

    ArgumentCaptor<OldNotifyCallback> callbackCaptor = ArgumentCaptor.forClass(OldNotifyCallback.class);
    ArgumentCaptor<String[]> exIdCaptor = ArgumentCaptor.forClass(String[].class);
    verify(waitNotifyEngine, times(3)).waitForAllOn(any(), callbackCaptor.capture(), exIdCaptor.capture());

    assertThat(callbackCaptor.getAllValues().get(2)).isInstanceOf(EngineResumeCallback.class);
    EngineResumeCallback engineResumeCallback = (EngineResumeCallback) callbackCaptor.getAllValues().get(2);
    assertThat(engineResumeCallback.getAmbiance()).isEqualTo(ambiance);

    verify(nodeExecutionService).updateV2(eq(nodeExecutionId), any());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleSpawnChildrenEventForRollbackMode() {
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    String nodeExecutionId = generateUuid();
    String child1Id = generateUuid();

    StrategyMetadata rollbackSTrategyMetadata =
        StrategyMetadata.newBuilder()
            .setMatrixMetadata(MatrixMetadata.newBuilder().putMatrixValues("serviceRef", "svc1").build())
            .build();
    StrategyMetadata nonRollbackSTrategyMetadata =
        StrategyMetadata.newBuilder()
            .setMatrixMetadata(MatrixMetadata.newBuilder().putMatrixValues("serviceRef", "svc2").build())
            .build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .setPlanExecutionId(planExecutionId)
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK).build())
            .addLevels(
                Level.newBuilder()
                    .setIdentifier("IDENTIFIER")
                    .setStepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
                    .setRuntimeId(nodeExecutionId)
                    .setSetupId(planNodeId)
                    .setGroup("STAGES")
                    .build())
            .addLevels(
                Level.newBuilder()
                    .setIdentifier("IDENTIFIER")
                    .setStepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STRATEGY).build())
                    .setRuntimeId(nodeExecutionId)
                    .setSetupId(planNodeId)
                    .build())
            .build();

    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder()
            .setSdkResponseEventType(SdkResponseEventType.SPAWN_CHILDREN)
            .setSpawnChildrenRequest(
                SpawnChildrenRequest.newBuilder()
                    .setChildren(ChildrenExecutableResponse.newBuilder()
                                     .addChildren(Child.newBuilder()
                                                      .setChildNodeId(child1Id)
                                                      .setStrategyMetadata(rollbackSTrategyMetadata)
                                                      .build())
                                     .addChildren(Child.newBuilder()
                                                      .setChildNodeId(child1Id)
                                                      .setStrategyMetadata(nonRollbackSTrategyMetadata)
                                                      .build())
                                     .build())
                    .build())
            .setAmbiance(ambiance)
            .build();

    PostExecutionRollbackInfo postExecutionRollbackInfo =
        PostExecutionRollbackInfo.newBuilder()
            .setPostExecutionRollbackStageId(planNodeId)
            .setRollbackStageStrategyMetadata(rollbackSTrategyMetadata)
            .build();
    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(
            accountIdentifier, planExecutionId, PlanExecutionProjectionConstants.fieldsForPostProdRollback);
    doReturn(Optional.of(PlanExecution.builder()
                             .postExecutionRollbackInfos(Collections.singletonList(postExecutionRollbackInfo))
                             .build()))
        .when(planExecutionService)
        .getWithFieldsIncludedOptional(planExecutionId, Set.of(PlanExecutionKeys.postExecutionRollbackInfos));

    processor.handleEvent(event);

    ArgumentCaptor<OldNotifyCallback> callbackCaptor = ArgumentCaptor.forClass(OldNotifyCallback.class);
    ArgumentCaptor<String[]> exIdCaptor = ArgumentCaptor.forClass(String[].class);
    verify(waitNotifyEngine, times(1)).waitForAllOn(any(), callbackCaptor.capture(), exIdCaptor.capture());

    assertThat(callbackCaptor.getAllValues().get(0)).isInstanceOf(EngineResumeCallback.class);
    EngineResumeCallback engineResumeCallback = (EngineResumeCallback) callbackCaptor.getAllValues().get(0);
    assertThat(engineResumeCallback.getAmbiance()).isEqualTo(ambiance);

    // Verify that only the filtered (rollback) child is stored in executableResponses, not the full matrix.
    // This is the core assertion for the bug fix: post-prod rollback must persist only the combination
    // that was actually spawned, so downstream identity replay uses the correct matrix metadata.
    ArgumentCaptor<Consumer> updateConsumerCaptor = ArgumentCaptor.forClass(Consumer.class);
    verify(nodeExecutionService).updateV2(eq(nodeExecutionId), updateConsumerCaptor.capture());
    Update ops = new Update();
    updateConsumerCaptor.getValue().accept(ops);
    Document addToSet = ops.getUpdateObject().get("$addToSet", Document.class);
    ExecutableResponse storedResponse = addToSet.get("executableResponses", ExecutableResponse.class);
    assertThat(storedResponse.getChildren().getChildrenList()).hasSize(1);
    assertThat(storedResponse.getChildren().getChildren(0).getChildNodeId()).isEqualTo(child1Id);
    assertThat(storedResponse.getChildren().getChildren(0).getStrategyMetadata()).isEqualTo(rollbackSTrategyMetadata);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetFilteredChildren() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .addLevels(Level.newBuilder().setGroup("STAGES").build())
            .addLevels(Level.newBuilder().setSetupId("parallelId").build())
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK).build())
            .build();
    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(accountIdentifier, ambiance.getPlanExecutionId(),
            PlanExecutionProjectionConstants.fieldsForPostProdRollback);
    doReturn(
        Optional.of(PlanExecution.builder()
                        .postExecutionRollbackInfos(Collections.singletonList(
                            PostExecutionRollbackInfo.newBuilder().setPostExecutionRollbackStageId("stageId").build()))
                        .build()))
        .when(planExecutionService)
        .getWithFieldsIncludedOptional(
            ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.postExecutionRollbackInfos));
    List<Child> children = Collections.singletonList(Child.newBuilder().build());
    List<Child> filteredChildren = processor.getFilteredChildren(ambiance, children);
    assertThat(filteredChildren.size()).isEqualTo(1);
    assertThat(filteredChildren.get(0)).isEqualTo(children.get(0));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetFilteredChildrenMatrix() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .addLevels(Level.newBuilder().setGroup("STAGES").build())
            .addLevels(Level.newBuilder()
                           .setSetupId("stageId")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                           .build())
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK).build())
            .build();
    List<Child> children = List.of(
        Child.newBuilder()
            .setChildNodeId("childId")
            .setStrategyMetadata(StrategyMetadata.newBuilder()
                                     .setMatrixMetadata(MatrixMetadata.newBuilder().addMatrixCombination(0).build())
                                     .build())
            .build(),
        Child.newBuilder()
            .setChildNodeId("childId")
            .setStrategyMetadata(StrategyMetadata.newBuilder()
                                     .setMatrixMetadata(MatrixMetadata.newBuilder().addMatrixCombination(1).build())
                                     .build())
            .build(),
        Child.newBuilder()
            .setChildNodeId("childId")
            .setStrategyMetadata(StrategyMetadata.newBuilder()
                                     .setMatrixMetadata(MatrixMetadata.newBuilder().addMatrixCombination(2).build())
                                     .build())
            .build());
    PostExecutionRollbackInfo postExecutionRollbackInfo =
        PostExecutionRollbackInfo.newBuilder()
            .setPostExecutionRollbackStageId("stageId")
            .setRollbackStageStrategyMetadata(
                StrategyMetadata.newBuilder()
                    .setMatrixMetadata(MatrixMetadata.newBuilder().addMatrixCombination(0).build())
                    .build())
            .build();
    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(accountIdentifier, ambiance.getPlanExecutionId(),
            PlanExecutionProjectionConstants.fieldsForPostProdRollback);
    doReturn(Optional.of(PlanExecution.builder()
                             .postExecutionRollbackInfos(Collections.singletonList(postExecutionRollbackInfo))
                             .build()))
        .when(planExecutionService)
        .getWithFieldsIncludedOptional(
            ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.postExecutionRollbackInfos));
    List<Child> filteredChildren = processor.getFilteredChildren(ambiance, children);
    assertThat(filteredChildren.size()).isEqualTo(1);
    assertThat(filteredChildren.get(0)).isEqualTo(children.get(0));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetBatches_EmptyChildren() {
    List<ChildrenStartRequestBatch> batches = processor.getBatches(Collections.emptyList(), Collections.emptyList());

    assertThat(batches).isEmpty();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetBatches_SingleBatch() {
    List<Child> children = ImmutableList.of(Child.newBuilder().setChildNodeId("setupId1").build(),
        Child.newBuilder().setChildNodeId("setupId2").build(), Child.newBuilder().setChildNodeId("setupId3").build());
    List<String> runtimeIds = ImmutableList.of("runtimeId1", "runtimeId2", "runtimeId3");

    List<ChildrenStartRequestBatch> batches = processor.getBatches(children, runtimeIds);

    assertThat(batches).hasSize(1);
    ChildrenStartRequestBatch batch = batches.get(0);
    assertThat(batch.getUuid()).isNotNull();
    assertThat(batch.getChildren()).hasSize(3);
    assertThat(batch.getChildren().get(0).getSetupId()).isEqualTo("setupId1");
    assertThat(batch.getChildren().get(0).getRuntimeId()).isEqualTo("runtimeId1");
    assertThat(batch.getChildren().get(1).getSetupId()).isEqualTo("setupId2");
    assertThat(batch.getChildren().get(1).getRuntimeId()).isEqualTo("runtimeId2");
    assertThat(batch.getChildren().get(2).getSetupId()).isEqualTo("setupId3");
    assertThat(batch.getChildren().get(2).getRuntimeId()).isEqualTo("runtimeId3");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetBatches_MultipleBatches() {
    // OrchestrationRule provides InitiateNodeRequestBatchSize = 20
    int batchSize = 20;
    int totalChildren = 25;
    List<Child> children = IntStream.range(0, totalChildren)
                               .mapToObj(i -> Child.newBuilder().setChildNodeId("setupId" + i).build())
                               .collect(Collectors.toList());
    List<String> runtimeIds =
        IntStream.range(0, totalChildren).mapToObj(i -> "runtimeId" + i).collect(Collectors.toList());

    List<ChildrenStartRequestBatch> batches = processor.getBatches(children, runtimeIds);

    assertThat(batches).hasSize(2);
    assertThat(batches.get(0).getChildren()).hasSize(batchSize);
    assertThat(batches.get(0).getChildren().get(0).getSetupId()).isEqualTo("setupId0");
    assertThat(batches.get(0).getChildren().get(0).getRuntimeId()).isEqualTo("runtimeId0");
    assertThat(batches.get(0).getChildren().get(19).getSetupId()).isEqualTo("setupId19");
    assertThat(batches.get(0).getChildren().get(19).getRuntimeId()).isEqualTo("runtimeId19");

    assertThat(batches.get(1).getChildren()).hasSize(5);
    assertThat(batches.get(1).getChildren().get(0).getSetupId()).isEqualTo("setupId20");
    assertThat(batches.get(1).getChildren().get(0).getRuntimeId()).isEqualTo("runtimeId20");
    assertThat(batches.get(1).getChildren().get(4).getSetupId()).isEqualTo("setupId24");
    assertThat(batches.get(1).getChildren().get(4).getRuntimeId()).isEqualTo("runtimeId24");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetBatches_WithStrategyMetadataCopied() {
    StrategyMetadata strategyMetadata =
        StrategyMetadata.newBuilder().setCurrentIteration(1).setTotalIterations(3).build();
    List<Child> children =
        ImmutableList.of(Child.newBuilder().setChildNodeId("setupId1").setStrategyMetadata(strategyMetadata).build());
    List<String> runtimeIds = ImmutableList.of("runtimeId1");

    List<ChildrenStartRequestBatch> batches = processor.getBatches(children, runtimeIds);

    assertThat(batches).hasSize(1);
    List<InitiateNodeBatchEvent.Child> batchChildren = batches.get(0).getChildren();
    assertThat(batchChildren).hasSize(1);
    assertThat(batchChildren.get(0).hasStrategyMetadata()).isTrue();
    assertThat(batchChildren.get(0).getStrategyMetadata()).isEqualTo(strategyMetadata);
    assertThat(batchChildren.get(0).getSetupId()).isEqualTo("setupId1");
    assertThat(batchChildren.get(0).getRuntimeId()).isEqualTo("runtimeId1");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetBatches_ChildWithoutStrategyMetadata() {
    List<Child> children = ImmutableList.of(Child.newBuilder().setChildNodeId("setupId1").build());
    List<String> runtimeIds = ImmutableList.of("runtimeId1");

    List<ChildrenStartRequestBatch> batches = processor.getBatches(children, runtimeIds);

    assertThat(batches).hasSize(1);
    assertThat(batches.get(0).getChildren().get(0).hasStrategyMetadata()).isFalse();
  }
}