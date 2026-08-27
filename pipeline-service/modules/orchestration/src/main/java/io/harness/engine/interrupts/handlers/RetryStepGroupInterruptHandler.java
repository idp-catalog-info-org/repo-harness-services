/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.advisers.retry.RetryStepGroupAdvisor;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.interrupts.handlers.intfc.InterruptHandler;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.serializer.KryoSerializer;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class RetryStepGroupInterruptHandler implements InterruptHandler {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private MarkFailedInterruptHandler userMarkedFailAllInterruptHandler;
  @Inject private KryoSerializer kryoSerializer;
  @Inject InterruptService interruptService;

  @Override
  public Interrupt registerInterrupt(Interrupt interrupt) {
    AdviserObtainment adviserObtainment = createRetryAdviserObtainment();
    NodeExecution nodeExecution = nodeExecutionService.get(interrupt.getNodeExecutionId());

    String stepGroupRuntimeId = getStepGroupRuntimeId(nodeExecution);
    updateNodeExecutionWithAdviser(stepGroupRuntimeId, adviserObtainment);

    Interrupt markFailedInterrupt = createMarkFailedInterrupt(interrupt);
    registerMarkFailedInterrupt(markFailedInterrupt);

    return interruptService.save(interrupt);
  }

  private AdviserObtainment createRetryAdviserObtainment() {
    Set<FailureType> applicableFailureTypes = Sets.newHashSet(FailureType.UNKNOWN_FAILURE,
        FailureType.DELEGATE_PROVISIONING_FAILURE, FailureType.CONNECTIVITY_FAILURE, FailureType.AUTHENTICATION_FAILURE,
        FailureType.VERIFICATION_FAILURE, FailureType.APPLICATION_FAILURE, FailureType.AUTHORIZATION_FAILURE,
        FailureType.TIMEOUT_FAILURE, FailureType.SKIPPING_FAILURE, FailureType.POLICY_EVALUATION_FAILURE,
        FailureType.INPUT_TIMEOUT_FAILURE, FailureType.FREEZE_ACTIVE_FAILURE, FailureType.APPROVAL_REJECTION,
        FailureType.DELEGATE_RESTART, FailureType.USER_MARKED_FAILURE, FailureType.INFRASTRUCTURE_FAILURE,
        FailureType.PLUGIN_IMAGE_FAILURE, FailureType.RESOURCE_LIMITS_FAILURE, FailureType.CONFIGURATION_FAILURE,
        FailureType.RETRYABLE_TRANSIENT_FAILURE);

    return AdviserObtainment.newBuilder()
        .setType(RetryStepGroupAdvisor.ADVISER_TYPE)
        .setParameters(ByteString.copyFrom(
            kryoSerializer.asBytes(io.harness.advisers.retry.RetryAdviserRollbackParameters.builder()
                                       .retryCount(25)
                                       .waitIntervalList(Collections.emptyList())
                                       .applicableFailureTypes(applicableFailureTypes)
                                       .build())))
        .build();
  }

  private String getStepGroupRuntimeId(NodeExecution nodeExecution) {
    Optional<Level> stepGroupLevelFromAmbiance =
        AmbianceUtils.getStepGroupLevelFromAmbiance(nodeExecutionService.getAmbiance(nodeExecution));
    String stepGroupRuntimeId = null;
    if (stepGroupLevelFromAmbiance.isPresent()) {
      stepGroupRuntimeId = stepGroupLevelFromAmbiance.get().getRuntimeId();
    }

    if (stepGroupRuntimeId == null) {
      throw new IllegalStateException("No StepGroup found in node execution ambiance");
    }

    return stepGroupRuntimeId;
  }

  private void updateNodeExecutionWithAdviser(String stepGroupRuntimeId, AdviserObtainment adviserObtainment) {
    nodeExecutionService.update(stepGroupRuntimeId,
        ops -> ops.set(NodeExecutionKeys.adviserObtainments, Collections.singletonList(adviserObtainment)));
  }

  private Interrupt createMarkFailedInterrupt(Interrupt interrupt) {
    return Interrupt.builder()
        .uuid(generateUuid())
        .planExecutionId(interrupt.getPlanExecutionId())
        .type(InterruptType.MARK_FAILED)
        .metadata(interrupt.getMetadata())
        .nodeExecutionId(interrupt.getNodeExecutionId())
        .interruptConfig(interrupt.getInterruptConfig())
        .build();
  }

  private void registerMarkFailedInterrupt(Interrupt markFailedInterrupt) {
    userMarkedFailAllInterruptHandler.registerInterrupt(markFailedInterrupt);
  }

  @Override
  public Interrupt handleInterrupt(Interrupt interrupt) {
    throw new UnsupportedOperationException("handleInterrupt for StepGroup interrupt is not supported");
  }

  @Override
  public Interrupt handleInterruptForNodeExecution(Interrupt interrupt, String nodeExecutionId) {
    throw new UnsupportedOperationException("handleInterrupt for StepGroup interrupt is not supported");
  }
}
