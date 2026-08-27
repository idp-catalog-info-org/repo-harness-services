/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eraro.ErrorCode.USER_MARKED_FAILURE;
import static io.harness.logging.UnitStatus.FAILURE;
import static io.harness.pms.contracts.interrupts.InterruptType.USER_MARKED_FAIL_ALL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.constants.OrchestrationPublisherName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.exception.NodeExecutionUpdateFailedException;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.interrupts.UserMarkedFailureInterruptCallback;
import io.harness.engine.interrupts.exception.InterruptProcessingFailedException;
import io.harness.engine.interrupts.handlers.publisher.InterruptEventPublisher;
import io.harness.engine.interrupts.helpers.intfc.UserMarkedFailAllHelper;
import io.harness.eraro.Level;
import io.harness.exception.InvalidRequestException;
import io.harness.exceptions.RecasterException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.InterruptEffect;
import io.harness.logging.AutoLogContext;
import io.harness.logging.UnitProgress;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.failure.FailureTypeInfo;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
@OwnedBy(PIPELINE)
public class UserMarkedFailAllHelperImpl implements UserMarkedFailAllHelper {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) String publisherName;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private InterruptHelper interruptHelper;
  @Inject private InterruptEventPublisher interruptEventPublisher;
  @Inject private OrchestrationEngine engine;

  @Override
  public void discontinueMarkedInstance(NodeExecution nodeExecution, Interrupt interrupt) {
    try (AutoLogContext ignore = interrupt.autoLogContext()) {
      boolean taskDiscontinued = interruptHelper.discontinueTaskIfRequired(nodeExecution);
      if (!taskDiscontinued) {
        log.error("Delegate Task Cannot be aborted for NodeExecutionId: {}", nodeExecution.getUuid());
      }

      Ambiance ambiance = nodeExecutionService.getAmbiance(nodeExecution);
      if (nodeExecution.getMode() == ExecutionMode.SYNC || ExecutionModeUtils.isParentMode(nodeExecution.getMode())) {
        log.info("Fail directly because mode is {}", nodeExecution.getMode());
        failDiscontinuingNode(
            ambiance, nodeExecution, interrupt.getType(), interrupt.getUuid(), interrupt.getInterruptConfig());
        return;
      }

      String notifyId = interruptEventPublisher.publishEvent(nodeExecution.getUuid(), interrupt, USER_MARKED_FAIL_ALL);
      UserMarkedFailureInterruptCallback userMarkedFailureInterruptCallback =
          UserMarkedFailureInterruptCallback.builder()
              .nodeExecutionId(nodeExecution.getUuid())
              .interruptId(interrupt.getUuid())
              .interruptType(interrupt.getType())
              .interruptConfig(interrupt.getInterruptConfig())
              .ambiance(ambiance)
              .build();
      waitNotifyEngine.waitForAllOnInList(publisherName, userMarkedFailureInterruptCallback,
          Collections.singletonList(notifyId), Duration.ofMinutes(1));

    } catch (NodeExecutionUpdateFailedException ex) {
      throw new InterruptProcessingFailedException(USER_MARKED_FAIL_ALL,
          "UserMarkedFailure failed for execution Plan :" + nodeExecution.getPlanExecutionId()
              + "for NodeExecutionId: " + nodeExecution.getUuid(),
          ex);
    } catch (Exception e) {
      log.error("Error in discontinuing", e);
      throw new InvalidRequestException("Error in discontinuing, " + e.getMessage());
    }
  }

  @Override
  public void failDiscontinuingNode(Ambiance ambiance, NodeExecution nodeExecution, InterruptType interruptType,
      String interruptId, InterruptConfig interruptConfig) {
    List<UnitProgress> unitProgressList = InterruptHelper.evaluateUnitProgresses(nodeExecution, FAILURE);
    List<UnitProgress> progressDataUnitProgress = List.of();

    try {
      progressDataUnitProgress = InterruptHelper.evaluateUnitProgressesFromProgressData(nodeExecution, FAILURE);
    } catch (RecasterException | ClassCastException ex) {
      log.error("Error deserializing progress data unit progress for nodeExecutionId: {}", nodeExecution.getUuid(), ex);
    }

    List<UnitProgress> finalUnitProgressList = mergeUnitProgressLists(unitProgressList, progressDataUnitProgress);

    nodeExecutionService.updateV2(nodeExecution.getUuid(),
        ops
        -> ops.addToSet(NodeExecutionKeys.interruptHistories,
            InterruptEffect.builder()
                .interruptType(interruptType)
                .tookEffectAt(System.currentTimeMillis())
                .interruptId(interruptId)
                .interruptConfig(interruptConfig)
                .build()));
    engine.processStepResponse(ambiance,
        StepResponseProto.newBuilder()
            .setStatus(Status.FAILED)
            .setFailureInfo(
                FailureInfo.newBuilder()
                    .setErrorMessage("User Initiated Failure")
                    .addFailureTypes(FailureType.USER_MARKED_FAILURE)
                    .addFailureData(
                        FailureData.newBuilder()
                            .addFailureTypes(FailureType.USER_MARKED_FAILURE)
                            .setLevel(Level.ERROR.name())
                            .setCode(USER_MARKED_FAILURE.name())
                            .setMessage("User Initiated Failure")
                            .addFailureTypeInfos(
                                FailureTypeInfo.newBuilder().setFailureType(FailureType.USER_MARKED_FAILURE).build())
                            .build())
                    .build())
            .addAllUnitProgress(finalUnitProgressList)
            .build());
  }

  /**
   * Merges unit progress from both unitProgresses (deprecated) and progressData sources.
   * Uses unitProgresses as the primary source (more reliable, no deserialization issues),
   * then adds any units from progressData that aren't already covered (needed for async steps
   * where the deprecated field is empty).
   */
  private List<UnitProgress> mergeUnitProgressLists(
      List<UnitProgress> unitProgressList, List<UnitProgress> progressDataUnitProgress) {
    if (EmptyPredicate.isEmpty(progressDataUnitProgress)) {
      return unitProgressList;
    }
    if (EmptyPredicate.isEmpty(unitProgressList)) {
      return progressDataUnitProgress;
    }

    Set<String> coveredUnits = new HashSet<>();
    for (UnitProgress up : unitProgressList) {
      coveredUnits.add(up.getUnitName());
    }

    List<UnitProgress> merged = new ArrayList<>(unitProgressList);
    for (UnitProgress up : progressDataUnitProgress) {
      if (!coveredUnits.contains(up.getUnitName())) {
        merged.add(up);
      }
    }
    return merged;
  }
}
