/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.pms.pipeline.PipelineEntity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Class to hold the results of preparing a pipeline for rerun with original YAML.
 * This is used when a pipeline is being rerun with its original definition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(HarnessTeam.PIPELINE)
public class OriginalYamlRerunResult {
  PipelineEntity pipelineEntity;
  JsonNode originalInputs;
  PlanExecutionMetadataWithContext metadataWithContext;
}
