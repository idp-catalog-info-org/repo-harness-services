/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.annotations;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;

/**
 * Request DTO for creating/updating pipeline annotations.
 * Called directly by lite-engine after step execution.
 */
@OwnedBy(CI)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "CreateAnnotationsRequest", description = "Request to create or update pipeline annotations")
public class CreateAnnotationsRequest {
  @NotNull @Schema(description = "Organization identifier ") String orgId;

  @NotNull @Schema(description = "Project identifier ") String projectId;

  @NotNull @Schema(description = "Pipeline identifier") String pipelineId;

  @NotNull @Schema(description = "Plan execution ID") String planExecutionId;

  @NotNull @Schema(description = "Stage execution ID") String stageExecutionId;

  @NotNull
  @Size(max = 50, message = "Maximum 50 annotations per execution")
  @Schema(description = "List of annotations to create or update")
  List<AnnotationRequest> annotations;
}
