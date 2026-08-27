/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR)
@TypeAlias("opaEvaluationAggregatorStepNode")
@RecasterAlias("io.harness.steps.opa.OPAEvaluationAggregatorStepNode")
public class OPAEvaluationAggregatorStepNode extends PmsAbstractStepNode {
  @JsonProperty("type") @NotNull StepType type = StepType.OPAEvaluationAggregator;

  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  OPAEvaluationAggregatorStepInfo opaEvaluationAggregatorStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return opaEvaluationAggregatorStepInfo;
  }

  enum StepType {
    OPAEvaluationAggregator(StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR);
    @Getter String name;

    StepType(String name) {
      this.name = name;
    }
  }
}
