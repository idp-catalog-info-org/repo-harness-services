/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.helper;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.entity.eventlog.NotificationEventLog;
import io.harness.notification.PipelineEventType;
import io.harness.repositories.notificationEventLog.NotificationEventLogRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class NotificationEventsHelper {
  @Inject NotificationEventLogRepository notificationEventLogRepository;

  public boolean isNotificationEventAlreadySent(
      String planExecutionId, String nodeExecutionId, PipelineEventType pipelineEventType) {
    return notificationEventLogRepository.checkIfEventExists(planExecutionId, nodeExecutionId, pipelineEventType);
  }

  // returns all notifications sent for a planExecutionId or planExecutionId, nodeExecutionId combination
  public List<NotificationEventLog> getNotificationsSent(String planExecutionId, List<String> nodeExecutionIds) {
    return notificationEventLogRepository.getNotificationsSent(planExecutionId, nodeExecutionIds);
  }

  public void markNotificationAsSent(
      String planExecutionId, String nodeExecutionId, PipelineEventType pipelineEventType) {
    notificationEventLogRepository.save(NotificationEventLog.builder()
                                            .createdAt(System.currentTimeMillis())
                                            .nodeExecutionId(nodeExecutionId)
                                            .planExecutionId(planExecutionId)
                                            .pipelineEventType(pipelineEventType)
                                            .build());
  }

  public Optional<NotificationEventLog> findMostRecentByEventType(
      String planExecutionId, PipelineEventType pipelineEventType) {
    return notificationEventLogRepository.findMostRecentByEventType(planExecutionId, pipelineEventType);
  }
}
