/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SHIVAM;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.advisers.AdvisersHelper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.InterruptBuilder;
import io.harness.interrupts.Interrupt.State;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.core.failurestrategy.action.NGFailureActionTypeConstants;

import java.util.Collections;
import java.util.EnumSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class MarkStatusInterruptHandlerTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private InterruptService interruptService;
  @Mock private OrchestrationEngine orchestrationEngine;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private AdvisersHelper advisersHelper;
  String nodeExecutionId = "nodeExecutionId";
  String planExecutionId = "planExecutionId";
  String accountId = "accountId";
  Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, accountId).build();

  @InjectMocks MarkStatusInterruptHandlerImpl markStatusInterruptHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testRegisterInterrupt() {
    InterruptBuilder interruptBuilder = Interrupt.builder()
                                            .planExecutionId(planExecutionId)
                                            .interruptConfig(InterruptConfig.newBuilder().build())
                                            .type(InterruptType.MARK_EXPIRED);
    assertThatThrownBy(() -> markStatusInterruptHandler.registerInterrupt(interruptBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("NodeExecutionId Cannot be empty for MARK_SUCCESS interrupt");

    doReturn(NodeExecution.builder().status(Status.RUNNING).ambiance(ambiance).build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(eq(nodeExecutionId), anySet());

    interruptBuilder.nodeExecutionId(nodeExecutionId);
    assertThatThrownBy(() -> markStatusInterruptHandler.registerInterrupt(interruptBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Failed to interrupt node execution " + InterruptType.MARK_EXPIRED
            + ". Either another interrupt is already in process or the current status: " + Status.RUNNING
            + "does not allow interruption");

    doReturn(true).when(pmsFeatureFlagService).isEnabled(accountId, FeatureName.CDS_DO_NOT_INTERRUPT_OLD_RETRIED_NODE);
    doReturn(NodeExecution.builder().status(FAILED).oldRetry(true).ambiance(ambiance).build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(eq(nodeExecutionId), anySet());

    interruptBuilder.nodeExecutionId(nodeExecutionId);
    assertThatThrownBy(() -> markStatusInterruptHandler.registerInterrupt(interruptBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Failed to interrupt node execution " + InterruptType.MARK_EXPIRED
            + ". Either another interrupt is already in process or the current status: " + Status.FAILED
            + "does not allow interruption");

    doReturn(NodeExecution.builder().status(FAILED).ambiance(ambiance).build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(eq(nodeExecutionId), anySet());

    ArgumentCaptor<Interrupt> interruptArgumentCaptor = ArgumentCaptor.forClass(Interrupt.class);
    markStatusInterruptHandler.registerInterrupt(interruptBuilder.build());

    verify(interruptService, times(1)).save(interruptArgumentCaptor.capture());

    Interrupt savedInterrupt = interruptArgumentCaptor.getValue();
    assertEquals(savedInterrupt.getState(), Interrupt.State.PROCESSING);
    assertEquals(savedInterrupt.getInterruptConfig(), interruptBuilder.build().getInterruptConfig());
    assertEquals(savedInterrupt.getNodeExecutionId(), nodeExecutionId);
    assertEquals(savedInterrupt.getPlanExecutionId(), planExecutionId);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleInterruptStatus() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    Status status = FAILED;
    Status nonFinalStatus = RUNNING;
    Status fromStatus = RUNNING;
    String interruptUuid = "interruptUuid";
    InterruptBuilder interruptBuilder = Interrupt.builder()
                                            .uuid(interruptUuid)
                                            .planExecutionId(planExecutionId)
                                            .interruptConfig(InterruptConfig.newBuilder().build())
                                            .type(InterruptType.MARK_EXPIRED);

    doReturn(NodeExecution.builder().status(fromStatus).uuid(nodeExecutionId).ambiance(ambiance).build())
        .when(nodeExecutionService)
        .update(eq(nodeExecutionId), any(), any());
    doReturn(ambiance).when(nodeExecutionService).getAmbiance(any());
    doReturn(interruptBuilder.state(State.PROCESSED_SUCCESSFULLY).build())
        .when(interruptService)
        .markProcessed(interruptUuid, State.PROCESSED_SUCCESSFULLY);

    Interrupt returnedInterrupt =
        markStatusInterruptHandler.handleInterruptStatus(interruptBuilder.build(), nodeExecutionId, status);

    assertEquals(returnedInterrupt.getState(), State.PROCESSED_SUCCESSFULLY);
    assertEquals(returnedInterrupt.getInterruptConfig(), interruptBuilder.build().getInterruptConfig());
    // 0 interaction because status was final in returned nodeExecutions.
    verify(planExecutionService, times(1)).calculateAndUpdateRunningStatusForStageAndPlanUnderLock(ambiance);
    verify(orchestrationEngine, times(1))
        .concludeNodeExecution(ambiance, status, fromStatus, EnumSet.noneOf(Status.class));
    verify(interruptService, times(1)).markProcessed(interruptUuid, State.PROCESSED_SUCCESSFULLY);

    returnedInterrupt =
        markStatusInterruptHandler.handleInterruptStatus(interruptBuilder.build(), nodeExecutionId, status);
    assertEquals(returnedInterrupt.getState(), State.PROCESSED_SUCCESSFULLY);
    assertEquals(returnedInterrupt.getInterruptConfig(), interruptBuilder.build().getInterruptConfig());

    verify(planExecutionService, times(2)).calculateAndUpdateRunningStatusForStageAndPlanUnderLock(ambiance);
    verify(orchestrationEngine, times(2))
        .concludeNodeExecution(ambiance, status, fromStatus, EnumSet.noneOf(Status.class));
    verify(interruptService, times(2)).markProcessed(interruptUuid, State.PROCESSED_SUCCESSFULLY);

    Exception thrownException = new InvalidRequestException("Exception message");
    doThrow(thrownException).when(orchestrationEngine).concludeNodeExecution(any(), any(), any(), any());

    assertThatThrownBy(
        () -> markStatusInterruptHandler.handleInterruptStatus(interruptBuilder.build(), nodeExecutionId, status))
        .isInstanceOf(thrownException.getClass())
        .hasMessage(thrownException.getMessage());
    verify(interruptService, times(1)).markProcessed(interruptUuid, State.PROCESSED_UNSUCCESSFULLY);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testRegisterInterruptForPipelineRollback() {
    Ambiance ambiance = Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("runtimeId").build()).build();
    InterruptBuilder interruptBuilder = Interrupt.builder()
                                            .planExecutionId(planExecutionId)
                                            .interruptConfig(InterruptConfig.newBuilder().build())
                                            .type(InterruptType.MARK_FAILED);
    Status fromStatus = INTERVENTION_WAITING;
    doReturn(NodeExecution.builder().status(fromStatus).uuid(nodeExecutionId).ambiance(ambiance).build())
        .when(nodeExecutionService)
        .update(eq(nodeExecutionId), any(), any());
    doReturn(NodeExecution.builder().status(fromStatus).uuid(nodeExecutionId).ambiance(ambiance).build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(any(), any());
    doReturn(ambiance).when(nodeExecutionService).getAmbiance(any());

    interruptBuilder.nodeExecutionId(nodeExecutionId);
    ArgumentCaptor<Interrupt> interruptArgumentCaptor = ArgumentCaptor.forClass(Interrupt.class);
    markStatusInterruptHandler.registerInterrupt(
        interruptBuilder.metadata(Collections.singletonMap("ROLLBACK", NGFailureActionTypeConstants.PIPELINE_ROLLBACK))
            .build());

    verify(interruptService, times(1)).save(interruptArgumentCaptor.capture());
    verify(advisersHelper, times(1)).savePipelineRollbackExecutionSweepingOutput(any());

    Interrupt savedInterrupt = interruptArgumentCaptor.getValue();
    assertEquals(savedInterrupt.getState(), Interrupt.State.PROCESSING);
    assertEquals(savedInterrupt.getInterruptConfig(), interruptBuilder.build().getInterruptConfig());
    assertEquals(savedInterrupt.getNodeExecutionId(), nodeExecutionId);
    assertEquals(savedInterrupt.getPlanExecutionId(), planExecutionId);
  }

  private static class MarkStatusInterruptHandlerImpl extends MarkStatusInterruptHandler {
    @Override
    public Interrupt handleInterruptForNodeExecution(Interrupt interrupt, String nodeExecutionId) {
      return null;
    }
  }
}
