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
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.FME)
@Value
@Builder
@TypeAlias("fmeFlagsetCreateParameters")
@RecasterAlias("io.harness.steps.fme.FmeFlagsetCreateParameters")
public class FmeFlagsetCreateParameters implements SpecParameters {
  String type = "FmeFlagsetCreate";

  @ApiModelProperty(
      required = true, value = "Name of the flag set to create", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> name;

  @ApiModelProperty(value = "Flag set description", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> description;
}
