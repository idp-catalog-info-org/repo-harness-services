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
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.FME)
@Value
@Builder
@TypeAlias("FmeFlagSetTreatmentsParameters")
@RecasterAlias("io.harness.steps.fme.FmeFlagSetTreatmentsParameters")
public class FmeFlagSetTreatmentsParameters implements SpecParameters {
  String type = "FmeFlagSetTreatments";

  @ApiModelProperty(required = true, value = "Environment identifier", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> environment;

  @ApiModelProperty(required = true, value = "Name of the feature flag", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> flagName;

  @ApiModelProperty(required = true, value = "Default treatment name", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> defaultTreatment;

  @ApiModelProperty(required = true, value = "Baseline treatment name", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> baselineTreatment;

  @ApiModelProperty(required = true, value = "List of treatments", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @NotNull
  ParameterField<List<TreatmentConfiguration>> treatments;
}
