/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.rule.OwnerRule.ALEKSANDAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTaskRequest;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.k8s.CIK8ExecuteStepTaskParams;
import io.harness.delegate.task.HDelegateTask;
import io.harness.delegate.task.SimpleHDelegateTask;
import io.harness.delegate.task.stepstatus.StepStatusTaskParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import software.wings.beans.TaskType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class CIDelegateTaskExecutorTest extends CIExecutionTestBase {
  private static final String TASK_ID = "123456";
  private static final String ACCOUNT_ID = "accountId";
  public static final String TASK_TYPE = "CI_LE_STATUS";
  public static final long TASK_TIMEOUT = 10 * 60L;
  public static final long TASK_TIMEOUT_12H = 12 * 60 * 60 * 1000L;
  @Mock private DelegateServiceGrpcClient delegateServiceGrpcClient;
  @Mock private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @InjectMocks private CIDelegateTaskExecutor ciDelegateTaskExecutor;

  private final StepStatusTaskParameters parameters = StepStatusTaskParameters.builder().build();
  private final DelegateTaskRequest expectedDelegateTaskRequest = DelegateTaskRequest.builder()
                                                                      .selectors(new ArrayList<>())
                                                                      .eligibleToExecuteDelegateIds(new ArrayList<>())
                                                                      .parked(true)
                                                                      .accountId(ACCOUNT_ID)
                                                                      .selectionLogsTrackingEnabled(true)
                                                                      .taskType(TASK_TYPE)
                                                                      .taskParameters(parameters)
                                                                      .executionTimeout(Duration.ofHours(12))
                                                                      .taskSetupAbstractions(new HashMap<>())
                                                                      .build();

  private final DelegateTaskRequest expectedDelegateTaskRequestWithEmptyParams =
      DelegateTaskRequest.builder()
          .parked(true)
          .accountId(ACCOUNT_ID)
          .taskType(TASK_TYPE)
          .taskParameters(null)
          .executionTimeout(Duration.ofHours(12))
          .taskSetupAbstractions(new HashMap<>())
          .build();

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldQueueTaskAndReturnTaskId() {
    HDelegateTask task = SimpleHDelegateTask.builder()
                             .accountId(ACCOUNT_ID)
                             .data(TaskData.builder()
                                       .async(true)
                                       .parked(true)
                                       .taskType(TASK_TYPE)
                                       .parameters(new Object[] {parameters})
                                       .timeout(TASK_TIMEOUT_12H)
                                       .build())
                             .setupAbstractions(new HashMap<>())
                             .build();

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().build());
    when(delegateServiceGrpcClient.submitAsyncTaskV2(eq(expectedDelegateTaskRequest), any(), any(), any()))
        .thenReturn(TASK_ID);

    String taskId =
        ciDelegateTaskExecutor.queueTask(new HashMap<>(), task, new ArrayList<>(), new ArrayList<>(), false, any());

    assertThat(taskId).isEqualTo(TASK_ID);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldQueueTaskWithoutTaskParams() {
    HDelegateTask task = SimpleHDelegateTask.builder()
                             .accountId(ACCOUNT_ID)
                             .data(TaskData.builder()
                                       .async(true)
                                       .parked(true)
                                       .taskType(TASK_TYPE)
                                       .parameters(null)
                                       .timeout(TASK_TIMEOUT)
                                       .build())
                             .setupAbstractions(new HashMap<>())
                             .build();

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().build());
    when(delegateServiceGrpcClient.submitAsyncTaskV2(any(), any(), any(), any())).thenReturn(TASK_ID);

    String taskId =
        ciDelegateTaskExecutor.queueTask(new HashMap<>(), task, new ArrayList<>(), new ArrayList<>(), false, "");

    assertThat(taskId).isEqualTo(TASK_ID);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldThrowOnNonTaskParamsRequestForQueueTask() {
    HDelegateTask task = SimpleHDelegateTask.builder()
                             .accountId(ACCOUNT_ID)
                             .data(TaskData.builder()
                                       .async(true)
                                       .parked(true)
                                       .taskType(TASK_TYPE)
                                       .parameters(new Object[] {"Wrong type"})
                                       .timeout(TASK_TIMEOUT)
                                       .build())
                             .setupAbstractions(new HashMap<>())
                             .build();

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().build());
    when(delegateServiceGrpcClient.submitAsyncTaskV2(
             eq(expectedDelegateTaskRequestWithEmptyParams), any(), any(), any()))
        .thenReturn(TASK_ID);

    assertThatThrownBy(

        () -> ciDelegateTaskExecutor.queueTask(new HashMap<>(), task, new ArrayList<>(), new ArrayList<>(), false, ""))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Task Execution not supported for type");
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldReturnFalseOnAbortTask() {
    assertThat(ciDelegateTaskExecutor.abortTask(new HashMap<>(), TASK_ID)).isFalse();
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void getDelegateTaskDataForExecuteStep_whenSingleTaskTrue_setsCiExecuteStepV2TaskType() {
    Ambiance ambiance = mock(Ambiance.class);
    when(ambiance.getExpressionFunctorToken()).thenReturn(7L);
    CIK8ExecuteStepTaskParams params = CIK8ExecuteStepTaskParams.builder().build();

    TaskData taskData = ciDelegateTaskExecutor.getDelegateTaskDataForExecuteStep(ambiance, TASK_TIMEOUT, params, true);

    assertThat(taskData.getTaskType()).isEqualTo(TaskType.CI_EXECUTE_STEP_V2.name());
    assertThat(taskData.isAsync()).isTrue();
    assertThat(taskData.isParked()).isFalse();
    assertThat(taskData.getParameters()[0]).isSameAs(params);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void getDelegateTaskDataForExecuteStep_whenSingleTaskFalse_setsContainerExecuteStepTaskType() {
    Ambiance ambiance = mock(Ambiance.class);
    when(ambiance.getExpressionFunctorToken()).thenReturn(9L);
    CIK8ExecuteStepTaskParams params = CIK8ExecuteStepTaskParams.builder().build();

    TaskData taskData = ciDelegateTaskExecutor.getDelegateTaskDataForExecuteStep(ambiance, TASK_TIMEOUT, params, false);

    assertThat(taskData.getTaskType()).isEqualTo(TaskType.CONTAINER_EXECUTE_STEP.name());
    assertThat(taskData.getParameters()[0]).isSameAs(params);
  }
}
