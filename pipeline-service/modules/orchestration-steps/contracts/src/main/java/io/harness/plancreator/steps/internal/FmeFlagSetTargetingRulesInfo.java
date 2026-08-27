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
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeFlagSetTargetingRulesParameters;
import io.harness.steps.fme.TargetRules;
import io.harness.walktree.visitor.helper.Visitable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.FME)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonTypeName(StepSpecTypeConstants.FME_FLAG_SET_TARGETING_RULES)
@TypeAlias("fmeFlagSetTargetingRulesInfo")
@RecasterAlias("io.harness.plancreator.steps.internal.FmeFlagSetTargetingRulesInfo")
public class FmeFlagSetTargetingRulesInfo implements PMSStepInfo, Visitable {
  @ApiModelProperty(required = true, value = "Name of the feature flag", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> flagName;

  @ApiModelProperty(required = true, value = "Environment name or ID", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> environment;

  @ApiModelProperty(required = true, value = "Target rules configuration containing conditions and allocations")
  @NotNull
  ParameterField<List<TargetRules>> targetingRules;

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.FME_FLAG_SET_TARGETING_RULES_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return FmeFlagSetTargetingRulesParameters.builder()
        .flagName(flagName)
        .environment(environment)
        .targetingRules(targetingRules)
        .build();
  }
}
