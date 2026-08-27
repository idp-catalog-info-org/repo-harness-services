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
import io.harness.steps.aisre.AisreCreateIncidentStepParameters;
import io.harness.steps.aisre.AisreField;
import io.harness.steps.aisre.AisreStepUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

/**
 * Step info for the AI SRE Create Incident step.
 */
@OwnedBy(HarnessTeam.CHAOS)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonTypeName(StepSpecTypeConstants.AISRE_CREATE_INCIDENT)
@TypeAlias("aisreCreateIncidentStepInfo")
@RecasterAlias("io.harness.plancreator.steps.internal.AisreCreateIncidentStepInfo")
public class AisreCreateIncidentStepInfo implements PMSStepInfo {
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> title;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> orgIdentifier;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> projectIdentifier;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> severity;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> status;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> service;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> environment;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> description;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> incidentType;
  @ApiModelProperty(value = "Incident labels", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<String>> labels;
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH) ParameterField<Boolean> attachPipelineContext;
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH) ParameterField<Boolean> pageOnCall;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> commanderHarnessUserId;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<String>> additionalImpactedServices;
  List<AisreField> fields;

  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Builder
  @ConstructorProperties({"title", "orgIdentifier", "projectIdentifier", "severity", "status", "service", "environment",
      "description", "incidentType", "labels", "attachPipelineContext", "pageOnCall", "commanderHarnessUserId",
      "additionalImpactedServices", "fields"})
  public AisreCreateIncidentStepInfo(ParameterField<String> title, ParameterField<String> orgIdentifier,
      ParameterField<String> projectIdentifier, ParameterField<String> severity, ParameterField<String> status,
      ParameterField<String> service, ParameterField<String> environment, ParameterField<String> description,
      ParameterField<String> incidentType, ParameterField<List<String>> labels,
      ParameterField<Boolean> attachPipelineContext, ParameterField<Boolean> pageOnCall,
      ParameterField<String> commanderHarnessUserId, ParameterField<List<String>> additionalImpactedServices,
      List<AisreField> fields) {
    this.title = title;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.severity = severity;
    this.status = status;
    this.service = service;
    this.environment = environment;
    this.description = description;
    this.incidentType = incidentType;
    this.labels = labels;
    this.attachPipelineContext = attachPipelineContext;
    this.pageOnCall = pageOnCall;
    this.commanderHarnessUserId = commanderHarnessUserId;
    this.additionalImpactedServices = additionalImpactedServices;
    this.fields = fields;
  }

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.AISRE_CREATE_INCIDENT_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return AisreCreateIncidentStepParameters.builder()
        .title(title)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .severity(severity)
        .status(status)
        .service(service)
        .environment(environment)
        .description(description)
        .incidentType(incidentType)
        .labels(labels)
        .attachPipelineContext(attachPipelineContext)
        .pageOnCall(pageOnCall)
        .commanderHarnessUserId(commanderHarnessUserId)
        .additionalImpactedServices(additionalImpactedServices)
        .fields(AisreStepUtils.processFieldsList(fields))
        .build();
  }
}
