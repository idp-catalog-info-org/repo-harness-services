/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.NEGI;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.app.beans.entities.CIResourceCleanup;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.taskrunner.TaskRunnerTaskResponse;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import dev.morphia.query.Query;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CICleanupTaskNotifyCallbackTest extends CIExecutionTestBase {
  @Mock private HPersistence persistence;
  @Mock private Query<CIResourceCleanup> mockQuery;
  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private CILogServiceUtils ciLogServiceUtils;
  @InjectMocks private CICleanupTaskNotifyCallback ciCleanupTaskNotifyCallback;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testNotifyCallbackK8Success() {
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("taskID",
        () -> K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
    when(serializedResponseDataHelper.deserialize(responseSupplier.get("taskID").get())).thenCallRealMethod();
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    when(persistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
    ciCleanupTaskNotifyCallback.notify(responseSupplier);
    verify(persistence, times(1)).delete((Query<PersistentEntity>) any());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testNotifyCallbackK8Failure() {
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("taskID",
        () -> K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build());
    when(serializedResponseDataHelper.deserialize(responseSupplier.get("taskID").get())).thenCallRealMethod();
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    ciCleanupTaskNotifyCallback.notify(responseSupplier);
    verify(persistence, times(0)).delete((Query<PersistentEntity>) any());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testNotifyCallbackVMSuccess() {
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("taskID",
        () -> VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
    when(serializedResponseDataHelper.deserialize(responseSupplier.get("taskID").get()))
        .thenReturn(responseSupplier.get("taskID").get());
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    when(persistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
    ciCleanupTaskNotifyCallback.notify(responseSupplier);
    verify(persistence, times(1)).delete((Query<PersistentEntity>) any());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testNotifyCallbackVMFailure() {
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("taskID",
        () -> VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build());
    when(serializedResponseDataHelper.deserialize(responseSupplier.get("taskID").get()))
        .thenReturn(responseSupplier.get("taskID").get());
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    ciCleanupTaskNotifyCallback.notify(responseSupplier);
    verify(persistence, times(0)).delete((Query<PersistentEntity>) any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNotifyCallback_whenLeLogKeyPresent_shouldCloseLogStream() {
    CICleanupTaskNotifyCallback callback = CICleanupTaskNotifyCallback.builder()
                                               .persistence(persistence)
                                               .serializedResponseDataHelper(serializedResponseDataHelper)
                                               .ciLogServiceUtils(ciLogServiceUtils)
                                               .stageExecutionID("stageExecId")
                                               .accountID("accountId")
                                               .planExecutionID("planExecId")
                                               .leLogKey("le-log-key")
                                               .build();
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("taskID",
        () -> K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
    when(serializedResponseDataHelper.deserialize(any(ResponseData.class))).thenCallRealMethod();
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    when(persistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
    doNothing().when(ciLogServiceUtils).closeLogStream(eq("accountId"), eq("le-log-key"), eq(true), eq(false));
    callback.notify(responseSupplier);
    verify(ciLogServiceUtils, times(1)).closeLogStream(eq("accountId"), eq("le-log-key"), eq(true), eq(false));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNotifyCallback_whenLeLogKeyPresent_andCloseThrows_shouldHandleException() {
    CICleanupTaskNotifyCallback callback = CICleanupTaskNotifyCallback.builder()
                                               .persistence(persistence)
                                               .serializedResponseDataHelper(serializedResponseDataHelper)
                                               .ciLogServiceUtils(ciLogServiceUtils)
                                               .stageExecutionID("stageExecId")
                                               .accountID("accountId")
                                               .planExecutionID("planExecId")
                                               .leLogKey("le-log-key")
                                               .build();
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("taskID",
        () -> K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
    when(serializedResponseDataHelper.deserialize(any(ResponseData.class))).thenCallRealMethod();
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    when(persistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
    doThrow(new RuntimeException("close failed"))
        .when(ciLogServiceUtils)
        .closeLogStream(eq("accountId"), eq("le-log-key"), eq(true), eq(false));
    callback.notify(responseSupplier);
    verify(ciLogServiceUtils, times(1)).closeLogStream(eq("accountId"), eq("le-log-key"), eq(true), eq(false));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNotifyCallback_whenMemoryMetricsLogKeyPresent_shouldCloseLogStream() {
    CICleanupTaskNotifyCallback callback = CICleanupTaskNotifyCallback.builder()
                                               .persistence(persistence)
                                               .serializedResponseDataHelper(serializedResponseDataHelper)
                                               .ciLogServiceUtils(ciLogServiceUtils)
                                               .stageExecutionID("stageExecId")
                                               .accountID("accountId")
                                               .planExecutionID("planExecId")
                                               .memoryMetricsLogKey("memory-metrics-key")
                                               .build();
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("taskID",
        () -> K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
    when(serializedResponseDataHelper.deserialize(any(ResponseData.class))).thenCallRealMethod();
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    when(persistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
    doNothing().when(ciLogServiceUtils).closeLogStream(eq("accountId"), eq("memory-metrics-key"), eq(true), eq(false));
    callback.notify(responseSupplier);
    verify(ciLogServiceUtils, times(1)).closeLogStream(eq("accountId"), eq("memory-metrics-key"), eq(true), eq(false));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNotifyCallback_whenMemoryMetricsLogKeyPresent_andCloseThrows_shouldHandleException() {
    CICleanupTaskNotifyCallback callback = CICleanupTaskNotifyCallback.builder()
                                               .persistence(persistence)
                                               .serializedResponseDataHelper(serializedResponseDataHelper)
                                               .ciLogServiceUtils(ciLogServiceUtils)
                                               .stageExecutionID("stageExecId")
                                               .accountID("accountId")
                                               .planExecutionID("planExecId")
                                               .memoryMetricsLogKey("memory-metrics-key")
                                               .build();
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("taskID",
        () -> K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
    when(serializedResponseDataHelper.deserialize(any(ResponseData.class))).thenCallRealMethod();
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    when(persistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
    doThrow(new RuntimeException("close failed"))
        .when(ciLogServiceUtils)
        .closeLogStream(eq("accountId"), eq("memory-metrics-key"), eq(true), eq(false));
    callback.notify(responseSupplier);
    verify(ciLogServiceUtils, times(1)).closeLogStream(eq("accountId"), eq("memory-metrics-key"), eq(true), eq(false));
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void testNotifyCallbackTaskRunnerSuccessDeletesRow() {
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put(
        "taskID", () -> new TaskRunnerTaskResponse(CommandExecutionStatus.SUCCESS, "", new HashMap<>()));
    when(serializedResponseDataHelper.deserialize(any(ResponseData.class))).thenCallRealMethod();
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    when(persistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
    ciCleanupTaskNotifyCallback.notify(responseSupplier);
    verify(persistence, times(1)).delete((Query<PersistentEntity>) any());
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void testNotifyCallbackTaskRunnerFailurePreservesRow() {
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put(
        "taskID", () -> new TaskRunnerTaskResponse(CommandExecutionStatus.FAILURE, "cleanup failed", new HashMap<>()));
    when(serializedResponseDataHelper.deserialize(any(ResponseData.class))).thenCallRealMethod();
    ciCleanupTaskNotifyCallback.notify(responseSupplier);
    verify(persistence, never()).delete((Query<PersistentEntity>) any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNotifyCallback_whenDeserializeThrows_shouldHandleOuterException() {
    CICleanupTaskNotifyCallback callback = CICleanupTaskNotifyCallback.builder()
                                               .persistence(persistence)
                                               .serializedResponseDataHelper(serializedResponseDataHelper)
                                               .ciLogServiceUtils(ciLogServiceUtils)
                                               .stageExecutionID("stageExecId")
                                               .accountID("accountId")
                                               .planExecutionID("planExecId")
                                               .build();
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("taskID",
        () -> K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
    when(serializedResponseDataHelper.deserialize(any(ResponseData.class)))
        .thenThrow(new RuntimeException("deserialization failed"));
    callback.notify(responseSupplier);
    verify(persistence, never()).delete((Query<PersistentEntity>) any());
  }
}
