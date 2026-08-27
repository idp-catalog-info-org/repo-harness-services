/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.runner.scheduledtask.response.instancesync;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.GetTaskStatusResponse;
import io.harness.delegate.ScheduledTaskFailureDetails;
import io.harness.delegate.ScheduledTaskLifecycleEvent;
import io.harness.delegate.ScheduledTaskLifecycleStatus;
import io.harness.delegate.ScheduledTaskResponse;
import io.harness.delegate.Status;
import io.harness.service.instancesync.unified.UnifiedInstanceSyncService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler for processing unified instance sync scheduled task responses.
 * Extracted from consumer for better testability and separation of concerns.
 */
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_INSTANCE_SYNC})
public class UnifiedInstanceSyncResponseHandler {
  private final UnifiedInstanceSyncService unifiedInstanceSyncService;

  @Inject
  public UnifiedInstanceSyncResponseHandler(UnifiedInstanceSyncService unifiedInstanceSyncService) {
    this.unifiedInstanceSyncService = unifiedInstanceSyncService;
  }

  /**
   * Processes a unified scheduled task response.
   * Handles both execution responses and lifecycle events.
   *
   * @param response the unified response wrapper
   * @return true if processing was successful
   */
  public boolean processScheduledTaskResponse(ScheduledTaskResponse response) {
    String scheduledTaskId = response.getScheduledTaskId();
    String accountId = response.getAccountId();

    try {
      if (response.hasExecutionResponse()) {
        return handleExecutionResponse(accountId, scheduledTaskId, response.getExecutionResponse());
      } else if (response.hasLifecycleEvent()) {
        return handleLifecycleEvent(accountId, scheduledTaskId, response.getLifecycleEvent());
      } else {
        log.warn("Received empty ScheduledTaskResponse with no event set for scheduledTaskId: {}", scheduledTaskId);
        return true;
      }
    } catch (Exception e) {
      log.error("Failed to process scheduled task response for scheduledTaskId: {}", scheduledTaskId, e);
      return true;
    }
  }

  /**
   * Handles individual task execution responses.
   */
  boolean handleExecutionResponse(String accountId, String scheduledTaskId, GetTaskStatusResponse response) {
    Status status = response.getStatus();
    String taskId = response.getTaskId().getId();

    log.debug("Received instance sync execution response - scheduledTaskId: {}, taskId: {}, accountId: {}, status: {}",
        scheduledTaskId, taskId, accountId, status.name());

    if (status == Status.SUCCESS) {
      unifiedInstanceSyncService.handleScheduledTaskResponse(scheduledTaskId, response);
    } else {
      String errorMessage = response.getError();
      log.warn("Instance sync task failed - scheduledTaskId: {}, status: {}, error: {}", scheduledTaskId, status.name(),
          errorMessage);
      unifiedInstanceSyncService.handleScheduledTaskError(scheduledTaskId,
          new RuntimeException("Task failed with status: " + status.name() + ", error: " + errorMessage));
    }

    return true;
  }

  /**
   * Handles scheduled task lifecycle events (DISABLED, SUSPENDED).
   */
  boolean handleLifecycleEvent(String accountId, String scheduledTaskId, ScheduledTaskLifecycleEvent event) {
    ScheduledTaskLifecycleStatus status = event.getStatus();
    String eventMessage = event.getMessage();
    // TODO [UNIFIED-SCHEDULED-TASK] delegate to unified instance sync service to handle lifecycle event

    log.debug("Received instance sync lifecycle event - scheduledTaskId: {}, accountId: {}, status: {}, message: {}",
        scheduledTaskId, accountId, status.name(), eventMessage);

    if (status == ScheduledTaskLifecycleStatus.SCHEDULED_TASK_LIFECYCLE_STATUS_SUSPENDED && event.hasFailureDetails()) {
      ScheduledTaskFailureDetails failureDetails = event.getFailureDetails();
      log.debug("Instance sync task suspended - scheduledTaskId: {}, consecutiveFailures: {}, totalFailures: {}, "
              + "lastFailureReason: {}",
          scheduledTaskId, failureDetails.getConsecutiveFailures(), failureDetails.getTotalFailures(),
          failureDetails.getLastFailureReason());
    }

    if (status == ScheduledTaskLifecycleStatus.SCHEDULED_TASK_LIFECYCLE_STATUS_DISABLED) {
      log.debug("Instance sync task disabled (terminal) - scheduledTaskId: {}", scheduledTaskId);
    }

    return true;
  }
}
