/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.DefaultDefinitionConfig;
import io.harness.steps.fme.FmeDefinitionInstruction;
import io.harness.steps.fme.FmeFlagDefinitionInstructionsStepParameters;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.FME)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonTypeName(StepSpecTypeConstants.FME_FLAG_DEFINITION_INSTRUCTIONS)
@TypeAlias("fmeFlagDefinitionInstructionsStepInfo")
@RecasterAlias("io.harness.plancreator.steps.internal.FmeFlagDefinitionInstructionsStepInfo")
public class FmeFlagDefinitionInstructionsStepInfo implements PMSStepInfo {
  @ApiModelProperty(required = true, value = "Name of the feature flag", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> flagName;

  @ApiModelProperty(required = true, value = "Environment identifier", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> environment;

  @Nullable
  @ApiModelProperty(value = "Optional default definition to create if none exists in the target environment")
  DefaultDefinitionConfig defaultDefinition;

  @ApiModelProperty(required = true, value = "Ordered list of flag definition instructions to apply")
  @NotNull
  ParameterField<List<FmeDefinitionInstruction>> instructions;

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.FME_FLAG_DEFINITION_INSTRUCTIONS_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return FmeFlagDefinitionInstructionsStepParameters.builder()
        .flagName(flagName)
        .environment(environment)
        .defaultDefinition(defaultDefinition)
        .instructions(instructions)
        .build();
  }
}
