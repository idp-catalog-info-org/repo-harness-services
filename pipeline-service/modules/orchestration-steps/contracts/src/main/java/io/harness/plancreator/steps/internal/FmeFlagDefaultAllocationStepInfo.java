/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

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
import io.harness.steps.fme.Allocation;
import io.harness.steps.fme.FmeFlagDefaultAllocationStepParameters;
import io.harness.yaml.schema.YamlSchemaTypes;

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

@OwnedBy(HarnessTeam.FME)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonTypeName(StepSpecTypeConstants.FME_FLAG_DEFAULT_ALLOCATION)
@TypeAlias("fmeFlagDefaultAllocationStepInfo")
@RecasterAlias("io.harness.plancreator.steps.internal.FmeFlagDefaultAllocationStepInfo")
public class FmeFlagDefaultAllocationStepInfo implements PMSStepInfo {
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> flagName;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> environment;
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.FME_ALLOCATION_LIST_CLASSPATH)
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<List<Allocation>> allocation;
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Builder
  @ConstructorProperties({"flagName", "environment", "allocation"})
  public FmeFlagDefaultAllocationStepInfo(ParameterField<String> flagName, ParameterField<String> environment,
      ParameterField<List<Allocation>> allocation) {
    this.flagName = flagName;
    this.environment = environment;
    this.allocation = allocation;
  }

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.FME_FLAG_DEFAULT_ALLOCATION_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return FmeFlagDefaultAllocationStepParameters.builder()
        .flagName(flagName)
        .environment(environment)
        .allocation(allocation)
        .build();
  }
}
