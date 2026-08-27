/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.runner.scheduledtask.response.artifactpolling;

import static io.harness.eventsframework.EventsFrameworkConstants.UNIFIED_ARTIFACT_POLLING_RESPONSE_CONSUMER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.Consumer;
import io.harness.task.response.callback.TaskResponseConsumer;
import io.harness.task.response.callback.TaskResponseMessageListener;
import io.harness.task.response.grpc.TaskResponseGrpcClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumer for artifact polling scheduled task responses and lifecycle events.
 * CallbackToken: "unified_artifact_polling"
 *
 * Uses {@link TaskResponseMessageListener} which handles both:
 * 1. TaskStatusCallback (DELEGATE_TASK_RESPONSE) - individual task execution results
 * 2. ScheduledTaskLifecycleEvent (SCHEDULED_TASK_LIFECYCLE_EVENT) - task lifecycle changes (DISABLED, SUSPENDED)
 */
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
public class ArtifactPollingResponseConsumer implements Runnable {
  private final TaskResponseConsumer taskResponseConsumer;

  @Inject
  public ArtifactPollingResponseConsumer(@Named(UNIFIED_ARTIFACT_POLLING_RESPONSE_CONSUMER) Consumer consumer,
      TaskResponseGrpcClient grpcClient, ArtifactPollingScheduledTaskHandler responseHandler) {
    TaskResponseMessageListener listener =
        new TaskResponseMessageListener(grpcClient, responseHandler::processScheduledTaskResponse);
    this.taskResponseConsumer = new TaskResponseConsumer(consumer, listener);
  }

  @Override
  public void run() {
    log.info("Starting artifact polling response consumer");
    taskResponseConsumer.run();
  }
}
