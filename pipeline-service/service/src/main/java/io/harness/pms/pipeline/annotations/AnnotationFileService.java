/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.annotations;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

/**
 * Service for managing annotation files in GCS (Google Cloud Storage).
 * Handles upload, retrieval, append, and deletion of annotation content files.
 */
@OwnedBy(CI)
public interface AnnotationFileService {
  /**
   * Check if GCS storage is enabled for annotations.
   * @return true if GCS client is configured, false otherwise
   */
  boolean isGcsStorageEnabled();

  /**
   * Uploads annotation content to GCS and returns the file path.
   *
   * @param accountId Account identifier
   * @param planExecutionId Plan execution identifier
   * @param contextId Context identifier
   * @param content Full annotation content
   * @return GCS file path where content was stored
   */
  String uploadAnnotationFile(String accountId, String planExecutionId, String contextId, String content);

  /**
   * Retrieves full annotation content from GCS.
   *
   * @param filePath GCS file path
   * @return Full annotation content
   */
  String getAnnotationFileContent(String filePath);

  /**
   * Appends new content to existing annotation file in GCS.
   * Fetches existing content, appends new content, and uploads back.
   *
   * @param filePath GCS file path
   * @param newContent Content to append
   * @return GCS file path (same as input)
   */
  String appendToAnnotationFile(String filePath, String newContent);

  /**
   * Deletes annotation file from GCS.
   * Does not throw exception if file doesn't exist.
   *
   * @param filePath GCS file path
   */
  void deleteAnnotationFile(String filePath);
}
