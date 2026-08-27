/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.consumer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.MessageHandler;
import io.harness.models.UnifiedDeploymentDTO;
import io.harness.pms.contracts.execution.events.UnifiedDeploymentEvent;
import io.harness.service.instancesync.unified.UnifiedInstanceSyncHelper;
import io.harness.service.instancesync.unified.UnifiedInstanceSyncService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class UnifiedPipelineEventMessageHandler implements MessageHandler<UnifiedDeploymentEvent> {
  private final UnifiedInstanceSyncHelper unifiedInstanceSyncHelper;
  private final UnifiedInstanceSyncService unifiedInstanceSyncService;

  @Inject
  public UnifiedPipelineEventMessageHandler(
      UnifiedInstanceSyncHelper unifiedInstanceSyncHelper, UnifiedInstanceSyncService unifiedInstanceSyncService) {
    this.unifiedInstanceSyncHelper = unifiedInstanceSyncHelper;
    this.unifiedInstanceSyncService = unifiedInstanceSyncService;
  }

  @Override
  public void onMessage(UnifiedDeploymentEvent event, Map<String, String> metadata, Map<String, Object> metricInfo) {
    String accountId = metadata.get("accountId");
    String planExecutionId = metadata.get("planExecutionId");
    String nodeExecutionId = metadata.get("nodeExecutionId");
    log.debug("Processing unified deployment event - accountId: {}, planExecutionId: {}, nodeExecutionId: {}, "
            + "stepStatus: {}",
        accountId, planExecutionId, nodeExecutionId, event.getStepStatus());

    try {
      processEvent(accountId, event);
    } catch (Exception ex) {
      log.error("Error processing unified deployment event for planExecutionId: {}, nodeExecutionId: {}",
          planExecutionId, nodeExecutionId, ex);
    }
  }

  private void processEvent(String accountId, UnifiedDeploymentEvent event) {
    if (unifiedInstanceSyncService.isEnabled(accountId)) {
      UnifiedDeploymentDTO unifiedDeploymentDTO = unifiedInstanceSyncHelper.createNewDeploymentDTO(event);
      unifiedInstanceSyncService.processInstanceSyncForNewDeployment(unifiedDeploymentDTO);
    }
  }
}
