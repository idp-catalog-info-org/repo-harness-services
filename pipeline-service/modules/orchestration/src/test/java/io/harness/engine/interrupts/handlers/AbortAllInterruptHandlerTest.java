/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.ABORTED;
import static io.harness.pms.contracts.execution.Status.DISCONTINUING;
import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationTestHelper;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.State;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class AbortAllInterruptHandlerTest extends OrchestrationTestBase {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanExecutionService planExecutionService;
  @Inject @InjectMocks private AbortAllInterruptHandler abortAllInterruptHandler;
  @Inject private MongoTemplate mongoTemplate;

  private List<NodeExecution> emptyList = new ArrayList<>();

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldNotProcessInterruptForFinalStatus() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    String accountId = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();
    var planExecution = PlanExecution.builder()
                            .ambiance(Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build())
                            .build();

    when(planExecutionService.getStatus(planExecutionId)).thenReturn(RUNNING, FAILED);
    when(planExecutionService.get(eq(planExecutionId))).thenReturn(planExecution);
    when(nodeExecutionService.markAllFinalizableNodesDiscontinuing(planExecutionId)).thenReturn(0L);

    Interrupt handledInterrupt = abortAllInterruptHandler.registerInterrupt(interrupt);

    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSED_UNSUCCESSFULLY);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldHandleInterruptNoDiscontinuingNodes() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    String accountId = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();
    var planExecution = PlanExecution.builder()
                            .ambiance(Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build())
                            .build();

    when(planExecutionService.get(eq(planExecutionId))).thenReturn(planExecution);
    when(nodeExecutionService.markAllFinalizableNodesDiscontinuing(planExecutionId)).thenReturn(0L);

    Interrupt handledInterrupt = abortAllInterruptHandler.registerInterrupt(interrupt);

    verify(planExecutionService).updateStatus(planExecutionId, ABORTED);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSED_SUCCESSFULLY);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldHandleInterruptNoLeavesAndAbort() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    String accountId = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();
    var planExecution = PlanExecution.builder()
                            .ambiance(Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build())
                            .build();

    when(planExecutionService.get(eq(planExecutionId))).thenReturn(planExecution);
    when(nodeExecutionService.markAllFinalizableNodesDiscontinuing(planExecutionId)).thenReturn(1L);
    List<NodeExecution> emptyList = new ArrayList<>();
    Stream<NodeExecution> stream = OrchestrationTestHelper.createCloseableIterator(emptyList.iterator()).stream();
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             planExecutionId, EnumSet.of(DISCONTINUING), NodeProjectionUtils.fieldsForDiscontinuingNodes))
        .thenReturn(stream);
    Interrupt handledInterrupt = abortAllInterruptHandler.registerInterrupt(interrupt);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSED_SUCCESSFULLY);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestHandleInterruptNoLeaves() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();

    mongoTemplate.save(interrupt);
    when(nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(
             planExecutionId, StatusUtils.finalizableStatuses()))
        .thenReturn(0L);
    when(planExecutionService.getStatus(planExecutionId)).thenReturn(RUNNING);
    Interrupt handledInterrupt = abortAllInterruptHandler.handleInterrupt(interrupt);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSED_SUCCESSFULLY);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestHandleInterruptError() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();

    mongoTemplate.save(interrupt);
    when(nodeExecutionService.markAllFinalizableNodesDiscontinuing(planExecutionId)).thenReturn(-1L);
    Interrupt handledInterrupt = abortAllInterruptHandler.handleInterrupt(interrupt);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSED_UNSUCCESSFULLY);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterrupt() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();

    when(planExecutionService.get(planExecutionId)).thenReturn(PlanExecution.builder().status(RUNNING).build());
    when(nodeExecutionService.markAllFinalizableNodesDiscontinuing(planExecutionId)).thenReturn(0L);
    when(planExecutionService.getStatus(planExecutionId)).thenReturn(Status.RUNNING);
    Interrupt handledInterrupt = abortAllInterruptHandler.registerInterrupt(interrupt);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSED_SUCCESSFULLY);

    // Interrupt with node execution id
    planExecutionId = generateUuid();
    interruptUuid = generateUuid();
    Interrupt interruptWithNodeExecutionId = Interrupt.builder()
                                                 .uuid(interruptUuid)
                                                 .nodeExecutionId("nodeExecutionId")
                                                 .type(InterruptType.ABORT_ALL)
                                                 .interruptConfig(InterruptConfig.newBuilder().build())
                                                 .planExecutionId(planExecutionId)
                                                 .state(State.REGISTERED)
                                                 .build();

    when(nodeExecutionService.markAllFinalizableNodesDiscontinuing(planExecutionId)).thenReturn(1L);
    when(nodeExecutionService.getWithFieldsIncluded(anyString(), any())).thenReturn(NodeExecution.builder().build());
    Stream<NodeExecution> iterator = OrchestrationTestHelper.createCloseableIterator(emptyList.iterator()).stream();
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             interruptWithNodeExecutionId.getPlanExecutionId(), StatusUtils.abortAndExpireStatuses(),
             NodeProjectionUtils.fieldsForInterruptPropagatorHandler))
        .thenReturn(iterator);
    handledInterrupt = abortAllInterruptHandler.registerInterrupt(interruptWithNodeExecutionId);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSED_SUCCESSFULLY);

    planExecutionId = generateUuid();
    interruptUuid = generateUuid();
    Interrupt interruptNode = Interrupt.builder()
                                  .uuid(interruptUuid)
                                  .nodeExecutionId("nodeExecutionId")
                                  .type(InterruptType.ABORT_ALL)
                                  .interruptConfig(InterruptConfig.newBuilder().build())
                                  .planExecutionId(planExecutionId)
                                  .state(State.PROCESSING)
                                  .build();
    when(planExecutionService.getStatus(planExecutionId)).thenReturn(Status.QUEUED);
    handledInterrupt = abortAllInterruptHandler.registerInterrupt(interruptNode);
    verify(planExecutionService, times(1)).updateStatus(planExecutionId, Status.ABORTED);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSING);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterruptWhenAlreadyPresent() {
    String planExecutionId = generateUuid();
    // Interrupt with node execution id
    Interrupt interruptWithNodeExecutionId = Interrupt.builder()
                                                 .uuid(generateUuid())
                                                 .nodeExecutionId("nodeExecutionId")
                                                 .type(InterruptType.ABORT_ALL)
                                                 .interruptConfig(InterruptConfig.newBuilder().build())
                                                 .planExecutionId(planExecutionId)
                                                 .state(State.REGISTERED)
                                                 .build();

    mongoTemplate.save(interruptWithNodeExecutionId);
    when(planExecutionService.getStatus(planExecutionId)).thenReturn(Status.RUNNING);

    // Another interrupt on nodeExecution. But nodeExecutionId is different so it will be saved.
    Interrupt interruptWithDiffNodeExecutionId = Interrupt.builder()
                                                     .uuid(generateUuid())
                                                     .nodeExecutionId("nodeExecutionId2")
                                                     .type(InterruptType.ABORT_ALL)
                                                     .interruptConfig(InterruptConfig.newBuilder().build())
                                                     .planExecutionId(planExecutionId)
                                                     .state(State.REGISTERED)
                                                     .build();
    when(nodeExecutionService.markAllFinalizableNodesDiscontinuing(planExecutionId)).thenReturn(1L);
    List<NodeExecution> emptyList = new ArrayList<>();
    Stream<NodeExecution> stream = OrchestrationTestHelper.createCloseableIterator(emptyList.iterator()).stream();
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             interruptWithDiffNodeExecutionId.getPlanExecutionId(), StatusUtils.abortAndExpireStatuses(),
             NodeProjectionUtils.fieldsForInterruptPropagatorHandler))
        .thenReturn(stream);
    when(nodeExecutionService.getWithFieldsIncluded(anyString(), any())).thenReturn(NodeExecution.builder().build());

    var handledInterrupt = abortAllInterruptHandler.registerInterrupt(interruptWithDiffNodeExecutionId);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptWithDiffNodeExecutionId.getUuid());
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSED_SUCCESSFULLY);

    // Another interrupt on nodeExecution with same nodeExecutionId. So it will not be saved.
    Interrupt interruptWithSameNodeExecutionId = Interrupt.builder()
                                                     .uuid(generateUuid())
                                                     .nodeExecutionId("nodeExecutionId")
                                                     .type(InterruptType.ABORT_ALL)
                                                     .interruptConfig(InterruptConfig.newBuilder().build())
                                                     .planExecutionId(planExecutionId)
                                                     .state(State.REGISTERED)
                                                     .build();

    // The previous stream would have been closed, so return a new one
    Stream<NodeExecution> iterator2 = OrchestrationTestHelper.createCloseableIterator(emptyList.iterator()).stream();
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             interruptWithNodeExecutionId.getPlanExecutionId(), StatusUtils.abortAndExpireStatuses(),
             NodeProjectionUtils.fieldsForInterruptPropagatorHandler))
        .thenReturn(iterator2);

    assertThatThrownBy(() -> abortAllInterruptHandler.registerInterrupt(interruptWithSameNodeExecutionId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Execution already has ABORT_ALL interrupt");

    // Plan level interrupts.
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();

    mongoTemplate.save(interrupt);

    Interrupt interrupt2 = Interrupt.builder()
                               .uuid(generateUuid())
                               .type(InterruptType.ABORT_ALL)
                               .interruptConfig(InterruptConfig.newBuilder().build())
                               .planExecutionId(planExecutionId)
                               .state(State.REGISTERED)
                               .build();
    assertThatThrownBy(() -> abortAllInterruptHandler.registerInterrupt(interrupt2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Execution already has ABORT_ALL interrupt");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testHandleAllNodes() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();

    mongoTemplate.save(interrupt);
    when(planExecutionService.getStatus(planExecutionId)).thenReturn(Status.RUNNING);

    // case1: updatedCount = 0
    when(nodeExecutionService.markAllFinalizableNodesDiscontinuing(planExecutionId)).thenReturn(0L);
    Interrupt handledInterrupt = abortAllInterruptHandler.handleAllNodes(interrupt);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSED_SUCCESSFULLY);

    // case2: updatedCount < 0
    when(nodeExecutionService.markAllFinalizableNodesDiscontinuing(planExecutionId)).thenReturn(-1L);
    handledInterrupt = abortAllInterruptHandler.handleAllNodes(interrupt);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSED_UNSUCCESSFULLY);

    // case3: updatedCount > 0
    when(nodeExecutionService.markAllFinalizableNodesDiscontinuing(planExecutionId)).thenReturn(1L);
    List<NodeExecution> emptyList = new ArrayList<>();
    Stream<NodeExecution> stream = OrchestrationTestHelper.createCloseableIterator(emptyList.iterator()).stream();
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             planExecutionId, EnumSet.of(DISCONTINUING), NodeProjectionUtils.fieldsForDiscontinuingNodes))
        .thenReturn(stream);
    handledInterrupt = abortAllInterruptHandler.handleAllNodes(interrupt);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSED_SUCCESSFULLY);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testHandleChildNodesForAbortAll() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interruptWithNodeExecutionId = Interrupt.builder()
                                                 .uuid(interruptUuid)
                                                 .nodeExecutionId("nodeExecutionId")
                                                 .type(InterruptType.ABORT_ALL)
                                                 .interruptConfig(InterruptConfig.newBuilder().build())
                                                 .planExecutionId(planExecutionId)
                                                 .state(State.REGISTERED)
                                                 .build();

    mongoTemplate.save(interruptWithNodeExecutionId);
    NodeExecution stageNodeExecution =
        NodeExecution.builder().uuid("stageNodeExecutionId").group("STAGE").identifier("Stage").build();
    NodeExecution runningNodeExecution =
        NodeExecution.builder()
            .uuid("runningNodeExecution")
            .parentId(stageNodeExecution.getUuid())
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
            .status(RUNNING)
            .mode(ExecutionMode.CHILD)
            .build();
    List<NodeExecution> allNodeExecutionList = List.of(stageNodeExecution, runningNodeExecution);
    Stream<NodeExecution> allNodeExecutionStream =
        OrchestrationTestHelper.createCloseableIterator(allNodeExecutionList.iterator()).stream();
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             anyString(), eq(StatusUtils.abortAndExpireStatuses()), any()))
        .thenReturn(allNodeExecutionStream);

    // stage node execution
    when(nodeExecutionService.getWithFieldsIncluded(anyString(), any())).thenReturn(stageNodeExecution);
    when(nodeExecutionService.extractChildExecutions(
             interruptWithNodeExecutionId.getNodeExecutionId(), true, new LinkedList<>(), allNodeExecutionList, true))
        .thenAnswer(invocationOnMock -> {
          List<NodeExecution> nodeExecutionList = invocationOnMock.getArgument(2);
          nodeExecutionList.addAll(allNodeExecutionList);
          return nodeExecutionList;
        });

    abortAllInterruptHandler.handleChildNodes(
        interruptWithNodeExecutionId, interruptWithNodeExecutionId.getNodeExecutionId());

    ArgumentCaptor<List<String>> argumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(nodeExecutionService, times(1)).markLeavesDiscontinuing(argumentCaptor.capture());

    // verify all the nodeExecutionIds related to stage will be marked discontinued.
    assertThat(argumentCaptor.getValue().size()).isEqualTo(2);
  }
}
