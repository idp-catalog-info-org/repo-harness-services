/*
 * Copyright 2025 Harness Inc. All rights reserved.
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
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeSegmentCreateParameters;
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
@JsonTypeName(StepSpecTypeConstants.FME_SEGMENT_CREATE)
@TypeAlias("fmeSegmentCreateInfo")
@RecasterAlias("io.harness.plancreator.steps.internal.FmeSegmentCreateInfo")
public class FmeSegmentCreateInfo implements PMSStepInfo, Visitable {
  @ApiModelProperty(required = true, value = "Name of the segment", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  ParameterField<String> name;

  @ApiModelProperty(
      required = true, value = "Type of traffic for the segment", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> trafficType;

  @ApiModelProperty(value = "Owners of the segment (optional - defaults to admin team if not provided)",
      dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Nullable
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<List<String>> owners;

  @ApiModelProperty(required = true, value = "Type of segment: Standard, Large, or RuleBased",
      dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> segmentType;

  @ApiModelProperty(value = "Description of the segment", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> description;

  @ApiModelProperty(value = "Tags for the segment", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Nullable
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<List<String>> tags;

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.FME_SEGMENT_CREATE_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return FmeSegmentCreateParameters.builder()
        .name(name)
        .trafficType(trafficType)
        .owners(owners)
        .segmentType(segmentType)
        .description(description)
        .tags(tags)
        .build();
  }
}
