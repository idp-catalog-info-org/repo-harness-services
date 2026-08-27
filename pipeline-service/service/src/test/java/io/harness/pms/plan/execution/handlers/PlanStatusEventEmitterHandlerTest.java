/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.OM;
import static io.harness.rule.OwnerRule.SHALINI;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.PipelineServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.event.dispatch.EventEnvelopePublisher;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.notification.orchestration.observers.NotificationObserver;
import io.harness.pms.triggers.systemevents.SystemEventPublisher;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class PlanStatusEventEmitterHandlerTest extends PipelineServiceTestBase {
  @Mock PlanExecutionService planExecutionService;
  @Mock EventEnvelopePublisher eventEnvelopePublisher;
  @Mock SystemEventPublisher systemEventPublisher;
  @InjectMocks @Spy PlanStatusEventEmitterHandler planStatusEventEmitterHandler;

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testOnPlanStatusUpdate() {
    NotificationObserver onSuccessObserver = new OnSuccessObserver();
    NotificationObserver onPauseObserver = new OnPauseObserve();
    NotificationObserver onFailureObserver = new OnFailureObserver();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("planExecutionId").build();

    doReturn(Status.SUCCEEDED).when(planExecutionService).getStatus("planExecutionId");
    planStatusEventEmitterHandler.getPlanExecutionSubject().register(onSuccessObserver);
    planStatusEventEmitterHandler.onPlanStatusUpdate(ambiance);

    doReturn(Status.IGNORE_FAILED).when(planExecutionService).getStatus("planExecutionId");
    planStatusEventEmitterHandler.onPlanStatusUpdate(ambiance);

    planStatusEventEmitterHandler.getPlanExecutionSubject().unregister(onSuccessObserver);

    planStatusEventEmitterHandler.getPlanExecutionSubject().register(onPauseObserver);
    doReturn(Status.PAUSED).when(planExecutionService).getStatus("planExecutionId");
    planStatusEventEmitterHandler.onPlanStatusUpdate(ambiance);

    planStatusEventEmitterHandler.getPlanExecutionSubject().unregister(onPauseObserver);

    planStatusEventEmitterHandler.getPlanExecutionSubject().register(onFailureObserver);
    doReturn(Status.FAILED).when(planExecutionService).getStatus("planExecutionId");
    StatusUtils.brokeStatuses().forEach(status -> planStatusEventEmitterHandler.onPlanStatusUpdate(ambiance));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testOnPlanStatusUpdate_SucceededPublishesEvent() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("planExecutionId").build();
    planStatusEventEmitterHandler.onPlanStatusUpdate(ambiance, Status.SUCCEEDED, null);
    verify(eventEnvelopePublisher).publishPipelineEvent(ambiance, Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testOnPlanStatusUpdate_FailedPublishesEvent() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("planExecutionId").build();
    planStatusEventEmitterHandler.onPlanStatusUpdate(ambiance, Status.FAILED, null);
    verify(eventEnvelopePublisher).publishPipelineEvent(ambiance, Status.FAILED);
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testOnPlanStatusUpdateForResume() {
    NotificationObserver notificationObserver = new OnResumedObserver();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("planExecutionId").build();

    planStatusEventEmitterHandler.getPlanExecutionSubject().register(notificationObserver);

    StatusUtils.userActionWaitingStatuses().forEach(
        previousStatus -> planStatusEventEmitterHandler.onPlanStatusUpdate(ambiance, Status.RUNNING, previousStatus));

    planStatusEventEmitterHandler.onPlanStatusUpdate(ambiance, Status.RUNNING, null);
    planStatusEventEmitterHandler.onPlanStatusUpdate(ambiance, Status.RUNNING, Status.QUEUED);
  }

  private static class OnSuccessObserver implements NotificationObserver {
    @Override
    public void onSuccess(Ambiance ambiance) {
      assert true;
    }

    @Override
    public void onPause(Ambiance ambiance) {
      assert false;
    }

    @Override
    public void onFailure(Ambiance ambiance) {
      assert false;
    }
  }
  private static class OnPauseObserve implements NotificationObserver {
    @Override
    public void onSuccess(Ambiance ambiance) {
      assert false;
    }

    @Override
    public void onPause(Ambiance ambiance) {
      assert true;
    }

    @Override
    public void onFailure(Ambiance ambiance) {
      assert false;
    }
  }

  private static class OnFailureObserver implements NotificationObserver {
    @Override
    public void onSuccess(Ambiance ambiance) {
      assert false;
    }

    @Override
    public void onPause(Ambiance ambiance) {
      assert false;
    }

    @Override
    public void onFailure(Ambiance ambiance) {
      assert true;
    }
  }

  private static class OnResumedObserver implements NotificationObserver {
    @Override
    public void onSuccess(Ambiance ambiance) {
      assert false;
    }

    @Override
    public void onPause(Ambiance ambiance) {
      assert false;
    }

    @Override
    public void onFailure(Ambiance ambiance) {
      assert false;
    }

    @Override
    public void onResumed(Ambiance ambiance) {
      assert true;
    }
  }
}
