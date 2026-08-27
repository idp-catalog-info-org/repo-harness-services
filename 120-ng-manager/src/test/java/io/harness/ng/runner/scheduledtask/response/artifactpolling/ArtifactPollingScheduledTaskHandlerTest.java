/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.runner.scheduledtask.response.artifactpolling;

import static io.harness.rule.OwnerRule.MUSKAN_GUPTA;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.GetTaskStatusResponse;
import io.harness.delegate.ScheduledTaskLifecycleEvent;
import io.harness.delegate.ScheduledTaskLifecycleStatus;
import io.harness.delegate.ScheduledTaskResponse;
import io.harness.delegate.Status;
import io.harness.delegate.TaskId;
import io.harness.ng.webhook.polling.PolledItemPublisher;
import io.harness.polling.bean.ArtifactPolledResponse;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.ScheduledPollingTaskInfo;
import io.harness.polling.bean.artifact.DockerHubArtifactInfo;
import io.harness.polling.bean.artifact.GithubPackagesArtifactInfo;
import io.harness.polling.bean.artifact.S3ArtifactInfo;
import io.harness.polling.contracts.PollingResponse;
import io.harness.polling.service.intfc.PollingService;
import io.harness.polling.service.intfc.ScheduledPollingTaskInfoService;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ArtifactPollingScheduledTaskHandlerTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "testAccountId";
  private static final String TEST_SCHEDULED_TASK_ID = "testScheduledTaskId";
  private static final String TEST_TASK_ID = "testTaskId";
  private static final String TEST_POLLING_DOC_ID = "testPollingDocId";
  private static final String TEST_SIGNATURE = "testSignature";

  @Mock private ScheduledPollingTaskInfoService scheduledPollingTaskInfoService;
  @Mock private PollingService pollingService;
  @Mock private PolledItemPublisher polledItemPublisher;

  private ObjectMapper objectMapper;
  private ArtifactPollingScheduledTaskHandler handler;

  @Before
  public void setup() {
    objectMapper = new ObjectMapper();
    handler = new ArtifactPollingScheduledTaskHandler(
        scheduledPollingTaskInfoService, pollingService, polledItemPublisher, objectMapper);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSuccessResponse_whenNewVersionsDetected_thenPublishesAndUpdatesPolledResponse() {
    ScheduledPollingTaskInfo taskInfo =
        ScheduledPollingTaskInfo.builder().pollingDocumentId(TEST_POLLING_DOC_ID).build();
    when(scheduledPollingTaskInfoService.findByScheduledTaskId(TEST_SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    Set<String> existingVersions = new HashSet<>(Arrays.asList("v1.0", "v1.1"));
    PollingDocument pollingDocument =
        PollingDocument.builder()
            .uuid(TEST_POLLING_DOC_ID)
            .accountId(TEST_ACCOUNT_ID)
            .signatures(List.of(TEST_SIGNATURE))
            .pollingInfo(DockerHubArtifactInfo.builder().imagePath("library/nginx").build())
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(existingVersions).build())
            .failedAttempts(0)
            .build();
    when(pollingService.get(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID)).thenReturn(pollingDocument);

    String outputJson = "{\"output_vars\":{\"BUILDS\":\"[{\\\"tag\\\":\\\"v1.0\\\"},{\\\"tag\\\":\\\"v1.2\\\"}]\"}}";
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.SUCCESS)
                                                  .setData(ByteString.copyFromUtf8(outputJson))
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    ArgumentCaptor<PollingResponse> captor = ArgumentCaptor.forClass(PollingResponse.class);
    verify(polledItemPublisher).publishPolledItems(captor.capture());
    PollingResponse publishedResponse = captor.getValue();
    assertThat(publishedResponse.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(publishedResponse.getPollingDocId()).isEqualTo(TEST_POLLING_DOC_ID);
    assertThat(publishedResponse.getBuildInfo().getVersionsList()).containsExactly("v1.2");
    assertThat(publishedResponse.getSignaturesList()).containsExactly(TEST_SIGNATURE);

    ArgumentCaptor<ArtifactPolledResponse> polledCaptor = ArgumentCaptor.forClass(ArtifactPolledResponse.class);
    verify(pollingService).updatePolledResponse(eq(TEST_ACCOUNT_ID), eq(TEST_POLLING_DOC_ID), polledCaptor.capture());
    assertThat(polledCaptor.getValue().getAllPolledKeys()).containsExactlyInAnyOrder("v1.0", "v1.1", "v1.2");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSuccessResponse_whenNoNewVersions_thenUpdatesPolledResponseWithoutPublishing() {
    ScheduledPollingTaskInfo taskInfo =
        ScheduledPollingTaskInfo.builder().pollingDocumentId(TEST_POLLING_DOC_ID).build();
    when(scheduledPollingTaskInfoService.findByScheduledTaskId(TEST_SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    Set<String> existingVersions = new HashSet<>(Arrays.asList("v1.0", "v1.1"));
    PollingDocument pollingDocument =
        PollingDocument.builder()
            .uuid(TEST_POLLING_DOC_ID)
            .accountId(TEST_ACCOUNT_ID)
            .signatures(List.of(TEST_SIGNATURE))
            .pollingInfo(DockerHubArtifactInfo.builder().imagePath("library/nginx").build())
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(existingVersions).build())
            .failedAttempts(0)
            .build();
    when(pollingService.get(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID)).thenReturn(pollingDocument);

    String outputJson = "{\"output_vars\":{\"BUILDS\":\"[{\\\"tag\\\":\\\"v1.0\\\"},{\\\"tag\\\":\\\"v1.1\\\"}]\"}}";
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.SUCCESS)
                                                  .setData(ByteString.copyFromUtf8(outputJson))
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(polledItemPublisher, never()).publishPolledItems(any());
    verify(pollingService).updatePolledResponse(eq(TEST_ACCOUNT_ID), eq(TEST_POLLING_DOC_ID), any());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSuccessResponse_whenFirstCollection_thenStoresVersionsWithoutTriggeringPublish() {
    ScheduledPollingTaskInfo taskInfo =
        ScheduledPollingTaskInfo.builder().pollingDocumentId(TEST_POLLING_DOC_ID).build();
    when(scheduledPollingTaskInfoService.findByScheduledTaskId(TEST_SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    PollingDocument pollingDocument =
        PollingDocument.builder()
            .uuid(TEST_POLLING_DOC_ID)
            .accountId(TEST_ACCOUNT_ID)
            .signatures(List.of(TEST_SIGNATURE))
            .pollingInfo(DockerHubArtifactInfo.builder().imagePath("library/nginx").build())
            .polledResponse(null)
            .failedAttempts(0)
            .build();
    when(pollingService.get(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID)).thenReturn(pollingDocument);

    String outputJson = "{\"output_vars\":{\"BUILDS\":\"[{\\\"tag\\\":\\\"v1.0\\\"},{\\\"tag\\\":\\\"v1.1\\\"}]\"}}";
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.SUCCESS)
                                                  .setData(ByteString.copyFromUtf8(outputJson))
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(polledItemPublisher, never()).publishPolledItems(any());

    ArgumentCaptor<ArtifactPolledResponse> polledCaptor = ArgumentCaptor.forClass(ArtifactPolledResponse.class);
    verify(pollingService).updatePolledResponse(eq(TEST_ACCOUNT_ID), eq(TEST_POLLING_DOC_ID), polledCaptor.capture());
    assertThat(polledCaptor.getValue().getAllPolledKeys()).containsExactlyInAnyOrder("v1.0", "v1.1");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testNonSuccessResponse_thenIncrementsFailedAttempts() {
    ScheduledPollingTaskInfo taskInfo =
        ScheduledPollingTaskInfo.builder().pollingDocumentId(TEST_POLLING_DOC_ID).build();
    when(scheduledPollingTaskInfoService.findByScheduledTaskId(TEST_SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    PollingDocument pollingDocument = PollingDocument.builder()
                                          .uuid(TEST_POLLING_DOC_ID)
                                          .accountId(TEST_ACCOUNT_ID)
                                          .signatures(List.of(TEST_SIGNATURE))
                                          .failedAttempts(2)
                                          .build();
    when(pollingService.get(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID)).thenReturn(pollingDocument);

    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.FAILURE)
                                                  .setError("Connection refused")
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(pollingService).updateFailedAttempts(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID, 3);
    verify(polledItemPublisher, never()).publishPolledItems(any());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSuspendedLifecycleEvent_thenIncrementsFailedAttempts() {
    ScheduledPollingTaskInfo taskInfo =
        ScheduledPollingTaskInfo.builder().pollingDocumentId(TEST_POLLING_DOC_ID).build();
    when(scheduledPollingTaskInfoService.findByScheduledTaskId(TEST_SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    PollingDocument pollingDocument = PollingDocument.builder()
                                          .uuid(TEST_POLLING_DOC_ID)
                                          .accountId(TEST_ACCOUNT_ID)
                                          .signatures(List.of(TEST_SIGNATURE))
                                          .failedAttempts(4)
                                          .build();
    when(pollingService.get(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID)).thenReturn(pollingDocument);

    ScheduledTaskLifecycleEvent lifecycleEvent =
        ScheduledTaskLifecycleEvent.newBuilder()
            .setStatus(ScheduledTaskLifecycleStatus.SCHEDULED_TASK_LIFECYCLE_STATUS_SUSPENDED)
            .setMessage("Task suspended due to consecutive failures")
            .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setLifecycleEvent(lifecycleEvent)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(pollingService).updateFailedAttempts(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID, 5);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testDisabledLifecycleEvent_thenDeletesScheduledPollingTaskInfo() {
    ScheduledPollingTaskInfo taskInfo =
        ScheduledPollingTaskInfo.builder().pollingDocumentId(TEST_POLLING_DOC_ID).build();
    when(scheduledPollingTaskInfoService.findByScheduledTaskId(TEST_SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    ScheduledTaskLifecycleEvent lifecycleEvent =
        ScheduledTaskLifecycleEvent.newBuilder()
            .setStatus(ScheduledTaskLifecycleStatus.SCHEDULED_TASK_LIFECYCLE_STATUS_DISABLED)
            .setMessage("Task disabled")
            .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setLifecycleEvent(lifecycleEvent)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(scheduledPollingTaskInfoService)
        .deleteByAccountIdentifierAndPollingDocumentId(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testMalformedArtifactsJson_thenReturnsGracefully() {
    ScheduledPollingTaskInfo taskInfo =
        ScheduledPollingTaskInfo.builder().pollingDocumentId(TEST_POLLING_DOC_ID).build();
    when(scheduledPollingTaskInfoService.findByScheduledTaskId(TEST_SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    PollingDocument pollingDocument =
        PollingDocument.builder()
            .uuid(TEST_POLLING_DOC_ID)
            .accountId(TEST_ACCOUNT_ID)
            .signatures(List.of(TEST_SIGNATURE))
            .pollingInfo(DockerHubArtifactInfo.builder().imagePath("library/nginx").build())
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(new HashSet<>()).build())
            .failedAttempts(0)
            .build();
    when(pollingService.get(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID)).thenReturn(pollingDocument);

    String outputJson = "{\"output_vars\":{\"BUILDS\":\"not valid json [[[\"}}";
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.SUCCESS)
                                                  .setData(ByteString.copyFromUtf8(outputJson))
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(polledItemPublisher, never()).publishPolledItems(any());
    verify(pollingService, never()).updatePolledResponse(anyString(), anyString(), any());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testEmptyResponse_thenReturnsTrue() {
    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(polledItemPublisher, never()).publishPolledItems(any());
    verify(pollingService, never()).updatePolledResponse(anyString(), anyString(), any());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSuccessResponse_whenPreviousFailedAttempts_thenResetsFailedAttempts() {
    ScheduledPollingTaskInfo taskInfo =
        ScheduledPollingTaskInfo.builder().pollingDocumentId(TEST_POLLING_DOC_ID).build();
    when(scheduledPollingTaskInfoService.findByScheduledTaskId(TEST_SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    Set<String> existingVersions = new HashSet<>(Arrays.asList("v1.0"));
    PollingDocument pollingDocument =
        PollingDocument.builder()
            .uuid(TEST_POLLING_DOC_ID)
            .accountId(TEST_ACCOUNT_ID)
            .signatures(List.of(TEST_SIGNATURE))
            .pollingInfo(DockerHubArtifactInfo.builder().imagePath("library/nginx").build())
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(existingVersions).build())
            .failedAttempts(3)
            .build();
    when(pollingService.get(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID)).thenReturn(pollingDocument);

    String outputJson = "{\"output_vars\":{\"BUILDS\":\"[{\\\"tag\\\":\\\"v1.0\\\"}]\"}}";
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.SUCCESS)
                                                  .setData(ByteString.copyFromUtf8(outputJson))
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(pollingService).updateFailedAttempts(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID, 0);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSuccessResponse_whenNoScheduledPollingTaskInfo_thenReturnsTrue() {
    when(scheduledPollingTaskInfoService.findByScheduledTaskId(TEST_SCHEDULED_TASK_ID)).thenReturn(Optional.empty());

    String outputJson = "{\"output_vars\":{\"BUILDS\":\"[{\\\"tag\\\":\\\"v1.0\\\"}]\"}}";
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.SUCCESS)
                                                  .setData(ByteString.copyFromUtf8(outputJson))
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(pollingService, never()).updatePolledResponse(anyString(), anyString(), any());
    verify(polledItemPublisher, never()).publishPolledItems(any());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSuccessResponse_whenNoArtifactsKeyInOutput_thenReturnsTrue() {
    ScheduledPollingTaskInfo taskInfo =
        ScheduledPollingTaskInfo.builder().pollingDocumentId(TEST_POLLING_DOC_ID).build();
    when(scheduledPollingTaskInfoService.findByScheduledTaskId(TEST_SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    PollingDocument pollingDocument =
        PollingDocument.builder()
            .uuid(TEST_POLLING_DOC_ID)
            .accountId(TEST_ACCOUNT_ID)
            .signatures(List.of(TEST_SIGNATURE))
            .pollingInfo(DockerHubArtifactInfo.builder().imagePath("library/nginx").build())
            .failedAttempts(0)
            .build();
    when(pollingService.get(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID)).thenReturn(pollingDocument);

    String outputJson = "{\"output_vars\":{\"SOME_OTHER_KEY\":\"value\"}}";
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.SUCCESS)
                                                  .setData(ByteString.copyFromUtf8(outputJson))
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(polledItemPublisher, never()).publishPolledItems(any());
    verify(pollingService, never()).updatePolledResponse(anyString(), anyString(), any());
  }

  @Test
  @Owner(developers = MUSKAN_GUPTA)
  @Category(UnitTests.class)
  public void testSuccessResponse_whenFilePathsKeyUsed_thenPublishesAndUpdatesPolledResponse() {
    ScheduledPollingTaskInfo taskInfo =
        ScheduledPollingTaskInfo.builder().pollingDocumentId(TEST_POLLING_DOC_ID).build();
    when(scheduledPollingTaskInfoService.findByScheduledTaskId(TEST_SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    Set<String> existingVersions = new HashSet<>(Arrays.asList("v13.txt"));
    PollingDocument pollingDocument =
        PollingDocument.builder()
            .uuid(TEST_POLLING_DOC_ID)
            .accountId(TEST_ACCOUNT_ID)
            .signatures(List.of(TEST_SIGNATURE))
            .pollingInfo(S3ArtifactInfo.builder().bucketName("my-bucket").filePathRegex("v*").build())
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(existingVersions).build())
            .failedAttempts(0)
            .build();
    when(pollingService.get(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID)).thenReturn(pollingDocument);

    String outputJson = "{\"output_vars\":{\"FILE_PATHS\":\"[{\\\"buildDetails\\\":{\\\"number\\\":\\\"v13.txt\\\"}},{"
        + "\\\"buildDetails\\\":{\\\"number\\\":\\\"v14.txt\\\"}}]\"}}";
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.SUCCESS)
                                                  .setData(ByteString.copyFromUtf8(outputJson))
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    ArgumentCaptor<PollingResponse> captor = ArgumentCaptor.forClass(PollingResponse.class);
    verify(polledItemPublisher).publishPolledItems(captor.capture());
    PollingResponse publishedResponse = captor.getValue();
    assertThat(publishedResponse.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(publishedResponse.getPollingDocId()).isEqualTo(TEST_POLLING_DOC_ID);
    assertThat(publishedResponse.getBuildInfo().getVersionsList()).containsExactly("v14.txt");
    assertThat(publishedResponse.getSignaturesList()).containsExactly(TEST_SIGNATURE);

    ArgumentCaptor<ArtifactPolledResponse> polledCaptor = ArgumentCaptor.forClass(ArtifactPolledResponse.class);
    verify(pollingService).updatePolledResponse(eq(TEST_ACCOUNT_ID), eq(TEST_POLLING_DOC_ID), polledCaptor.capture());
    assertThat(polledCaptor.getValue().getAllPolledKeys()).containsExactlyInAnyOrder("v13.txt", "v14.txt");
  }

  @Test
  @Owner(developers = MUSKAN_GUPTA)
  @Category(UnitTests.class)
  public void testSuccessResponse_whenBothBuildsAndFilePathsPresent_thenBuildsTakesPrecedence() {
    ScheduledPollingTaskInfo taskInfo =
        ScheduledPollingTaskInfo.builder().pollingDocumentId(TEST_POLLING_DOC_ID).build();
    when(scheduledPollingTaskInfoService.findByScheduledTaskId(TEST_SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    PollingDocument pollingDocument =
        PollingDocument.builder()
            .uuid(TEST_POLLING_DOC_ID)
            .accountId(TEST_ACCOUNT_ID)
            .signatures(List.of(TEST_SIGNATURE))
            .pollingInfo(DockerHubArtifactInfo.builder().imagePath("library/nginx").build())
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(new HashSet<>()).build())
            .failedAttempts(0)
            .build();
    when(pollingService.get(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID)).thenReturn(pollingDocument);

    String outputJson = "{\"output_vars\":{\"BUILDS\":\"[{\\\"tag\\\":\\\"fromBuilds\\\"}]\","
        + "\"FILE_PATHS\":\"[{\\\"buildDetails\\\":{\\\"number\\\":\\\"fromFilePaths\\\"}}]\"}}";
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.SUCCESS)
                                                  .setData(ByteString.copyFromUtf8(outputJson))
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    ArgumentCaptor<PollingResponse> captor = ArgumentCaptor.forClass(PollingResponse.class);
    verify(polledItemPublisher).publishPolledItems(captor.capture());
    assertThat(captor.getValue().getBuildInfo().getVersionsList()).containsExactly("fromBuilds");
  }

  @Test
  @Owner(developers = MUSKAN_GUPTA)
  @Category(UnitTests.class)
  public void testSuccessResponse_whenFilePathsIsEmpty_thenUpdatesPolledResponseWithoutPublishing() {
    ScheduledPollingTaskInfo taskInfo =
        ScheduledPollingTaskInfo.builder().pollingDocumentId(TEST_POLLING_DOC_ID).build();
    when(scheduledPollingTaskInfoService.findByScheduledTaskId(TEST_SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    Set<String> existingVersions = new HashSet<>(Arrays.asList("v13.txt"));
    PollingDocument pollingDocument =
        PollingDocument.builder()
            .uuid(TEST_POLLING_DOC_ID)
            .accountId(TEST_ACCOUNT_ID)
            .signatures(List.of(TEST_SIGNATURE))
            .pollingInfo(S3ArtifactInfo.builder().bucketName("my-bucket").filePathRegex("nomatch*").build())
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(existingVersions).build())
            .failedAttempts(0)
            .build();
    when(pollingService.get(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID)).thenReturn(pollingDocument);

    String outputJson = "{\"output_vars\":{\"FILE_PATHS\":\"[]\"}}";
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.SUCCESS)
                                                  .setData(ByteString.copyFromUtf8(outputJson))
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    verify(polledItemPublisher, never()).publishPolledItems(any());
    ArgumentCaptor<ArtifactPolledResponse> polledCaptor = ArgumentCaptor.forClass(ArtifactPolledResponse.class);
    verify(pollingService).updatePolledResponse(eq(TEST_ACCOUNT_ID), eq(TEST_POLLING_DOC_ID), polledCaptor.capture());
    assertThat(polledCaptor.getValue().getAllPolledKeys()).containsExactly("v13.txt");
  }

  @Test
  @Owner(developers = MUSKAN_GUPTA)
  @Category(UnitTests.class)
  public void testSuccessResponse_whenBuildsUseTopLevelNumber_thenPublishesAndUpdatesPolledResponse() {
    ScheduledPollingTaskInfo taskInfo =
        ScheduledPollingTaskInfo.builder().pollingDocumentId(TEST_POLLING_DOC_ID).build();
    when(scheduledPollingTaskInfoService.findByScheduledTaskId(TEST_SCHEDULED_TASK_ID))
        .thenReturn(Optional.of(taskInfo));

    Set<String> existingVersions = new HashSet<>(Arrays.asList("v44"));
    PollingDocument pollingDocument =
        PollingDocument.builder()
            .uuid(TEST_POLLING_DOC_ID)
            .accountId(TEST_ACCOUNT_ID)
            .signatures(List.of(TEST_SIGNATURE))
            .pollingInfo(GithubPackagesArtifactInfo.builder().packageName("muskan").packageType("container").build())
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(existingVersions).build())
            .failedAttempts(0)
            .build();
    when(pollingService.get(TEST_ACCOUNT_ID, TEST_POLLING_DOC_ID)).thenReturn(pollingDocument);

    String outputJson = "{\"output_vars\":{\"BUILDS\":\"[{\\\"number\\\":\\\"v44\\\"},{\\\"number\\\":\\\"v45\\\","
        + "\\\"revision\\\":null,\\\"artifactPath\\\":\\\"muskan\\\"}]\"}}";
    GetTaskStatusResponse executionResponse = GetTaskStatusResponse.newBuilder()
                                                  .setAccountId(TEST_ACCOUNT_ID)
                                                  .setTaskId(TaskId.newBuilder().setId(TEST_TASK_ID).build())
                                                  .setStatus(Status.SUCCESS)
                                                  .setData(ByteString.copyFromUtf8(outputJson))
                                                  .build();

    ScheduledTaskResponse response = ScheduledTaskResponse.newBuilder()
                                         .setAccountId(TEST_ACCOUNT_ID)
                                         .setScheduledTaskId(TEST_SCHEDULED_TASK_ID)
                                         .setExecutionResponse(executionResponse)
                                         .build();

    boolean result = handler.processScheduledTaskResponse(response);

    assertThat(result).isTrue();
    ArgumentCaptor<PollingResponse> captor = ArgumentCaptor.forClass(PollingResponse.class);
    verify(polledItemPublisher).publishPolledItems(captor.capture());
    PollingResponse publishedResponse = captor.getValue();
    assertThat(publishedResponse.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(publishedResponse.getPollingDocId()).isEqualTo(TEST_POLLING_DOC_ID);
    assertThat(publishedResponse.getBuildInfo().getVersionsList()).containsExactly("v45");
    assertThat(publishedResponse.getSignaturesList()).containsExactly(TEST_SIGNATURE);

    ArgumentCaptor<ArtifactPolledResponse> polledCaptor = ArgumentCaptor.forClass(ArtifactPolledResponse.class);
    verify(pollingService).updatePolledResponse(eq(TEST_ACCOUNT_ID), eq(TEST_POLLING_DOC_ID), polledCaptor.capture());
    assertThat(polledCaptor.getValue().getAllPolledKeys()).containsExactlyInAnyOrder("v44", "v45");
  }
}
