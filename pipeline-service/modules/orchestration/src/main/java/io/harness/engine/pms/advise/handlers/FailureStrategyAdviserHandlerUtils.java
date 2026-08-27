/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.USER_MARKED_FAILURE;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.engine.interrupts.manager.InterruptManager;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.interrupts.AdviserIssuer;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.serializer.ProtoUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class FailureStrategyAdviserHandlerUtils {
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private InterruptManager interruptManager;
  @Inject private InterruptService interruptService;
  @Inject PersistentLocker persistentLocker;

  public void interruptPipelineIfFailAll(NodeExecution nodeExecution, Ambiance ambiance, boolean failAll) {
    if (pmsFeatureFlagService.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.PIPE_FAIL_ALL_FAILURE_STRATEGY)
        && failAll) {
      InterruptPackage interruptPackage =
          InterruptPackage.builder()
              .planExecutionId(nodeExecution.getPlanExecutionId())
              .interruptType(InterruptType.USER_MARKED_FAIL_ALL)
              .interruptConfig(
                  InterruptConfig.newBuilder()
                      .setIssuedBy(
                          IssuedBy.newBuilder()
                              .setAdviserIssuer(
                                  AdviserIssuer.newBuilder().setAdviserType(AdviseType.MARK_AS_FAILURE).build())
                              .setIssueTime(ProtoUtils.unixMillisToTimestamp(System.currentTimeMillis()))
                              .build())
                      .build())
              .build();

      Interrupt interrupt = Interrupt.builder()
                                .uuid(generateUuid())
                                .planExecutionId(interruptPackage.getPlanExecutionId())
                                .type(interruptPackage.getInterruptType())
                                .metadata(interruptPackage.getMetadata())
                                .interruptConfig(interruptPackage.getInterruptConfig())
                                .build();
      String lockKey = getLockKey(interrupt.getPlanExecutionId());
      try (AcquiredLock<?> lock =
               persistentLocker.waitToAcquireLockOptional(lockKey, Duration.ofSeconds(15), Duration.ofMinutes(1));
           AutoLogContext ignore = interrupt.autoLogContext()) {
        if (lock == null) {
          log.error("Cannot register the interrupt. Please retry.");
          return;
        }
        throwExceptionIfInterruptAlreadyPresent(interrupt.getPlanExecutionId());
        interruptManager.register(interruptPackage);
      } catch (Exception e) {
        log.error(e.getMessage());
      }
    }
  }

  private void throwExceptionIfInterruptAlreadyPresent(String planExecutionId) {
    List<Interrupt> interrupts = interruptService.fetchActiveInterruptsForNodeExecution(planExecutionId, null);
    for (Interrupt presentInterrupt : interrupts) {
      if (presentInterrupt.getType() == InterruptType.USER_MARKED_FAIL_ALL) {
        if (EmptyPredicate.isEmpty(presentInterrupt.getNodeExecutionId())) {
          // Checking if any plan level UserMarkedFailAll interrupt present.
          throw new InvalidRequestException(
              "Execution already has USER_MARKED_FAIL_ALL interrupt", USER_MARKED_FAILURE, USER);
        }
      }
    }
  }

  private static String getLockKey(String planExecutionId) {
    return "FAIL_ALL_INTERRUPT_" + planExecutionId;
  }
}
