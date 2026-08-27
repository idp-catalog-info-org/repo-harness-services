/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.walktree.visitor.helper.SimpleVisitorHelper;
import io.harness.walktree.visitor.helper.Visitable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@SimpleVisitorHelper(helperClass = OPAEvaluationAggregatorStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR)
@TypeAlias("opaEvaluationAggregatorStepInfo")
@RecasterAlias("io.harness.steps.opa.OPAEvaluationAggregatorStepInfo")
public class OPAEvaluationAggregatorStepInfo implements PMSStepInfo, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> evaluationId;

  @Builder(builderMethodName = "infoBuilder")
  public OPAEvaluationAggregatorStepInfo(ParameterField<String> evaluationId) {
    this.evaluationId = evaluationId;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return OPAEvaluationAggregatorStepParameters.infoBuilder().evaluationId(getEvaluationId()).build();
  }

  @Override
  public ExpressionMode getExpressionMode() {
    return ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED;
  }
}
