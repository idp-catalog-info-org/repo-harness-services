/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class RetryStepGroupInterruptHandlerTest extends OrchestrationTestBase {
  private static final String STEP_GROUP_TYPE = "STEP_GROUP";

  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private MarkFailedInterruptHandler userMarkedFailAllInterruptHandler;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private InterruptService interruptService;

  @InjectMocks private RetryStepGroupInterruptHandler retryStepGroupInterruptHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testRegisterInterrupt_Success() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    String nodeExecutionId = generateUuid();

    Ambiance ambiance = createAmbianceWithStepGroup();
    String stepGroupRuntimeId = ambiance.getLevelsList().get(0).getRuntimeId();
    NodeExecution nodeExecution = createNodeExecution(nodeExecutionId, ambiance);

    Interrupt interrupt = createInterrupt(interruptUuid, nodeExecutionId, planExecutionId);

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(nodeExecution);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);
    when(kryoSerializer.asBytes(any())).thenReturn("test-bytes".getBytes());
    when(interruptService.save(interrupt)).thenReturn(interrupt);

    Interrupt result = retryStepGroupInterruptHandler.registerInterrupt(interrupt);

    assertThat(result).isNotNull();
    assertThat(result.getUuid()).isEqualTo(interruptUuid);

    verify(nodeExecutionService, times(1)).get(nodeExecutionId);
    verify(nodeExecutionService, times(1)).update(eq(stepGroupRuntimeId), any());
    verify(userMarkedFailAllInterruptHandler, times(1)).registerInterrupt(any(Interrupt.class));
    verify(interruptService, times(1)).save(interrupt);
    verify(kryoSerializer, times(1)).asBytes(any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testRegisterInterrupt_PropagatesMarkFailedInterruptFromOriginal() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    String nodeExecutionId = generateUuid();

    Ambiance ambiance = createAmbianceWithStepGroup();
    NodeExecution nodeExecution = createNodeExecution(nodeExecutionId, ambiance);

    Interrupt interrupt = createInterrupt(interruptUuid, nodeExecutionId, planExecutionId);

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(nodeExecution);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);
    when(kryoSerializer.asBytes(any())).thenReturn("test-bytes".getBytes());
    when(interruptService.save(interrupt)).thenReturn(interrupt);

    retryStepGroupInterruptHandler.registerInterrupt(interrupt);

    ArgumentCaptor<Interrupt> markFailedCaptor = ArgumentCaptor.forClass(Interrupt.class);
    verify(userMarkedFailAllInterruptHandler).registerInterrupt(markFailedCaptor.capture());

    Interrupt markFailed = markFailedCaptor.getValue();
    assertThat(markFailed.getType()).isEqualTo(InterruptType.MARK_FAILED);
    assertThat(markFailed.getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(markFailed.getNodeExecutionId()).isEqualTo(nodeExecutionId);
    assertThat(markFailed.getInterruptConfig()).isEqualTo(interrupt.getInterruptConfig());
    assertThat(markFailed.getMetadata()).isEqualTo(interrupt.getMetadata());
    assertThat(markFailed.getUuid()).isNotEqualTo(interruptUuid);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testRegisterInterrupt_NoStepGroupFound_ThrowsException() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    String nodeExecutionId = generateUuid();

    Ambiance ambiance = createAmbianceWithoutStepGroup();
    NodeExecution nodeExecution = createNodeExecution(nodeExecutionId, ambiance);

    Interrupt interrupt = createInterrupt(interruptUuid, nodeExecutionId, planExecutionId);

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(nodeExecution);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);
    when(kryoSerializer.asBytes(any())).thenReturn("test-bytes".getBytes());

    assertThatThrownBy(() -> retryStepGroupInterruptHandler.registerInterrupt(interrupt))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No StepGroup found in node execution ambiance");

    verify(nodeExecutionService, times(1)).get(nodeExecutionId);
    verify(nodeExecutionService, never()).update(anyString(), any());
    verify(userMarkedFailAllInterruptHandler, never()).registerInterrupt(any(Interrupt.class));
    verify(interruptService, never()).save(any(Interrupt.class));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testRegisterInterrupt_NodeExecutionNull_ThrowsNullPointerException() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    String nodeExecutionId = generateUuid();

    Interrupt interrupt = createInterrupt(interruptUuid, nodeExecutionId, planExecutionId);

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(null);
    when(kryoSerializer.asBytes(any())).thenReturn("test-bytes".getBytes());

    assertThatThrownBy(() -> retryStepGroupInterruptHandler.registerInterrupt(interrupt))
        .isInstanceOf(NullPointerException.class);

    verify(nodeExecutionService, times(1)).get(nodeExecutionId);
    verify(nodeExecutionService, never()).update(anyString(), any());
    verify(userMarkedFailAllInterruptHandler, never()).registerInterrupt(any(Interrupt.class));
    verify(interruptService, never()).save(any(Interrupt.class));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testHandleInterrupt_ThrowsUnsupportedOperationException() {
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.MARK_FAILED)
                              .planExecutionId(generateUuid())
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .build();

    assertThatThrownBy(() -> retryStepGroupInterruptHandler.handleInterrupt(interrupt))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("handleInterrupt for StepGroup interrupt is not supported");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testHandleInterruptForNodeExecution_ThrowsUnsupportedOperationException() {
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.MARK_FAILED)
                              .planExecutionId(generateUuid())
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .build();
    String nodeExecutionId = generateUuid();

    assertThatThrownBy(() -> retryStepGroupInterruptHandler.handleInterruptForNodeExecution(interrupt, nodeExecutionId))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("handleInterrupt for StepGroup interrupt is not supported");
  }

  private Ambiance createAmbianceWithStepGroup() {
    Level stepGroupLevel = Level.newBuilder()
                               .setRuntimeId(generateUuid())
                               .setSetupId(generateUuid())
                               .setStepType(StepType.newBuilder().setType(STEP_GROUP_TYPE).build())
                               .build();

    Level otherLevel = Level.newBuilder()
                           .setRuntimeId(generateUuid())
                           .setSetupId(generateUuid())
                           .setStepType(StepType.newBuilder().setType("SOME_OTHER_TYPE").build())
                           .build();

    return Ambiance.newBuilder()
        .setPlanExecutionId(generateUuid())
        .setPlanId(generateUuid())
        .addLevels(stepGroupLevel)
        .addLevels(otherLevel)
        .build();
  }

  private Ambiance createAmbianceWithoutStepGroup() {
    Level otherLevel = Level.newBuilder()
                           .setRuntimeId(generateUuid())
                           .setSetupId(generateUuid())
                           .setStepType(StepType.newBuilder().setType("SOME_OTHER_TYPE").build())
                           .build();

    return Ambiance.newBuilder()
        .setPlanExecutionId(generateUuid())
        .setPlanId(generateUuid())
        .addLevels(otherLevel)
        .build();
  }

  private NodeExecution createNodeExecution(String nodeExecutionId, Ambiance ambiance) {
    return NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build();
  }

  private Interrupt createInterrupt(String interruptUuid, String nodeExecutionId, String planExecutionId) {
    return Interrupt.builder()
        .uuid(interruptUuid)
        .type(InterruptType.USER_MARKED_FAIL_ALL)
        .nodeExecutionId(nodeExecutionId)
        .interruptConfig(InterruptConfig.newBuilder().build())
        .planExecutionId(planExecutionId)
        .build();
  }
}
