/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.eventlistener;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HeaderConfig;
import io.harness.execution.NodeExecution;
import io.harness.steps.eventlistener.beans.EventListenerStepInstanceStatus;
import io.harness.steps.eventlistener.entities.EventListenerStepInstance;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
public interface EventListenerStepInstanceService {
  EventListenerStepInstance save(@NotNull EventListenerStepInstance instance);

  EventListenerStepInstance get(@NotNull String eventListenerInstanceId);

  Iterator<EventListenerStepInstance> findByWebhookIdAndStatusWaiting(
      @NotNull String accountIdentifier, @NotNull String webhookIdentifier);

  void deleteByNodeExecutionIds(@NotNull Set<String> nodeExecutionIds);

  boolean isNodeExecutionOfEventListenerStepType(NodeExecution nodeExecution);

  void abortByNodeExecutionId(@NotNull String nodeExecutionId);

  void expireByNodeExecutionId(@NotNull String nodeExecutionId);
  EventListenerStepInstance finalizeStatus(@NotNull String eventListenerInstanceId, String eventCorrelationId,
      EventListenerStepInstanceStatus status, List<HeaderConfig> headerConfigs);
}
