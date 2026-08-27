/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.tasks;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.DEVANSH;
import static io.harness.rule.OwnerRule.VINICIUS;

import static software.wings.beans.TaskType.SCM_GIT_REF_TASK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.delegate.AccountId;
import io.harness.delegate.CancelTaskResponse;
import io.harness.delegate.DelegateServiceGrpc;
import io.harness.delegate.DelegateTaskSpec;
import io.harness.delegate.ScheduleTaskResponse;
import io.harness.delegate.ScheduleTaskServiceGrpc;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.SubmitTaskResponse;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.TaskId;
import io.harness.delegate.TaskMode;
import io.harness.delegate.TaskType;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.pms.contracts.execution.tasks.DelegateTaskRequest;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.rule.Owner;
import io.harness.runnercommons.cgi.utils.UnifiedConditionChecker;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.protobuf.Timestamp;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.slf4j.MDC;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
@PowerMockIgnore({"javax.security.*", "javax.net.*"})
public class NgDelegate2TaskExecutorTest extends CategoryTest {
  @Mock private DelegateServiceGrpc.DelegateServiceBlockingStub delegateServiceBlockingStub;
  @Mock private ScheduleTaskServiceGrpc.ScheduleTaskServiceBlockingStub scheduleTaskServiceBlockingStub;
  @Mock private DelegateServiceGrpcClient delegateServiceGrpcClient;
  @Mock private DelegateSyncService delegateSyncService;
  @Mock private DelegateAsyncService delegateAsyncService;
  @Mock private Supplier<DelegateCallbackToken> tokenSupplier;
  @Mock UnifiedConditionChecker unifiedConditionChecker;

  @Inject @InjectMocks private NgDelegate2TaskExecutor ngDelegate2TaskExecutor;

  @Before
  public void setup() {
    when(delegateServiceBlockingStub.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
        .thenReturn(delegateServiceBlockingStub);
    when(unifiedConditionChecker.shouldUseUnifiedFlow(any(), anyBoolean())).thenReturn(false);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenQueueTask() {
    TaskRequest taskRequest =
        TaskRequest.newBuilder()
            .setDelegateTaskRequest(
                DelegateTaskRequest.newBuilder()
                    .setRequest(SubmitTaskRequest.newBuilder()
                                    .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                    .setDetails(TaskDetails.newBuilder().setMode(TaskMode.SYNC).build())
                                    .build())
                    .build())
            .build();

    assertThatThrownBy(() -> ngDelegate2TaskExecutor.queueTask(new HashMap<>(), taskRequest, Duration.ZERO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(String.format("DelegateTaskRequest Mode %s Not Supported", TaskMode.SYNC));
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenQueueTaskWithWrongTaskMode() {
    TaskRequest taskRequest = TaskRequest.newBuilder().setSkipTaskRequest(SkipTaskRequest.newBuilder().build()).build();

    assertThatThrownBy(() -> ngDelegate2TaskExecutor.queueTask(new HashMap<>(), taskRequest, Duration.ZERO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Task Request doesnt contain delegate Task Request");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldQueueTask() {
    String taskId = generateUuid();
    TaskRequest taskRequest =
        TaskRequest.newBuilder()
            .setDelegateTaskRequest(
                DelegateTaskRequest.newBuilder()
                    .setRequest(SubmitTaskRequest.newBuilder()
                                    .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                    .setDetails(TaskDetails.newBuilder().setMode(TaskMode.ASYNC).build())
                                    .build())
                    .build())
            .build();

    when(delegateServiceBlockingStub.submitTaskV2(any()))
        .thenReturn(SubmitTaskResponse.newBuilder()
                        .setTotalExpiry(Timestamp.newBuilder().setSeconds(30).build())
                        .setTaskId(TaskId.newBuilder().setId(taskId).build())
                        .build());
    doNothing().when(delegateAsyncService).setupTimeoutForTask(anyString(), anyLong(), anyLong());
    when(tokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(generateUuid()).build());

    String actualTaskId = ngDelegate2TaskExecutor.queueTask(new HashMap<>(), taskRequest, Duration.ZERO);

    assertThat(actualTaskId).isEqualTo(taskId);

    verify(delegateServiceBlockingStub).submitTaskV2(any());
    verify(delegateAsyncService).setupTimeoutForTask(anyString(), anyLong(), anyLong());
    verify(tokenSupplier).get();

    verifyNoMoreInteractions(delegateSyncService);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenExecuteTask() {
    TaskRequest taskRequest =
        TaskRequest.newBuilder()
            .setDelegateTaskRequest(
                DelegateTaskRequest.newBuilder()
                    .setRequest(SubmitTaskRequest.newBuilder()
                                    .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                    .setDetails(TaskDetails.newBuilder().setMode(TaskMode.ASYNC).build())
                                    .build())
                    .build())
            .build();

    assertThatThrownBy(() -> ngDelegate2TaskExecutor.executeTask(new HashMap<>(), taskRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(String.format("DelegateTaskRequest Mode %s Not Supported", TaskMode.ASYNC));
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenExecuteTaskWithWrongTaskMode() {
    TaskRequest taskRequest = TaskRequest.newBuilder().setSkipTaskRequest(SkipTaskRequest.newBuilder().build()).build();

    assertThatThrownBy(() -> ngDelegate2TaskExecutor.executeTask(new HashMap<>(), taskRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Task Request doesnt contain delegate Task Request");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldExecuteTask() {
    TaskRequest taskRequest = TaskRequest.newBuilder().setSkipTaskRequest(SkipTaskRequest.newBuilder().build()).build();

    assertThatThrownBy(() -> ngDelegate2TaskExecutor.executeTask(new HashMap<>(), taskRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Task Request doesnt contain delegate Task Request");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestExpireTask() {
    String taskId = generateUuid();

    TaskRequest taskRequest =
        TaskRequest.newBuilder()
            .setDelegateTaskRequest(
                DelegateTaskRequest.newBuilder()
                    .setRequest(SubmitTaskRequest.newBuilder()
                                    .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                    .setDetails(TaskDetails.newBuilder().setMode(TaskMode.SYNC).build())
                                    .build())
                    .build())
            .build();

    when(delegateServiceBlockingStub.submitTaskV2(any()))
        .thenReturn(SubmitTaskResponse.newBuilder()
                        .setTotalExpiry(Timestamp.newBuilder().setSeconds(30).build())
                        .setTaskId(TaskId.newBuilder().setId(taskId).build())
                        .build());
    when(delegateSyncService.waitForTask(anyString(), anyString(), any(), any())).thenReturn(new ResponseData() {});
    when(tokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(generateUuid()).build());

    ResponseData responseData = ngDelegate2TaskExecutor.executeTask(new HashMap<>(), taskRequest);
    assertThat(responseData).isNotNull();

    verify(delegateServiceBlockingStub).submitTaskV2(any());
    verify(delegateSyncService).waitForTask(anyString(), anyString(), any(), any());
    verify(tokenSupplier).get();
    verifyNoMoreInteractions(delegateAsyncService);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void abortTask() {
    String taskId = generateUuid();
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", generateUuid());

    when(delegateServiceBlockingStub.cancelTaskV2(any())).thenThrow(new RuntimeException("cancel failed"));

    boolean result = ngDelegate2TaskExecutor.abortTask(setupAbstractions, taskId);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void abortTask_SeedsMdcAccountIdAndClearsAfter() {
    String taskId = generateUuid();
    String accountId = generateUuid();
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", accountId);

    MDC.remove("accountId");
    when(delegateServiceBlockingStub.cancelTaskV2(any())).thenThrow(new RuntimeException("cancel failed"));

    ngDelegate2TaskExecutor.abortTask(setupAbstractions, taskId);

    assertThat(MDC.get("accountId")).isNull();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void abortTask_ReturnsTrueOnSuccess() {
    String taskId = generateUuid();
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", generateUuid());

    when(delegateServiceBlockingStub.cancelTaskV2(any())).thenReturn(CancelTaskResponse.newBuilder().build());

    boolean result = ngDelegate2TaskExecutor.abortTask(setupAbstractions, taskId);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void abortTask_NullAccountId_ReturnsFalse() {
    String taskId = generateUuid();
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", null);

    when(delegateServiceBlockingStub.cancelTaskV2(any())).thenThrow(new RuntimeException("cancel failed"));

    boolean result = ngDelegate2TaskExecutor.abortTask(setupAbstractions, taskId);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void abortTask_EmptyAccountId_ReturnsFalse() {
    String taskId = generateUuid();
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "");

    when(delegateServiceBlockingStub.cancelTaskV2(any())).thenThrow(new RuntimeException("cancel failed"));

    boolean result = ngDelegate2TaskExecutor.abortTask(setupAbstractions, taskId);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void shouldQueueTaskForScmGitRefTask() {
    String taskId = generateUuid();
    TaskRequest taskRequest =
        TaskRequest.newBuilder()
            .setDelegateTaskRequest(
                DelegateTaskRequest.newBuilder()
                    .setRequest(
                        SubmitTaskRequest.newBuilder()
                            .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                            .setDetails(TaskDetails.newBuilder()
                                            .setMode(TaskMode.ASYNC)
                                            .setType(TaskType.newBuilder().setType(SCM_GIT_REF_TASK.name()).build())
                                            .build())
                            .build())
                    .build())
            .build();

    when(delegateServiceBlockingStub.submitTaskV2(any()))
        .thenReturn(SubmitTaskResponse.newBuilder()
                        .setTotalExpiry(Timestamp.newBuilder().setSeconds(30).build())
                        .setTaskId(TaskId.newBuilder().setId(taskId).build())
                        .build());
    doNothing().when(delegateAsyncService).setupTimeoutForTask(anyString(), anyLong(), anyLong());
    when(tokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(generateUuid()).build());

    String actualTaskId = ngDelegate2TaskExecutor.queueTask(new HashMap<>(), taskRequest, Duration.ZERO);

    assertThat(actualTaskId).isEqualTo(taskId);

    verify(delegateServiceBlockingStub).submitTaskV2(any());
    verify(delegateAsyncService).setupTimeoutForTask(anyString(), anyLong(), anyLong());
    verify(tokenSupplier).get();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldQueueUnifiedTaskForScmGitRefTask() {
    String accountId = generateUuid();
    String taskId = generateUuid();
    TaskRequest taskRequest =
        TaskRequest.newBuilder()
            .setDelegateTaskRequest(
                DelegateTaskRequest.newBuilder()
                    .setRequest(
                        SubmitTaskRequest.newBuilder()
                            .setAccountId(AccountId.newBuilder().setId(accountId).build())
                            .setDetails(TaskDetails.newBuilder()
                                            .setMode(TaskMode.ASYNC)
                                            .setType(TaskType.newBuilder().setType(SCM_GIT_REF_TASK.name()).build())
                                            .build())
                            .build())
                    .build())
            .build();

    when(unifiedConditionChecker.shouldUseUnifiedFlow(accountId, true)).thenReturn(true);
    when(delegateServiceGrpcClient.buildDelegateTaskSpecForUnifiedTask(any()))
        .thenReturn(DelegateTaskSpec.newBuilder().build());
    when(scheduleTaskServiceBlockingStub.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
        .thenReturn(scheduleTaskServiceBlockingStub);
    when(scheduleTaskServiceBlockingStub.submit(any()))
        .thenReturn(ScheduleTaskResponse.newBuilder().setTaskId(TaskId.newBuilder().setId(taskId).build()).build());
    doNothing().when(delegateAsyncService).setupTimeoutForTask(anyString(), anyLong(), anyLong());
    when(tokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(generateUuid()).build());

    String actualTaskId = ngDelegate2TaskExecutor.queueTask(new HashMap<>(), taskRequest, Duration.ZERO);

    assertThat(actualTaskId).isEqualTo(taskId);

    verify(scheduleTaskServiceBlockingStub).submit(any());
    verify(delegateAsyncService).setupTimeoutForTask(anyString(), anyLong(), anyLong());
    verify(tokenSupplier).get();
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void shouldDetectScmGitRefTaskType() {
    String taskId = generateUuid();
    TaskRequest taskRequest =
        TaskRequest.newBuilder()
            .setDelegateTaskRequest(
                DelegateTaskRequest.newBuilder()
                    .setRequest(
                        SubmitTaskRequest.newBuilder()
                            .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                            .setDetails(TaskDetails.newBuilder()
                                            .setMode(TaskMode.ASYNC)
                                            .setType(TaskType.newBuilder().setType(SCM_GIT_REF_TASK.name()).build())
                                            .build())
                            .build())
                    .build())
            .build();

    String taskType = taskRequest.getDelegateTaskRequest().getRequest().getDetails().getType().getType();
    assertThat(taskType).isEqualTo(SCM_GIT_REF_TASK.name());
    assertThat(SCM_GIT_REF_TASK.name().equals(taskType)).isTrue();
  }
}
