/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.governance.GovernanceMetadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/**
 * Response DTO for OPA onSave governance status in the GET Pipeline API response.
 * Mirrors the internal OpaOnSaveStatusDTO shape for API consumers / UI codegen.
 */
@Value
@Builder
@OwnedBy(PIPELINE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "OpaOnSaveStatus",
    description = "OPA onSave governance status for a remote pipeline; present when evaluated")
public class OpaOnSaveStatusResponseDTO {
  @Schema(description = "OPA evaluation status") OpaOnSaveEvaluationStatus status;

  @Schema(description = "Git repository URL of the evaluated entity") String repoURL;

  @Schema(description = "File path of the pipeline in the repository") String filePath;

  @Schema(description = "Commit ID at which the evaluation was performed") String evaluatedAtCommitId;

  @Schema(description = "Last commit ID that passed OPA evaluation cleanly") String lastValidCommitId;

  @Schema(description = "Epoch millis when the evaluation was performed") Long evaluatedAt;

  @Schema(description = "Aggregated deny messages from policy violations; empty for passing evaluations")
  String message;

  @Schema(description = "Commit ID of the pipeline version being viewed/validated; compare with evaluatedAtCommitId to "
          + "detect staleness")
  String currentCommitId;

  @Schema(description = "Full governance metadata including policy set details") GovernanceMetadata governanceMetadata;
}
