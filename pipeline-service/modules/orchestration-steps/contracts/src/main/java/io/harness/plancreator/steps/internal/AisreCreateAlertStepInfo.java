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
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.aisre.AisreCreateAlertStepParameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

/**
 * Step info for the AI SRE Create Alert step.
 */
@OwnedBy(HarnessTeam.CHAOS)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonTypeName(StepSpecTypeConstants.AISRE_CREATE_ALERT)
@TypeAlias("aisreCreateAlertStepInfo")
@RecasterAlias("io.harness.plancreator.steps.internal.AisreCreateAlertStepInfo")
public class AisreCreateAlertStepInfo implements PMSStepInfo {
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> title;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> alertId;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> orgIdentifier;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> projectIdentifier;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> status;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> priority;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> service;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> environment;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> description;

  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Builder
  @ConstructorProperties({"title", "alertId", "orgIdentifier", "projectIdentifier", "status", "priority", "service",
      "environment", "description"})
  public AisreCreateAlertStepInfo(ParameterField<String> title, ParameterField<String> alertId,
      ParameterField<String> orgIdentifier, ParameterField<String> projectIdentifier, ParameterField<String> status,
      ParameterField<String> priority, ParameterField<String> service, ParameterField<String> environment,
      ParameterField<String> description) {
    this.title = title;
    this.alertId = alertId;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.status = status;
    this.priority = priority;
    this.service = service;
    this.environment = environment;
    this.description = description;
  }

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.AISRE_CREATE_ALERT_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return AisreCreateAlertStepParameters.builder()
        .title(title)
        .alertId(alertId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .status(status)
        .priority(priority)
        .service(service)
        .environment(environment)
        .description(description)
        .build();
  }
}
