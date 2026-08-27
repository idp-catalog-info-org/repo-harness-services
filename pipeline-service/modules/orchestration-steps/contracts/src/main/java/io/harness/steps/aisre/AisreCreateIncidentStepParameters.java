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
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

/**
 * Parameters for the AI SRE Create Incident step.
 */
@OwnedBy(HarnessTeam.CHAOS)
@Value
@Builder
@TypeAlias("aisreCreateIncidentStepParameters")
@RecasterAlias("io.harness.steps.aisre.AisreCreateIncidentStepParameters")
public class AisreCreateIncidentStepParameters implements SpecParameters {
  String type = "AISRE_CreateIncident";

  @ApiModelProperty(required = true, value = "Incident title", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> title;

  @ApiModelProperty(value = "Harness organization identifier where the incident should be created.",
      dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> orgIdentifier;

  @ApiModelProperty(value = "Harness project identifier where the incident should be created.",
      dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> projectIdentifier;

  @ApiModelProperty(
      required = true, value = "Incident severity (e.g. SEV1-SEV4)", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> severity;

  @ApiModelProperty(value = "Incident status option id or label (e.g. new, investigating)",
      dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> status;

  @ApiModelProperty(required = true, value = "Harness NG service identifier of the impacted service",
      dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> service;

  @ApiModelProperty(value = "Harness NG environment identifier", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> environment;

  @ApiModelProperty(value = "Incident description", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> description;

  // Selects the AI SRE incident template (maps to transposit templateShortId). Defaults to "INC".
  @ApiModelProperty(value = "AI SRE incident type / template short id (defaults to INC)",
      dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> incidentType;

  @ApiModelProperty(value = "Incident labels", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Nullable
  ParameterField<List<String>> labels;

  @ApiModelProperty(value = "When true, attach pipeline execution context (execution URL, stage, user, artifact)"
          + " to the incident. Defaults to true.",
      dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @Nullable
  ParameterField<Boolean> attachPipelineContext;

  @ApiModelProperty(value = "When true, page the on-call owner of the impacted service after the incident is"
          + " created. Defaults to true (opt-out).",
      dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @Nullable
  ParameterField<Boolean> pageOnCall;

  @ApiModelProperty(value = "Harness user id of the incident commander", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> commanderHarnessUserId;

  @ApiModelProperty(
      value = "Additional impacted Harness NG service identifiers", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Nullable
  ParameterField<List<String>> additionalImpactedServices;

  // Dynamic incident-type fields keyed by transposit field id (populated by the incident-type schema
  // in the UI). Forwarded to transposit as customFields.
  @ApiModelProperty(
      value = "Incident-type specific fields keyed by field id", dataType = SwaggerConstants.STRING_MAP_CLASSPATH)
  @Nullable
  ParameterField<Map<String, String>> fields;
}
