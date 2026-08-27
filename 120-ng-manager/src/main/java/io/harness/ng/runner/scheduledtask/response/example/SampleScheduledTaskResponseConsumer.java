/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.runner.scheduledtask.response.example;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.delegate.GetTaskStatusResponse;
import io.harness.delegate.ScheduledTaskFailureDetails;
import io.harness.delegate.ScheduledTaskLifecycleEvent;
import io.harness.delegate.ScheduledTaskLifecycleStatus;
import io.harness.delegate.ScheduledTaskResponse;
import io.harness.eventsframework.api.Consumer;
import io.harness.task.response.callback.TaskResponseConsumer;
import io.harness.task.response.callback.TaskResponseMessageListener;
import io.harness.task.response.grpc.TaskResponseGrpcClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

/**
 * Sample Consumer for scheduled task responses and lifecycle events with callbackToken = "scheduled_task_default".
 * Expects JSON-shaped payloads in {@link GetTaskStatusResponse#getData()} (runner / classic Redis response path via
 * {@code ExecuteTaskResponse}).
 *
 * Uses {@link TaskResponseMessageListener} which handles both:
 * 1. TaskStatusCallback (DELEGATE_TASK_RESPONSE) - individual task execution results
 * 2. ScheduledTaskLifecycleEvent (SCHEDULED_TASK_LIFECYCLE_EVENT) - task lifecycle changes (DISABLED, SUSPENDED)
 *
 * The consumer receives a unified {@link ScheduledTaskResponse} and can easily distinguish between
 * execution responses and lifecycle events.
 */
@Slf4j
public class SampleScheduledTaskResponseConsumer implements Runnable {
  private final TaskResponseConsumer taskResponseConsumer;
  private final ObjectMapper objectMapper;

  @Inject
  public SampleScheduledTaskResponseConsumer(@Named("scheduled_task_default_response_consumer") Consumer consumer,
      TaskResponseGrpcClient grpcClient, ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    TaskResponseMessageListener listener =
        new TaskResponseMessageListener(grpcClient, this::processScheduledTaskResponse);
    this.taskResponseConsumer = new TaskResponseConsumer(consumer, listener);
  }

  /**
   * Processes a unified scheduled task response.
   * Handles both execution responses and lifecycle events.
   *
   * @param response the unified response wrapper
   * @return true if processing was successful
   */
  private boolean processScheduledTaskResponse(ScheduledTaskResponse response) {
    try {
      if (response.hasExecutionResponse()) {
        return handleExecutionResponse(
            response.getAccountId(), response.getScheduledTaskId(), response.getExecutionResponse());
      } else if (response.hasLifecycleEvent()) {
        return handleLifecycleEvent(
            response.getAccountId(), response.getScheduledTaskId(), response.getLifecycleEvent());
      } else {
        log.warn("Received empty ScheduledTaskResponse with no event set");
        return false;
      }
    } catch (Exception e) {
      log.error("Failed to process scheduled task response", e);
      return false;
    }
  }

  /**
   * Handles individual task execution responses.
   */
  private boolean handleExecutionResponse(String accountId, String scheduledTaskId, GetTaskStatusResponse response) {
    String taskType = response.getTaskType();
    String taskId = response.getTaskId().getId();
    String status = response.getStatus().name();
    boolean hasData = !response.getData().isEmpty();

    log.info("=== SCHEDULED TASK EXECUTION RESPONSE ===");
    log.info("scheduledTaskId={}, taskId={}, accountId={}, taskType={}, status={}, hasData={}", scheduledTaskId, taskId,
        accountId, taskType, status, hasData);

    if (isNotEmpty(response.getError())) {
      log.info("error={}", response.getError());
    }

    if (hasData) {
      log.info("responseData:\n{}", deserializeJsonResponseData(response.getData().toByteArray()));
    }
    log.info("=== END SCHEDULED TASK RESPONSE ===");

    // Add your business logic here to handle the task response
    // For example: update database, trigger downstream processes, etc.

    return true;
  }

  /**
   * Handles scheduled task lifecycle events (DISABLED, SUSPENDED).
   */
  private boolean handleLifecycleEvent(String accountId, String scheduledTaskId, ScheduledTaskLifecycleEvent event) {
    ScheduledTaskLifecycleStatus status = event.getStatus();
    String eventMessage = event.getMessage();

    log.info("=== SCHEDULED TASK LIFECYCLE EVENT ===");
    log.info("scheduledTaskId={}, accountId={}, status={}, message={}", scheduledTaskId, accountId, status.name(),
        eventMessage);

    if (event.hasUpdatedAt()) {
      log.info("updatedAt={}", event.getUpdatedAt());
    }

    if (status == ScheduledTaskLifecycleStatus.SCHEDULED_TASK_LIFECYCLE_STATUS_SUSPENDED && event.hasFailureDetails()) {
      ScheduledTaskFailureDetails failureDetails = event.getFailureDetails();
      log.info("failureDetails: consecutiveFailures={}, totalFailures={}, lastFailureReason={}",
          failureDetails.getConsecutiveFailures(), failureDetails.getTotalFailures(),
          failureDetails.getLastFailureReason());
    }

    log.info("=== END LIFECYCLE EVENT ===");

    // Add your business logic here to handle lifecycle events
    // For example:
    // - DISABLED: Stop monitoring, notify stakeholders
    // - SUSPENDED: Attempt recovery

    return true;
  }

  private String deserializeJsonResponseData(byte[] data) {
    try {
      Object jsonObject = objectMapper.readValue(data, Object.class);
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
    } catch (Exception jsonException) {
      log.warn("Unable to deserialize response data as JSON", jsonException);
      return "";
    }
  }

  @Override
  public void run() {
    log.info("Starting scheduled task default response consumer");
    //    taskResponseConsumer.run();
  }
}
