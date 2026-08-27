/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.annotations;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.annotations.PipelineAnnotationEntity;

import java.util.List;

@OwnedBy(CI)
public interface PipelineAnnotationRepositoryCustom {
  /**
   * Upsert a single annotation for a specific context within a pipeline execution.
   * Behavior depends on mode parameter (operational instruction, not persisted):
   *   - MODE_REPLACE (or null/empty): Replace all fields with new values if present, keep old if not
   *   - MODE_APPEND: Append summary to existing, replace other fields if present
   *   - MODE_DELETE: Should call deleteAnnotation() instead
   * If not exists, create new document.
   *
   * @param annotationEntity The annotation entity to upsert (one context per entity)
   * @param mode The operation mode (not stored in DB, just used for this operation)
   * @return The updated annotation entity
   * @see io.harness.pms.annotations.AnnotationConstants#MODE_REPLACE
   * @see io.harness.pms.annotations.AnnotationConstants#MODE_APPEND
   * @see io.harness.pms.annotations.AnnotationConstants#MODE_DELETE
   */
  PipelineAnnotationEntity upsertAnnotation(PipelineAnnotationEntity annotationEntity, String mode);

  /**
   * Delete a specific annotation by its composite key
   *
   * @param planExecutionId The plan execution ID
   * @param contextId The context name
   * @return true if document was deleted, false if not found
   */
  boolean deleteAnnotation(String planExecutionId, String contextId);

  /**
   * Find ALL annotations by planExecutionId (multiple documents, one per context)
   *
   * @param planExecutionId The plan execution ID
   * @return List of annotation entities (one per context)
   */
  List<PipelineAnnotationEntity> findAllByPlanExecutionId(String planExecutionId);

  /**
   * Find a single annotation by planExecutionId and contextId
   *
   * @param planExecutionId The plan execution ID
   * @param contextId The context identifier
   * @return The annotation entity, or null if not found
   */
  PipelineAnnotationEntity findByPlanExecutionIdAndContextId(String planExecutionId, String contextId);
}
