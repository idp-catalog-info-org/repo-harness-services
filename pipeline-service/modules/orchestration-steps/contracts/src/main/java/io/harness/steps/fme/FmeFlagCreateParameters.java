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
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.FME)
@Value
@Builder
@TypeAlias("fmeFlagCreateParameters")
@RecasterAlias("io.harness.steps.fme.FmeFlagCreateParameters")
public class FmeFlagCreateParameters implements SpecParameters {
  String type = "FmeFlagCreate";

  @ApiModelProperty(required = true, value = "Name of the feature flag", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> name;

  @ApiModelProperty(
      required = true, value = "Type of traffic for the feature flag", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> trafficType;

  @ApiModelProperty(value = "Description of the feature flag", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> description;

  @ApiModelProperty(value = "Tags for the feature flag", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Nullable
  ParameterField<List<String>> tags;

  @ApiModelProperty(value = "Owners of the feature flag", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Nullable
  ParameterField<List<String>> owners;

  @ApiModelProperty(
      value = "List of treatments for the feature flag", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Nullable
  ParameterField<List<TreatmentConfiguration>> treatments;

  @ApiModelProperty(value = "Default treatment name", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> defaultTreatment;

  @ApiModelProperty(value = "Baseline treatment name", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> baselineTreatment;
}
