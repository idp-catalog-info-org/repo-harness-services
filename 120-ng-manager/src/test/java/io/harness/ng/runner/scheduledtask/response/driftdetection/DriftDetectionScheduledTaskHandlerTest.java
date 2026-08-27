/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.runner.scheduledtask.response.driftdetection;

import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sDiffResponse;
import io.harness.delegate.task.k8s.K8sDriftFetchResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.driftdetection.entity.DriftDetectionEntity;
import io.harness.driftdetection.entity.DriftDetectionScheduledTaskInfo;
import io.harness.driftdetection.entity.DriftStatus;
import io.harness.driftdetection.expression.DriftDetectionExpressionService;
import io.harness.driftdetection.service.DriftDetectionDelegateConfigResolver;
import io.harness.driftdetection.service.DriftDetectionResultRecord;
import io.harness.driftdetection.service.DriftDetectionResultService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.repositories.driftdetection.DriftDetectionEntityRepository;
import io.harness.repositories.driftdetection.DriftDetectionScheduledTaskInfoRepository;
import io.harness.rule.Owner;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

public class DriftDetectionScheduledTaskHandlerTest extends CategoryTest {
  private static final String ACCOUNT_ID = "acc1";
  private static final String PARENT_UNIQUE_ID = "parent1";
  private static final String ENTITY_ID = "ent1";
  private static final String SCHEDULED_TASK_ID = "stid1";
  private static final String FETCH_TASK_ID = "fetchTask1";
  private static final String DIFF_TASK_ID = "diffTask1";

  private DriftDetectionScheduledTaskHandler handler;
  private DriftDetectionScheduledTaskInfoRepository scheduledTaskInfoRepository;
  private DriftDetectionEntityRepository entityRepository;
  private DriftDetectionDelegateConfigResolver delegateConfigResolver;
  private DriftDetectionResultService resultService;
  private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  private ScopeInfoService scopeInfoService;
  private DriftDetectionExpressionService expressionService;
  private WaitNotifyEngine waitNotifyEngine;

  private DriftDetectionScheduledTaskInfo taskInfo;
  private DriftDetectionEntity entity;
  private K8sInfraDelegateConfig infraConfig;
  private ManifestDelegateConfig manifestConfig;

  @Before
  public void setUp() {
    scheduledTaskInfoRepository = mock(DriftDetectionScheduledTaskInfoRepository.class);
    entityRepository = mock(DriftDetectionEntityRepository.class);
    delegateConfigResolver = mock(DriftDetectionDelegateConfigResolver.class);
    resultService = mock(DriftDetectionResultService.class);
    delegateGrpcClientWrapper = mock(DelegateGrpcClientWrapper.class);
    scopeInfoService = mock(ScopeInfoService.class);
    expressionService = mock(DriftDetectionExpressionService.class);
    waitNotifyEngine = mock(WaitNotifyEngine.class);

    handler =
        new DriftDetectionScheduledTaskHandler(scheduledTaskInfoRepository, entityRepository, delegateConfigResolver,
            resultService, delegateGrpcClientWrapper, scopeInfoService, expressionService, waitNotifyEngine);

    taskInfo = DriftDetectionScheduledTaskInfo.builder()
                   .accountId(ACCOUNT_ID)
                   .scheduledTaskId(SCHEDULED_TASK_ID)
                   .parentUniqueId(PARENT_UNIQUE_ID)
                   .entityId(ENTITY_ID)
                   .build();

    entity = DriftDetectionEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .parentUniqueId(PARENT_UNIQUE_ID)
                 .entityId(ENTITY_ID)
                 .build();

    infraConfig = mock(K8sInfraDelegateConfig.class);
    manifestConfig = mock(ManifestDelegateConfig.class);

    when(entityRepository.findByIdentity(ACCOUNT_ID, PARENT_UNIQUE_ID, ENTITY_ID)).thenReturn(Optional.of(entity));
    when(delegateConfigResolver.resolveInfraConfig(entity)).thenReturn(Optional.of(infraConfig));
    when(delegateConfigResolver.resolveManifestConfig(entity)).thenReturn(Optional.of(manifestConfig));
    when(delegateConfigResolver.resolveValuesManifestConfigs(entity)).thenReturn(Collections.emptyList());
    when(delegateConfigResolver.resolveInlineValuesContent(entity)).thenReturn(Collections.emptyList());
    when(scopeInfoService.getScopeInfoFromUniqueId(anyString(), anyString())).thenReturn(Optional.empty());
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any(Duration.class))).thenReturn(DIFF_TASK_ID);
  }

  // ── submitDiffAsync ───────────────────────────────────────────────────────

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSubmitDiffAsync_alwaysSubmitsDiffTaskRegardlessOfPriorResult() {
    handler.submitDiffAsync(
        ACCOUNT_ID, taskInfo, entity, infraConfig, manifestConfig, List.of("manifest: v2"), Map.of());

    ArgumentCaptor<NotifyCallback> callbackCaptor = ArgumentCaptor.forClass(NotifyCallback.class);
    verify(delegateGrpcClientWrapper).submitAsyncTaskV2(any(), eq(Duration.ZERO));
    verify(waitNotifyEngine).waitForAllOn(anyString(), callbackCaptor.capture(), anyString());
    assertThat(callbackCaptor.getValue()).isInstanceOf(DriftDetectionDiffNotifyCallback.class);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testOnFetchValuesComplete_infraConfigMissing_recordsErrorAndNoDelegate() {
    when(delegateConfigResolver.resolveInfraConfig(entity)).thenReturn(Optional.empty());
    when(scheduledTaskInfoRepository.findByScheduledTaskId(ACCOUNT_ID, SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    K8sDriftFetchResponse fetchResponse =
        K8sDriftFetchResponse.builder().overrideFiles(List.of("fetched: true")).build();
    ResponseData responseData = K8sDeployResponse.builder()
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .k8sNGTaskResponse(fetchResponse)
                                    .build();
    handler.onFetchValuesComplete(ACCOUNT_ID, SCHEDULED_TASK_ID, taskInfo.getParentUniqueId(), taskInfo.getEntityId(),
        List.of(), Map.of(), responseData);

    verify(delegateGrpcClientWrapper, never()).submitAsyncTaskV2(any(), any());
    verify(resultService).recordResult(any());
  }

  // ── onFetchValuesComplete ─────────────────────────────────────────────────

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testOnFetchValuesComplete_mergesFetchedAndInlineValuesAndChainsToDiff() {
    when(scheduledTaskInfoRepository.findByScheduledTaskId(ACCOUNT_ID, SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    K8sDriftFetchResponse fetchResponse = K8sDriftFetchResponse.builder()
                                              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                              .overrideFiles(List.of("fetched: override"))
                                              .build();
    ResponseData responseData = K8sDeployResponse.builder()
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .k8sNGTaskResponse(fetchResponse)
                                    .build();

    handler.onFetchValuesComplete(
        ACCOUNT_ID, SCHEDULED_TASK_ID, PARENT_UNIQUE_ID, ENTITY_ID, List.of("inline: value"), Map.of(), responseData);

    ArgumentCaptor<NotifyCallback> callbackCaptor = ArgumentCaptor.forClass(NotifyCallback.class);
    verify(delegateGrpcClientWrapper).submitAsyncTaskV2(any(), eq(Duration.ZERO));
    verify(waitNotifyEngine).waitForAllOn(anyString(), callbackCaptor.capture(), anyString());
    assertThat(callbackCaptor.getValue()).isInstanceOf(DriftDetectionDiffNotifyCallback.class);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testOnFetchValuesComplete_fetchFailureResponse_stillChainsToDiffWithEmptyValues() {
    when(scheduledTaskInfoRepository.findByScheduledTaskId(ACCOUNT_ID, SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    ResponseData responseData =
        K8sDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();

    handler.onFetchValuesComplete(
        ACCOUNT_ID, SCHEDULED_TASK_ID, PARENT_UNIQUE_ID, ENTITY_ID, List.of(), Map.of(), responseData);

    verify(delegateGrpcClientWrapper).submitAsyncTaskV2(any(), eq(Duration.ZERO));
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testOnFetchValuesComplete_taskInfoNotFound_noDelegate() {
    when(scheduledTaskInfoRepository.findByScheduledTaskId(ACCOUNT_ID, SCHEDULED_TASK_ID)).thenReturn(Optional.empty());

    handler.onFetchValuesComplete(
        ACCOUNT_ID, SCHEDULED_TASK_ID, PARENT_UNIQUE_ID, ENTITY_ID, List.of(), Map.of(), mock(ResponseData.class));

    verify(delegateGrpcClientWrapper, never()).submitAsyncTaskV2(any(), any());
    verify(waitNotifyEngine, never()).waitForAllOn(anyString(), any(), anyString());
  }

  // ── onDiffComplete ────────────────────────────────────────────────────────

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testOnDiffComplete_nonEmptyDiff_persistsDriftedResult() {
    when(scheduledTaskInfoRepository.findByScheduledTaskId(ACCOUNT_ID, SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    ResponseData responseData = K8sDeployResponse.builder()
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .k8sNGTaskResponse(K8sDiffResponse.builder().manifestDiffYaml("- foo: bar").build())
                                    .build();

    handler.onDiffComplete(ACCOUNT_ID, SCHEDULED_TASK_ID, "checksum123", responseData);

    ArgumentCaptor<DriftDetectionResultRecord> captor = ArgumentCaptor.forClass(DriftDetectionResultRecord.class);
    verify(resultService).recordResult(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(DriftStatus.DRIFTED);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testOnDiffComplete_emptyDiff_persistsNoDriftResult() {
    when(scheduledTaskInfoRepository.findByScheduledTaskId(ACCOUNT_ID, SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    ResponseData responseData = K8sDeployResponse.builder()
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .k8sNGTaskResponse(K8sDiffResponse.builder().manifestDiffYaml("").build())
                                    .build();

    handler.onDiffComplete(ACCOUNT_ID, SCHEDULED_TASK_ID, "checksum123", responseData);

    ArgumentCaptor<DriftDetectionResultRecord> captor = ArgumentCaptor.forClass(DriftDetectionResultRecord.class);
    verify(resultService).recordResult(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(DriftStatus.NO_DRIFT);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testOnDiffComplete_taskInfoNotFound_noResultPersisted() {
    when(scheduledTaskInfoRepository.findByScheduledTaskId(ACCOUNT_ID, SCHEDULED_TASK_ID)).thenReturn(Optional.empty());

    handler.onDiffComplete(ACCOUNT_ID, SCHEDULED_TASK_ID, "checksum123",
        K8sDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());

    verify(resultService, never()).recordResult(any());
  }

  // ── callback wiring ───────────────────────────────────────────────────────

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testDriftDetectionDiffNotifyCallback_invokesOnDiffComplete() {
    when(scheduledTaskInfoRepository.findByScheduledTaskId(ACCOUNT_ID, SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    DriftDetectionDiffNotifyCallback callback =
        new DriftDetectionDiffNotifyCallback(ACCOUNT_ID, SCHEDULED_TASK_ID, PARENT_UNIQUE_ID, ENTITY_ID, "chk1");
    callback.handler = handler;

    ResponseData responseData = K8sDeployResponse.builder()
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .k8sNGTaskResponse(K8sDiffResponse.builder().manifestDiffYaml("- drift").build())
                                    .build();
    Supplier<ResponseData> supplier = () -> responseData;
    callback.notify(Map.of(DIFF_TASK_ID, supplier));

    verify(resultService).recordResult(any());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testDriftDetectionFetchValuesNotifyCallback_invokesOnFetchValuesComplete() {
    when(scheduledTaskInfoRepository.findByScheduledTaskId(ACCOUNT_ID, SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    DriftDetectionFetchValuesNotifyCallback callback = new DriftDetectionFetchValuesNotifyCallback(
        ACCOUNT_ID, SCHEDULED_TASK_ID, PARENT_UNIQUE_ID, ENTITY_ID, List.of(), Map.of());
    callback.handler = handler;

    K8sDriftFetchResponse fetchResponse = K8sDriftFetchResponse.builder()
                                              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                              .overrideFiles(List.of("fetched: v1"))
                                              .build();
    ResponseData responseData = K8sDeployResponse.builder()
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .k8sNGTaskResponse(fetchResponse)
                                    .build();
    callback.notify(Map.of(FETCH_TASK_ID, () -> responseData));

    verify(delegateGrpcClientWrapper).submitAsyncTaskV2(any(), eq(Duration.ZERO));
  }
}
