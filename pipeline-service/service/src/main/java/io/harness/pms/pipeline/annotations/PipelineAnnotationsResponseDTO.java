/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.annotations;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.annotations.PipelineAnnotation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CI)
public class PipelineAnnotationsResponseDTO {
  @NotNull private String accountId;
  @NotNull private String orgId;
  @NotNull private String projectId;
  @NotNull private String pipelineId;
  @NotNull private String planExecutionId;
  @NotNull private List<PipelineAnnotation> annotations;
  @NotNull private Long createdAt;
  @NotNull private Long lastUpdatedAt;
}
