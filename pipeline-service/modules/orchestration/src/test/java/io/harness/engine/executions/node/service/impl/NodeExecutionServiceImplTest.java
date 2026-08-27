/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.ERRORED;
import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;
import static io.harness.rule.OwnerRule.BHUMIJ;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SOUMYO_PURKAYASTHA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.YUVRAJ;
import static io.harness.springdata.SpringDataMongoUtils.setUnset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.engine.OrchestrationTestHelper;
import io.harness.engine.executions.concurrency.counter.StepConcurrencyCounterMutationHook;
import io.harness.engine.executions.node.exception.NodeExecutionUpdateFailedException;
import io.harness.engine.executions.node.helper.NodeExecutionReadHelper;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.observers.NodeExecutionDeleteObserver;
import io.harness.event.OrchestrationLogPublisher;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PlanExecution;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.monitoring.ExecutionStatistics;
import io.harness.observer.Subject;
import io.harness.plan.Node;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.tasks.ProgressData;
import io.harness.utils.AmbianceTestUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.bson.Document;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionServiceImplTest extends OrchestrationTestBase {
  @Mock private Subject<NodeExecutionDeleteObserver> nodeDeleteObserverSubject;
  @Mock private PlanService planService;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private OrchestrationLogPublisher orchestrationLogPublisher;
  @Mock private StepConcurrencyCounterMutationHook counterMutationHook;

  @Inject @InjectMocks @Spy private NodeExecutionServiceImpl nodeExecutionService;

  private static final Set<String> GRAPH_FIELDS = Set.of(NodeExecutionKeys.mode, NodeExecutionKeys.progressData,
      NodeExecutionKeys.unitProgresses, NodeExecutionKeys.executableResponses, NodeExecutionKeys.interruptHistories,
      NodeExecutionKeys.retryIds, NodeExecutionKeys.oldRetry, NodeExecutionKeys.failureInfo, NodeExecutionKeys.endTs);

  @Before
  public void beforeTest() {
    doNothing().when(nodeExecutionService).emitEvent(any(), any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestSave() {
    String nodeExecutionId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(AmbianceTestUtils.buildAmbiance())
            .nodeId(generateUuid())
            .name("name")
            .identifier("dummy")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.QUEUED)
            .build();
    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());

    NodeExecution savedExecution = nodeExecutionService.save(nodeExecution);
    assertThat(savedExecution.getUuid()).isEqualTo(nodeExecutionId);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestErrorOutNodes() {
    String nodeExecutionId1 = generateUuid();
    String nodeExecutionId2 = generateUuid();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(nodeExecutionId1)
            .ambiance(AmbianceTestUtils.buildAmbiance())
            .nodeId(generateUuid())
            .name("name")
            .identifier("dummy")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(nodeExecutionId2)
            .ambiance(AmbianceTestUtils.buildAmbiance())
            .nodeId(generateUuid())
            .name("name")
            .identifier("dummy")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.SUCCEEDED)
            .build();
    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());
    nodeExecutionService.save(nodeExecution1);
    nodeExecutionService.save(nodeExecution2);

    boolean res = nodeExecutionService.errorOutActiveNodes(AmbianceTestUtils.PLAN_EXECUTION_ID);
    assertThat(res).isTrue();
    NodeExecution ne1 = nodeExecutionService.get(nodeExecutionId1);
    assertThat(ne1).isNotNull();
    assertThat(ne1.getStatus()).isEqualTo(ERRORED);

    NodeExecution ne2 = nodeExecutionService.get(nodeExecutionId2);
    assertThat(ne2).isNotNull();
    assertThat(ne2.getStatus()).isEqualTo(SUCCEEDED);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestExtractChildExecutions() {
    NodeExecutionService service = spy(NodeExecutionServiceImpl.class);
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build();
    NodeExecution pipelineNode =
        NodeExecution.builder().uuid("pipelineNode").status(Status.RUNNING).ambiance(ambiance).version(1L).build();
    NodeExecution stageNode = NodeExecution.builder()
                                  .uuid("stageNode")
                                  .status(Status.RUNNING)
                                  .parentId(pipelineNode.getUuid())
                                  .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .build();
    NodeExecution forkNode = NodeExecution.builder()
                                 .uuid("forkNode")
                                 .status(RUNNING)
                                 .parentId(stageNode.getUuid())
                                 .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                 .build();
    NodeExecution child1 = NodeExecution.builder()
                               .uuid("child1")
                               .status(Status.RUNNING)
                               .parentId(forkNode.getUuid())
                               .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                               .build();
    NodeExecution child2 = NodeExecution.builder()
                               .uuid("child2")
                               .status(Status.RUNNING)
                               .parentId(forkNode.getUuid())
                               .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                               .build();
    NodeExecution child3 = NodeExecution.builder()
                               .uuid("child3")
                               .status(Status.RUNNING)
                               .parentId(forkNode.getUuid())
                               .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                               .build();

    NodeExecution strategyParent = NodeExecution.builder()
                                       .uuid("strategyParent")
                                       .status(FAILED)
                                       .parentId(stageNode.getUuid())
                                       .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                       .build();

    NodeExecution strategyNode = NodeExecution.builder()
                                     .uuid("strategyNode")
                                     .status(FAILED)
                                     .parentId(strategyParent.getUuid())
                                     .stepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                     .build();

    NodeExecution strategyChildNode = NodeExecution.builder()
                                          .uuid("childNode")
                                          .status(FAILED)
                                          .parentId(strategyNode.getUuid())
                                          .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                          .build();

    List<NodeExecution> nodeExecutions = Arrays.asList(
        pipelineNode, stageNode, forkNode, child1, child2, child3, strategyNode, strategyChildNode, strategyParent);
    Stream<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutions.iterator()).stream();
    doReturn(iterator).when(service).fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(any(), any(), any());

    List<NodeExecution> stageChildList =
        service.findAllChildrenWithStatusInAndWithoutOldRetries(ambiance.getPlanExecutionId(), stageNode.getUuid(),
            EnumSet.of(Status.RUNNING), true, Collections.emptySet(), false);
    assertThat(stageChildList).isNotEmpty();
    assertThat(stageChildList).hasSize(7);
    assertThat(stageChildList)
        .containsExactlyInAnyOrder(stageNode, forkNode, child1, child2, child3, strategyNode, strategyParent);

    // Iterator cannot be reused again, thus initialise again
    iterator = OrchestrationTestHelper.createCloseableIterator(nodeExecutions.iterator()).stream();
    doReturn(iterator).when(service).fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(any(), any(), any());
    List<NodeExecution> stageChildListWithoutParent =
        service.findAllChildrenWithStatusInAndWithoutOldRetries(ambiance.getPlanExecutionId(), stageNode.getUuid(),
            EnumSet.of(Status.RUNNING), false, Collections.emptySet(), false);
    assertThat(stageChildListWithoutParent).isNotEmpty();
    assertThat(stageChildListWithoutParent).hasSize(6);
    assertThat(stageChildListWithoutParent)
        .containsExactlyInAnyOrder(forkNode, child1, child2, child3, strategyNode, strategyParent);

    // Iterator cannot be reused again, thus initialise again
    iterator = OrchestrationTestHelper.createCloseableIterator(nodeExecutions.iterator()).stream();
    doReturn(iterator).when(service).fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(any(), any(), any());
    List<NodeExecution> forkChildList =
        service.findAllChildrenWithStatusInAndWithoutOldRetries(ambiance.getPlanExecutionId(), forkNode.getUuid(),
            EnumSet.of(Status.RUNNING), true, Collections.emptySet(), false);
    assertThat(forkChildList).isNotEmpty();
    assertThat(forkChildList).hasSize(4);
    assertThat(forkChildList).containsExactlyInAnyOrder(forkNode, child1, child2, child3);

    iterator = OrchestrationTestHelper.createCloseableIterator(nodeExecutions.iterator()).stream();
    doReturn(iterator).when(service).fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(any(), any(), any());
    List<NodeExecution> strategyChildList =
        service.findAllChildrenWithStatusInAndWithoutOldRetries(ambiance.getPlanExecutionId(), stageNode.getUuid(),
            EnumSet.of(Status.RUNNING), true, Collections.emptySet(), true);
    assertThat(strategyChildList).isNotEmpty();
    assertThat(strategyChildList).hasSize(8);
    assertThat(strategyChildList)
        .containsExactlyInAnyOrder(
            forkNode, child1, child2, child3, strategyNode, strategyChildNode, stageNode, strategyParent);

    iterator = OrchestrationTestHelper.createCloseableIterator(nodeExecutions.iterator()).stream();
    doReturn(iterator).when(service).fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(any(), any(), any());
    strategyChildList = service.findAllChildrenWithStatusInAndWithoutOldRetries(ambiance.getPlanExecutionId(),
        stageNode.getUuid(), EnumSet.of(Status.RUNNING), true, Collections.emptySet(), false);
    assertThat(strategyChildList).isNotEmpty();
    assertThat(strategyChildList).hasSize(7);
    assertThat(strategyChildList)
        .containsExactlyInAnyOrder(forkNode, child1, child2, child3, strategyNode, strategyParent, stageNode);

    iterator = OrchestrationTestHelper.createCloseableIterator(nodeExecutions.iterator()).stream();
    doReturn(iterator).when(service).fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(any(), any(), any());
    strategyChildList = service.findAllChildrenWithStatusInAndWithoutOldRetries(ambiance.getPlanExecutionId(),
        pipelineNode.getUuid(), EnumSet.of(Status.RUNNING), true, Collections.emptySet(), false);
    assertThat(strategyChildList).isNotEmpty();
    assertThat(strategyChildList).hasSize(8);
    assertThat(strategyChildList)
        .containsExactlyInAnyOrder(
            pipelineNode, forkNode, child1, child2, child3, strategyNode, strategyParent, stageNode);

    // The method findAllChildrenWithStatusInAndWithoutOldRetries should not depend on the order of nodeExecutions.
    Collections.reverse(nodeExecutions);
    iterator = OrchestrationTestHelper.createCloseableIterator(nodeExecutions.iterator()).stream();
    doReturn(iterator).when(service).fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(any(), any(), any());
    strategyChildList = service.findAllChildrenWithStatusInAndWithoutOldRetries(ambiance.getPlanExecutionId(),
        pipelineNode.getUuid(), EnumSet.of(Status.RUNNING), true, Collections.emptySet(), false);
    assertThat(strategyChildList).isNotEmpty();
    assertThat(strategyChildList).hasSize(8);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetByPlanNodeUuidShouldThrowInvalidRequestException() {
    assertThatThrownBy(() -> nodeExecutionService.getByPlanNodeUuid(generateUuid(), generateUuid()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Node Execution is null for planNodeUuid: ");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetByPlanNodeUuid() {
    String nodeExecutionId = generateUuid();
    String planNodeUuid = generateUuid();
    String planExecutionUuid = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .startTs(System.currentTimeMillis())
            .nodeId(planNodeUuid)
            .name("name")
            .identifier("dummy")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .status(Status.SUCCEEDED)
            .build();
    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());
    nodeExecutionService.save(nodeExecution);

    NodeExecution found = nodeExecutionService.getByPlanNodeUuid(planNodeUuid, planExecutionUuid);
    assertThat(found).isNotNull();

    assertThat(found.getUuid()).isEqualTo(nodeExecutionId);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestMarkLeavesDiscontinuing() {
    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());
    nodeExecutionService.save(nodeExecution);
    nodeExecutionService.save(nodeExecution1);

    long updatedNumber = nodeExecutionService.markLeavesDiscontinuing(
        ImmutableList.of(nodeExecution.getUuid(), nodeExecution1.getUuid()));
    assertThat(updatedNumber).isEqualTo(2);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestMarkAllLeavesDiscontinuing() {
    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.QUEUED)
            .build();
    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());
    nodeExecutionService.save(nodeExecution);
    nodeExecutionService.save(nodeExecution1);
    nodeExecutionService.save(nodeExecution2);

    long updatedNumber =
        nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(planExecutionUuid, EnumSet.of(Status.RUNNING));
    assertThat(updatedNumber).isEqualTo(3);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void markAllFinalizableNodesDiscontinuing_flipsRunningAndQueuedLeaves() {
    // Repro of the abort-flow bug: 1 RUNNING leaf + 2 QUEUED leaves. All three flip to
    // DISCONTINUING. The subsequent errorOutActiveNodes will fire -3 against the counter; without
    // the +2 pre-count fire in this method the counter drifts to -2.
    String accountId = generateUuid();
    String planExecutionUuid = generateUuid();
    when(planExecutionService.getWithFieldsIncludedOptional(eq(planExecutionUuid), any()))
        .thenReturn(Optional.of(PlanExecution.builder()
                                    .uuid(planExecutionUuid)
                                    .ambiance(Ambiance.newBuilder()
                                                  .setPlanExecutionId(planExecutionUuid)
                                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                                                  .build())
                                    .build()));
    String parentId = generateUuid();
    NodeExecution running =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .name("running-leaf")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    NodeExecution queued1 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .name("queued-leaf-1")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.QUEUED)
            .build();
    NodeExecution queued2 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .name("queued-leaf-2")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.QUEUED)
            .build();
    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());
    nodeExecutionService.save(running);
    nodeExecutionService.save(queued1);
    nodeExecutionService.save(queued2);

    long updatedNumber = nodeExecutionService.markAllFinalizableNodesDiscontinuing(planExecutionUuid);
    assertThat(updatedNumber).isEqualTo(3);
    verify(counterMutationHook).onBulkEntry(accountId, 2L);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void markAllFinalizableNodesDiscontinuing_firesBulkEntryForApprovalWaitingLeaf() {
    // Repro of the abort-flow bug: an APPROVAL_WAITING leaf does not hold a step-concurrency slot
    // (APPROVAL is a leaf mode, but APPROVAL_WAITING is excluded from
    // ACTIVE_STATUSES_OCCUPYING_STEP_CONCURRENCY_SLOT), yet it flips to DISCONTINUING (in-slot)
    // here. Without the +1 pre-count fire, the later DISCONTINUING -> ABORTED single-row -1 would
    // drive the counter negative.
    String accountId = generateUuid();
    String planExecutionUuid = generateUuid();
    when(planExecutionService.getWithFieldsIncludedOptional(eq(planExecutionUuid), any()))
        .thenReturn(Optional.of(PlanExecution.builder()
                                    .uuid(planExecutionUuid)
                                    .ambiance(Ambiance.newBuilder()
                                                  .setPlanExecutionId(planExecutionUuid)
                                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                                                  .build())
                                    .build()));
    String parentId = generateUuid();
    NodeExecution approvalWaiting =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.APPROVAL)
            .name("approval-leaf")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.APPROVAL_WAITING)
            .build();
    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());
    nodeExecutionService.save(approvalWaiting);

    long updatedNumber = nodeExecutionService.markAllFinalizableNodesDiscontinuing(planExecutionUuid);
    assertThat(updatedNumber).isEqualTo(1);
    verify(counterMutationHook).onBulkEntry(accountId, 1L);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void markLeavesDiscontinuing_firesBulkEntryForApprovalWaitingLeaf() {
    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    String accountId = generateUuid();
    NodeExecution approvalWaiting =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId(planExecutionUuid)
                          .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                          .build())
            .mode(ExecutionMode.APPROVAL)
            .name("approval-leaf")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.APPROVAL_WAITING)
            .build();
    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());
    nodeExecutionService.save(approvalWaiting);

    long updatedNumber = nodeExecutionService.markLeavesDiscontinuing(ImmutableList.of(approvalWaiting.getUuid()));
    assertThat(updatedNumber).isEqualTo(1);
    verify(counterMutationHook).onBulkEntry(accountId, 1L);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void markAllLeavesAndQueuedNodesDiscontinuing_firesBulkEntryForApprovalWaitingLeaf() {
    String accountId = generateUuid();
    String planExecutionUuid = generateUuid();
    when(planExecutionService.getWithFieldsIncludedOptional(eq(planExecutionUuid), any()))
        .thenReturn(Optional.of(PlanExecution.builder()
                                    .uuid(planExecutionUuid)
                                    .ambiance(Ambiance.newBuilder()
                                                  .setPlanExecutionId(planExecutionUuid)
                                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                                                  .build())
                                    .build()));
    String parentId = generateUuid();
    NodeExecution approvalWaiting =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.APPROVAL)
            .name("approval-leaf")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.APPROVAL_WAITING)
            .build();
    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());
    nodeExecutionService.save(approvalWaiting);

    long updatedNumber = nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(
        planExecutionUuid, StatusUtils.userMarkedFailureStatuses());
    assertThat(updatedNumber).isEqualTo(1);
    verify(counterMutationHook).onBulkEntry(accountId, 1L);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestFetchChildrenNodeExecutionsIterator() {
    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());

    nodeExecutionService.save(nodeExecution);
    nodeExecutionService.save(nodeExecution1);

    List<NodeExecution> nodeExecutions = new LinkedList<>();
    try (Stream<NodeExecution> stream = nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             parentId, NodeProjectionUtils.fieldsForResponseNotifyData)) {
      stream.forEach(nodeExec -> { nodeExecutions.add(nodeExec); });
    }
    assertThat(nodeExecutions).isNotEmpty();

    assertThat(nodeExecutions.size()).isEqualTo(2);
    assertThat(nodeExecutions)
        .extracting(NodeExecution::getUuid)
        .containsExactlyInAnyOrder(nodeExecution.getUuid(), nodeExecution1.getUuid());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFetchChildrenNodeExecutionsRecursivelyFromGivenParentIdWithoutOldRetries() {
    String stpGrp1Fqn = "pipeline.stages.stage1.stg1";
    String stpGrp1Child1Fqn = "pipeline.stages.stage1.stg1.shell1";
    String stpGrp2Fqn = "pipeline.stages.stage1.stg1.stg2";
    String stpGrp2Child1Fqn = "pipeline.stages.stage1.stg1.stg2.shell2";

    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    String nodeUuid = generateUuid();
    String nodeExecutionUuid = generateUuid();

    // Node execution of Parent StepGroup
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid)
            .name("name")
            .identifier("stage1")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP_GROUP).build())
            .module("CD")
            .build();
    String nodeUuid2 = generateUuid();
    String nodeExecutionUuid2 = generateUuid();
    // Node execution of type step
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid2)
            .parentId(nodeExecutionUuid)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid2)
            .name("name")
            .identifier("shell1")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .build();

    String nodeUuid3 = generateUuid();
    String nodeExecutionUuid3 = generateUuid();
    // Node execution of type child Step group
    NodeExecution nodeExecution3 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid3)
            .parentId(nodeExecutionUuid)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid3)
            .name("name")
            .identifier("stage3")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP_GROUP).build())
            .module("CD")
            .build();

    String nodeUuid4 = generateUuid();
    String nodeExecutionUuid4 = generateUuid();
    // Node execution of type step old retry
    NodeExecution nodeExecution4 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid4)
            .parentId(nodeExecutionUuid3)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid4)
            .name("name")
            .oldRetry(true)
            .identifier("shell1")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .build();

    String nodeUuid5 = generateUuid();
    String nodeExecutionUuid5 = generateUuid();
    // Node execution of type shell latest retry
    NodeExecution nodeExecution5 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid5)
            .parentId(nodeExecutionUuid3)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid5)
            .name("name")
            .oldRetry(false)
            .identifier("shell1")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .build();

    // saving nodeExecution
    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());
    nodeExecutionService.save(nodeExecution1);
    nodeExecutionService.save(nodeExecution2);
    nodeExecutionService.save(nodeExecution3);
    nodeExecutionService.save(nodeExecution4);
    nodeExecutionService.save(nodeExecution5);
    List<NodeExecution> nodeExecutions =
        nodeExecutionService.fetchChildrenNodeExecutionsRecursivelyFromGivenParentIdWithoutOldRetries(
            planExecutionUuid, Arrays.asList(parentId));

    assertThat(nodeExecutions).isNotEmpty();
    assertThat(nodeExecutions.size()).isEqualTo(5);
    assertThat(nodeExecutions)
        .extracting(NodeExecution::getUuid)
        .containsExactlyInAnyOrder(nodeExecution1.getUuid(), nodeExecution2.getUuid(), nodeExecution3.getUuid(),
            nodeExecution4.getUuid(), nodeExecution5.getUuid());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFetchAllChildrenNodeIdsRecursively() {
    String stpGrp1Fqn = "pipeline.stages.stage1.stg1";
    String stpGrp1Child1Fqn = "pipeline.stages.stage1.stg1.shell1";
    String stpGrp2Fqn = "pipeline.stages.stage1.stg1.stg2";
    String stpGrp2Child1Fqn = "pipeline.stages.stage1.stg1.stg2.shell2";

    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    String nodeUuid = generateUuid();
    String nodeExecutionUuid = generateUuid();

    // Node execution of Parent StepGroup
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid)
            .name("name")
            .identifier("stage1")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP_GROUP).build())
            .module("CD")
            .build();
    String nodeUuid2 = generateUuid();
    String nodeExecutionUuid2 = generateUuid();
    // Node execution of type step
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid2)
            .parentId(nodeExecutionUuid)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid2)
            .name("name")
            .identifier("shell1")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .build();

    String nodeUuid3 = generateUuid();
    String nodeExecutionUuid3 = generateUuid();
    // Node execution of type child Step group
    NodeExecution nodeExecution3 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid3)
            .parentId(nodeExecutionUuid)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid3)
            .name("name")
            .identifier("stage3")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP_GROUP).build())
            .module("CD")
            .build();

    String nodeUuid4 = generateUuid();
    String nodeExecutionUuid4 = generateUuid();
    // Node execution of type step old retry
    NodeExecution nodeExecution4 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid4)
            .parentId(nodeExecutionUuid3)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid4)
            .name("name")
            .oldRetry(true)
            .identifier("shell1")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .build();

    String nodeUuid5 = generateUuid();
    String nodeExecutionUuid5 = generateUuid();
    // Node execution of type shell latest retry
    NodeExecution nodeExecution5 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid5)
            .parentId(nodeExecutionUuid3)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid5)
            .name("name")
            .oldRetry(false)
            .identifier("shell1")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .build();

    // saving nodeExecution
    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());
    nodeExecutionService.save(nodeExecution1);
    nodeExecutionService.save(nodeExecution2);
    nodeExecutionService.save(nodeExecution3);
    nodeExecutionService.save(nodeExecution4);
    nodeExecutionService.save(nodeExecution5);

    List<String> nodeExecutions1 =
        nodeExecutionService.fetchAllChildrenNodeIdsRecursively(planExecutionUuid, Arrays.asList(parentId));

    assertThat(nodeExecutions1).isNotEmpty();
    assertThat(nodeExecutions1.size()).isEqualTo(5);
    assertThat(nodeExecutions1)
        .containsExactlyInAnyOrder(nodeExecution1.getUuid(), nodeExecution2.getUuid(), nodeExecution3.getUuid(),
            nodeExecution4.getUuid(), nodeExecution5.getUuid());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void test() {
    // stageFqn
    String stage1Fqn = "pipeline.stages.stage1";
    String stage2Fqn = "pipeline.stages.stage2";
    String stage3Fqn = "pipeline.stages.stage3";

    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    String nodeUuid = generateUuid();
    String nodeExecutionUuid = generateUuid();

    // Node execution of type other than stage
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid)
            .name("name")
            .identifier("stage1")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .stageFqn(stage1Fqn)
            .build();
    String nodeUuid2 = generateUuid();
    String nodeExecutionUuid2 = generateUuid();
    // Node execution of type stage
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid2)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid2)
            .name("name")
            .identifier("stage2")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
            .module("CD")
            .stageFqn(stage2Fqn)
            .build();

    String nodeUuid3 = generateUuid();
    String nodeExecutionUuid3 = generateUuid();
    // Node execution of type stage
    NodeExecution nodeExecution3 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid3)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid3)
            .name("name")
            .identifier("stage3")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
            .module("CD")
            .stageFqn(stage3Fqn)
            .build();

    // saving nodeExecution
    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());
    nodeExecutionService.save(nodeExecution1);
    nodeExecutionService.save(nodeExecution2);
    nodeExecutionService.save(nodeExecution3);

    List<String> stageFqns = nodeExecutionService.fetchStageFqnFromStageIdentifiers(
        planExecutionUuid, Arrays.asList("stage1", "stage2", "stage3"));
    assertThat(stageFqns.size()).isEqualTo(2);
    assertThat(stageFqns).contains(stage2Fqn);
    assertThat(stageFqns).contains(stage3Fqn);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMapNodeExecutionIdWithPlanNodeForGivenStageFQN() {
    // stageFqn
    String stage1Fqn = "pipeline.stages.stage1";
    String stage2Fqn = "pipeline.stages.stage2";
    String stage3Fqn = "pipeline.stages.stage3";

    String planExecutionUuid = generateUuid();
    String planId = generateUuid();
    String parentId = generateUuid();
    String nodeUuid = generateUuid();
    String nodeExecutionUuid = generateUuid();

    // Node execution of type other than stage
    PlanNode node1 = PlanNode.builder()
                         .uuid(nodeUuid)
                         .name("name")
                         .identifier("stage1")
                         .stageFqn(stage1Fqn)
                         .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                         .serviceName("CD")
                         .build();

    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).setPlanId(planId).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid)
            .name("name")
            .oldRetry(false)
            .identifier("stage1")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .startTs(500L)
            .module("CD")
            .stageFqn(stage1Fqn)
            .build();

    String nodeExecutionUuid12 = generateUuid();
    NodeExecution nodeExecution12 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid12)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).setPlanId(planId).build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid)
            .name("name")
            .identifier("stage1")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .oldRetry(true)
            .startTs(200L)
            .stageFqn(stage1Fqn)
            .build();
    String nodeUuid2 = generateUuid();
    String nodeExecutionUuid2 = generateUuid();
    // Node execution of type stage
    PlanNode node2 = PlanNode.builder()
                         .uuid(nodeUuid2)
                         .name("name")
                         .identifier("stage2")
                         .stageFqn(stage2Fqn)
                         .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
                         .serviceName("CD")
                         .build();
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid2)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).setPlanId(planId).build())
            .stageFqn(stage2Fqn)
            .status(Status.RUNNING)
            .nodeId(nodeUuid2)
            .name("name")
            .identifier("stage2")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
            .oldRetry(false)
            .startTs(400L)
            .module("CD")
            .build();

    String nodeUuid3 = generateUuid();
    String nodeExecutionUuid3 = generateUuid();
    // Node execution of type stage
    PlanNode node3 = PlanNode.builder()
                         .uuid(nodeUuid3)
                         .name("name")
                         .stageFqn(stage3Fqn)
                         .identifier("stage3")
                         .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
                         .serviceName("CD")
                         .build();
    NodeExecution nodeExecution3 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid3)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).setPlanId(planId).build())
            .stageFqn(stage3Fqn)
            .status(Status.RUNNING)
            .nodeId(nodeUuid3)
            .name("name")
            .startTs(500L)
            .identifier("stage3")
            .oldRetry(false)
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
            .module("CD")
            .build();

    // saving nodeExecution
    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());
    nodeExecutionService.save(nodeExecution1);
    nodeExecutionService.save(nodeExecution12);
    nodeExecutionService.save(nodeExecution2);
    nodeExecutionService.save(nodeExecution3);

    when(planService.fetchAllNodes(planId, Set.of(nodeUuid, nodeUuid2, nodeUuid3)))
        .thenReturn(Set.of(node1, node2, node3));
    Map<String, Node> uuidNodeMap = nodeExecutionService.mapNodeExecutionIdWithPlanNodeForGivenStageFQN(
        planExecutionUuid, Arrays.asList(stage1Fqn, stage2Fqn, stage3Fqn));
    // Total 4 nodeExecutions but only 3 will be returned. Because nodeExecution1 and nodeExecution12 correspond to same
    // planNode.
    assertThat(uuidNodeMap.size()).isEqualTo(3);

    assertThat(uuidNodeMap.containsKey(nodeExecutionUuid)).isEqualTo(true);
    assertThat(uuidNodeMap.get(nodeExecutionUuid).getIdentifier()).isEqualTo("stage1");

    // nodeExecutionUuid12 will not be present because nodeExecutionUuid12 and nodeExecutionUuid12 both belongs to same
    // PlanNode. And nodeExecutionUuid is final nodeExecution later startTs.
    assertThat(uuidNodeMap.containsKey(nodeExecutionUuid12)).isFalse();

    assertThat(uuidNodeMap.containsKey(nodeExecutionUuid2)).isEqualTo(true);
    assertThat(uuidNodeMap.get(nodeExecutionUuid2).getIdentifier()).isEqualTo("stage2");

    assertThat(uuidNodeMap.containsKey(nodeExecutionUuid3)).isEqualTo(true);
    assertThat(uuidNodeMap.get(nodeExecutionUuid3).getIdentifier()).isEqualTo("stage3");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testShouldLog() {
    Update update = new Update();
    update.addToSet(NodeExecutionKeys.executableResponses, null);
    update.set(NodeExecutionKeys.failureInfo, FailureInfo.newBuilder().build());
    assertThat(nodeExecutionService.shouldLog(update)).isTrue();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testShouldNotLog() {
    Update update = new Update();
    update.set(NodeExecutionKeys.nodeId, "test");
    assertThat(nodeExecutionService.shouldLog(update)).isFalse();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testCountByParentIdAndStatusIn() {
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    String parentId = "parentId";
    Set<Status> flowingStatuses = new HashSet<>();
    Query query =
        query(where(NodeExecutionKeys.parentId).is(parentId)).addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    nodeExecutionService.findCountByParentIdAndStatusIn(parentId, flowingStatuses);
    verify(nodeExecutionReadHelperMock, times(1)).findCount(query);
    flowingStatuses.add(RUNNING);
    query.addCriteria(where(NodeExecutionKeys.status).in(flowingStatuses));
    nodeExecutionService.findCountByParentIdAndStatusIn(parentId, flowingStatuses);
    verify(nodeExecutionReadHelperMock, times(1)).findCount(query);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteAllNodeExecutionAndMetadata() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    Reflect.on(nodeExecutionService).set("nodeDeleteObserverSubject", nodeDeleteObserverSubject);

    List<NodeExecution> nodeExecutionList = new LinkedList<>();
    Set<String> firstBatchNodeExecutionIds = new HashSet<>();
    Set<String> secondBatchNodeExecutionIds = new HashSet<>();
    for (int i = 0; i < 900; i++) {
      String uuid = generateUuid();
      nodeExecutionList.add(NodeExecution.builder().uuid(uuid).build());
      if (i < 500) {
        firstBatchNodeExecutionIds.add(uuid);
      } else {
        secondBatchNodeExecutionIds.add(uuid);
      }
    }
    Stream<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator()).stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsFromAnalytics(
            new HashSet<>(Arrays.asList("EXECUTION_1")), NodeProjectionUtils.fieldsForNodeExecutionDelete);
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    nodeExecutionService.deleteAllNodeExecutionAndMetadata(new HashSet<>(Arrays.asList("EXECUTION_1")));
    verify(nodeDeleteObserverSubject, times(2)).fireInform(any(), any());

    verify(mongoTemplateMock, times(2)).remove(queryArgumentCaptor.capture(), eq(NodeExecution.class));
    assertThat(queryArgumentCaptor.getAllValues().size()).isEqualTo(2);
    assertThat(queryArgumentCaptor.getAllValues().get(1))
        .isEqualTo(query(where(NodeExecutionKeys.id).in(secondBatchNodeExecutionIds)));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdateTTLForAllNodeExecutionAndMetadata() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);

    Date ttlExpiry = Date.from(OffsetDateTime.now().plus(Duration.ofMinutes(30)).toInstant());
    nodeExecutionService.updateTTLForNodeExecution("EXECUTION_1", ttlExpiry);
    Update ops = new Update();
    ops.set(NodeExecutionKeys.validUntil, ttlExpiry);
    verify(mongoTemplateMock, times(1))
        .updateMulti(query(where(NodeExecutionKeys.planExecutionId).is("EXECUTION_1")), ops, NodeExecution.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchNodeExecutionsForGivenStageFQNs() {
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    List<NodeExecution> nodeExecutionList = new LinkedList<>();
    for (int i = 0; i < 1200; i++) {
      String uuid = generateUuid();
      nodeExecutionList.add(NodeExecution.builder().uuid(uuid).build());
    }
    Stream<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator()).stream();
    String planExecutionId = "planId";
    List<String> stageFQNs = Collections.singletonList("s1");
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.stageFqn)
                            .in(stageFQNs)
                            .and(NodeExecutionKeys.oldRetry)
                            .is(false);
    Query query = query(criteria);
    query.fields().include("node");
    doReturn(iterator).when(nodeExecutionReadHelperMock).fetchNodeExecutions(query);
    Stream<NodeExecution> fetchedIterator = nodeExecutionService.fetchNodeExecutionsForGivenStageFQNs(
        planExecutionId, stageFQNs, Collections.singletonList("node"));
    assertThat(fetchedIterator).isEqualTo(iterator);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testFetchNodeExecutionsWithoutOldRetriesIterator() {
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    String planExecutionId = "planId";
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesIterator(planExecutionId);
    verify(nodeExecutionReadHelperMock, times(1)).fetchNodeExecutionsIteratorWithoutProjections(query);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testFetchChildrenNodeExecutionsIteratorWithoutProjection() {
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    NodeExecution nodeExecutions = NodeExecution.builder().build();
    List<NodeExecution> nodeExecutionList = new LinkedList<>();
    for (int i = 0; i < 1000; i++) {
      String uuid = generateUuid();
      nodeExecutionList.add(NodeExecution.builder().uuid(uuid).build());
    }
    Stream<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator()).stream();
    List<String> parentId = new ArrayList<>();
    doReturn(iterator)
        .when(nodeExecutionReadHelperMock)
        .fetchNodeExecutionsIteratorWithoutProjectionsFromSecondary(any());

    Stream<NodeExecution> actualResult =
        nodeExecutionService.fetchChildrenNodeExecutionsIteratorWithoutProjection("planExecutionId", parentId);
    verify(nodeExecutionReadHelperMock, times(1)).fetchNodeExecutionsIteratorWithoutProjectionsFromSecondary(any());
    assertThat(actualResult).isEqualTo(iterator);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testFetchAllStepNodeExecutions() {
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);

    String planExecutionId = "planId";
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).is(StepCategory.STEP));
    Set<String> fieldsToInclude = new HashSet<>();
    for (String fieldName : fieldsToInclude) {
      query.fields().include(fieldName);
    }
    nodeExecutionService.fetchAllStepNodeExecutions(planExecutionId, fieldsToInclude);
    verify(nodeExecutionReadHelperMock, times(1)).fetchNodeExecutions(query);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetPipelineNodeExecutionWithProjections() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    String planExecutionId = "planId";
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).is(StepCategory.PIPELINE))
                      .with(Sort.by(Sort.Direction.ASC, NodeExecutionKeys.createdAt));
    Set<String> fields = new HashSet<>();
    for (String fieldName : fields) {
      query.fields().include(fieldName);
    }
    Optional<NodeExecution> nodeExecution =
        nodeExecutionService.getPipelineNodeExecutionWithProjections(planExecutionId, fields);
    verify(mongoTemplateMock, times(1)).findOne(query, NodeExecution.class);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetForNodeExecutionIDs() {
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    List<String> nodeExecutionIds = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      String uuid = generateUuid();
      nodeExecutionIds.add(uuid);
    }

    Query query = query(where(NodeExecutionKeys.uuid).in(nodeExecutionIds));
    nodeExecutionService.get(nodeExecutionIds);
    verify(nodeExecutionReadHelperMock, times(1)).fetchNodeExecutionsWithAllFields(query);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testfetchStrategyNodeExecutions() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    List<String> stageFQNs = new ArrayList<>();
    List<NodeExecution> nodeExecutions = new ArrayList<>();
    doReturn(nodeExecutions).when(mongoTemplateMock).find(any(), any());
    List<NodeExecution> actualNodeExecution =
        nodeExecutionService.fetchStrategyNodeExecutions("placeExecutionID", stageFQNs);
    verify(mongoTemplateMock, times(1)).find(any(), any());
    assertThat(actualNodeExecution).isEqualTo(nodeExecutions);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testFetchNodeExecutionForPlanNodeAndRetriedId() {
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    NodeExecution nodeExecutions = NodeExecution.builder().build();
    List<String> retriedId = new ArrayList<>();
    doReturn(nodeExecutions).when(nodeExecutionReadHelperMock).fetchNodeExecutionsFromSecondaryTemplate(any());
    NodeExecution actualNodeExecution = nodeExecutionService.fetchNodeExecutionForPlanNodeAndRetriedId(
        "placeExecutionID", "planeNodeId", false, retriedId);
    verify(nodeExecutionReadHelperMock, times(1)).fetchNodeExecutionsFromSecondaryTemplate(any());
    assertThat(actualNodeExecution).isEqualTo(nodeExecutions);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testFetchStageExecutionsWithProjection() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    Set<String> fieldsToBeIncluded = new HashSet<>();
    List<NodeExecution> nodeExecution = new ArrayList<>();
    doReturn(nodeExecution).when(mongoTemplateMock).find(any(), any());
    List<NodeExecution> actualNodeExecution =
        nodeExecutionService.fetchStageExecutionsWithProjection("planExecutionId", fieldsToBeIncluded);
    verify(mongoTemplateMock, times(1)).find(any(), any());
    assertThat(nodeExecution).isEqualTo(actualNodeExecution);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void fetchStageExecutionsWithEndTsAndStatusProjection() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);

    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(SUCCEEDED)
            .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.SKIPPED)
            .build();

    List<NodeExecution> nodeExecutionList = Arrays.asList(nodeExecution, nodeExecution1);

    doReturn(nodeExecutionList).when(mongoTemplateMock).find(any(), any());

    List<NodeExecution> executionList =
        nodeExecutionService.fetchStageExecutionsWithEndTsAndStatusProjection(planExecutionUuid);
    verify(mongoTemplateMock, times(1)).find(any(), any());
    assertThat(Arrays.asList(executionList.get(0).getStatus(), executionList.get(1).getStatus()))
        .contains(Status.SKIPPED);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testFetchAllLeavesWithPlanExecutionId() {
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    NodeExecution nodeExecutions = NodeExecution.builder().build();
    List<NodeExecution> nodeExecutionList = new LinkedList<>();
    for (int i = 0; i < 1000; i++) {
      String uuid = generateUuid();
      nodeExecutionList.add(NodeExecution.builder().uuid(uuid).build());
    }
    Stream<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator()).stream();
    doReturn(iterator).when(nodeExecutionReadHelperMock).fetchNodeExecutionsFromAnalytics(any());
    Set<String> fieldsToBeIncluded = new HashSet<>();
    Stream<NodeExecution> actualNodeExecution =
        nodeExecutionService.fetchAllLeavesUsingPlanExecutionId("placeExecutionID", fieldsToBeIncluded);
    verify(nodeExecutionReadHelperMock, times(1)).fetchNodeExecutionsFromAnalytics(any());
    assertThat(actualNodeExecution).isEqualTo(iterator);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testFetchAllNodeExecutionsByPlanExecutionIdNotUpdatedInGraph() {
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    List<NodeExecution> nodeExecutionList = new LinkedList<>();
    for (int i = 0; i < 5; i++) {
      String uuid = generateUuid();
      nodeExecutionList.add(NodeExecution.builder().uuid(uuid).build());
    }
    Stream<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator()).stream();
    doReturn(iterator).when(nodeExecutionReadHelperMock).fetchNodeExecutionsWithoutValidation(any());
    Long lastUpdatedAt = System.currentTimeMillis();
    Stream<NodeExecution> actualNodeExecution =
        nodeExecutionService.fetchAllNodeExecutionsByPlanExecutionIdLastUpdatedAtGT("placeExecutionID", lastUpdatedAt);

    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is("placeExecutionID")
                            .and(NodeExecutionKeys.lastUpdatedAt)
                            .gt(lastUpdatedAt);
    verify(nodeExecutionReadHelperMock, times(1))
        .fetchNodeExecutionsWithoutValidation(
            query(criteria).with(Sort.by(Sort.Direction.ASC, NodeExecutionKeys.createdAt)));
    assertThat(actualNodeExecution).isEqualTo(iterator);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testSaveAll() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    List<NodeExecution> nodeExecutions = new ArrayList<>();
    NodeExecution nodeExecution = NodeExecution.builder().uuid("12345").build();
    doReturn(Collections.singletonList(nodeExecution)).when(mongoTemplateMock).insertAll(nodeExecutions);
    List<NodeExecution> nodeExecutionsList = nodeExecutionService.saveAll(nodeExecutions);
    assertThat(nodeExecutionsList.size()).isEqualTo(1);
    assertThat(nodeExecutionsList.get(0).getUuid()).isEqualTo("12345");
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testMarkRetried() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    Update ops = new Update().set(NodeExecutionKeys.oldRetry, Boolean.TRUE);
    String nodeExecutionId = "";
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    doReturn(null).when(mongoTemplateMock).findAndModify(query, ops, NodeExecution.class);
    boolean result = nodeExecutionService.markRetried(nodeExecutionId);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testMarkRetriedWhenNodeExecutionNotNull() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    OrchestrationLogPublisher orchestrationLogPublisher = Mockito.mock(OrchestrationLogPublisher.class);
    Update ops = new Update().set(NodeExecutionKeys.oldRetry, Boolean.TRUE);
    String nodeExecutionId = "";
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("3").build();
    NodeExecution nodeExecution = NodeExecution.builder().uuid("12345").ambiance(ambiance).build();
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    doReturn(nodeExecution).when(mongoTemplateMock).findAndModify(eq(query), any(), eq(NodeExecution.class));
    boolean result = nodeExecutionService.markRetried(nodeExecutionId);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testUpdateRelationShipsForRetryNode() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    UpdateResult updated = UpdateResult.acknowledged(1, null, null);
    doReturn(updated).when(mongoTemplateMock).updateMulti(any(Query.class), any(Update.class), eq(NodeExecution.class));
    boolean result = nodeExecutionService.updateRelationShipsForRetryNode(any(), any());
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testUpdateRelationShipsForRetryNodeNotAcknowledged() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    UpdateResult updated = UpdateResult.unacknowledged();
    doReturn(updated).when(mongoTemplateMock).updateMulti(any(Query.class), any(Update.class), eq(NodeExecution.class));
    boolean result = nodeExecutionService.updateRelationShipsForRetryNode(any(), any());
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetTimeoutInstanceIdsForSkippedNode() {
    List<String> timeoutInstanceIds = nodeExecutionService.getTimeoutInstanceIds(Status.SKIPPED, generateUuid());
    assertThat(timeoutInstanceIds).isEmpty();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testFetchNonFinalStatuses() {
    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(SUCCEEDED)
            .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.QUEUED_LICENSE_LIMIT_REACHED)
            .build();
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.APPROVAL_WAITING)
            .build();

    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());

    nodeExecutionService.save(nodeExecution);
    nodeExecutionService.save(nodeExecution1);
    nodeExecutionService.save(nodeExecution2);

    List<Status> statuses = nodeExecutionService.fetchNonFlowingAndNonFinalStatuses(planExecutionUuid);
    assertThat(statuses).contains(Status.QUEUED_LICENSE_LIMIT_REACHED, Status.APPROVAL_WAITING);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testAggregateRunningExecutionCountPerAccount() {
    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    Map<String, String> m1 = new HashMap<>();
    m1.put(SetupAbstractionKeys.accountId, generateUuid());
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().putAllSetupAbstractions(m1).setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(SUCCEEDED)
            .build();
    m1.put(SetupAbstractionKeys.accountId, generateUuid());
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().putAllSetupAbstractions(m1).setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.INPUT_WAITING)
            .build();
    m1.put(SetupAbstractionKeys.accountId, generateUuid());
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().putAllSetupAbstractions(m1).setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.APPROVAL_WAITING)
            .build();

    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());

    nodeExecutionService.save(nodeExecution);
    nodeExecutionService.save(nodeExecution1);
    nodeExecutionService.save(nodeExecution2);

    ExecutionStatistics executionStatistics = nodeExecutionService.aggregateRunningNodeExecutionsCount();
    assertThat(executionStatistics.getAccountStats().size()).isEqualTo(2);
    assertThat(executionStatistics.getModuleAndStepTypeStats().size()).isEqualTo(1);
    assertThat(executionStatistics.getAccountStats().get(0).getCount()).isEqualTo(1);
    assertThat(executionStatistics.getAccountStats().get(1).getCount()).isEqualTo(1);
    assertThat(executionStatistics.getModuleAndStepTypeStats().get(0).getCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testFetchStageExecutionsWithEndTsAndStatusProjection() {
    String planExecutionUuid = generateUuid();
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionUuid)
                            .and(NodeExecutionKeys.stepCategory)
                            .in(Arrays.asList(StepCategory.STAGE, StepCategory.STRATEGY));
    Query query = new Query(criteria);
    query.fields()
        .include(NodeExecutionKeys.uuid)
        .include(NodeExecutionKeys.status)
        .include(NodeExecutionKeys.startTs)
        .include(NodeExecutionKeys.endTs)
        .include(NodeExecutionKeys.createdAt)
        .include(NodeExecutionKeys.mode)
        .include(NodeExecutionKeys.stepType)
        .include(NodeExecutionKeys.ambiance)
        .include(NodeExecutionKeys.nodeId)
        .include(NodeExecutionKeys.parentId)
        .include(NodeExecutionKeys.oldRetry)
        .include(NodeExecutionKeys.ambiance)
        .include(NodeExecutionKeys.resolvedParams)
        .include(NodeExecutionKeys.failureInfo)
        .include(NodeExecutionKeys.executableResponses);
    query.with(Sort.by(NodeExecutionKeys.createdAt));

    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    nodeExecutionService.fetchStageExecutionsWithEndTsAndStatusProjection(planExecutionUuid);
    verify(mongoTemplateMock, times(1)).find(queryArgumentCaptor.capture(), eq(NodeExecution.class));
    assertThat(queryArgumentCaptor.getValue().getQueryObject()).isEqualTo(query.getQueryObject());
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testFetchStageFqnFromStageIdentifiers() {
    String planExecutionUuid = generateUuid();
    String nodeUuid = generateUuid();
    String nodeExecutionUuid = generateUuid();
    String stage1Fqn = "pipeline.stages.test1";

    // Node execution of stage test1
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .status(SUCCEEDED)
            .nodeId(nodeUuid)
            .name("test1")
            .identifier("test1")
            .stepType(StepType.newBuilder().setType("CUSTOM_STAGE").setStepCategory(StepCategory.STAGE).build())
            .module("CD")
            .stageFqn(stage1Fqn)
            .build();

    // Node execution of next stage test2, step test1 with strategy
    String nodeUuid2 = generateUuid();
    String nodeExecutionUuid2 = generateUuid();
    String stage2Fqn = "pipeline.stages.test2";
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid2)
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId(planExecutionUuid)
                          .addLevels(Level.newBuilder().setIdentifier("pipeline").build())
                          .addLevels(Level.newBuilder().setIdentifier("stages").build())
                          .addLevels(Level.newBuilder().setIdentifier("test2").build())
                          .addLevels(Level.newBuilder().setIdentifier("spec").build())
                          .addLevels(Level.newBuilder().setIdentifier("execution").build())
                          .addLevels(Level.newBuilder().setIdentifier("steps").build())
                          .addLevels(Level.newBuilder()
                                         .setIdentifier("test1")
                                         .setStepType(StepType.newBuilder()
                                                          .setType("STRATEGY")
                                                          .setStepCategory(StepCategory.STRATEGY)
                                                          .build())
                                         .build())
                          .build())
            .status(FAILED)
            .nodeId(nodeUuid2)
            .name("test1")
            .identifier("test1")
            .stepType(StepType.newBuilder().setType("STRATEGY").setStepCategory(StepCategory.STRATEGY).build())
            .module("CD")
            .stageFqn(stage2Fqn)
            .build();

    doReturn(false).when(nodeExecutionService).checkPresenceOfResolvedParametersForNonIdentityNodes(any());
    nodeExecutionService.save(nodeExecution1);
    nodeExecutionService.save(nodeExecution2);

    List<String> stageFqnList =
        nodeExecutionService.fetchStageFqnFromStageIdentifiers(planExecutionUuid, Collections.singletonList("test1"));
    assertThat(stageFqnList).hasSize(1).contains(stage1Fqn);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetAmbianceWithFFOn() {
    String planExecutionUuid = generateUuid();
    String nodeUuid = generateUuid();
    String nodeExecutionUuid = generateUuid();
    String stage1Fqn = "pipeline.stages.test1";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionUuid)
                            .putSetupAbstractions("accountId", "ACCOUNT_ID")
                            .build();

    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid)
            .ambiance(ambiance)
            .status(SUCCEEDED)
            .nodeId(nodeUuid)
            .name("test1")
            .identifier("test1")
            .stepType(StepType.newBuilder().setType("CUSTOM_STAGE").setStepCategory(StepCategory.STAGE).build())
            .module("CD")
            .stageFqn(stage1Fqn)
            .build();
    verify(planExecutionService, times(0)).getExecutionMetadataFromPlanExecution(planExecutionUuid);
    assertThat(nodeExecutionService.getAmbiance(nodeExecution1)).isEqualTo(ambiance);

    String planExecutionUuid1 = generateUuid();
    String nodeUuid1 = generateUuid();
    String nodeExecutionUuid1 = generateUuid();
    String stage2Fqn = "pipeline.stages.test1";
    Ambiance ambiance1 = Ambiance.newBuilder()
                             .setPlanExecutionId(planExecutionUuid1)
                             .putSetupAbstractions("accountId", "ACCOUNT_ID")
                             .setMetadata(ExecutionMetadata.newBuilder().build())
                             .build();
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid1)
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId(planExecutionUuid1)
                                  .putSetupAbstractions("accountId", "ACCOUNT_ID")
                                  .build())
            .ambiance(ambiance1)
            .status(SUCCEEDED)
            .nodeId(nodeUuid1)
            .name("test2")
            .identifier("test2")
            .stepType(StepType.newBuilder().setType("CUSTOM_STAGE").setStepCategory(StepCategory.STAGE).build())
            .module("CD")
            .stageFqn(stage2Fqn)
            .build();
    doReturn(ExecutionMetadata.newBuilder().build())
        .when(planExecutionService)
        .getExecutionMetadataFromPlanExecution(planExecutionUuid1);
    assertThat(nodeExecutionService.getAmbiance(nodeExecution2)).isEqualTo(ambiance1);
    verify(planExecutionService, times(1)).getExecutionMetadataFromPlanExecution(planExecutionUuid1);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testUpdateV2ForNonFinalStatusNodeExecution() {
    // setting up arguments
    String nodeExecutionId = generateUuid();
    ProgressData dummyProgressDoc =
        UnitProgressData.builder()
            .unitProgresses(
                List.of(UnitProgress.newBuilder().setStatus(UnitStatus.SUCCESS).setUnitName("testing").build()))
            .build();
    Consumer<Update> dummyOps = ops -> setUnset(ops, NodeExecutionKeys.progressData, dummyProgressDoc);

    // setting up mocks
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    doReturn(NodeExecution.builder().build())
        .when(mongoTemplateMock)
        .findAndModify(any(), any(), any(), eq(NodeExecution.class));
    doNothing().when(orchestrationLogPublisher).onNodeUpdate(any());
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<UpdateDefinition> updateArgumentCaptor = ArgumentCaptor.forClass(UpdateDefinition.class);

    // expected values
    Criteria criteria = where(NodeExecutionKeys.uuid)
                            .is(nodeExecutionId)
                            .and(NodeExecutionKeys.status)
                            .nin(StatusUtils.finalStatuses());
    Query expectedQuery = new Query(criteria);
    var projectionFields = Sets.newHashSet(NodeExecutionKeys.uuid);
    projectionFields.addAll(NodeProjectionUtils.fieldsForNodeUpdateObserver);
    for (String field : projectionFields) {
      expectedQuery.fields().include(field);
    }

    nodeExecutionService.updateV2ForNonFinalStatusNodeExecution(nodeExecutionId, dummyOps);
    verify(mongoTemplateMock, times(1))
        .findAndModify(queryArgumentCaptor.capture(), updateArgumentCaptor.capture(), any(), eq(NodeExecution.class));
    var recievedQuery = queryArgumentCaptor.getValue();
    assertThat(recievedQuery.getQueryObject()).isEqualTo(expectedQuery.getQueryObject());
    assertThat(recievedQuery.getFieldsObject()).isEqualTo(expectedQuery.getFieldsObject());

    // Validate that the projection includes critical fields required by updateCalculatedStatusForParentNodes
    Document fieldsObject = recievedQuery.getFieldsObject();
    assertThat(fieldsObject).containsEntry(NodeExecutionKeys.stepType, 1);
    assertThat(fieldsObject).containsEntry(NodeExecutionKeys.mode, 1);
    assertThat(fieldsObject).containsEntry(NodeExecutionKeys.uuid, 1);
    assertThat(fieldsObject).containsEntry(NodeExecutionKeys.advisorsProcessed, 1);

    var recievedUpdateOps = updateArgumentCaptor.getValue();
    assertThat(recievedUpdateOps.getUpdateObject()).containsOnlyKeys("$set");
    assertThat(recievedUpdateOps.getUpdateObject().get("$set", Document.class))
        .containsEntry("progressData", dummyProgressDoc)
        .containsKey(NodeExecutionKeys.lastUpdatedAt);
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testUpdateUnitProgressesIfNewerAppliesUpdateAndPublishesLogWhenTimestampIsNewer() {
    String nodeExecutionId = generateUuid();
    List<UnitProgress> unitProgresses =
        List.of(UnitProgress.newBuilder().setStatus(UnitStatus.SUCCESS).setUnitName("testing").build());
    long timestamp = 123L;

    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    doReturn(NodeExecution.builder().build())
        .when(mongoTemplateMock)
        .findAndModify(any(), any(), any(), eq(NodeExecution.class));
    doNothing().when(orchestrationLogPublisher).onNodeUpdate(any());
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<UpdateDefinition> updateArgumentCaptor = ArgumentCaptor.forClass(UpdateDefinition.class);

    Criteria expectedCriteria =
        where(NodeExecutionKeys.uuid)
            .is(nodeExecutionId)
            .and(NodeExecutionKeys.status)
            .nin(StatusUtils.finalStatuses())
            .andOperator(new Criteria().orOperator(where(NodeExecutionKeys.unitProgressesTimestamp).exists(false),
                where(NodeExecutionKeys.unitProgressesTimestamp).lte(timestamp)));
    Query expectedQuery = new Query(expectedCriteria);

    nodeExecutionService.updateUnitProgressesIfNewer(nodeExecutionId, unitProgresses, timestamp);

    verify(mongoTemplateMock, times(1))
        .findAndModify(queryArgumentCaptor.capture(), updateArgumentCaptor.capture(), any(), eq(NodeExecution.class));
    assertThat(queryArgumentCaptor.getValue().getQueryObject()).isEqualTo(expectedQuery.getQueryObject());

    Document setDoc = updateArgumentCaptor.getValue().getUpdateObject().get("$set", Document.class);
    assertThat(setDoc)
        .containsEntry(NodeExecutionKeys.unitProgresses, unitProgresses)
        .containsEntry("progressData.unitProgresses", unitProgresses)
        .containsEntry(NodeExecutionKeys.unitProgressesTimestamp, timestamp);
    verify(orchestrationLogPublisher, times(1)).onNodeUpdate(any());
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testUpdateUnitProgressesIfNewerIsNoOpWhenAStaleUpdateLosesTheRace() {
    String nodeExecutionId = generateUuid();
    List<UnitProgress> unitProgresses =
        List.of(UnitProgress.newBuilder().setStatus(UnitStatus.SUCCESS).setUnitName("testing").build());

    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    doReturn(null).when(mongoTemplateMock).findAndModify(any(), any(), any(), eq(NodeExecution.class));

    nodeExecutionService.updateUnitProgressesIfNewer(nodeExecutionId, unitProgresses, 123L);

    verify(orchestrationLogPublisher, times(0)).onNodeUpdate(any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testShouldUpdateCacheReturnsTrueWhenAdvisorsProcessedIsTrue() {
    Update updateOps = new Update();
    updateOps.set(NodeExecutionKeys.advisorsProcessed, true);

    boolean result = nodeExecutionService.shouldUpdateCache(updateOps);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testShouldUpdateCacheReturnsFalseWhenAdvisorsProcessedIsFalse() {
    Update updateOps = new Update();
    updateOps.set(NodeExecutionKeys.advisorsProcessed, false);

    boolean result = nodeExecutionService.shouldUpdateCache(updateOps);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testShouldUpdateCacheReturnsFalseWhenAdvisorsProcessedNotSet() {
    Update updateOps = new Update();
    updateOps.set(NodeExecutionKeys.status, Status.RUNNING);

    boolean result = nodeExecutionService.shouldUpdateCache(updateOps);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testShouldUpdateCacheReturnsFalseWhenNoSetOperator() {
    Update updateOps = new Update();
    updateOps.inc(NodeExecutionKeys.version, 1);

    boolean result = nodeExecutionService.shouldUpdateCache(updateOps);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateTriggersCacheWhenAdvisorsProcessedIsTrue() {
    String nodeExecutionId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).status(Status.FAILED).advisorsProcessed(true).build();

    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    doReturn(nodeExecution).when(mongoTemplateMock).findAndModify(any(), any(), any(), eq(NodeExecution.class));

    NodeExecutionInfoService nodeExecutionInfoServiceMock = Mockito.mock(NodeExecutionInfoService.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionInfoService", nodeExecutionInfoServiceMock);
    doNothing().when(nodeExecutionInfoServiceMock).updateCalculatedStatusForParentNodes(any());

    NodeExecution result = nodeExecutionService.update(nodeExecutionId, ops -> {
      ops.set(NodeExecutionKeys.advisorsProcessed, true);
      ops.set(NodeExecutionKeys.status, Status.FAILED);
    });

    assertThat(result).isNotNull();
    assertThat(result.getUuid()).isEqualTo(nodeExecutionId);
    verify(nodeExecutionInfoServiceMock, times(1)).updateCalculatedStatusForParentNodes(nodeExecution);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateDoesNotTriggerCacheWhenAdvisorsProcessedNotSet() {
    String nodeExecutionId = generateUuid();
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).status(Status.RUNNING).build();

    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    doReturn(nodeExecution).when(mongoTemplateMock).findAndModify(any(), any(), any(), eq(NodeExecution.class));

    NodeExecutionInfoService nodeExecutionInfoServiceMock = Mockito.mock(NodeExecutionInfoService.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionInfoService", nodeExecutionInfoServiceMock);

    NodeExecution result =
        nodeExecutionService.update(nodeExecutionId, ops -> { ops.set(NodeExecutionKeys.status, Status.RUNNING); });

    assertThat(result).isNotNull();
    assertThat(result.getUuid()).isEqualTo(nodeExecutionId);
    verify(nodeExecutionInfoServiceMock, times(0)).updateCalculatedStatusForParentNodes(any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateDoesNotTriggerCacheWhenAdvisorsProcessedIsFalse() {
    String nodeExecutionId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).status(Status.RUNNING).advisorsProcessed(false).build();

    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    doReturn(nodeExecution).when(mongoTemplateMock).findAndModify(any(), any(), any(), eq(NodeExecution.class));

    NodeExecutionInfoService nodeExecutionInfoServiceMock = Mockito.mock(NodeExecutionInfoService.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionInfoService", nodeExecutionInfoServiceMock);

    NodeExecution result =
        nodeExecutionService.update(nodeExecutionId, ops -> { ops.set(NodeExecutionKeys.advisorsProcessed, false); });

    assertThat(result).isNotNull();
    verify(nodeExecutionInfoServiceMock, times(0)).updateCalculatedStatusForParentNodes(any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateTriggersCacheWithMultipleFields() {
    String nodeExecutionId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).status(Status.FAILED).advisorsProcessed(true).build();

    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    doReturn(nodeExecution).when(mongoTemplateMock).findAndModify(any(), any(), any(), eq(NodeExecution.class));

    NodeExecutionInfoService nodeExecutionInfoServiceMock = Mockito.mock(NodeExecutionInfoService.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionInfoService", nodeExecutionInfoServiceMock);
    doNothing().when(nodeExecutionInfoServiceMock).updateCalculatedStatusForParentNodes(any());

    NodeExecution result = nodeExecutionService.update(nodeExecutionId, ops -> {
      ops.set(NodeExecutionKeys.advisorsProcessed, true);
      ops.set(NodeExecutionKeys.status, Status.FAILED);
      ops.set(NodeExecutionKeys.processingEvent, false);
    });

    assertThat(result).isNotNull();
    verify(nodeExecutionInfoServiceMock, times(1)).updateCalculatedStatusForParentNodes(nodeExecution);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateV2TriggersCacheWhenAdvisorsProcessedIsTrue() {
    String nodeExecutionId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).status(Status.FAILED).advisorsProcessed(true).build();

    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    doReturn(nodeExecution).when(mongoTemplateMock).findAndModify(any(), any(), any(), eq(NodeExecution.class));

    NodeExecutionInfoService nodeExecutionInfoServiceMock = Mockito.mock(NodeExecutionInfoService.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionInfoService", nodeExecutionInfoServiceMock);
    doNothing().when(nodeExecutionInfoServiceMock).updateCalculatedStatusForParentNodes(any());

    nodeExecutionService.updateV2(nodeExecutionId, ops -> { ops.set(NodeExecutionKeys.advisorsProcessed, true); });

    verify(nodeExecutionInfoServiceMock, times(1)).updateCalculatedStatusForParentNodes(nodeExecution);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateWithNullNodeExecutionDoesNotCrash() {
    String nodeExecutionId = generateUuid();

    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    doReturn(null).when(mongoTemplateMock).findAndModify(any(), any(), any(), eq(NodeExecution.class));

    NodeExecutionInfoService nodeExecutionInfoServiceMock = Mockito.mock(NodeExecutionInfoService.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionInfoService", nodeExecutionInfoServiceMock);

    assertThatThrownBy(() -> nodeExecutionService.update(nodeExecutionId, ops -> {
      ops.set(NodeExecutionKeys.advisorsProcessed, true);
    })).isInstanceOf(NodeExecutionUpdateFailedException.class);

    verify(nodeExecutionInfoServiceMock, times(0)).updateCalculatedStatusForParentNodes(any());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetResolveStepInputs() {
    Map<String, Object> stepInputs = new LinkedHashMap<>();
    stepInputs.put("__recast", "a.b.c");
    stepInputs.put("a", "b");
    Map<String, Object> nestedMap = new LinkedHashMap<>();
    nestedMap.put("d", "e");
    nestedMap.put("g", "h");
    stepInputs.put("c", nestedMap);

    PmsStepParameters originalStepParams = PmsStepParameters.parse(stepInputs);

    PmsStepParameters resolvedStepInputs =
        nodeExecutionService.getResolvedStepInputs(new LinkedList<>(Arrays.asList("a", "c.d")), originalStepParams);
    assertThat(resolvedStepInputs).isNotNull();
    assertThat(resolvedStepInputs.size()).isEqualTo(2);
    // Check originalStepParams are not changed
    assertThat(originalStepParams).isNotNull();
    assertThat(originalStepParams.size()).isEqualTo(3);
    assertThat(((Map) originalStepParams.get("c")).containsKey("d")).isTrue();
  }
}
