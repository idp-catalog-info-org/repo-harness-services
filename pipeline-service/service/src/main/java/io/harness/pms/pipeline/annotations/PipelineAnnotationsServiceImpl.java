/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.annotations;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.annotations.AnnotationConstants.DEFAULT_PRIORITY;
import static io.harness.pms.annotations.AnnotationConstants.MAX_ANNOTATIONS_PER_EXECUTION;
import static io.harness.pms.annotations.AnnotationConstants.MAX_CONTEXT_NAME_LENGTH;
import static io.harness.pms.annotations.AnnotationConstants.MAX_SUMMARY_SIZE_BYTES;
import static io.harness.pms.annotations.AnnotationConstants.MODE_APPEND;
import static io.harness.pms.annotations.AnnotationConstants.MODE_DELETE;
import static io.harness.pms.annotations.AnnotationConstants.MODE_REPLACE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.annotations.AnnotationStyle;
import io.harness.beans.annotations.PipelineAnnotation;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.annotations.AnnotationContentResponseDTO;
import io.harness.pms.annotations.AnnotationRequest;
import io.harness.pms.annotations.CreateAnnotationsRequest;
import io.harness.pms.annotations.CreateAnnotationsResponse;
import io.harness.pms.annotations.PipelineAnnotationEntity;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.repositories.annotations.PipelineAnnotationRepository;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Singleton
@Slf4j
public class PipelineAnnotationsServiceImpl implements PipelineAnnotationsService {
  @Inject private PipelineAnnotationRepository pipelineAnnotationRepository;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private AnnotationFileService annotationFileService;
  @Inject private PipelineTelemetryHelper pipelineTelemetryHelper;

  @Override
  public Optional<PipelineAnnotationsResponseDTO> get(
      String accountId, String orgId, String projectId, String pipelineId, String planExecutionId) {
    validateRequiredParams(accountId, orgId, projectId, pipelineId, planExecutionId);
    if (!pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_HARNESS_ANNOTATIONS)) {
      log.debug("Pipeline annotations feature is disabled, returning empty response");
      return Optional.empty();
    }

    List<PipelineAnnotationEntity> entities = pipelineAnnotationRepository.findAllByPlanExecutionId(planExecutionId);

    if (entities == null || entities.isEmpty()) {
      return Optional.empty();
    }

    List<PipelineAnnotation> pipelineAnnotations = new ArrayList<>();
    for (PipelineAnnotationEntity entity : entities) {
      PipelineAnnotation pipelineAnnotation =
          PipelineAnnotation.builder()
              .contextId(entity.getContextId())
              .timestamp(entity.getLastUpdatedAt() != null ? entity.getLastUpdatedAt() : System.currentTimeMillis())
              .style(convertStyle(entity.getStyle()))
              .summary(entity.getSummary())
              .priority(convertPriority(entity.getPriority()))
              .build();
      pipelineAnnotations.add(pipelineAnnotation);
    }

    // Use first entity for common fields (all entities have same values for these)
    PipelineAnnotationEntity firstEntity = entities.get(0);

    // Find the latest and earliest lastUpdatedAt across all documents
    Long latestUpdatedAt = entities.stream()
                               .map(PipelineAnnotationEntity::getLastUpdatedAt)
                               .filter(java.util.Objects::nonNull)
                               .max(Long::compare)
                               .orElse(null);

    Long earliestUpdatedAt = entities.stream()
                                 .map(PipelineAnnotationEntity::getLastUpdatedAt)
                                 .filter(java.util.Objects::nonNull)
                                 .min(Long::compare)
                                 .orElse(null);

    return Optional.of(PipelineAnnotationsResponseDTO.builder()
                           .accountId(firstEntity.getAccountId())
                           .orgId(firstEntity.getOrgId())
                           .projectId(firstEntity.getProjectId())
                           .planExecutionId(firstEntity.getPlanExecutionId())
                           .annotations(pipelineAnnotations)
                           .createdAt(earliestUpdatedAt)
                           .lastUpdatedAt(latestUpdatedAt)
                           .build());
  }

  @Override
  public CreateAnnotationsResponse createAnnotations(
      String planExecutionId, String accountId, CreateAnnotationsRequest request) {
    // Check feature flag
    if (!pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_HARNESS_ANNOTATIONS)) {
      log.debug("Pipeline annotations feature is disabled, returning empty response");
      return CreateAnnotationsResponse.builder()
          .processed(0)
          .failed(0)
          .message("Pipeline annotations feature is disabled")
          .build();
    }

    // Check if GCS storage is enabled for annotations
    if (!annotationFileService.isGcsStorageEnabled()) {
      log.warn("Annotations GCS storage is not enabled. Annotations feature requires GCS to be configured. "
          + "Set ENABLE_ANNOTATIONS_GCS_STORAGE=true and ANNOTATIONS_BUCKET_NAME to enable this feature.");
      return CreateAnnotationsResponse.builder()
          .processed(0)
          .failed(0)
          .message("Annotations storage is not configured. Please contact your administrator to enable GCS storage for "
              + "annotations.")
          .build();
    }

    // Validate required fields
    if (request == null) {
      throw new InvalidRequestException("Request cannot be null");
    }

    if (request.getAnnotations() == null || request.getAnnotations().isEmpty()) {
      throw new InvalidRequestException("Annotations map cannot be null or empty");
    }
    if (request.getAnnotations().size() > MAX_ANNOTATIONS_PER_EXECUTION) {
      throw new InvalidRequestException(
          String.format("Maximum %d annotations allowed per execution", MAX_ANNOTATIONS_PER_EXECUTION));
    }

    int processedCount = 0;
    int failedCount = 0;
    StringBuilder errorMessages = new StringBuilder(256);

    // Process each annotation
    for (AnnotationRequest annotationRequest : request.getAnnotations()) {
      String contextId = annotationRequest.getContextId();

      try {
        // Validate context name
        if (isEmpty(contextId)) {
          failedCount++;
          errorMessages.append("Context name cannot be empty; ");
          continue;
        }
        if (contextId.length() > MAX_CONTEXT_NAME_LENGTH) {
          contextId = contextId.substring(0, MAX_CONTEXT_NAME_LENGTH);
        }

        // Extract mode (default to "replace")
        String mode = MODE_REPLACE;
        if (isNotEmpty(annotationRequest.getMode())) {
          mode = annotationRequest.getMode();
          if (!MODE_REPLACE.equals(mode) && !MODE_APPEND.equals(mode) && !MODE_DELETE.equals(mode)) {
            log.warn("Invalid mode '{}' for context '{}', defaulting to replace", mode, contextId);
            mode = MODE_REPLACE;
          }
        }

        // Handle DELETE mode
        if (MODE_DELETE.equals(mode)) {
          try {
            String gcsFilePath = AnnotationUtils.getAnnotationFilePath(accountId, planExecutionId, contextId);
            annotationFileService.deleteAnnotationFile(gcsFilePath);
          } catch (Exception e) {
            log.warn("Failed to delete annotation file from GCS for context: {} (file may not exist)", contextId, e);
          }

          boolean deleted = pipelineAnnotationRepository.deleteAnnotation(planExecutionId, contextId);
          if (deleted) {
            log.info("Deleted annotation for context: {} in planExecutionId: {}", contextId, planExecutionId);
          }
          processedCount++;
          continue;
        }

        // Extract annotation fields
        String summary = annotationRequest.getSummary();

        // Validate summary size (in bytes)
        if (summary.getBytes().length > MAX_SUMMARY_SIZE_BYTES) {
          log.warn("Summary exceeds max size for context '{}', truncating", contextId);
          // Truncate to MAX_SUMMARY_SIZE_BYTES
          byte[] summaryBytes = summary.getBytes();
          byte[] truncated = new byte[MAX_SUMMARY_SIZE_BYTES - 20]; // Reserve space for message
          System.arraycopy(summaryBytes, 0, truncated, 0, truncated.length);
          summary = new String(truncated) + "\n... (truncated)";
        }

        String summaryPreview;

        try {
          String gcsFilePath = AnnotationUtils.getAnnotationFilePath(accountId, planExecutionId, contextId);

          if (MODE_REPLACE.equals(mode)) {
            // REPLACE mode: Upload full content to GCS, store preview in MongoDB
            annotationFileService.uploadAnnotationFile(accountId, planExecutionId, contextId, summary);
            summaryPreview = AnnotationUtils.extractPreviewLines(summary);
            log.debug("REPLACE: Uploaded to GCS and extracted preview for context: {}", contextId);

          } else {
            // APPEND mode: Get existing GCS content, append new content, upload combined content
            String combinedContent;
            try {
              String existingContent = annotationFileService.getAnnotationFileContent(gcsFilePath);
              combinedContent = AnnotationUtils.appendContent(existingContent, summary);
              log.debug("APPEND: Appending to existing GCS file for context: {}", contextId);
            } catch (Exception e) {
              PipelineAnnotationEntity existingEntity =
                  pipelineAnnotationRepository.findByPlanExecutionIdAndContextId(planExecutionId, contextId);

              if (existingEntity != null && existingEntity.getSummary() != null) {
                combinedContent = AnnotationUtils.appendContent(existingEntity.getSummary(), summary);
                log.info("APPEND: Migrating legacy MongoDB content to GCS for context: {}", contextId);
              } else {
                combinedContent = summary;
                log.debug("APPEND: Creating new GCS file for context: {}", contextId);
              }
            }

            annotationFileService.uploadAnnotationFile(accountId, planExecutionId, contextId, combinedContent);
            summaryPreview = AnnotationUtils.extractPreviewLines(combinedContent);
          }
        } catch (Exception e) {
          log.error("Failed to store annotation in GCS for account: {}, planExecutionId: {}, contextId: {}, mode: {}. "
                  + "This annotation will be skipped. Error: {}",
              accountId, planExecutionId, contextId, mode, e.getMessage(), e);
          throw e;
        }

        PipelineAnnotationEntity entity = PipelineAnnotationEntity.builder()
                                              .accountId(accountId)
                                              .orgId(request.getOrgId())
                                              .projectId(request.getProjectId())
                                              .pipelineId(request.getPipelineId())
                                              .planExecutionId(planExecutionId)
                                              .stageExecutionId(request.getStageExecutionId())
                                              .contextId(contextId)
                                              .style(convertStyle(annotationRequest.getStyle()).getDisplayName())
                                              .summary(summaryPreview)
                                              .createdAt(annotationRequest.getTimestamp())
                                              .priority(convertPriority(annotationRequest.getPriority()))
                                              .stepId(annotationRequest.getStepId())
                                              .build();

        // Upsert to MongoDB
        pipelineAnnotationRepository.upsertAnnotation(entity, mode);
        processedCount++;

        log.debug("Successfully processed annotation for context: {} with mode: {}", contextId, mode);

      } catch (Exception e) {
        failedCount++;
        String errorMsg = String.format("Failed to process annotation for context '%s': %s", contextId, e.getMessage());
        errorMessages.append(errorMsg).append("; ");
        log.error(errorMsg, e);
      }
    }

    log.info("Annotation processing complete for planExecutionId: {}. Processed: {}, Failed: {}", planExecutionId,
        processedCount, failedCount);

    if (processedCount > 0) {
      try {
        pipelineTelemetryHelper.sendHarnessAnnotationsUsageTelemetry(accountId, request);
      } catch (Exception e) {
        log.error("Failed to send Harness annotations usage telemetry for planExecutionId: {}", planExecutionId, e);
      }
    }

    return CreateAnnotationsResponse.builder()
        .processed(processedCount)
        .failed(failedCount)
        .message(failedCount > 0 ? errorMessages.toString() : "All annotations processed successfully")
        .build();
  }

  @Override
  public AnnotationContentResponseDTO getAnnotationFullContent(
      String accountId, String orgId, String projectId, String pipelineId, String planExecutionId, String contextId) {
    validateRequiredParams(accountId, orgId, projectId, pipelineId, planExecutionId);
    if (isEmpty(contextId)) {
      throw new InvalidRequestException("Context ID cannot be null or empty");
    }

    // If GCS is not enabled, fetch from MongoDB for legacy annotations
    if (!annotationFileService.isGcsStorageEnabled()) {
      log.debug("GCS storage not enabled, fetching from MongoDB for context: {}", contextId);
      return fetchAnnotationFromMongoDB(planExecutionId, contextId);
    }

    // Construct deterministic GCS file path and try to fetch from GCS
    String gcsFilePath = AnnotationUtils.getAnnotationFilePath(accountId, planExecutionId, contextId);

    try {
      String content = annotationFileService.getAnnotationFileContent(gcsFilePath);
      log.debug("Retrieved full annotation content from GCS for context: {}", contextId);
      return AnnotationContentResponseDTO.builder().contextId(contextId).content(content).build();

    } catch (Exception e) {
      // GCS file not found - fallback to MongoDB for legacy annotations
      log.debug("GCS file not found for context: {}, falling back to MongoDB (legacy annotation)", contextId);
      return fetchAnnotationFromMongoDB(planExecutionId, contextId);
    }
  }

  /**
   * Fetches annotation content from MongoDB (for legacy annotations or when GCS is disabled).
   *
   * @param planExecutionId The plan execution ID
   * @param contextId The context ID
   * @return AnnotationContentResponseDTO containing the annotation content
   * @throws EntityNotFoundException if annotation is not found or has no content
   */
  private AnnotationContentResponseDTO fetchAnnotationFromMongoDB(String planExecutionId, String contextId) {
    PipelineAnnotationEntity entity =
        pipelineAnnotationRepository.findByPlanExecutionIdAndContextId(planExecutionId, contextId);

    if (entity == null) {
      log.warn("Annotation not found for planExecutionId: {}, contextId: {}", planExecutionId, contextId);
      throw new EntityNotFoundException(
          String.format("Annotation not found for planExecutionId: %s, contextId: %s", planExecutionId, contextId));
    }

    String content = entity.getSummary();
    if (isEmpty(content)) {
      log.error("No content available for annotation - planExecutionId: {}, contextId: {}", planExecutionId, contextId);
      throw new EntityNotFoundException(
          "Annotation content not found for planExecutionId: " + planExecutionId + ", contextId: " + contextId);
    }

    log.debug("Retrieved annotation content from MongoDB for context: {}", contextId);
    return AnnotationContentResponseDTO.builder().contextId(contextId).content(content).build();
  }

  private Integer convertPriority(Integer value) {
    if (value == null || value < 1 || value > 10) {
      return DEFAULT_PRIORITY;
    }
    return value;
  }

  private AnnotationStyle convertStyle(String style) {
    if (style == null || style.isEmpty()) {
      return AnnotationStyle.INFO;
    }
    try {
      return AnnotationStyle.valueOf(style.toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("Unknown annotation style: {}, defaulting to INFO", style);
      return AnnotationStyle.INFO;
    }
  }

  private void validateRequiredParams(
      String accountId, String orgId, String projectId, String pipelineId, String planExecutionId) {
    if (isEmpty(accountId)) {
      throw new InvalidRequestException("Account ID cannot be null or empty");
    }
    if (isEmpty(orgId)) {
      throw new InvalidRequestException("Organization ID cannot be null or empty");
    }
    if (isEmpty(projectId)) {
      throw new InvalidRequestException("Project ID cannot be null or empty");
    }
    if (isEmpty(pipelineId)) {
      throw new InvalidRequestException("Pipeline ID cannot be null or empty");
    }
    if (isEmpty(planExecutionId)) {
      throw new InvalidRequestException("Plan Execution ID cannot be null or empty");
    }
  }
}
