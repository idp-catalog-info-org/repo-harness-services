/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.runner.scheduledtask.response.instancesync;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.GetTaskStatusResponse;
import io.harness.delegate.ScheduledTaskFailureDetails;
import io.harness.delegate.ScheduledTaskLifecycleEvent;
import io.harness.delegate.ScheduledTaskLifecycleStatus;
import io.harness.delegate.ScheduledTaskResponse;
import io.harness.delegate.Status;
import io.harness.delegate.TaskId;
import io.harness.rule.Owner;
import io.harness.service.instancesync.unified.UnifiedInstanceSyncService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UnifiedInstanceSyncResponseHandlerTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "testAccountId";
  private static final String TEST_SCHEDULED_TASK_ID = "testScheduledTaskId";
  private static final String TEST_TASK_ID = "testTaskId";
  private static final String TEST_ERROR_MESSAGE = "Task execution failed";

  @Mock private UnifiedInstanceSyncService unifiedInstanceSyncService;

  private UnifiedInstanceSyncResponseHandler handler;

  @Before
  public void setup() {
    handler = new UnifiedInstanceSyncResponseHandler(unifiedInstanceSyncService);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testProcessScheduledTaskResponse_executionResponseSuccess_callsHandleScheduledTaskResponse() {
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.SUCCESS)
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(unifiedInstanceSyncService).handleScheduledTaskResponse(eq(TEST_SCHEDULED_TASK_ID), eq(executionResponse));
    verify(unifiedInstanceSyncService, never()).handleScheduledTaskError(anyString(), any());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testProcessScheduledTaskResponse_executionResponseFailure_callsHandleScheduledTaskError() {
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.FAILURE)
                                                  .setError(TEST_ERROR_MESSAGE)
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(unifiedInstanceSyncService, never()).handleScheduledTaskResponse(anyString(), any());
    verify(unifiedInstanceSyncService)
        .handleScheduledTaskError(eq(TEST_SCHEDULED_TASK_ID), any(RuntimeException.class));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testProcessScheduledTaskResponse_executionResponseTimeout_callsHandleScheduledTaskError() {
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.TIMEOUT)
                                                  .setError("Task timed out")
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(unifiedInstanceSyncService, never()).handleScheduledTaskResponse(anyString(), any());
    verify(unifiedInstanceSyncService)
        .handleScheduledTaskError(eq(TEST_SCHEDULED_TASK_ID), any(RuntimeException.class));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testProcessScheduledTaskResponse_lifecycleEventSuspended_returnsTrue() {
    ScheduledTaskFailureDetails failureDetails = ScheduledTaskFailureDetails.newBuilder()
                                                     .setConsecutiveFailures(5)
                                                     .setTotalFailures(10)
                                                     .setLastFailureReason("Connection timeout")
                                                     .build();

    ScheduledTaskLifecycleEvent lifecycleEvent =
        ScheduledTaskLifecycleEvent.newBuilder()
            .setStatus(ScheduledTaskLifecycleStatus.SCHEDULED_TASK_LIFECYCLE_STATUS_SUSPENDED)
            .setMessage("Task suspended due to consecutive failures")
            .setFailureDetails(failureDetails)
            .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setLifecycleEvent(lifecycleEvent)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(unifiedInstanceSyncService, never()).handleScheduledTaskResponse(anyString(), any());
    verify(unifiedInstanceSyncService, never()).handleScheduledTaskError(anyString(), any());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testProcessScheduledTaskResponse_lifecycleEventDisabled_returnsTrue() {
    ScheduledTaskLifecycleEvent lifecycleEvent =
        ScheduledTaskLifecycleEvent.newBuilder()
            .setStatus(ScheduledTaskLifecycleStatus.SCHEDULED_TASK_LIFECYCLE_STATUS_DISABLED)
            .setMessage("Task disabled by user")
            .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setLifecycleEvent(lifecycleEvent)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(unifiedInstanceSyncService, never()).handleScheduledTaskResponse(anyString(), any());
    verify(unifiedInstanceSyncService, never()).handleScheduledTaskError(anyString(), any());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testProcessScheduledTaskResponse_emptyResponse_returnsTrue() {
    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(unifiedInstanceSyncService, never()).handleScheduledTaskResponse(anyString(), any());
    verify(unifiedInstanceSyncService, never()).handleScheduledTaskError(anyString(), any());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testProcessScheduledTaskResponse_serviceThrowsException_returnsTrue() {
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.SUCCESS)
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    doThrow(new RuntimeException("Service unavailable"))
        .when(unifiedInstanceSyncService)
        .handleScheduledTaskResponse(anyString(), any());

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testProcessScheduledTaskResponse_lifecycleEventSuspendedWithoutFailureDetails_returnsTrue() {
    ScheduledTaskLifecycleEvent lifecycleEvent =
        ScheduledTaskLifecycleEvent.newBuilder()
            .setStatus(ScheduledTaskLifecycleStatus.SCHEDULED_TASK_LIFECYCLE_STATUS_SUSPENDED)
            .setMessage("Task suspended")
            .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setLifecycleEvent(lifecycleEvent)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
  }
}
