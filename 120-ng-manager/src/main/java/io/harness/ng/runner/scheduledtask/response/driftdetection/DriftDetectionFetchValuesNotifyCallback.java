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
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class DriftDetectionFetchValuesNotifyCallback implements NotifyCallbackWithErrorHandling {
  @Transient @Inject transient DriftDetectionScheduledTaskHandler handler;

  private final String accountId;
  private final String scheduledTaskId;
  private final String parentUniqueId;
  private final String entityId;
  private final List<String> inlineValuesContent;
  private final Map<String, String> taskSetupAbstractions;

  public DriftDetectionFetchValuesNotifyCallback(String accountId, String scheduledTaskId, String parentUniqueId,
      String entityId, List<String> inlineValuesContent, Map<String, String> taskSetupAbstractions) {
    this.accountId = accountId;
    this.scheduledTaskId = scheduledTaskId;
    this.parentUniqueId = parentUniqueId;
    this.entityId = entityId;
    this.inlineValuesContent = inlineValuesContent;
    this.taskSetupAbstractions = taskSetupAbstractions;
  }

  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    try {
      ResponseData responseData = response.values().iterator().next().get();
      handler.onFetchValuesComplete(accountId, scheduledTaskId, parentUniqueId, entityId, inlineValuesContent,
          taskSetupAbstractions, responseData);
    } catch (Exception e) {
      log.error("Error in DriftDetectionFetchValuesNotifyCallback for entityId: {}", entityId, e);
    }
  }
}
