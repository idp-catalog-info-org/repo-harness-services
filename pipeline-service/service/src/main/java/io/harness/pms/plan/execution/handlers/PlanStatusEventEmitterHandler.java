/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.PlanStatusUpdateObserver;
import io.harness.ngtriggers.beans.source.systemevents.SystemEventType;
import io.harness.observer.AsyncInformObserver;
import io.harness.observer.Subject;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.event.dispatch.EventEnvelopePublisher;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.notification.orchestration.observers.NotificationObserver;
import io.harness.pms.triggers.systemevents.SystemEventPublisher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.Getter;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PlanStatusEventEmitterHandler implements AsyncInformObserver, PlanStatusUpdateObserver {
  private final ExecutorService executorService;
  private final EventEnvelopePublisher eventEnvelopePublisher;
  private final SystemEventPublisher systemEventPublisher;

  @Getter private final Subject<NotificationObserver> planExecutionSubject = new Subject<>();

  @Inject
  public PlanStatusEventEmitterHandler(@Named("PipelineExecutorService") ExecutorService executorService,
      EventEnvelopePublisher eventEnvelopePublisher, SystemEventPublisher systemEventPublisher) {
    this.executorService = executorService;
    this.eventEnvelopePublisher = eventEnvelopePublisher;
    this.systemEventPublisher = systemEventPublisher;
  }

  @Override
  public void onPlanStatusUpdate(Ambiance ambiance) {}

  @Override
  public void onPlanStatusUpdate(Ambiance ambiance, Status currentStatus, Status previousStatus) {
    if (currentStatus == Status.SUCCEEDED || currentStatus == Status.IGNORE_FAILED
        || currentStatus == Status.PASSED_WITH_WARNING) {
      planExecutionSubject.fireInform(NotificationObserver::onSuccess, ambiance);
      eventEnvelopePublisher.publishPipelineEvent(ambiance, currentStatus);
      systemEventPublisher.publish(ambiance, SystemEventType.PIPELINE_SUCCESS);
    } else if (StatusUtils.brokeStatuses().contains(currentStatus)) {
      planExecutionSubject.fireInform(NotificationObserver::onFailure, ambiance);
      eventEnvelopePublisher.publishPipelineEvent(ambiance, currentStatus);
      systemEventPublisher.publish(ambiance, SystemEventType.PIPELINE_FAILURE);
    } else if (currentStatus == Status.PAUSED) {
      planExecutionSubject.fireInform(NotificationObserver::onPause, ambiance);
    } else if (currentStatus == Status.RUNNING && previousStatus != null
        && StatusUtils.userActionWaitingStatuses().contains(previousStatus)) {
      planExecutionSubject.fireInform(NotificationObserver::onResumed, ambiance);
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
