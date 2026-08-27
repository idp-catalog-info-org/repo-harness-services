/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.pms.contracts.execution.Status.DISCONTINUING;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SHIVAM;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationTestHelper;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.State;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExpireAllInterruptHandlerTest extends OrchestrationTestBase {
  @Mock private PlanExecutionService planExecutionService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private InterruptService interruptService;

  @Inject @InjectMocks private ExpireAllInterruptHandler expireAllInterruptHandler;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testRegisterInterruptAbortAllPresent() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(Interrupt.State.REGISTERED)
                              .build();

    var planExecution = PlanExecution.builder().ambiance(Ambiance.newBuilder().build()).build();
    doReturn(planExecution)
        .when(planExecutionService)
        .getWithFieldsIncluded(planExecutionId, Collections.singleton(PlanExecutionKeys.ambiance));
    doReturn(List.of(interrupt))
        .when(interruptService)
        .fetchActivePlanLevelInterrupts(List.of(planExecutionId), Ambiance.newBuilder().build());

    assertThatThrownBy(
        ()
            -> expireAllInterruptHandler.registerInterrupt(Interrupt.builder()
                                                               .uuid(generateUuid())
                                                               .type(InterruptType.EXPIRE_ALL)
                                                               .interruptConfig(InterruptConfig.newBuilder().build())
                                                               .planExecutionId(planExecutionId)
                                                               .state(Interrupt.State.REGISTERED)
                                                               .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Execution already has ABORT_ALL interrupt");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterruptExpireAllPresent() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.EXPIRE_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(Interrupt.State.REGISTERED)
                              .build();

    var planExecution = PlanExecution.builder().ambiance(Ambiance.newBuilder().build()).build();
    doReturn(planExecution)
        .when(planExecutionService)
        .getWithFieldsIncluded(planExecutionId, Collections.singleton(PlanExecutionKeys.ambiance));
    doReturn(List.of(interrupt))
        .when(interruptService)
        .fetchActivePlanLevelInterrupts(List.of(planExecutionId), Ambiance.newBuilder().build());
    when(planExecutionService.getStatus(planExecutionId)).thenReturn(Status.RUNNING);
    assertThatThrownBy(
        ()
            -> expireAllInterruptHandler.registerInterrupt(Interrupt.builder()
                                                               .uuid(generateUuid())
                                                               .type(InterruptType.EXPIRE_ALL)
                                                               .interruptConfig(InterruptConfig.newBuilder().build())
                                                               .planExecutionId(planExecutionId)
                                                               .state(Interrupt.State.REGISTERED)
                                                               .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Execution already has EXPIRE_ALL interrupt");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterruptExpireAllPresentForNode() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    String nodeExecutionId = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.EXPIRE_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .nodeExecutionId(nodeExecutionId)
                              .state(Interrupt.State.REGISTERED)
                              .build();

    var planExecution = PlanExecution.builder().ambiance(Ambiance.newBuilder().build()).build();
    doReturn(planExecution)
        .when(planExecutionService)
        .getWithFieldsIncluded(planExecutionId, Collections.singleton(PlanExecutionKeys.ambiance));
    doReturn(List.of(interrupt))
        .when(interruptService)
        .fetchActivePlanLevelInterrupts(List.of(planExecutionId), Ambiance.newBuilder().build());
    when(planExecutionService.getStatus(planExecutionId)).thenReturn(Status.RUNNING);
    assertThatThrownBy(
        ()
            -> expireAllInterruptHandler.registerInterrupt(Interrupt.builder()
                                                               .uuid(generateUuid())
                                                               .type(InterruptType.EXPIRE_ALL)
                                                               .interruptConfig(InterruptConfig.newBuilder().build())
                                                               .planExecutionId(planExecutionId)
                                                               .nodeExecutionId(nodeExecutionId)
                                                               .state(Interrupt.State.REGISTERED)
                                                               .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Execution already has EXPIRE_ALL interrupt for node");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterruptPlanEnded() {
    String planExecutionId = generateUuid();
    var planExecution = PlanExecution.builder().ambiance(Ambiance.newBuilder().build()).build();
    doReturn(planExecution)
        .when(planExecutionService)
        .getWithFieldsIncluded(planExecutionId, Collections.singleton(PlanExecutionKeys.ambiance));
    when(planExecutionService.getStatus(planExecutionId)).thenReturn(Status.ABORTED);
    when(nodeExecutionService.getPipelineNodeExecutionWithProjections(planExecutionId, NodeProjectionUtils.withStatus))
        .thenReturn(Optional.of(NodeExecution.builder().status(Status.FAILED).build()));
    assertThatThrownBy(
        ()
            -> expireAllInterruptHandler.registerInterrupt(Interrupt.builder()
                                                               .uuid(generateUuid())
                                                               .type(InterruptType.EXPIRE_ALL)
                                                               .interruptConfig(InterruptConfig.newBuilder().build())
                                                               .planExecutionId(planExecutionId)
                                                               .state(Interrupt.State.REGISTERED)
                                                               .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Plan Execution is already finished");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterruptSuccessful() {
    String planExecutionId = generateUuid();
    String interruptId = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptId)
                              .type(InterruptType.EXPIRE_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(Interrupt.State.REGISTERED)
                              .build();
    var planExecution = PlanExecution.builder().ambiance(Ambiance.newBuilder().build()).build();
    doReturn(planExecution)
        .when(planExecutionService)
        .getWithFieldsIncluded(planExecutionId, Collections.singleton(PlanExecutionKeys.ambiance));
    doReturn(interrupt).when(interruptService).save(any());
    when(planExecutionService.getStatus(planExecutionId)).thenReturn(Status.RUNNING);
    assertThatThrownBy(
        ()
            -> expireAllInterruptHandler.registerInterrupt(Interrupt.builder()
                                                               .uuid(interruptId)
                                                               .type(InterruptType.EXPIRE_ALL)
                                                               .interruptConfig(InterruptConfig.newBuilder().build())
                                                               .planExecutionId(planExecutionId)
                                                               .state(Interrupt.State.REGISTERED)
                                                               .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            String.format("NodeExecution not found for pipeline node for planExecutionId %s and interruptId %s",
                planExecutionId, interruptId));

    when(nodeExecutionService.getPipelineNodeExecutionWithProjections(planExecutionId, NodeProjectionUtils.withStatus))
        .thenReturn(Optional.of(NodeExecution.builder().status(Status.RUNNING).build()));
    interrupt = expireAllInterruptHandler.registerInterrupt(Interrupt.builder()
                                                                .uuid(interruptId)
                                                                .type(InterruptType.EXPIRE_ALL)
                                                                .interruptConfig(InterruptConfig.newBuilder().build())
                                                                .planExecutionId(planExecutionId)
                                                                .state(Interrupt.State.REGISTERED)
                                                                .build());

    assertThat(interrupt).isNotNull();
    assertThat(interrupt.getUuid()).isEqualTo(interruptId);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testHandleChildNodesForExpireAll() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interruptWithNodeExecutionId = Interrupt.builder()
                                                 .uuid(interruptUuid)
                                                 .nodeExecutionId("nodeExecutionId")
                                                 .type(InterruptType.EXPIRE_ALL)
                                                 .interruptConfig(InterruptConfig.newBuilder().build())
                                                 .planExecutionId(planExecutionId)
                                                 .state(State.REGISTERED)
                                                 .build();
    doReturn(interruptWithNodeExecutionId).when(interruptService).save(any());

    doReturn(List.of(interruptWithNodeExecutionId))
        .when(interruptService)
        .fetchActivePlanLevelInterrupts(List.of(planExecutionId), null);
    doReturn(interruptWithNodeExecutionId)
        .when(interruptService)
        .markProcessing(interruptWithNodeExecutionId.getUuid());

    String nodeExecution1Id = generateUuid();
    Ambiance.Builder ambianceBuilder = Ambiance.newBuilder().setPlanExecutionId(planExecutionId);
    PlanNode planNode1 = preparePlanNode(false, "pipeline", "pipelineValue", "PIPELINE");
    NodeExecution runningNodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecution1Id)
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1Id, planNode1)).build())
            .status(RUNNING)
            .build();
    List<NodeExecution> runningNodeExecutionList = asList(runningNodeExecution);
    List<NodeExecution> discontinuedNodeExecutionList = new ArrayList<>();

    when(nodeExecutionService.markLeavesDiscontinuing(any())).thenReturn(1L);

    Stream<NodeExecution> runningNodeExecutionStream =
        OrchestrationTestHelper.createCloseableIterator(runningNodeExecutionList.iterator()).stream();
    Stream<NodeExecution> discontinuedNodeExecutionStream =
        OrchestrationTestHelper.createCloseableIterator(discontinuedNodeExecutionList.iterator()).stream();

    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             anyString(), eq(StatusUtils.abortAndExpireStatuses()), any()))
        .thenReturn(runningNodeExecutionStream);
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             anyString(), eq(EnumSet.of(DISCONTINUING)), any()))
        .thenReturn(discontinuedNodeExecutionStream);
    when(nodeExecutionService.getWithFieldsIncluded(anyString(), any()))
        .thenReturn(NodeExecution.builder().group("STAGE").identifier("Stage").build());

    List<NodeExecution> extractedChildExecutions = new LinkedList<>();
    extractedChildExecutions.add(NodeExecution.builder().uuid("childUuid").status(RUNNING).build());
    when(nodeExecutionService.extractChildExecutions(
             interruptWithNodeExecutionId.getNodeExecutionId(), true, new LinkedList<>(), new LinkedList<>(), true))
        .thenReturn(extractedChildExecutions);
    interruptWithNodeExecutionId.setState(PROCESSED_SUCCESSFULLY);
    doReturn(interruptWithNodeExecutionId).when(interruptService).markProcessed(eq(interruptUuid), any());

    Interrupt handledInterrupt = expireAllInterruptHandler.handleChildNodes(
        interruptWithNodeExecutionId, interruptWithNodeExecutionId.getNodeExecutionId());
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(PROCESSED_SUCCESSFULLY);
  }

  private PlanNode preparePlanNode(
      boolean skipExpressionChain, String identifier, String paramValue, String groupName) {
    return PlanNode.builder()
        .uuid(generateUuid())
        .name(identifier)
        .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
        .identifier(identifier)
        .skipExpressionChain(skipExpressionChain)
        .group(groupName)
        .build();
  }
}
