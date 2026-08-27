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
import io.harness.steps.fme.FmeFlagUpdateParameters;
import io.harness.walktree.visitor.helper.Visitable;
import io.harness.yaml.schema.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.FME)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonTypeName(StepSpecTypeConstants.FME_FLAG_UPDATE)
@TypeAlias("fmeFlagUpdateInfo")
@RecasterAlias("io.harness.plancreator.steps.internal.FmeFlagUpdateInfo")
public class FmeFlagUpdateInfo implements PMSStepInfo, Visitable {
  @ApiModelProperty(required = true, value = "Name of the feature flag", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> name;

  @ApiModelProperty(value = "Description of the feature flag", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> description;

  @ApiModelProperty(value = "Rollout status of the feature flag", dataType = SwaggerConstants.STRING_CLASSPATH)
  @Nullable
  ParameterField<String> rolloutStatus;

  @ApiModelProperty(value = "Tags for the feature flag", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Nullable
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<List<String>> tags;

  @ApiModelProperty(value = "Owners of the feature flag", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Nullable
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<List<String>> owners;

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.FME_FLAG_UPDATE_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return FmeFlagUpdateParameters.builder()
        .name(name)
        .description(description)
        .rolloutStatus(rolloutStatus)
        .owners(owners)
        .tags(tags)
        .build();
  }
}
