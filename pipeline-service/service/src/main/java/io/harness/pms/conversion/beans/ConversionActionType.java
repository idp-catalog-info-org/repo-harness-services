/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.beans;

/**
 * Enum representing the type of conversion action.
 */
public enum ConversionActionType {
  /**
   * Single entity conversion - convert a single pipeline or template.
   */
  SINGLE,

  /**
   * Batch conversion - convert a specific list of entities (mix of pipelines/templates).
   */
  BATCH,

  /**
   * Project-level conversion - convert all entities in a project.
   */
  PROJECT
}
