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
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeSegmentUpdateParameters;
import io.harness.walktree.visitor.helper.Visitable;
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
@JsonTypeName(StepSpecTypeConstants.FME_SEGMENT_UPDATE)
@TypeAlias("fmeSegmentUpdateInfo")
@RecasterAlias("io.harness.plancreator.steps.internal.FmeSegmentUpdateInfo")
public class FmeSegmentUpdateInfo implements PMSStepInfo, Visitable {
  @ApiModelProperty(required = true, value = "Segment name", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<String> name;

  @ApiModelProperty(value = "Description to set (optional patch)")
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<String> description;

  @ApiModelProperty(value = "Owners to set (optional patch)")
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<List<String>> owners;

  @ApiModelProperty(value = "Tags to set (optional patch)")
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<List<String>> tags;

  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Builder
  @ConstructorProperties({"name", "description", "owners", "tags"})
  public FmeSegmentUpdateInfo(ParameterField<String> name, ParameterField<String> description,
      ParameterField<List<String>> owners, ParameterField<List<String>> tags) {
    this.name = name;
    this.description = description;
    this.owners = owners;
    this.tags = tags;
  }

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.FME_SEGMENT_UPDATE_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return FmeSegmentUpdateParameters.builder().name(name).description(description).owners(owners).tags(tags).build();
  }
}
