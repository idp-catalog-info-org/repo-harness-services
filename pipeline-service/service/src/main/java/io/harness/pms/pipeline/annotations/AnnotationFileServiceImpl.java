/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.annotations;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InternalServerErrorException;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.objectstore.StorageObject;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of AnnotationFileService that stores annotation content in GCS.
 * Uses dedicated AnnotationsObjectStoreClient for annotation storage.
 */
@OwnedBy(CI)
@Slf4j
public class AnnotationFileServiceImpl implements AnnotationFileService {
  @Nullable @Inject @Named("AnnotationsObjectStoreClient") private ObjectStoreClient objectStoreClient;

  /**
   * Check if GCS storage is enabled for annotations.
   * @return true if GCS client is configured, false otherwise
   */
  public boolean isGcsStorageEnabled() {
    return objectStoreClient != null;
  }

  private void validateObjectStoreClient() {
    if (objectStoreClient == null) {
      log.error("GCS ObjectStore client is not configured. Annotation storage requires GCS.");
      throw new InternalServerErrorException(
          "GCS ObjectStore client is not configured for annotations. This is a critical system error. "
          + "Verify ENABLE_ANNOTATIONS_GCS_STORAGE=true and ANNOTATIONS_BUCKET_NAME environment variables are set "
          + "correctly.");
    }
  }

  @Override
  public String uploadAnnotationFile(String accountId, String planExecutionId, String contextId, String content) {
    validateObjectStoreClient();
    try {
      String filePath = AnnotationUtils.getAnnotationFilePath(accountId, planExecutionId, contextId);
      byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

      StorageObject object = objectStoreClient.uploadObject(filePath, contentBytes);
      log.debug("Successfully uploaded annotation file: {}, size: {} bytes", filePath, object.getSize());

      return filePath;
    } catch (Exception e) {
      log.error("Failed to upload annotation to GCS for account: {}, planExecutionId: {}, contextId: {}. "
              + "Annotation storage requires GCS to be operational.",
          accountId, planExecutionId, contextId, e);
      throw new InternalServerErrorException("Failed to store annotation in GCS. This is a critical system error.", e);
    }
  }

  @Override
  public String getAnnotationFileContent(String filePath) {
    validateObjectStoreClient();
    try {
      StorageObject object = objectStoreClient.getObject(filePath);
      if (object == null) {
        log.debug("Annotation file not found in GCS: {} (expected for legacy annotations)", filePath);
        throw new EntityNotFoundException("Annotation file not found at path: " + filePath);
      }

      byte[] content = object.getContent();
      String contentString = new String(content, StandardCharsets.UTF_8);
      log.debug("Successfully retrieved annotation file: {}, size: {} bytes", filePath, content.length);
      return contentString;
    } catch (EntityNotFoundException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to retrieve annotation from GCS: {}. Annotation retrieval requires GCS access.", filePath, e);
      throw new InternalServerErrorException(
          "Failed to retrieve annotation from GCS. This is a critical system error.", e);
    }
  }

  @Override
  public String appendToAnnotationFile(String filePath, String newContent) {
    validateObjectStoreClient();
    try {
      String existingContent = getAnnotationFileContent(filePath);
      String combinedContent = AnnotationUtils.appendContent(existingContent, newContent);
      byte[] contentBytes = combinedContent.getBytes(StandardCharsets.UTF_8);
      objectStoreClient.uploadObject(filePath, contentBytes);
      log.debug("Successfully appended to annotation file: {}", filePath);
      return filePath;
    } catch (Exception e) {
      log.error("Failed to append annotation in GCS: {}. Annotation append requires GCS access.", filePath, e);
      throw new InternalServerErrorException("Failed to append annotation in GCS. This is a critical system error.", e);
    }
  }

  @Override
  public void deleteAnnotationFile(String filePath) {
    validateObjectStoreClient();
    try {
      Map<String, Boolean> result = objectStoreClient.deleteObjectsByPaths(List.of(filePath));
      if (Boolean.FALSE.equals(result.get(filePath))) {
        log.warn("Failed to delete annotation file from GCS: {}, continuing with MongoDB deletion", filePath);
      } else {
        log.info("Successfully deleted annotation file from GCS: {}", filePath);
      }
    } catch (Exception e) {
      log.error("Error deleting annotation file from GCS: {}. MongoDB deletion will proceed.", filePath, e);
    }
  }
}
