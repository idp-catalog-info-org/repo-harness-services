/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.engine.interrupts.manager.InterruptManager;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.ImmutableMap;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class FailureStrategyAdviserHandlerUtilsTest extends OrchestrationTestBase {
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private InterruptManager interruptManager;
  @Mock private InterruptService interruptService;
  @Mock private PersistentLocker persistentLocker;

  @InjectMocks private FailureStrategyAdviserHandlerUtils failureStrategyAdviserHandlerUtils;

  private String planExecutionId;
  private String nodeExecutionId;
  private NodeExecution nodeExecution;
  private Ambiance ambiance;
  private AcquiredLock<?> acquiredLock;

  @Before
  public void setup() {
    planExecutionId = generateUuid();
    nodeExecutionId = generateUuid();

    Map<String, String> setupAbstractions =
        ImmutableMap.<String, String>builder().put(SetupAbstractionKeys.accountId, "test-account").build();

    ambiance =
        Ambiance.newBuilder().setPlanExecutionId(planExecutionId).putAllSetupAbstractions(setupAbstractions).build();

    nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build();

    acquiredLock = mock(AcquiredLock.class);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testInterruptPipelineIfFailAll_FeatureFlagDisabled() {
    // Setup
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.PIPE_FAIL_ALL_FAILURE_STRATEGY)))
        .thenReturn(false);

    // Execute
    failureStrategyAdviserHandlerUtils.interruptPipelineIfFailAll(nodeExecution, ambiance, true);

    // Verify
    verify(interruptManager, never()).register(any(InterruptPackage.class));
    verify(persistentLocker, never()).waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testInterruptPipelineIfFailAll_FailAllFalse() {
    // Setup
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.PIPE_FAIL_ALL_FAILURE_STRATEGY))).thenReturn(true);

    // Execute
    failureStrategyAdviserHandlerUtils.interruptPipelineIfFailAll(nodeExecution, ambiance, false);

    // Verify
    verify(interruptManager, never()).register(any(InterruptPackage.class));
    verify(persistentLocker, never()).waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testInterruptPipelineIfFailAll_LockNotAcquired() {
    // Setup
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.PIPE_FAIL_ALL_FAILURE_STRATEGY))).thenReturn(true);
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(null);

    // Execute
    failureStrategyAdviserHandlerUtils.interruptPipelineIfFailAll(nodeExecution, ambiance, true);

    // Verify
    verify(interruptManager, never()).register(any(InterruptPackage.class));
    verify(persistentLocker, times(1))
        .waitToAcquireLockOptional(
            eq("FAIL_ALL_INTERRUPT_" + planExecutionId), eq(Duration.ofSeconds(15)), eq(Duration.ofMinutes(1)));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testInterruptPipelineIfFailAll_Success() {
    // Setup
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.PIPE_FAIL_ALL_FAILURE_STRATEGY))).thenReturn(true);
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    when(interruptService.fetchActiveInterruptsForNodeExecution(eq(planExecutionId), any()))
        .thenReturn(Collections.emptyList());

    // Execute
    failureStrategyAdviserHandlerUtils.interruptPipelineIfFailAll(nodeExecution, ambiance, true);

    // Verify
    ArgumentCaptor<InterruptPackage> interruptPackageCaptor = ArgumentCaptor.forClass(InterruptPackage.class);
    verify(interruptManager, times(1)).register(interruptPackageCaptor.capture());

    InterruptPackage capturedPackage = interruptPackageCaptor.getValue();
    assertThat(capturedPackage).isNotNull();
    assertThat(capturedPackage.getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(capturedPackage.getInterruptType()).isEqualTo(InterruptType.USER_MARKED_FAIL_ALL);
    assertThat(capturedPackage.getInterruptConfig()).isNotNull();
    assertThat(capturedPackage.getInterruptConfig().getIssuedBy().getAdviserIssuer().getAdviserType())
        .isEqualTo(AdviseType.MARK_AS_FAILURE);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testInterruptPipelineIfFailAll_InterruptAlreadyPresent() {
    // Setup
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.PIPE_FAIL_ALL_FAILURE_STRATEGY))).thenReturn(true);
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);

    List<Interrupt> existingInterrupts = new ArrayList<>();
    Interrupt existingInterrupt = Interrupt.builder()
                                      .uuid(generateUuid())
                                      .planExecutionId(planExecutionId)
                                      .type(InterruptType.USER_MARKED_FAIL_ALL)
                                      .build();
    existingInterrupts.add(existingInterrupt);

    when(interruptService.fetchActiveInterruptsForNodeExecution(eq(planExecutionId), any()))
        .thenReturn(existingInterrupts);

    // Execute & Verify
    failureStrategyAdviserHandlerUtils.interruptPipelineIfFailAll(nodeExecution, ambiance, true);

    // The method should catch the exception and log it
    verify(interruptManager, never()).register(any(InterruptPackage.class));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testThrowExceptionIfInterruptAlreadyPresent_NoInterrupts() throws Exception {
    // Setup
    when(interruptService.fetchActiveInterruptsForNodeExecution(eq(planExecutionId), any()))
        .thenReturn(Collections.emptyList());

    // Access private method using reflection
    Method method = FailureStrategyAdviserHandlerUtils.class.getDeclaredMethod(
        "throwExceptionIfInterruptAlreadyPresent", String.class);
    method.setAccessible(true);

    // Execute - should not throw exception
    method.invoke(failureStrategyAdviserHandlerUtils, planExecutionId);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testThrowExceptionIfInterruptAlreadyPresent_DifferentInterruptType() throws Exception {
    // Setup
    List<Interrupt> existingInterrupts = new ArrayList<>();
    Interrupt existingInterrupt =
        Interrupt.builder().uuid(generateUuid()).planExecutionId(planExecutionId).type(InterruptType.IGNORE).build();
    existingInterrupts.add(existingInterrupt);

    when(interruptService.fetchActiveInterruptsForNodeExecution(eq(planExecutionId), any()))
        .thenReturn(existingInterrupts);

    // Access private method using reflection
    Method method = FailureStrategyAdviserHandlerUtils.class.getDeclaredMethod(
        "throwExceptionIfInterruptAlreadyPresent", String.class);
    method.setAccessible(true);

    // Execute - should not throw exception
    method.invoke(failureStrategyAdviserHandlerUtils, planExecutionId);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testThrowExceptionIfInterruptAlreadyPresent_SameTypeWithNodeExecutionId() throws Exception {
    // Setup
    List<Interrupt> existingInterrupts = new ArrayList<>();
    Interrupt existingInterrupt = Interrupt.builder()
                                      .uuid(generateUuid())
                                      .planExecutionId(planExecutionId)
                                      .nodeExecutionId(nodeExecutionId) // Has a node execution ID
                                      .type(InterruptType.USER_MARKED_FAIL_ALL)
                                      .build();
    existingInterrupts.add(existingInterrupt);

    when(interruptService.fetchActiveInterruptsForNodeExecution(eq(planExecutionId), any()))
        .thenReturn(existingInterrupts);

    // Access private method using reflection
    Method method = FailureStrategyAdviserHandlerUtils.class.getDeclaredMethod(
        "throwExceptionIfInterruptAlreadyPresent", String.class);
    method.setAccessible(true);

    // Execute - should not throw exception because the interrupt has a node execution ID
    method.invoke(failureStrategyAdviserHandlerUtils, planExecutionId);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testThrowExceptionIfInterruptAlreadyPresent_SameTypeWithoutNodeExecutionId() throws Exception {
    // Setup
    List<Interrupt> existingInterrupts = new ArrayList<>();
    Interrupt existingInterrupt = Interrupt.builder()
                                      .uuid(generateUuid())
                                      .planExecutionId(planExecutionId)
                                      .nodeExecutionId(null) // No node execution ID
                                      .type(InterruptType.USER_MARKED_FAIL_ALL)
                                      .build();
    existingInterrupts.add(existingInterrupt);

    when(interruptService.fetchActiveInterruptsForNodeExecution(eq(planExecutionId), any()))
        .thenReturn(existingInterrupts);

    // Access private method using reflection
    Method method = FailureStrategyAdviserHandlerUtils.class.getDeclaredMethod(
        "throwExceptionIfInterruptAlreadyPresent", String.class);
    method.setAccessible(true);

    // Execute & Verify - should throw InvalidRequestException
    assertThatThrownBy(() -> method.invoke(failureStrategyAdviserHandlerUtils, planExecutionId))
        .hasCauseInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetLockKey() throws Exception {
    // Access private method using reflection
    Method method = FailureStrategyAdviserHandlerUtils.class.getDeclaredMethod("getLockKey", String.class);
    method.setAccessible(true);

    // Execute
    String result = (String) method.invoke(null, planExecutionId);

    // Verify
    assertThat(result).isEqualTo("FAIL_ALL_INTERRUPT_" + planExecutionId);
  }
}
