/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.preprocess;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.stages.OpaEvaluationStageHelper;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

/**
 * V0 implementation of PlanCreationYamlPreprocessor.
 * Handles OPA evaluation stage injection for V0 pipelines.
 */
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(HarnessTeam.PIPELINE)
public class PlanCreationYamlPreprocessorV0 implements PlanCreationYamlPreprocessor {
  private final OpaEvaluationStageHelper opaEvaluationStageHelper;

  public PlanCreationYamlPreprocessorV0(OpaEvaluationStageHelper opaEvaluationStageHelper) {
    this.opaEvaluationStageHelper = opaEvaluationStageHelper;
  }

  @Override
  public JsonNode preprocessPipelineYaml(JsonNode pipelineJsonNode, String accountId, String orgId, String projectId,
      String executionUuid, String pipelineId, ExecutionMode executionMode) {
    if (opaEvaluationStageHelper == null) {
      log.debug("OPA evaluation stage helper is null, skipping stage injection");
      return pipelineJsonNode;
    }

    try {
      String jsonString = JsonPipelineUtils.getJsonString(pipelineJsonNode);
      String updatedYaml = opaEvaluationStageHelper.injectOpaStageIntoProcessedYaml(
          accountId, orgId, projectId, executionUuid, pipelineId, executionMode, jsonString);
      return JsonPipelineUtils.readTree(updatedYaml);
    } catch (Exception ex) {
      log.error("Error preprocessing pipeline YAML in V0 preprocessor", ex);
      return pipelineJsonNode;
    }
  }
}
