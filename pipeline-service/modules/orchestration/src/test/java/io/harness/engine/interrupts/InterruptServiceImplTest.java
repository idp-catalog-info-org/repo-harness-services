/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts;

import static io.harness.beans.FeatureName.PIPE_FIX_ABORT_RACE_CONDITIONS;
import static io.harness.beans.FeatureName.PIPE_FIX_USER_MARKED_FAIL_ALL_PRE_NODE_START_CHECK;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.interrupts.Interrupt.State.DISCARDED;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSING;
import static io.harness.interrupts.Interrupt.State.REGISTERED;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executioncheck.ExecutionCheck;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.handlers.AbortInterruptHandler;
import io.harness.engine.interrupts.handlers.MarkExpiredInterruptHandler;
import io.harness.engine.interrupts.handlers.PauseAllInterruptHandler;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.ff.FeatureFlagService;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class InterruptServiceImplTest extends OrchestrationTestBase {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private AbortInterruptHandler abortInterruptHandler;
  @Mock private MarkExpiredInterruptHandler markExpiredInterruptHandler;
  @Mock private PauseAllInterruptHandler pauseAllInterruptHandler;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject @InjectMocks private InterruptService interruptService;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestSave() {
    String planExecutionId = generateUuid();
    Interrupt interrupt = Interrupt.builder().planExecutionId(planExecutionId).type(InterruptType.ABORT_ALL).build();

    Interrupt savedInterrupt = interruptService.save(interrupt);
    assertThat(savedInterrupt).isNotNull();
    assertThat(savedInterrupt.getUuid()).isNotNull();
    assertThat(savedInterrupt.getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(savedInterrupt.getType()).isEqualTo(InterruptType.ABORT_ALL);
    assertThat(savedInterrupt.getState()).isEqualTo(REGISTERED);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void fetchActivePlanLevelInterrupts() {
    String planExecutionId = generateUuid();
    saveInterruptList(planExecutionId, false);
    var ambiance = Ambiance.newBuilder().build();

    List<Interrupt> planLevelInterrupts =
        interruptService.fetchActivePlanLevelInterrupts(Collections.singletonList(planExecutionId), ambiance);
    assertThat(planLevelInterrupts).isNotEmpty();
    assertThat(planLevelInterrupts).hasSize(2);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void fetchActivePlanLevelInterrupts_withFFOn() {
    String planExecutionId = generateUuid();
    when(planExecutionService.getWithFieldsIncluded(planExecutionId, Set.of(PlanExecutionKeys.accountId)))
        .thenReturn(PlanExecution.builder()
                        .ambiance(Ambiance.newBuilder().putSetupAbstractions("accountId", "accId").build())
                        .build());
    when(featureFlagService.isEnabled(PIPE_FIX_ABORT_RACE_CONDITIONS, "accId")).thenReturn(true);
    var abortAllInterrupt = Interrupt.builder()
                                .planExecutionId(planExecutionId)
                                .type(InterruptType.ABORT_ALL)
                                .state(PROCESSED_SUCCESSFULLY)
                                .build();
    var pauseAllInterrupt = Interrupt.builder()
                                .planExecutionId(planExecutionId)
                                .type(InterruptType.PAUSE_ALL)
                                .state(PROCESSED_UNSUCCESSFULLY)
                                .build();
    interruptService.save(abortAllInterrupt);
    interruptService.save(pauseAllInterrupt);
    var ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putFeatureFlagToValueMap(PIPE_FIX_ABORT_RACE_CONDITIONS.toString(), Boolean.TRUE)
                             .build())
            .build();

    List<Interrupt> planLevelInterrupts =
        interruptService.fetchActivePlanLevelInterrupts(Collections.singletonList(planExecutionId), ambiance);
    assertThat(planLevelInterrupts).isNotEmpty();
    assertThat(planLevelInterrupts).hasSize(2);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestMarkProcessed() {
    String planExecutionId = generateUuid();
    Interrupt abortAllInterrupt =
        Interrupt.builder().planExecutionId(planExecutionId).type(InterruptType.ABORT_ALL).build();
    Interrupt savedInterrupt = interruptService.save(abortAllInterrupt);
    Interrupt processed = interruptService.markProcessed(savedInterrupt.getUuid(), DISCARDED);
    assertThat(processed).isNotNull();
    assertThat(processed.getState()).isEqualTo(DISCARDED);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void markProcessing() {
    String planExecutionId = generateUuid();
    Interrupt abortAllInterrupt =
        Interrupt.builder().planExecutionId(planExecutionId).type(InterruptType.ABORT_ALL).build();
    Interrupt savedInterrupt = interruptService.save(abortAllInterrupt);
    Interrupt processing = interruptService.markProcessing(savedInterrupt.getUuid());
    assertThat(processing).isNotNull();
    assertThat(processing.getState()).isEqualTo(PROCESSING);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void fetchAllInterrupts() {
    String planExecutionId = generateUuid();
    saveInterruptList(planExecutionId, false);

    List<Interrupt> interrupts = interruptService.fetchAllInterrupts(planExecutionId);
    assertThat(interrupts).isNotEmpty();
    assertThat(interrupts).hasSize(3);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteAllInterrupts() {
    String planExecutionId = generateUuid();
    saveInterruptList(planExecutionId, false);
    String planExecutionId2 = generateUuid();
    saveInterruptList(planExecutionId2, false);

    List<Interrupt> interruptsForExecution1 = interruptService.fetchAllInterrupts(planExecutionId);
    assertThat(interruptsForExecution1).isNotEmpty();
    assertThat(interruptsForExecution1).hasSize(3);

    List<Interrupt> interruptsForExecution2 = interruptService.fetchAllInterrupts(planExecutionId2);
    assertThat(interruptsForExecution2).isNotEmpty();
    assertThat(interruptsForExecution2).hasSize(3);

    interruptService.deleteAllInterrupts(Set.of(planExecutionId));
    interruptsForExecution1 = interruptService.fetchAllInterrupts(planExecutionId);
    assertThat(interruptsForExecution1).isEmpty();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void fetchActiveInterrupts() {
    String planExecutionId = generateUuid();
    saveInterruptList(planExecutionId, true);
    List<Interrupt> interrupts = interruptService.fetchActiveInterrupts(planExecutionId);
    assertThat(interrupts).isNotEmpty();
    assertThat(interrupts).hasSize(2);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testPreInvocationNoInterrupts() {
    String planExecutionId = generateUuid();
    var ambiance = Ambiance.newBuilder().build();

    ExecutionCheck executionCheck = interruptService.checkInterruptsPreInvocation(
        Collections.singletonList(planExecutionId), generateUuid(), Collections.emptyList(), ambiance);
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isTrue();
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testAbortAllPreInvocationParent_withFFOn() {
    String planExecutionId = generateUuid();
    Interrupt abortAllInterrupt =
        Interrupt.builder().uuid(generateUuid()).planExecutionId(planExecutionId).type(InterruptType.ABORT_ALL).build();
    interruptService.save(abortAllInterrupt);
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .status(Status.QUEUED)
                                      .ambiance(Ambiance.newBuilder().build())
                                      .mode(ExecutionMode.CHILD)
                                      .version(1L)
                                      .build();
    when(nodeExecutionService.getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode))
        .thenReturn(nodeExecution);
    var ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putFeatureFlagToValueMap(PIPE_FIX_ABORT_RACE_CONDITIONS.toString(), Boolean.TRUE)
                             .build())
            .build();
    ExecutionCheck executionCheck =
        interruptService.checkInterruptsPreInvocation(Collections.singletonList(planExecutionId),
            nodeExecution.getUuid(), Collections.singletonList(nodeExecution.getUuid()), ambiance);
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isFalse();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testAbortAllPreInvocationParent() {
    String planExecutionId = generateUuid();
    Interrupt abortAllInterrupt =
        Interrupt.builder().uuid(generateUuid()).planExecutionId(planExecutionId).type(InterruptType.ABORT_ALL).build();
    interruptService.save(abortAllInterrupt);
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .status(Status.QUEUED)
                                      .ambiance(Ambiance.newBuilder().build())
                                      .mode(ExecutionMode.CHILD)
                                      .version(1L)
                                      .build();
    when(nodeExecutionService.getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode))
        .thenReturn(nodeExecution);
    var ambiance = Ambiance.newBuilder().build();

    ExecutionCheck executionCheck =
        interruptService.checkInterruptsPreInvocation(Collections.singletonList(planExecutionId),
            nodeExecution.getUuid(), Collections.singletonList(nodeExecution.getUuid()), ambiance);
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isTrue();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testAbortAllPreInvocationNotParent() {
    String planExecutionId = generateUuid();
    Interrupt abortAllInterrupt =
        Interrupt.builder().uuid(generateUuid()).planExecutionId(planExecutionId).type(InterruptType.ABORT_ALL).build();
    interruptService.save(abortAllInterrupt);
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .status(Status.QUEUED)
                                      .ambiance(Ambiance.newBuilder().build())
                                      .mode(ExecutionMode.TASK)
                                      .version(1L)
                                      .build();
    when(nodeExecutionService.getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode))
        .thenReturn(nodeExecution);
    when(abortInterruptHandler.handleAndMarkInterruptForNodeExecution(any(), eq(nodeExecution.getUuid()), eq(false)))
        .thenReturn(abortAllInterrupt);
    var ambiance = Ambiance.newBuilder().build();

    ExecutionCheck executionCheck =
        interruptService.checkInterruptsPreInvocation(Collections.singletonList(planExecutionId),
            nodeExecution.getUuid(), Collections.singletonList(nodeExecution.getUuid()), ambiance);
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isFalse();
    verify(abortInterruptHandler).handleAndMarkInterruptForNodeExecution(any(), eq(nodeExecution.getUuid()), eq(false));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testAbortAllOnNodeExecutionPreInvocationNotParent() {
    String planExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    // Passing the nodeExecutionId so it will not match for the plan level interrupts.
    Interrupt abortAllInterrupt = Interrupt.builder()
                                      .uuid(generateUuid())
                                      .planExecutionId(planExecutionId)
                                      .nodeExecutionId(stageNodeExecutionId)
                                      .type(InterruptType.ABORT_ALL)
                                      .build();
    interruptService.save(abortAllInterrupt);
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .status(Status.QUEUED)
                                      .ambiance(Ambiance.newBuilder().build())
                                      .mode(ExecutionMode.TASK)
                                      .version(1L)
                                      .build();
    when(nodeExecutionService.getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode))
        .thenReturn(nodeExecution);
    when(abortInterruptHandler.handleAndMarkInterruptForNodeExecution(any(), eq(nodeExecution.getUuid()), eq(false)))
        .thenReturn(abortAllInterrupt);
    var ambiance = Ambiance.newBuilder().build();
    // Will return as false. Because the Interrupt is present on the stageNodeExecutionId.
    ExecutionCheck executionCheck =
        interruptService.checkInterruptsPreInvocation(Collections.singletonList(planExecutionId),
            nodeExecution.getUuid(), Arrays.asList(nodeExecution.getUuid(), stageNodeExecutionId), ambiance);
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isFalse();
    verify(abortInterruptHandler).handleAndMarkInterruptForNodeExecution(any(), eq(nodeExecution.getUuid()), eq(false));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testAbortAllOnDifferentNodeExecutionPreInvocation() {
    String planExecutionId = generateUuid();
    String abortNodeExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    Interrupt abortAllInterrupt = Interrupt.builder()
                                      .uuid(generateUuid())
                                      .planExecutionId(planExecutionId)
                                      .nodeExecutionId(abortNodeExecutionId)
                                      .type(InterruptType.ABORT_ALL)
                                      .build();
    interruptService.save(abortAllInterrupt);
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .status(Status.QUEUED)
                                      .ambiance(Ambiance.newBuilder().build())
                                      .mode(ExecutionMode.TASK)
                                      .version(1L)
                                      .build();
    var ambiance = Ambiance.newBuilder().build();
    // Since the interrupt was on abortNodeExecutionId and not on the stageNodeExecutionId. So Interrupt will not match
    // and executionCheck will return as true.
    ExecutionCheck executionCheck =
        interruptService.checkInterruptsPreInvocation(Collections.singletonList(planExecutionId),
            nodeExecution.getUuid(), Arrays.asList(nodeExecution.getUuid(), stageNodeExecutionId), ambiance);
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isTrue();
    assertThat(executionCheck.getReason()).isEqualTo("[InterruptCheck] No applicable Interrupt Found");
    verify(abortInterruptHandler, times(0))
        .handleAndMarkInterruptForNodeExecution(any(), eq(nodeExecution.getUuid()), eq(false));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testExpireAllPreInvocationNotParent() {
    String planExecutionId = generateUuid();
    Interrupt expireAllInterrupt = Interrupt.builder()
                                       .uuid(generateUuid())
                                       .planExecutionId(planExecutionId)
                                       .type(InterruptType.EXPIRE_ALL)
                                       .build();
    interruptService.save(expireAllInterrupt);
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .status(Status.QUEUED)
                                      .ambiance(Ambiance.newBuilder().build())
                                      .mode(ExecutionMode.TASK)
                                      .version(1L)
                                      .build();
    when(nodeExecutionService.getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode))
        .thenReturn(nodeExecution);
    when(markExpiredInterruptHandler.handleAndMarkInterruptForNodeExecution(
             any(), eq(nodeExecution.getUuid()), eq(false)))
        .thenReturn(expireAllInterrupt);
    var ambiance = Ambiance.newBuilder().build();
    ExecutionCheck executionCheck =
        interruptService.checkInterruptsPreInvocation(Collections.singletonList(planExecutionId),
            nodeExecution.getUuid(), Collections.singletonList(nodeExecution.getUuid()), ambiance);
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isFalse();
    verify(markExpiredInterruptHandler)
        .handleAndMarkInterruptForNodeExecution(any(), eq(nodeExecution.getUuid()), eq(false));
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testPauseAllPreInvocationParent() {
    String planExecutionId = generateUuid();
    Interrupt interrupt =
        Interrupt.builder().uuid(generateUuid()).planExecutionId(planExecutionId).type(InterruptType.PAUSE_ALL).build();
    interruptService.save(interrupt);
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .status(Status.QUEUED)
                                      .ambiance(Ambiance.newBuilder().build())
                                      .mode(ExecutionMode.CHILD)
                                      .version(1L)
                                      .build();

    when(nodeExecutionService.getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode))
        .thenReturn(nodeExecution);
    var ambiance = Ambiance.newBuilder().build();
    ExecutionCheck executionCheck = interruptService.checkInterruptsPreInvocation(
        Collections.singletonList(planExecutionId), nodeExecution.getUuid(), Collections.emptyList(), ambiance);
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isTrue();
    assertThat(executionCheck.getReason()).isEqualTo("[InterruptCheck] No Interrupts Found");

    verify(nodeExecutionService).getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testPauseAllPreInvocationNotParentWhereNodeExecutionIdIsNull() {
    String planExecutionId = generateUuid();
    Interrupt interrupt =
        Interrupt.builder().uuid(generateUuid()).planExecutionId(planExecutionId).type(InterruptType.PAUSE_ALL).build();
    interruptService.save(interrupt);

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .status(Status.QUEUED)
                                      .ambiance(Ambiance.newBuilder().build())
                                      .mode(ExecutionMode.TASK)
                                      .version(1L)
                                      .build();

    when(nodeExecutionService.getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode))
        .thenReturn(nodeExecution);
    when(pauseAllInterruptHandler.handleInterruptForNodeExecution(interrupt, nodeExecution.getUuid()))
        .thenReturn(interrupt);
    var ambiance = Ambiance.newBuilder().build();

    ExecutionCheck executionCheck = interruptService.checkInterruptsPreInvocation(
        Collections.singletonList(planExecutionId), nodeExecution.getUuid(), Collections.emptyList(), ambiance);
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isFalse();
    assertThat(executionCheck.getReason()).isEqualTo("[InterruptCheck] PAUSE_ALL interrupt found");

    verify(nodeExecutionService).getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode);
    verify(pauseAllInterruptHandler).handleInterruptForNodeExecution(any(), eq(nodeExecution.getUuid()));
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testPauseAllPreInvocationNotParentForStageInterruptWhenNodeIsFinal() {
    String planExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    Interrupt interruptInstance = Interrupt.builder()
                                      .uuid(generateUuid())
                                      .planExecutionId(planExecutionId)
                                      .nodeExecutionId(stageNodeExecutionId)
                                      .type(InterruptType.PAUSE_ALL)
                                      .build();
    interruptService.save(interruptInstance);
    var ambiance = Ambiance.newBuilder().build();
    ExecutionCheck executionCheck = interruptService.checkInterruptsPreInvocation(
        Collections.singletonList(planExecutionId), stageNodeExecutionId, Collections.emptyList(), ambiance);
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isTrue();
    assertThat(executionCheck.getReason()).isEqualTo("[InterruptCheck] No applicable Interrupt Found");

    verify(nodeExecutionService, times(0)).getWithFieldsIncluded(any(), any());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGet() {
    String interruptId = generateUuid();
    Interrupt expectedInterrupt =
        Interrupt.builder().uuid(interruptId).planExecutionId(generateUuid()).type(InterruptType.EXPIRE_ALL).build();
    interruptService.save(expectedInterrupt);

    Interrupt interrupt = interruptService.get(interruptId);

    assertThat(interrupt).isNotNull();
    assertThat(interrupt.getUuid()).isNotNull();
    assertThat(interrupt.getPlanExecutionId()).isEqualTo(expectedInterrupt.getPlanExecutionId());
    assertThat(interrupt.getType()).isEqualTo(expectedInterrupt.getType());
    assertThat(interrupt.getState()).isEqualTo(REGISTERED);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenGet() {
    String interruptId = generateUuid();
    assertThatThrownBy(() -> interruptService.get(interruptId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Interrupt Not found for id: " + interruptId);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUserMarkedFailAll_ffOff_interruptNotFetched() {
    // When the FF is off, USER_MARKED_FAIL_ALL should not be included in the query, so the interrupt
    // is never returned and execution proceeds normally.
    String planExecutionId = generateUuid();
    String accountId = "testAccountId";
    Interrupt userMarkedFailAllInterrupt = Interrupt.builder()
                                               .uuid(generateUuid())
                                               .planExecutionId(planExecutionId)
                                               .type(InterruptType.USER_MARKED_FAIL_ALL)
                                               .build();
    interruptService.save(userMarkedFailAllInterrupt);
    when(pmsFeatureFlagService.isEnabled(accountId, PIPE_FIX_USER_MARKED_FAIL_ALL_PRE_NODE_START_CHECK.name()))
        .thenReturn(false);
    var ambiance =
        Ambiance.newBuilder().setPlanExecutionId(planExecutionId).putSetupAbstractions("accountId", accountId).build();

    ExecutionCheck executionCheck = interruptService.checkInterruptsPreInvocation(
        Collections.singletonList(planExecutionId), generateUuid(), Collections.emptyList(), ambiance);
    assertThat(executionCheck.isProceed()).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUserMarkedFailAll_ffOn_planTerminal_blockNewNode() {
    // When FF is on and plan is already in a terminal status (the updatedCount==0 path stamped it
    // FAILED directly), a new node must not start.
    String planExecutionId = generateUuid();
    String accountId = "testAccountId";
    String nodeExecutionId = generateUuid();
    Interrupt userMarkedFailAllInterrupt = Interrupt.builder()
                                               .uuid(generateUuid())
                                               .planExecutionId(planExecutionId)
                                               .type(InterruptType.USER_MARKED_FAIL_ALL)
                                               .build();
    interruptService.save(userMarkedFailAllInterrupt);
    when(pmsFeatureFlagService.isEnabled(accountId, PIPE_FIX_USER_MARKED_FAIL_ALL_PRE_NODE_START_CHECK.name()))
        .thenReturn(true);
    when(planExecutionService.getStatus(planExecutionId)).thenReturn(Status.FAILED);
    var ambiance =
        Ambiance.newBuilder().setPlanExecutionId(planExecutionId).putSetupAbstractions("accountId", accountId).build();

    ExecutionCheck executionCheck =
        interruptService.checkInterruptsPreInvocation(Collections.singletonList(planExecutionId), nodeExecutionId,
            Collections.singletonList(nodeExecutionId), ambiance);
    assertThat(executionCheck.isProceed()).isFalse();
    assertThat(executionCheck.getReason())
        .isEqualTo("[InterruptCheck] USER_MARKED_FAIL_ALL interrupt found and plan is already terminal");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUserMarkedFailAll_ffOn_planStillRunning_allowFailureStrategy() {
    // When FF is on but plan is still RUNNING, a failure strategy (e.g. MARK_AS_SUCCESS) is in
    // flight and the new node triggered by the adviser must be allowed to start.
    String planExecutionId = generateUuid();
    String accountId = "testAccountId";
    String nodeExecutionId = generateUuid();
    Interrupt userMarkedFailAllInterrupt = Interrupt.builder()
                                               .uuid(generateUuid())
                                               .planExecutionId(planExecutionId)
                                               .type(InterruptType.USER_MARKED_FAIL_ALL)
                                               .build();
    interruptService.save(userMarkedFailAllInterrupt);
    when(pmsFeatureFlagService.isEnabled(accountId, PIPE_FIX_USER_MARKED_FAIL_ALL_PRE_NODE_START_CHECK.name()))
        .thenReturn(true);
    when(planExecutionService.getStatus(planExecutionId)).thenReturn(Status.RUNNING);
    var ambiance =
        Ambiance.newBuilder().setPlanExecutionId(planExecutionId).putSetupAbstractions("accountId", accountId).build();

    ExecutionCheck executionCheck =
        interruptService.checkInterruptsPreInvocation(Collections.singletonList(planExecutionId), nodeExecutionId,
            Collections.singletonList(nodeExecutionId), ambiance);
    assertThat(executionCheck.isProceed()).isTrue();
    assertThat(executionCheck.getReason())
        .isEqualTo(
            "[InterruptCheck] USER_MARKED_FAIL_ALL interrupt found but plan still running - honoring failure strategy");
  }

  private void saveInterruptList(String planExecutionId, boolean retryDiscarded) {
    Interrupt abortAllInterrupt =
        Interrupt.builder().planExecutionId(planExecutionId).type(InterruptType.ABORT_ALL).build();
    Interrupt pauseAllInterrupt =
        Interrupt.builder().planExecutionId(planExecutionId).type(InterruptType.PAUSE_ALL).build();
    Interrupt retryInterrupt = Interrupt.builder()
                                   .planExecutionId(planExecutionId)
                                   .type(InterruptType.RETRY)
                                   .nodeExecutionId(generateUuid())
                                   .state(retryDiscarded ? DISCARDED : REGISTERED)
                                   .build();
    interruptService.save(abortAllInterrupt);
    interruptService.save(pauseAllInterrupt);
    interruptService.save(retryInterrupt);
  }
}
