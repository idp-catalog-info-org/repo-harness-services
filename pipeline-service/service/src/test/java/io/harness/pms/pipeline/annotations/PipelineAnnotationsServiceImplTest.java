/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.annotations;

import static io.harness.pms.annotations.AnnotationConstants.MODE_APPEND;
import static io.harness.pms.annotations.AnnotationConstants.MODE_DELETE;
import static io.harness.pms.annotations.AnnotationConstants.MODE_REPLACE;
import static io.harness.rule.OwnerRule.DHIRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.annotations.AnnotationStyle;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.annotations.AnnotationContentResponseDTO;
import io.harness.pms.annotations.AnnotationRequest;
import io.harness.pms.annotations.CreateAnnotationsRequest;
import io.harness.pms.annotations.CreateAnnotationsResponse;
import io.harness.pms.annotations.PipelineAnnotationEntity;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.repositories.annotations.PipelineAnnotationRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class PipelineAnnotationsServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "acc123";
  private static final String ORG_ID = "org123";
  private static final String PROJECT_ID = "proj123";
  private static final String PIPELINE_ID = "pipe123";
  private static final String PLAN_EXECUTION_ID = "plan456";
  private static final String CONTEXT_ID = "test-context";
  private static final String STAGE_EXECUTION_ID = "stage789";

  @Mock private PipelineAnnotationRepository pipelineAnnotationRepository;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private AnnotationFileService annotationFileService;
  @Mock private PipelineTelemetryHelper pipelineTelemetryHelper;

  @InjectMocks private PipelineAnnotationsServiceImpl service;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    when(pmsFeatureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);
    // Default: GCS storage enabled
    when(annotationFileService.isGcsStorageEnabled()).thenReturn(true);
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testCreateAnnotations_ReplaceMode_Success() throws Exception {
    // Arrange
    String summary = "Line1\nLine2\nLine3\nLine4\nLine5\nLine6";
    AnnotationRequest annotationRequest = createAnnotationRequest(CONTEXT_ID, summary, MODE_REPLACE);
    CreateAnnotationsRequest request = createRequest(Arrays.asList(annotationRequest));

    when(annotationFileService.uploadAnnotationFile(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("gcs-path");

    // Act
    CreateAnnotationsResponse response = service.createAnnotations(PLAN_EXECUTION_ID, ACCOUNT_ID, request);

    // Assert
    assertThat(response.getProcessed()).isEqualTo(1);
    assertThat(response.getFailed()).isEqualTo(0);

    // Verify GCS upload with full content
    verify(annotationFileService)
        .uploadAnnotationFile(eq(ACCOUNT_ID), eq(PLAN_EXECUTION_ID), eq(CONTEXT_ID), eq(summary));

    // Verify MongoDB upsert with preview (first 5 lines)
    ArgumentCaptor<PipelineAnnotationEntity> entityCaptor = ArgumentCaptor.forClass(PipelineAnnotationEntity.class);
    verify(pipelineAnnotationRepository).upsertAnnotation(entityCaptor.capture(), eq(MODE_REPLACE));

    PipelineAnnotationEntity savedEntity = entityCaptor.getValue();
    assertThat(savedEntity.getSummary()).isEqualTo("Line1\nLine2\nLine3\nLine4\nLine5");
    assertThat(savedEntity.getContextId()).isEqualTo(CONTEXT_ID);
    assertThat(savedEntity.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);

    verify(pipelineTelemetryHelper, times(1)).sendHarnessAnnotationsUsageTelemetry(eq(ACCOUNT_ID), eq(request));
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testCreateAnnotations_AppendMode_GcsFileExists() throws Exception {
    // Arrange
    String existingContent = "Old1\nOld2\nOld3";
    String newContent = "New1\nNew2";
    String expectedCombined = "Old1\nOld2\nOld3\nNew1\nNew2";

    AnnotationRequest annotationRequest = createAnnotationRequest(CONTEXT_ID, newContent, MODE_APPEND);
    CreateAnnotationsRequest request = createRequest(Arrays.asList(annotationRequest));

    when(annotationFileService.getAnnotationFileContent(anyString())).thenReturn(existingContent);
    when(annotationFileService.uploadAnnotationFile(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("gcs-path");

    // Act
    CreateAnnotationsResponse response = service.createAnnotations(PLAN_EXECUTION_ID, ACCOUNT_ID, request);

    // Assert
    assertThat(response.getProcessed()).isEqualTo(1);
    assertThat(response.getFailed()).isEqualTo(0);

    // Verify GCS operations
    verify(annotationFileService).getAnnotationFileContent(anyString());
    verify(annotationFileService)
        .uploadAnnotationFile(eq(ACCOUNT_ID), eq(PLAN_EXECUTION_ID), eq(CONTEXT_ID), eq(expectedCombined));

    // Verify MongoDB has preview of COMBINED content
    ArgumentCaptor<PipelineAnnotationEntity> entityCaptor = ArgumentCaptor.forClass(PipelineAnnotationEntity.class);
    verify(pipelineAnnotationRepository).upsertAnnotation(entityCaptor.capture(), eq(MODE_APPEND));

    assertThat(entityCaptor.getValue().getSummary()).isEqualTo(expectedCombined); // All 5 lines fit in preview
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testCreateAnnotations_AppendMode_LegacyMigration() throws Exception {
    // Arrange
    String legacyContent = "Legacy1\nLegacy2";
    String newContent = "New1\nNew2";
    String expectedCombined = "Legacy1\nLegacy2\nNew1\nNew2";

    AnnotationRequest annotationRequest = createAnnotationRequest(CONTEXT_ID, newContent, MODE_APPEND);
    CreateAnnotationsRequest request = createRequest(Arrays.asList(annotationRequest));

    // GCS file doesn't exist (throws exception)
    when(annotationFileService.getAnnotationFileContent(anyString())).thenThrow(new RuntimeException("File not found"));

    // MongoDB has legacy data
    PipelineAnnotationEntity legacyEntity = createEntity(CONTEXT_ID, legacyContent);
    when(pipelineAnnotationRepository.findByPlanExecutionIdAndContextId(PLAN_EXECUTION_ID, CONTEXT_ID))
        .thenReturn(legacyEntity);

    when(annotationFileService.uploadAnnotationFile(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("gcs-path");

    // Act
    CreateAnnotationsResponse response = service.createAnnotations(PLAN_EXECUTION_ID, ACCOUNT_ID, request);

    // Assert
    assertThat(response.getProcessed()).isEqualTo(1);
    assertThat(response.getFailed()).isEqualTo(0);

    // Verify legacy migration: MongoDB content + new content uploaded to GCS
    verify(annotationFileService)
        .uploadAnnotationFile(eq(ACCOUNT_ID), eq(PLAN_EXECUTION_ID), eq(CONTEXT_ID), eq(expectedCombined));

    ArgumentCaptor<PipelineAnnotationEntity> entityCaptor = ArgumentCaptor.forClass(PipelineAnnotationEntity.class);
    verify(pipelineAnnotationRepository).upsertAnnotation(entityCaptor.capture(), eq(MODE_APPEND));

    assertThat(entityCaptor.getValue().getSummary()).isEqualTo(expectedCombined);
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testCreateAnnotations_DeleteMode_Success() throws Exception {
    // Arrange
    AnnotationRequest annotationRequest = createAnnotationRequest(CONTEXT_ID, null, MODE_DELETE);
    CreateAnnotationsRequest request = createRequest(Arrays.asList(annotationRequest));

    when(pipelineAnnotationRepository.deleteAnnotation(PLAN_EXECUTION_ID, CONTEXT_ID)).thenReturn(true);

    // Act
    CreateAnnotationsResponse response = service.createAnnotations(PLAN_EXECUTION_ID, ACCOUNT_ID, request);

    // Assert
    assertThat(response.getProcessed()).isEqualTo(1);
    assertThat(response.getFailed()).isEqualTo(0);

    // Verify GCS and MongoDB deletion
    verify(annotationFileService).deleteAnnotationFile(anyString());
    verify(pipelineAnnotationRepository).deleteAnnotation(PLAN_EXECUTION_ID, CONTEXT_ID);

    // Verify no upsert was called
    verify(pipelineAnnotationRepository, never()).upsertAnnotation(any(), anyString());
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testCreateAnnotations_GcsStorageDisabled() {
    // Arrange
    when(annotationFileService.isGcsStorageEnabled()).thenReturn(false);

    AnnotationRequest annotationRequest = createAnnotationRequest(CONTEXT_ID, "test", MODE_REPLACE);
    CreateAnnotationsRequest request = createRequest(Arrays.asList(annotationRequest));

    // Act
    CreateAnnotationsResponse response = service.createAnnotations(PLAN_EXECUTION_ID, ACCOUNT_ID, request);

    // Assert
    assertThat(response.getProcessed()).isEqualTo(0);
    assertThat(response.getFailed()).isEqualTo(0);
    assertThat(response.getMessage()).contains("Annotations storage is not configured");

    // Verify no GCS or MongoDB operations
    verify(annotationFileService, never()).uploadAnnotationFile(anyString(), anyString(), anyString(), anyString());
    verify(pipelineAnnotationRepository, never()).upsertAnnotation(any(), anyString());
    verify(pipelineTelemetryHelper, never()).sendHarnessAnnotationsUsageTelemetry(anyString(), any());
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testCreateAnnotations_GcsFailure_AnnotationSkipped() throws Exception {
    // Arrange
    AnnotationRequest annotationRequest = createAnnotationRequest(CONTEXT_ID, "test", MODE_REPLACE);
    CreateAnnotationsRequest request = createRequest(Arrays.asList(annotationRequest));

    when(annotationFileService.uploadAnnotationFile(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new RuntimeException("GCS upload failed"));

    // Act
    CreateAnnotationsResponse response = service.createAnnotations(PLAN_EXECUTION_ID, ACCOUNT_ID, request);

    // Assert
    assertThat(response.getProcessed()).isEqualTo(0);
    assertThat(response.getFailed()).isEqualTo(1);
    assertThat(response.getMessage()).contains("Failed to process annotation");

    // Verify MongoDB was not called since GCS failed
    verify(pipelineAnnotationRepository, never()).upsertAnnotation(any(), anyString());
    verify(pipelineTelemetryHelper, never()).sendHarnessAnnotationsUsageTelemetry(anyString(), any());
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testGetAnnotationFullContent_FromGcs_Success() throws Exception {
    // Arrange
    String fullContent = "Line1\nLine2\nLine3\nLine4\nLine5\nLine6\nLine7";
    when(annotationFileService.getAnnotationFileContent(anyString())).thenReturn(fullContent);

    // Act
    AnnotationContentResponseDTO response =
        service.getAnnotationFullContent(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, PLAN_EXECUTION_ID, CONTEXT_ID);

    // Assert
    assertThat(response.getContextId()).isEqualTo(CONTEXT_ID);
    assertThat(response.getContent()).isEqualTo(fullContent);

    // Verify MongoDB was NOT called (optimized path)
    verify(pipelineAnnotationRepository, never()).findByPlanExecutionIdAndContextId(anyString(), anyString());
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testGetAnnotationFullContent_LegacyFallbackAndNotFound() {
    // Test 1: Legacy fallback success
    String legacyContent = "Legacy content";
    when(annotationFileService.getAnnotationFileContent(anyString())).thenThrow(new RuntimeException("File not found"));
    PipelineAnnotationEntity entity = createEntity(CONTEXT_ID, legacyContent);
    when(pipelineAnnotationRepository.findByPlanExecutionIdAndContextId(PLAN_EXECUTION_ID, CONTEXT_ID))
        .thenReturn(entity);

    AnnotationContentResponseDTO response =
        service.getAnnotationFullContent(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, PLAN_EXECUTION_ID, CONTEXT_ID);
    assertThat(response.getContent()).isEqualTo(legacyContent);

    // Test 2: Not found throws exception
    when(pipelineAnnotationRepository.findByPlanExecutionIdAndContextId(PLAN_EXECUTION_ID, CONTEXT_ID))
        .thenReturn(null);
    assertThatThrownBy(()
                           -> service.getAnnotationFullContent(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, PLAN_EXECUTION_ID, CONTEXT_ID))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("Annotation not found");
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testGet_ReturnsAnnotationsWithPreview() {
    // Arrange
    PipelineAnnotationEntity entity1 = createEntity("ctx1", "Preview1");
    PipelineAnnotationEntity entity2 = createEntity("ctx2", "Preview2");

    when(pipelineAnnotationRepository.findAllByPlanExecutionId(PLAN_EXECUTION_ID))
        .thenReturn(Arrays.asList(entity1, entity2));

    // Act
    Optional<PipelineAnnotationsResponseDTO> response =
        service.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, PLAN_EXECUTION_ID);

    // Assert
    assertThat(response).isPresent();
    assertThat(response.get().getAnnotations()).hasSize(2);
    assertThat(response.get().getAnnotations().get(0).getSummary()).isEqualTo("Preview1");
    assertThat(response.get().getAnnotations().get(1).getSummary()).isEqualTo("Preview2");
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testCreateAnnotations_ExceedsMaxAnnotations_ThrowsException() {
    // Arrange - Create 51 annotations (MAX is 50)
    List<AnnotationRequest> tooManyAnnotations = new ArrayList<>();
    for (int i = 0; i < 51; i++) {
      tooManyAnnotations.add(createAnnotationRequest("ctx" + i, "content" + i, MODE_REPLACE));
    }
    CreateAnnotationsRequest request = createRequest(tooManyAnnotations);

    // Act & Assert
    assertThatThrownBy(() -> service.createAnnotations(PLAN_EXECUTION_ID, ACCOUNT_ID, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Maximum 50 annotations allowed");
  }

  // Helper methods
  private AnnotationRequest createAnnotationRequest(String contextId, String summary, String mode) {
    return AnnotationRequest.builder()
        .contextId(contextId)
        .summary(summary)
        .mode(mode)
        .style(AnnotationStyle.INFO.getDisplayName())
        .priority(5)
        .timestamp(System.currentTimeMillis())
        .build();
  }

  private CreateAnnotationsRequest createRequest(List<AnnotationRequest> annotations) {
    return CreateAnnotationsRequest.builder()
        .orgId(ORG_ID)
        .projectId(PROJECT_ID)
        .pipelineId(PIPELINE_ID)
        .stageExecutionId(STAGE_EXECUTION_ID)
        .annotations(annotations)
        .build();
  }

  private PipelineAnnotationEntity createEntity(String contextId, String summary) {
    return PipelineAnnotationEntity.builder()
        .accountId(ACCOUNT_ID)
        .orgId(ORG_ID)
        .projectId(PROJECT_ID)
        .pipelineId(PIPELINE_ID)
        .planExecutionId(PLAN_EXECUTION_ID)
        .contextId(contextId)
        .summary(summary)
        .style(AnnotationStyle.INFO.getDisplayName())
        .priority(5)
        .lastUpdatedAt(System.currentTimeMillis())
        .build();
  }
}
