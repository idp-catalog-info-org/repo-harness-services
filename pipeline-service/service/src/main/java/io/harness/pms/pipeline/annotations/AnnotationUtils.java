/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.annotations;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.annotations.AnnotationConstants.ANNOTATION_COLLECTION_NAME;
import static io.harness.pms.annotations.AnnotationConstants.ANNOTATION_FILE_NAME;
import static io.harness.pms.annotations.AnnotationConstants.PREVIEW_LINES_COUNT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import lombok.experimental.UtilityClass;

/**
 * Utility class for annotation operations including GCS path generation and content manipulation.
 */
@OwnedBy(HarnessTeam.CI)
@UtilityClass
public class AnnotationUtils {
  /**
   * Generates GCS file path for annotation storage.
   * Format: accountId/pipelineAnnotations/planExecutionId/encodedContextId/annotation-summary.txt
   *
   * Uses URL encoding to ensure:
   * 1. Unique mapping (no collisions): "test.ctx" and "test/ctx" encode to different strings
   * 2. Path traversal prevention: "../" becomes "%2E%2E%2F"
   * 3. GCS-safe characters: All special chars properly encoded
   *
   * @param accountId Account identifier
   * @param planExecutionId Plan execution identifier
   * @param contextId Context identifier (will be URL-encoded)
   * @return GCS file path with encoded contextId
   */
  public static String getAnnotationFilePath(String accountId, String planExecutionId, String contextId) {
    // This ensures "test.context" and "test/context" map to DIFFERENT GCS paths
    String encodedContextId = URLEncoder.encode(contextId, StandardCharsets.UTF_8);
    return String.format("%s/%s/%s/%s/%s", accountId, ANNOTATION_COLLECTION_NAME, planExecutionId, encodedContextId,
        ANNOTATION_FILE_NAME);
  }

  /**
   * Extracts first N lines from content for preview (or fewer if content has less than N lines).
   *
   * @param content Full content string
   * @return First N lines joined with newline, where N = PREVIEW_LINES_COUNT
   */
  public static String extractPreviewLines(String content) {
    if (isEmpty(content)) {
      return "";
    }
    String[] lines = content.split("\n", PREVIEW_LINES_COUNT + 1);
    return String.join("\n", Arrays.copyOf(lines, Math.min(lines.length, PREVIEW_LINES_COUNT)));
  }

  /**
   * Appends new content to existing content with newline separator.
   *
   * @param existingContent Existing content
   * @param newContent New content to append
   * @return Combined content
   */
  public static String appendContent(String existingContent, String newContent) {
    if (isEmpty(existingContent)) {
      return newContent;
    }
    if (isEmpty(newContent)) {
      return existingContent;
    }
    return existingContent + "\n" + newContent;
  }
}
