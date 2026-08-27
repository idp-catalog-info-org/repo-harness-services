/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.gitNotification;

import static io.harness.eventsframework.EventsFrameworkConstants.STAGE_STATUS_EVENT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.stage.StageStatusEvent;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.notification.gitstatus.GitStatusUpdateNotifier;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
public class StageStatusEventHandler extends PmsBaseEventHandler<StageStatusEvent> {
  @Inject NodeExecutionService nodeExecutionService;
  @Inject GitStatusUpdateNotifier gitStatusUpdateNotifier;
  @Inject private PmsFeatureFlagService featureFlagService;

  @NonNull
  @Override
  protected Map<String, String> extraLogProperties(StageStatusEvent event) {
    return ImmutableMap.<String, String>builder().build();
  }

  @Override
  protected Ambiance extractAmbiance(StageStatusEvent event) {
    return event.getAmbiance();
  }

  @Override
  protected String getEventType(StageStatusEvent message) {
    return STAGE_STATUS_EVENT;
  }

  @Override
  protected void handleEventWithContext(StageStatusEvent event) {
    try {
      if (event == null) {
        return;
      }

      boolean sendStatusToGitEnabled =
          featureFlagService.isEnabled(event.getAccountIdentifier(), FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT);
      boolean gitOpsStatusEnabled = !featureFlagService.isEnabled(
          event.getAccountIdentifier(), FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED);
      if (!sendStatusToGitEnabled && !gitOpsStatusEnabled) {
        return;
      }

      NodeExecution nodeExecution = nodeExecutionService.get(event.getNodeExecutionId());
      Ambiance ambiance = nodeExecutionService.getAmbiance(nodeExecution);
      if (sendStatusToGitEnabled) {
        gitStatusUpdateNotifier.onNodeStatusUpdate(nodeExecution, ambiance);
      }
      if (gitOpsStatusEnabled) {
        gitStatusUpdateNotifier.onGitOpsNodeStatusUpdate(nodeExecution, ambiance);
      }
    } catch (Exception ex) {
      log.error("Failed to process the stage status event for node execution: {}, plan execution: {}",
          event.getStageExecutionId(), event.getPlanExecutionId(), ex);
    }
  }
}
