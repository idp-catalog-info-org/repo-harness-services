/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.annotations;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.annotations.AnnotationContentResponseDTO;
import io.harness.pms.annotations.CreateAnnotationsRequest;
import io.harness.pms.annotations.CreateAnnotationsResponse;

import java.util.Optional;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PipelineAnnotationsService {
  /**
   * Get annotations for a pipeline execution.
   */
  Optional<PipelineAnnotationsResponseDTO> get(
      String accountId, String orgId, String projectId, String pipelineId, String planExecutionId);

  /**
   * Create or update annotations for a pipeline execution.
   * Called directly by lite-engine after step execution.
   */
  CreateAnnotationsResponse createAnnotations(
      String planExecutionId, String accountId, CreateAnnotationsRequest request);

  /**
   * Get full annotation content from GCS for a specific context.
   * Uses deterministic GCS path based on accountId, planExecutionId, and contextId.
   *
   * @param accountId Account identifier
   * @param orgId Organization identifier
   * @param projectId Project identifier
   * @param pipelineId Pipeline identifier
   * @param planExecutionId Plan execution identifier
   * @param contextId Context identifier
   * @return Full annotation content
   */
  AnnotationContentResponseDTO getAnnotationFullContent(
      String accountId, String orgId, String projectId, String pipelineId, String planExecutionId, String contextId);
}
