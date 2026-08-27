/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.FME)
@Value
@Builder
@TypeAlias("fmeFlagDefinitionInstructionsStepParameters")
@RecasterAlias("io.harness.steps.fme.FmeFlagDefinitionInstructionsStepParameters")
public class FmeFlagDefinitionInstructionsStepParameters implements SpecParameters {
  @Builder.Default String type = "FmeFlagDefinitionInstructions";

  @ApiModelProperty(required = true, value = "Name of the feature flag", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> flagName;

  @ApiModelProperty(required = true, value = "Environment identifier", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> environment;

  @Nullable
  @ApiModelProperty(value = "Optional default definition to create if none exists in the target environment. Ignored "
          + "if definition already exists.")
  DefaultDefinitionConfig defaultDefinition;

  @ApiModelProperty(required = true, value = "Ordered list of flag definition instructions to apply")
  @NotNull
  ParameterField<List<FmeDefinitionInstruction>> instructions;
}
