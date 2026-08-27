/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.preprocess;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.agent.convert.V1ToV0StepGroupConverter;
import io.harness.agent.expansion.AgentTemplateExpansionService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.ExecutionMode;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin adapter that delegates to the commons implementation in 877-pipeline-ci-cd-commons.
 */
@Slf4j
@OwnedBy(PIPELINE)
public class AgentStepPreprocessor implements PlanCreationYamlPreprocessor {
  private final io.harness.agent.preprocess.AgentStepPreprocessor delegate;

  public AgentStepPreprocessor(
      AgentTemplateExpansionService agentTemplateExpansionService, V1ToV0StepGroupConverter v1ToV0StepGroupConverter) {
    this.delegate =
        new io.harness.agent.preprocess.AgentStepPreprocessor(agentTemplateExpansionService, v1ToV0StepGroupConverter);
  }

  @Override
  public JsonNode preprocessPipelineYaml(JsonNode pipelineJsonNode, String accountId, String orgId, String projectId,
      String executionUuid, String pipelineId, ExecutionMode executionMode) {
    return delegate.preprocessPipelineYaml(
        pipelineJsonNode, accountId, orgId, projectId, executionUuid, pipelineId, executionMode);
  }
}
