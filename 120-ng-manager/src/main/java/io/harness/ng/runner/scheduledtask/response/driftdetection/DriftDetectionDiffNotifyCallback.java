/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.runner.scheduledtask.response.driftdetection;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class DriftDetectionDiffNotifyCallback implements NotifyCallbackWithErrorHandling {
  @Transient @Inject transient DriftDetectionScheduledTaskHandler handler;

  private final String accountId;
  private final String scheduledTaskId;
  private final String parentUniqueId;
  private final String entityId;
  private final String sourceChecksum;

  public DriftDetectionDiffNotifyCallback(
      String accountId, String scheduledTaskId, String parentUniqueId, String entityId, String sourceChecksum) {
    this.accountId = accountId;
    this.scheduledTaskId = scheduledTaskId;
    this.parentUniqueId = parentUniqueId;
    this.entityId = entityId;
    this.sourceChecksum = sourceChecksum;
  }

  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    try {
      ResponseData responseData = response.values().iterator().next().get();
      handler.onDiffComplete(accountId, scheduledTaskId, sourceChecksum, responseData);
    } catch (Exception e) {
      log.error("Error in DriftDetectionDiffNotifyCallback for entityId: {}", entityId, e);
    }
  }
}
