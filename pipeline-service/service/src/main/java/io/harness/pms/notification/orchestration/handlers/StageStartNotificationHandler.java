/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration.handlers;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.observers.NodeExecutionStartObserver;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.NodeExecution;
import io.harness.notification.PipelineEventType;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.notification.helper.NotificationHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class StageStartNotificationHandler implements AsyncInformObserver, NodeExecutionStartObserver {
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject NotificationHelper notificationHelper;
  @Inject NodeExecutionService nodeExecutionService;
  @Inject PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Override
  public void onNodeStart(NodeStartInfo nodeStartInfo) {
    NodeExecution nodeExecution = nodeStartInfo.getNodeExecution();
    if (nodeExecution == null || notificationHelper.shouldNotifyAfterGraphGen(nodeExecution.getAccountId())) {
      return;
    }
    Ambiance ambiance = nodeExecutionService.getAmbiance(nodeStartInfo.getNodeExecution());
    if (OrchestrationUtils.isPipelineNode(nodeExecution)) {
      if (!(pmsFeatureFlagHelper.isEnabled(
                AmbianceUtils.getAccountId(ambiance), FeatureName.PIPE_DISABLE_PIPELINE_NOTIFICATIONS_ON_ROLLBACK)
              && AmbianceUtils.isPipelineRollbackExecution(ambiance))) {
        notificationHelper.sendNotification(
            ambiance, PipelineEventType.PIPELINE_START, nodeExecution, nodeStartInfo.getUpdatedTs());
      }
    }
    if (OrchestrationUtils.isStageNode(nodeExecution)) {
      notificationHelper.sendNotification(
          ambiance, PipelineEventType.STAGE_START, nodeExecution, nodeStartInfo.getUpdatedTs());
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
