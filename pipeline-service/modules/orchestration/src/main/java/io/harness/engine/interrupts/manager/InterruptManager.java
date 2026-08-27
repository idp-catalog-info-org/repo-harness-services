/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.manager;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.engine.interrupts.factory.InterruptHandlerFactory;
import io.harness.engine.interrupts.handlers.intfc.InterruptHandler;
import io.harness.exception.InvalidRequestException;
import io.harness.interrupts.Interrupt;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.logging.AutoLogContext;

import com.google.inject.Inject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(PIPELINE)
@Slf4j
public class InterruptManager {
  private static final String LOCK_NAME_PREFIX = "PLAN_EXECUTION_INFO_";
  @Inject private InterruptHandlerFactory interruptHandlerFactory;
  @Inject PersistentLocker persistentLocker;

  public Interrupt register(InterruptPackage interruptPackage) {
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .planExecutionId(interruptPackage.getPlanExecutionId())
                              .type(interruptPackage.getInterruptType())
                              .metadata(interruptPackage.getMetadata())
                              .nodeExecutionId(interruptPackage.getNodeExecutionId())
                              .interruptConfig(interruptPackage.getInterruptConfig())
                              .build();
    String lockKey = getLockKey(interrupt);
    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLockOptional(lockKey, Duration.ofSeconds(15), Duration.ofMinutes(1));
         AutoLogContext ignore = interrupt.autoLogContext()) {
      if (lock == null) {
        throw new InvalidRequestException("Cannot register the interrupt. Please retry.");
      }
      InterruptHandler interruptHandler = interruptHandlerFactory.obtainHandler(interruptPackage.getInterruptType());
      Interrupt registeredInterrupt = interruptHandler.registerInterrupt(interrupt);
      log.info("Interrupt Registered uuid: {}, type: {}", registeredInterrupt.getUuid(), registeredInterrupt.getType());
      return registeredInterrupt;
    }
  }

  @NotNull
  private static String getLockKey(Interrupt interrupt) {
    String lockKey = LOCK_NAME_PREFIX + interrupt.getPlanExecutionId();
    if (isNotEmpty(interrupt.getNodeExecutionId())) {
      lockKey = lockKey + "_" + interrupt.getNodeExecutionId();
    }
    return lockKey;
  }
}
