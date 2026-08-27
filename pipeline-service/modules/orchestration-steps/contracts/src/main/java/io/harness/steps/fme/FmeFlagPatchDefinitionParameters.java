/*
 * Copyright 2025 Harness Inc. All rights reserved.
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
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.FME)
@Value
@Builder
@TypeAlias("FmeFlagPatchDefinitionParameters")
@RecasterAlias("io.harness.steps.fme.FmeFlagPatchDefinitionParameters")
public class FmeFlagPatchDefinitionParameters implements SpecParameters {
  String type = "FmeFlagPatchDefinition";

  @ApiModelProperty(required = true, value = "Name of the feature flag", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> flagName;

  @ApiModelProperty(required = true, value = "Environment identifier", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> environment;

  @ApiModelProperty(
      required = true, value = "Operations to patch the flag definition", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> operations;

  @ApiModelProperty(value = "Input variables for the step") Map<String, Object> inputVariables;
}
