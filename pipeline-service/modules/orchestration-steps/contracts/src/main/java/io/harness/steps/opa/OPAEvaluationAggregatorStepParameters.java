/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Data
@NoArgsConstructor
@TypeAlias("opaEvaluationAggregatorStepParameters")
@RecasterAlias("io.harness.steps.opa.OPAEvaluationAggregatorStepParameters")
public class OPAEvaluationAggregatorStepParameters implements SpecParameters {
  ParameterField<String> evaluationId;

  @Builder(builderMethodName = "infoBuilder")
  public OPAEvaluationAggregatorStepParameters(ParameterField<String> evaluationId) {
    this.evaluationId = evaluationId;
  }

  public String getStepType() {
    return StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR;
  }
}
