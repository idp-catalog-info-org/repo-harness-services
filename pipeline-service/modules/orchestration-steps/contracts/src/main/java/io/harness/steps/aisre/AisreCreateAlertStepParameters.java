/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.aisre;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import io.swagger.annotations.ApiModelProperty;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

/**
 * Parameters for the AI SRE Create Alert step.
 */
@OwnedBy(HarnessTeam.CHAOS)
@Value
@Builder
@TypeAlias("aisreCreateAlertStepParameters")
@RecasterAlias("io.harness.steps.aisre.AisreCreateAlertStepParameters")
public class AisreCreateAlertStepParameters implements SpecParameters {
  String type = "AISRE_CreateAlert";

  @ApiModelProperty(value = "Existing alert pretty id (e.g. ALERT-123). When set, updates that alert.",
      dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> alertId;

  @ApiModelProperty(value = "Alert title. Required when creating; optional when alertId is set.",
      dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> title;

  @ApiModelProperty(value = "Harness organization identifier where the alert should be created.",
      dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> orgIdentifier;

  @ApiModelProperty(value = "Harness project identifier where the alert should be created.",
      dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> projectIdentifier;

  // triggered | acknowledged | resolved | dismissed
  @ApiModelProperty(
      value = "Alert status (triggered/acknowledged/resolved/dismissed)", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> status;

  @ApiModelProperty(
      value = "Alert priority (p1_critical/p2_error/p3_warning/p4_info)", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> priority;

  @ApiModelProperty(
      value = "Harness NG service identifier of the impacted service", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> service;

  @ApiModelProperty(value = "Harness NG environment identifier", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> environment;

  @ApiModelProperty(value = "Alert description / details", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> description;
}
