/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.runner.scheduledtask.response.driftdetection;

import static io.harness.eventsframework.EventsFrameworkConstants.DRIFT_DETECTION_RESPONSE_CONSUMER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.task.response.callback.TaskResponseConsumer;
import io.harness.task.response.callback.TaskResponseMessageListener;
import io.harness.task.response.grpc.TaskResponseGrpcClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class DriftDetectionResponseConsumer implements Runnable {
  private final TaskResponseConsumer taskResponseConsumer;

  @Inject
  public DriftDetectionResponseConsumer(@Named(DRIFT_DETECTION_RESPONSE_CONSUMER) Consumer consumer,
      TaskResponseGrpcClient grpcClient, DriftDetectionScheduledTaskHandler responseHandler) {
    TaskResponseMessageListener listener =
        new TaskResponseMessageListener(grpcClient, responseHandler::processScheduledTaskResponse);
    this.taskResponseConsumer = new TaskResponseConsumer(consumer, listener);
  }

  @Override
  public void run() {
    log.info("Starting drift detection response consumer");
    taskResponseConsumer.run();
  }
}
