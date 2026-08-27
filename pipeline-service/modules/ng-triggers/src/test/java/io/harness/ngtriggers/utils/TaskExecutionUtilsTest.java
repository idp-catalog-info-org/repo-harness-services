/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ngtriggers.utils;

import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DelegateTaskRequest;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskExecutionUtilsTest extends CategoryTest {
  @Mock private DelegateServiceGrpcClient delegateServiceGrpcClient;

  @Mock private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;

  @Mock private DelegateCallbackToken delegateCallbackToken;

  @Mock private DelegateTaskRequest delegateTaskRequest;

  @Mock private DelegateResponseData delegateResponseData;

  @InjectMocks private TaskExecutionUtils taskExecutionUtils;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    taskExecutionUtils = new TaskExecutionUtils(delegateServiceGrpcClient, delegateCallbackTokenSupplier);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExecuteSyncTask_Success() {
    // Given
    String taskId = "test-task-id";
    String taskType = "TEST_TASK";
    Pair<String, DelegateResponseData> responseDataPair = Pair.of(taskId, delegateResponseData);

    when(delegateCallbackTokenSupplier.get()).thenReturn(delegateCallbackToken);
    when(delegateTaskRequest.getTaskType()).thenReturn(taskType);
    doReturn(responseDataPair).when(delegateServiceGrpcClient).executeSyncTaskReturningResponseDataV2(any(), any());
    // When
    ResponseData result = taskExecutionUtils.executeSyncTask(delegateTaskRequest);
    // Then
    assertThat(result).isEqualTo(delegateResponseData);
    verify(delegateCallbackTokenSupplier).get();
    verify(delegateServiceGrpcClient)
        .executeSyncTaskReturningResponseDataV2(eq(delegateTaskRequest), eq(delegateCallbackToken));
    verify(delegateTaskRequest).getTaskType();
  }
}
