/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.unified;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.unified.UnifiedPmsAbstractStepNode;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepSpecTypeConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.PIPELINE_STAGE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class UnifiedPipelineStageNode extends UnifiedPmsAbstractStepNode {
  @JsonProperty(YAMLFieldNameConstants.UNIFIED_PIPELINE_CHAIN) UnifiedPipelineStageInfo unifiedPipelineStageInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.PIPELINE_STAGE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }
}
