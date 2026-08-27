/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.annotations;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

/**
 * Constants for annotation operations
 */
@UtilityClass
@OwnedBy(CI)
public class AnnotationConstants {
  // Mode constants (lowercase for comparison)
  public static final String MODE_REPLACE = "replace";
  public static final String MODE_APPEND = "append";
  public static final String MODE_DELETE = "delete";

  // Validation constants
  public static final int MAX_ANNOTATIONS_PER_EXECUTION = 50;
  public static final int MAX_SUMMARY_SIZE_BYTES = 64 * 1024; // 64KB
  public static final int MAX_CONTEXT_NAME_LENGTH = 256;

  // GCS storage constants
  public static final String ANNOTATION_FILE_NAME = "annotation-summary.txt";
  public static final String ANNOTATION_COLLECTION_NAME = "pipelineAnnotations";
  public static final int PREVIEW_LINES_COUNT = 5;

  // Default values
  public static final String DEFAULT_STYLE = "INFO";
  public static final int DEFAULT_PRIORITY = 3;
}
