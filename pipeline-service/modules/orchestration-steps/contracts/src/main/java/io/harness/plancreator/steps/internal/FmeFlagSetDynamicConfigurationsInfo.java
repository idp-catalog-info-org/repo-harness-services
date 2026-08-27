/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeFlagSetDynamicConfigurationsParameters;
import io.harness.steps.fme.TreatmentDynamicConfiguration;
import io.harness.walktree.visitor.helper.Visitable;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.schema.YamlSchemaTypes;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.FME)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonTypeName(StepSpecTypeConstants.FME_FLAG_SET_DYNAMIC_CONFIGURATIONS)
@TypeAlias("fmeFlagSetDynamicConfigurationsInfo")
@RecasterAlias("io.harness.plancreator.steps.internal.FmeFlagSetDynamicConfigurationsInfo")
public class FmeFlagSetDynamicConfigurationsInfo implements PMSStepInfo, Visitable {
  @ApiModelProperty(required = true, value = "Environment identifier", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> environment;

  @ApiModelProperty(required = true, value = "Feature flag name", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> flagName;

  @ApiModelProperty(required = true, value = "List of treatment configurations")
  @NotNull
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<List<TreatmentDynamicConfiguration>> treatments;

  @ApiModelProperty(value = "Input variables for the step") List<NGVariable> inputVariables;

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.FME_FLAG_SET_DYNAMIC_CONFIGURATIONS_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return FmeFlagSetDynamicConfigurationsParameters.builder()
        .environment(environment)
        .flagName(flagName)
        .treatments(treatments)
        .inputVariables(NGVariablesUtils.getMapOfVariables(inputVariables, 0L))
        .build();
  }
}
