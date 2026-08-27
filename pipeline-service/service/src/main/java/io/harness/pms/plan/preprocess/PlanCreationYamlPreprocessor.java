/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.preprocess;

import io.harness.pms.contracts.plan.ExecutionMode;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Interface for preprocessing YAML during plan creation.
 * Implementations handle version-specific preprocessing operations such as stage injection.
 */
public interface PlanCreationYamlPreprocessor {
  /**
   * Preprocesses the pipeline YAML by injecting additional stages or making other modifications.
   *
   * @param pipelineJsonNode the pipeline JSON node to preprocess
   * @param accountId the account identifier
   * @param orgId the organization identifier
   * @param projectId the project identifier
   * @param executionUuid the execution UUID
   * @param pipelineId the pipeline identifier
   * @param executionMode the execution mode
   * @return the preprocessed JSON node, or the original node if no changes were made
   */
  JsonNode preprocessPipelineYaml(JsonNode pipelineJsonNode, String accountId, String orgId, String projectId,
      String executionUuid, String pipelineId, ExecutionMode executionMode);
}
