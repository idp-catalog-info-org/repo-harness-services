/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtimeEmptyStringAllowed;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeFlagCreateParameters;
import io.harness.steps.fme.TreatmentConfiguration;
import io.harness.walktree.visitor.helper.Visitable;
import io.harness.yaml.schema.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.FME)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonTypeName(StepSpecTypeConstants.FME_FLAG_CREATE)
@TypeAlias("fmeFlagCreateInfo")
@RecasterAlias("io.harness.plancreator.steps.internal.FmeFlagCreateInfo")
public class FmeFlagCreateInfo implements PMSStepInfo, Visitable {
  @ApiModelProperty(required = true, value = "Name of the feature flag", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
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
  @YamlSchemaTypes(value = {runtimeEmptyStringAllowed, expression})
  ParameterField<List<String>> tags;

  @ApiModelProperty(value = "Owners of the feature flag", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Nullable
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<List<String>> owners;

  @ApiModelProperty(
      value = "List of treatments for the feature flag", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Nullable
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<List<TreatmentConfiguration>> treatments;

  @ApiModelProperty(value = "Default treatment name", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> defaultTreatment;

  @ApiModelProperty(value = "Baseline treatment name", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> baselineTreatment;

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.FME_FLAG_CREATE_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return FmeFlagCreateParameters.builder()
        .name(name)
        .trafficType(trafficType)
        .description(description)
        .tags(tags)
        .owners(owners)
        .treatments(treatments)
        .defaultTreatment(defaultTreatment)
        .baselineTreatment(baselineTreatment)
        .build();
  }
}
