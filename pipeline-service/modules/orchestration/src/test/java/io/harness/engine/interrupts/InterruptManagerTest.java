/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.interrupts.factory.InterruptHandlerFactory;
import io.harness.engine.interrupts.handlers.AbortAllInterruptHandler;
import io.harness.engine.interrupts.handlers.AbortInterruptHandler;
import io.harness.engine.interrupts.manager.InterruptManager;
import io.harness.interrupts.Interrupt;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class InterruptManagerTest extends CategoryTest {
  @Mock InterruptHandlerFactory interruptHandlerFactory;
  @Mock PersistentLocker persistentLocker;
  @Mock AbortAllInterruptHandler abortAllInterruptHandler;
  @Mock AbortInterruptHandler abortInterruptHandler;
  @InjectMocks InterruptManager interruptManager;

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRegister() {
    InterruptConfig interruptConfig = spy(InterruptConfig.class);
    InterruptPackage interruptPackage = InterruptPackage.builder()
                                            .planExecutionId("planExecutionId")
                                            .interruptConfig(interruptConfig)
                                            .interruptType(InterruptType.ABORT_ALL)
                                            .build();

    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .planExecutionId(interruptPackage.getPlanExecutionId())
                              .type(interruptPackage.getInterruptType())
                              .interruptConfig(interruptPackage.getInterruptConfig())
                              .build();

    doReturn(RedisAcquiredLock.builder().build()).when(persistentLocker).waitToAcquireLockOptional(any(), any(), any());
    doReturn(abortAllInterruptHandler).when(interruptHandlerFactory).obtainHandler(any());
    doReturn(interrupt).when(abortAllInterruptHandler).registerInterrupt(any());

    interruptManager.register(interruptPackage);

    verify(persistentLocker, times(1))
        .waitToAcquireLockOptional(eq("PLAN_EXECUTION_INFO_planExecutionId"), any(), any());
    ArgumentCaptor<Interrupt> argumentCaptor = ArgumentCaptor.forClass(Interrupt.class);
    verify(abortAllInterruptHandler, times(1)).registerInterrupt(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getPlanExecutionId()).isEqualTo("planExecutionId");
    verify(interruptHandlerFactory, times(1)).obtainHandler(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testLockKeyInRegister() {
    InterruptConfig interruptConfig = spy(InterruptConfig.class);
    InterruptPackage interruptPackage = InterruptPackage.builder()
                                            .planExecutionId("planExecutionId")
                                            .nodeExecutionId("nodeExecutionId")
                                            .interruptConfig(interruptConfig)
                                            .interruptType(InterruptType.ABORT)
                                            .build();

    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .planExecutionId(interruptPackage.getPlanExecutionId())
                              .nodeExecutionId(interruptPackage.getNodeExecutionId())
                              .type(interruptPackage.getInterruptType())
                              .interruptConfig(interruptPackage.getInterruptConfig())
                              .build();

    doReturn(RedisAcquiredLock.builder().build()).when(persistentLocker).waitToAcquireLockOptional(any(), any(), any());
    doReturn(abortInterruptHandler).when(interruptHandlerFactory).obtainHandler(any());
    doReturn(interrupt).when(abortInterruptHandler).registerInterrupt(any());

    interruptManager.register(interruptPackage);

    verify(persistentLocker, times(1))
        .waitToAcquireLockOptional(eq("PLAN_EXECUTION_INFO_planExecutionId_nodeExecutionId"), any(), any());
    ArgumentCaptor<Interrupt> argumentCaptor = ArgumentCaptor.forClass(Interrupt.class);
    verify(abortInterruptHandler, times(1)).registerInterrupt(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getPlanExecutionId()).isEqualTo("planExecutionId");
    verify(interruptHandlerFactory, times(1)).obtainHandler(any());
  }
}
